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
import androidx.fragment.app.Fragment
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
import androidx.navigation.fragment.navArgs
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.common.collect.ImmutableList
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.databinding.FragmentEpisodeBinding
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
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

    private var player: ExoPlayer? = null
    private var episodeDetailsExpanded = false
    private var currentItem = 0
    private var playbackPosition = 0L
    private var loadingProgressSteps = INITIAL_PROGRESS_STEPS

    private var mediaItem: MediaItem? = null
    private var subtitleFilePath: String? = null

    private val downloadCompleteBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("Home", "download complete broadcast received")
            handleDownloadComplete()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEpisodeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        episode = args.episode
        feed = args.feed
        viewModel = ViewModelProvider(this)[EpisodeViewModel::class.java]
        workManager = WorkManager.getInstance(requireContext())

        requireActivity().registerReceiver(
            downloadCompleteBroadcast,
            IntentFilter(DOWNLOAD_FINISHED_ACTION)
        )

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        checkEpisodeStatus()

        binding.episodeDownloadButton.setOnClickListener { sendDownloadRequest() }
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
        binding.exoPlayer.visibility = View.GONE
        binding.episodeProgress.visibility = View.GONE
        binding.episodeDownloadButton.visibility = View.VISIBLE
        subtitleFilePath = null
    }

    private fun showPlayer() {
        Log.d(TAG, "Show player")
        if (binding.exoPlayer.visibility == View.VISIBLE) {
            Log.w(TAG, "Already showing player; ignoring")
            return
        }

        subtitleFilePath = getSubtitles(
            app.downloadCache.getCachedSpans(episode.url).first().file!!.absolutePath
        )

        if (subtitleFilePath.isNullOrBlank()) {
            Log.w(TAG, "Subtitle file not found. Not showing player")
            showProgress()
            return
        }

        binding.episodeDownloadButton.visibility = View.GONE
        binding.episodeProgress.visibility = View.GONE
        binding.exoPlayer.visibility = View.VISIBLE
        initializePlayer()
    }

    private fun showProgress() {
        binding.exoPlayer.visibility = View.GONE
        binding.episodeDownloadButton.visibility = View.GONE
        binding.episodeProgress.visibility = View.VISIBLE

        val spans = app.downloadCache.getCachedSpans(episode.url)
        loadingProgressSteps += spans.size
        binding.episodeProgress.progress = (100 / loadingProgressSteps)

        setUpObservers()
    }

    private fun handleDownloadComplete() {
        try {
            Log.d(
                TAG,
                "Download complete for URL ${episode.url}; go create subtitles at $subtitleFilePath"
            )

            subtitleFilePath = Utils.getSubtitlePathForCachePath(
                app.downloadCache.getCachedSpans(episode.url).first().file!!.absolutePath
            )

            viewModel.onDownloadCompleted(episode.url, subtitleFilePath!!)
            setUpObservers()
        } catch (ex: NoSuchElementException) {
            Log.w(TAG, "Download completed, but view gone, so cannot start transcription")
        }
    }

    private fun checkEpisodeStatus() {
        val spans = app.downloadCache.getCachedSpans(episode.url)
        val needsDownload = spans.isEmpty() || spans.find { !it.isCached } != null

        if (needsDownload) {
            Log.d(TAG, "episode needs download")
            showDownloadTranscribeButton()
            return
        }

        // Check if this episode has already been transcribed
        subtitleFilePath = getSubtitles(spans.first().file!!.absolutePath)
        val needsTranscription = subtitleFilePath.isNullOrBlank()

        if (!needsTranscription) {
            Log.d(TAG, "episode already transcribed to $subtitleFilePath; show player")
            showPlayer()
            return
        }

        // check for workers tagged with expected subtitle file path
        subtitleFilePath = Utils.getSubtitlePathForCachePath(spans.first().file!!.absolutePath)

        val transcribeWorkInfos = workManager.getWorkInfosByTag(
            "${TRANSCRIBE_JOB_TAG}_$subtitleFilePath"
        )

        val mergeWorkInfos = workManager.getWorkInfosByTag(
            "${TRANSCRIPT_MERGE_JOB_TAG}_$subtitleFilePath"
        )

        val isRunning = !transcribeWorkInfos.isDone || !mergeWorkInfos.isDone

        if (isRunning) {
            Log.d(TAG, "Transcription job already in progress; show progress bar")
            showProgress()
            return
        }

        // needs transcription and it is not running already
        Log.d(TAG, "episode needs transcription; show button")
        showDownloadTranscribeButton()
    }

    private fun sendDownloadRequest() {
        binding.episodeDownloadButton.visibility = View.GONE
        binding.exoPlayer.visibility = View.GONE
        binding.episodeProgress.visibility = View.VISIBLE

        loadingProgressSteps = INITIAL_PROGRESS_STEPS
        binding.episodeProgress.isIndeterminate = false
        binding.episodeProgress.progress = INITIAL_PROGRESS

        val downloadRequest = DownloadRequest
            .Builder(episode.url, Uri.parse(episode.url)).build()

        mediaItem = downloadRequest.toMediaItem()

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
        val cacheDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(app.downloadCache)
            .setUpstreamDataSourceFactory(app.dataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Disable writing.

        Log.d("Player", "Going to set subtitle URI from file path $subtitleFilePath")
        val subtitleUri = Uri.fromFile(File(subtitleFilePath!!))
        Log.d("Player", "Got path to subtitles: $subtitleUri")
        Log.d("Player", "Existing media item URI: ${mediaItem?.localConfiguration?.uri}")

        // use URI from download if just completed, or parse it from the episode URL
        val mediaUri = mediaItem?.localConfiguration?.uri ?: Uri.parse(episode.url)

        Log.d("Player", "Using media URI $mediaUri")

        val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(feed.language)
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
                    .setPreferredTextLanguage(feed.language) // will not display if language not set
                    .setTrackTypeDisabled(TRACK_TYPE_DEFAULT, true) // otherwise sometimes doubled
                    .build()

                exoPlayer.setMediaItem(subbedMedia)
                Log.d("MediaPlayer", "ready to play  >>>>>>>>>>>>")
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

    private fun getSubtitles(firstChunkPath: String): String? {
        val localSubtitlePath = Utils.getSubtitlePathForCachePath(firstChunkPath)
        val fileStreamPath = requireContext().getFileStreamPath(localSubtitlePath)
        return if (fileStreamPath.exists()) {
            fileStreamPath.absolutePath
        } else {
            null
        }
    }

    private fun setUpObservers() {
        if (activity == null) {
            Log.w(TAG, "Not attached to an activity; not setting up observers")
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
