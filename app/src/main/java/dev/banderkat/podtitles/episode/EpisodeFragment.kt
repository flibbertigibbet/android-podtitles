package dev.banderkat.podtitles.episode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.C.SELECTION_FLAG_AUTOSELECT
import androidx.media3.common.C.TRACK_TYPE_DEFAULT
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.snackbar.Snackbar
import com.google.common.collect.ImmutableList
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentEpisodeBinding
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.player.DOWNLOAD_FAILED_ACTION
import dev.banderkat.podtitles.player.DOWNLOAD_FINISHED_ACTION
import dev.banderkat.podtitles.player.PodTitlesDownloadService
import dev.banderkat.podtitles.utils.Utils
import java.io.File

class EpisodeFragment : Fragment() {
    companion object {
        const val TAG = "EpisodeFragment"
        const val FRACTIONAL_TEXT_SIZE = 0.1f
        const val INITIAL_PROGRESS_STEPS = 2 // one for download, one for the merge task
        const val INITIAL_PROGRESS = 5
    }

    private var _binding: FragmentEpisodeBinding? = null
    private val binding get() = _binding!!

    private val app: PodTitlesApplication by lazy {
        requireActivity().application as PodTitlesApplication
    }

    private val args: EpisodeFragmentArgs by navArgs()
    private lateinit var episode: PodEpisode
    private lateinit var feed: PodFeed
    private lateinit var workManager: WorkManager
    private lateinit var viewModel: EpisodeViewModel

    private var episodeUrl = ""
    private var player: ExoPlayer? = null
    private var episodeDetailsExpanded = false
    private var currentItem = 0
    private var playbackPosition = 0L
    private var loadingProgressSteps = INITIAL_PROGRESS_STEPS

    private val workObserver = Observer<List<WorkInfo>> { workInfo ->
        val subtitles = viewModel.subtitlePath.value ?: return@Observer
        val isRunning = workInfo.find { info ->
            val forThisEpisode = info.tags.find { it.contains(subtitles) } != null
            forThisEpisode && (info.state == WorkInfo.State.ENQUEUED
                    || info.state == WorkInfo.State.BLOCKED
                    || info.state == WorkInfo.State.RUNNING)
        } != null
        if (isRunning) showProgress() else showDownloadTranscribeButton()
    }

    private val downloadCompleteBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "download complete broadcast received")
            if (activity != null) handleDownloadComplete()
        }
    }

    private val downloadFailedBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.e(TAG, "download failure broadcast received")
            cancel(R.string.download_failed_message)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEpisodeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        feed = args.feed
        viewModel = ViewModelProvider(this)[EpisodeViewModel::class.java]
        workManager = WorkManager.getInstance(requireContext())

        requireActivity().registerReceiver(
            downloadCompleteBroadcast,
            IntentFilter(DOWNLOAD_FINISHED_ACTION)
        )

        requireActivity().registerReceiver(
            downloadFailedBroadcast,
            IntentFilter(DOWNLOAD_FAILED_ACTION)
        )

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "view created!")

        viewModel.getEpisode(feed.url, args.episodeGuid).observe(viewLifecycleOwner) {
            if (it != null) {
                episode = it
                setEpisodeFields()
                if (episodeUrl.isEmpty()) checkEpisodeStatus(episode.url)
                episodeUrl = episode.url // flag to only check status once
            }
        }

        viewModel.isCancelling.observe(viewLifecycleOwner) {
            binding.episodeDownloadButton.isEnabled = !it
        }
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setEpisodeFields() {
        binding.episodeDetailsCard.apply {
            episodeCardTitle.text = episode.title
            episodeCardPubDate.text = Utils.getFormattedDate(episode.pubDate)
            episodeCardDuration.text = Utils.getFormattedDuration(episode.duration)
            Utils.loadLogo(getDefaultImage(), requireContext(), episodeCardImage)
        }

        binding.episodeCardDetailsExpandFab.setOnClickListener {
            if (episodeDetailsExpanded) collapseCardDetails() else expandCardDetails()
            episodeDetailsExpanded = !episodeDetailsExpanded
        }

        binding.episodeDownloadButton.setOnClickListener { promptVoskModel() }
        binding.episodeDownloadCancelButton.setOnClickListener {
            cancel(R.string.download_cancelled_message)
        }
    }

    private fun expandCardDetails() {
        binding.episodeCardDetailsExpandFab.apply {
            setImageResource(android.R.drawable.arrow_up_float)
            contentDescription = getString(R.string.card_details_collapse_fab_description)
        }

        binding.episodeDetailsCard.apply {
            if (episode.size > 0) {
                episodeCardSize.text = Formatter.formatShortFileSize(
                    requireContext(),
                    episode.size.toLong()
                )
            }
            if (episode.episodeType.isNotBlank()) {
                episodeCardType.text = episode.episodeType
            }
            if (episode.category.isNotBlank()) {
                episodeCardCategory.text = episode.category
            }
            if (episode.season > 0 && episode.episode > 0) {
                episodeCardSeasonEpisode.text = getString(
                    R.string.serial_season_episode,
                    episode.season,
                    episode.episode
                )
            }
            if (episode.link.isNotBlank()) {
                episodeCardLink.visibility = View.VISIBLE
                episodeCardLink.setOnClickListener {
                    val webIntent = Intent(Intent.ACTION_VIEW)
                    webIntent.data = Uri.parse(episode.link)
                    startActivity(webIntent)
                }
            }
            if (episode.description.isNotBlank()) {
                episodeCardDescription.text = Html.fromHtml(
                    episode.description,
                    Html.FROM_HTML_MODE_LEGACY
                )
                episodeCardDescription.visibility = View.VISIBLE
            }
        }
    }

    private fun cancel(cancelMessageId: Int) {
        if (activity == null) {
            Log.w(TAG, "Not attached to an activity; not showing error")
            return
        }

        if (!viewModel.subtitlePath.value.isNullOrEmpty()) viewModel.cancelTranscription()

        binding.episodeProgress.visibility = View.GONE
        binding.episodeDownloadCancelButton.visibility = View.GONE
        binding.exoPlayer.visibility = View.GONE
        binding.episodeDownloadButton.visibility = View.VISIBLE

        Snackbar.make(
            requireContext(),
            binding.root,
            getString(cancelMessageId),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun collapseCardDetails() {
        binding.episodeCardDetailsExpandFab.apply {
            setImageResource(android.R.drawable.arrow_down_float)
            contentDescription = getString(R.string.card_details_expand_fab_description)
        }

        binding.episodeDetailsCard.apply {
            episodeCardSize.visibility = View.GONE
            episodeCardType.visibility = View.GONE
            episodeCardCategory.visibility = View.GONE
            episodeCardSeasonEpisode.visibility = View.GONE
            episodeCardLink.visibility = View.GONE
            episodeCardDescription.visibility = View.GONE
        }
    }

    private fun getDefaultImage(): String {
        return if (episode.image.isNotBlank()) {
            episode.image
        } else if (feed.image.isNotBlank()) {
            feed.image
        } else {
            ""
        }
    }

    private fun setDefaultArtwork() {
        val defaultImage = getDefaultImage()
        if (defaultImage.isNotBlank()) {
            Glide.with(this)
                .asBitmap()
                .load(defaultImage)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        binding.exoPlayer.defaultArtwork = BitmapDrawable(resources, resource)
                        binding.exoPlayer.useArtwork = true
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }
    }

    private fun showDownloadTranscribeButton() {
        workManager
            .getWorkInfosForUniqueWorkLiveData(TRANSCRIBE_JOB_CHAIN_TAG)
            .removeObserver(workObserver)

        binding.exoPlayer.visibility = View.GONE
        binding.episodeProgress.visibility = View.GONE
        binding.episodeDownloadCancelButton.visibility = View.GONE
        binding.episodeDownloadButton.visibility = View.VISIBLE
    }

    private fun showPlayer() {
        Log.d(TAG, "Show player")
        if (binding.exoPlayer.visibility == View.VISIBLE) {
            Log.w(TAG, "Already showing player; ignoring")
            return
        }

        val subtitleFilePath = Utils.getSubtitles(
            requireContext(),
            app.downloadCache.getCachedSpans(episode.url).first().file!!.absolutePath
        )

        viewModel.setSubtitlePath(subtitleFilePath ?: "")

        if (subtitleFilePath.isNullOrBlank()) {
            Log.w(TAG, "Subtitle file not found. Not showing player")
            showProgress()
            return
        }

        binding.episodeDownloadButton.visibility = View.GONE
        binding.episodeDownloadCancelButton.visibility = View.GONE
        binding.episodeProgress.visibility = View.GONE
        binding.exoPlayer.visibility = View.VISIBLE
        initializePlayer()
    }

    private fun showProgress() {
        workManager
            .getWorkInfosForUniqueWorkLiveData(TRANSCRIBE_JOB_CHAIN_TAG)
            .removeObserver(workObserver)

        binding.exoPlayer.visibility = View.GONE
        binding.episodeDownloadButton.visibility = View.GONE
        binding.episodeProgress.visibility = View.VISIBLE
        binding.episodeDownloadCancelButton.visibility = View.VISIBLE

        val spans = app.downloadCache.getCachedSpans(episode.url)
        loadingProgressSteps += spans.size
        binding.episodeProgress.progress = (100 / loadingProgressSteps)

        setUpObservers()
    }

    private fun handleDownloadComplete() {
        try {
            Log.d(TAG, "handle completed download")
            viewModel.transcriptionModel.observe(viewLifecycleOwner) { transcriptModel ->
                if (transcriptModel.isNullOrEmpty()) {
                    Log.w(TAG, "No transcript model selected")
                    return@observe
                }
                val subtitleFilePath = Utils.getSubtitlePathForCachePath(
                    app.downloadCache.getCachedSpans(episode.url).first().file!!.absolutePath
                )

                viewModel.setSubtitlePath(subtitleFilePath)

                val voskModelDirectory = Utils.getVoskModelDirectory(app.applicationContext)
                val voskModelParentPath =
                    File(voskModelDirectory, transcriptModel).absolutePath
                val voskModelPath = File(voskModelParentPath, transcriptModel).canonicalPath
                Log.d(TAG, "Transcribe using model path $voskModelPath")
                viewModel.onDownloadCompleted(episode.url, voskModelPath)
                setUpObservers()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to handle audio download", ex)
            cancel(R.string.download_failed_message)
        }
    }

    private fun checkEpisodeStatus(episodeUrl: String) {
        Log.d(TAG, "check episode status")
        val spans = app.downloadCache.getCachedSpans(episodeUrl)
        val needsDownload = spans.isEmpty() || spans.find { !it.isCached } != null

        if (needsDownload) {
            showDownloadTranscribeButton()
            return
        }

        // Check if this episode has already been transcribed
        val subtitleFilePath =
            Utils.getSubtitles(requireContext(), spans.first().file!!.absolutePath)
        val needsTranscription = subtitleFilePath.isNullOrBlank()

        if (!needsTranscription) {
            Log.d(TAG, "episode already transcribed to $subtitleFilePath; show player")
            showPlayer()
            return
        }

        // check for workers tagged with expected subtitle file path
        val subtitles = spans.first().file?.absolutePath?.let {
            Utils.getSubtitlePathForCachePath(it)
        }
        viewModel.setSubtitlePath(subtitles ?: "")

        if (subtitles.isNullOrEmpty()) {
            Log.w(TAG, "Subtitle file path not found to use to check worker status")
            showDownloadTranscribeButton()
            return
        }

        // It is necessary to get the live data status to accurately determine
        // if one or more workers for this episode are running or done.
        workManager.getWorkInfosForUniqueWorkLiveData(TRANSCRIBE_JOB_CHAIN_TAG)
            .observe(viewLifecycleOwner, workObserver)
    }

    private fun promptVoskModel() {
        val builder: AlertDialog.Builder? = activity?.let {
            AlertDialog.Builder(it)
        }

        val models = Utils.getDownloadedVoskModels(requireContext()).toTypedArray()
        if (models.isNotEmpty()) {
            builder
                ?.setTitle(R.string.pick_transcription_model_title)
                ?.setItems(models) { _, which ->
                    viewModel.setTranscriptionModel(models[which])
                    sendDownloadRequest()
                }
        } else {
            builder
                ?.setTitle(R.string.no_transcribe_models_found)
                ?.setMessage(R.string.no_transcribe_models_found_message)
        }
        builder
            ?.setPositiveButton(R.string.go_to_download_vosk_model) { _, _ ->
                findNavController().navigate(
                    EpisodeFragmentDirections.actionEpisodeFragmentToManageVoskModelsFragment()
                )
            }
            ?.setNegativeButton(android.R.string.cancel) { _, _ ->
                /* no-op */
            }
        val dialog: AlertDialog? = builder?.create()
        dialog?.show()
    }

    private fun sendDownloadRequest() {
        binding.episodeDownloadButton.visibility = View.GONE
        binding.exoPlayer.visibility = View.GONE
        binding.episodeProgress.visibility = View.VISIBLE
        binding.episodeDownloadCancelButton.visibility = View.VISIBLE

        loadingProgressSteps = INITIAL_PROGRESS_STEPS
        binding.episodeProgress.isIndeterminate = false
        binding.episodeProgress.progress = INITIAL_PROGRESS

        val downloadRequest = DownloadRequest
            .Builder(episode.url, Uri.parse(episode.url)).build()

        viewModel.setMediaItem(downloadRequest.toMediaItem())

        DownloadService.sendAddDownload(
            requireContext(),
            PodTitlesDownloadService::class.java,
            downloadRequest,
            false
        )
    }

    private fun initializePlayer() {
        if (activity == null) {
            Log.w(TAG, "Not attached to an activity; not initializing player")
            return
        }
        val subtitles = viewModel.subtitlePath.value
        if (subtitles.isNullOrEmpty()) {
            Log.w(TAG, "Subtitles path not set; not initializing player")
            return
        }
        val cacheDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(app.downloadCache)
            .setUpstreamDataSourceFactory(app.dataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Disable writing.

        val subtitleUri = Uri.fromFile(File(subtitles))

        // use URI from download if just completed, or parse it from the episode URL
        val mediaUri = viewModel.mediaItem.value
            ?.localConfiguration?.uri ?: Uri.parse(episode.url)

        val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(feed.language) // TODO: set based on model
            .setSelectionFlags(SELECTION_FLAG_AUTOSELECT)
            .build()

        val subbedMedia = MediaItem
            .Builder()
            .setUri(mediaUri)
            .setSubtitleConfigurations(ImmutableList.of(subtitle))
            .build()

        ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(requireContext())
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .build()
            .also { exoPlayer ->
                binding.exoPlayer.player = exoPlayer
                player = exoPlayer

                exoPlayer.trackSelector?.parameters = TrackSelectionParameters
                    .getDefaults(requireContext())
                    .buildUpon()
                    // TODO: set based on model
                    .setPreferredTextLanguage(feed.language)
                    // disable default track to prevent potentially doubled subtitles
                    .setTrackTypeDisabled(TRACK_TYPE_DEFAULT, true)
                    .build()

                exoPlayer.setMediaItem(subbedMedia)
                exoPlayer.seekTo(currentItem, playbackPosition)
                exoPlayer.prepare()
            }

        setDefaultArtwork()
        binding.exoPlayer.subtitleView?.setFractionalTextSize(FRACTIONAL_TEXT_SIZE)
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            currentItem = exoPlayer.currentMediaItemIndex
            exoPlayer.release()
        }
        player = null
    }

    private fun setUpObservers() {
        if (activity == null) {
            Log.w(TAG, "Not attached to an activity; not setting up observers")
            return
        }
        val subtitleFilePath = viewModel.subtitlePath.value
        if (subtitleFilePath.isNullOrBlank()) {
            Log.w(TAG, "Subtitle path not set; not setting up observers")
            return
        }
        workManager.getWorkInfosByTagLiveData("${TRANSCRIBE_JOB_TAG}_$subtitleFilePath")
            .observe(viewLifecycleOwner) { workInfo ->
                val completed = workInfo.count {
                    it.state == WorkInfo.State.SUCCEEDED
                } + 1
                binding.episodeProgress.progress = (100 / loadingProgressSteps) * completed
            }

        workManager
            .getWorkInfosByTagLiveData("${TRANSCRIPT_MERGE_JOB_TAG}_$subtitleFilePath")
            .observe(viewLifecycleOwner) { workInfo ->
                val isFinished = workInfo.find {
                    it.state == WorkInfo.State.SUCCEEDED
                } != null

                if (isFinished) {
                    Log.d(TAG, "Transcription finished; going to show player")
                    showPlayer()
                    player?.playWhenReady = true
                }
            }
    }
}
