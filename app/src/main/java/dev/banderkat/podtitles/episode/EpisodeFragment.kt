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
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.navigation.fragment.navArgs
import androidx.work.*
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.common.collect.ImmutableList
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.databinding.FragmentEpisodeBinding
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.player.DOWNLOAD_FINISHED_ACTION
import dev.banderkat.podtitles.player.PodTitlesDownloadService
import dev.banderkat.podtitles.utils.Utils
import dev.banderkat.podtitles.workers.*
import java.io.File
import java.util.*

// TODO: remove test URI
const val MEDIA_URI = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"

class EpisodeFragment : Fragment() {
    companion object {
        const val TAG = "EpisodeFragment"
        const val FRACTIONAL_TEXT_SIZE = 0.1f
    }

    private var _binding: FragmentEpisodeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EpisodeViewModel by lazy {
        ViewModelProvider(this)[EpisodeViewModel::class.java]
    }

    private val args: EpisodeFragmentArgs by navArgs()
    private lateinit var episode: PodEpisode
    private lateinit var feed: PodFeed

    private var player: ExoPlayer? = null
    private var currentItem = 0
    private var playbackPosition = 0L
    private var loadingProgressSteps = 2

    private var mediaItem: MediaItem? = null
    private var subtitleFilePath: String? = null

    private val downloadCompleteBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("Home", "download complete broadcast received")
            initializePlayer()
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

        requireActivity().registerReceiver(
            downloadCompleteBroadcast,
            IntentFilter(DOWNLOAD_FINISHED_ACTION)
        )

        sendDownloadRequest()
        return root
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

    private fun setDefaultArtwork() {
        val defaultImage = if (episode.image.isNotBlank()) {
            episode.image
        } else if (feed.image.isNotBlank()) {
            feed.image
        } else {
            ""
        }

        if (defaultImage.isNotBlank()) {
            Log.d(TAG, "Going to load default artwork from $defaultImage")
            Glide.with(this)
                .asBitmap()
                .load(defaultImage)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        binding.exoPlayer.defaultArtwork = BitmapDrawable(resources, resource)
                        Log.d(TAG, "default artwork set")
                        binding.exoPlayer.useArtwork = true
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        } else {
            Log.d(TAG, "No default artwork found for this episode/podcasts")
        }
    }

    private fun sendDownloadRequest() {

        binding.exoPlayer.visibility = View.GONE
        binding.episodeProgress.visibility = View.VISIBLE

        // TODO: move magic numbers
        loadingProgressSteps = 2
        binding.episodeProgress.isIndeterminate = false
        binding.episodeProgress.progress = 5

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
        val app = requireActivity().application as PodTitlesApplication
        val cacheDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(app.downloadCache)
            .setUpstreamDataSourceFactory(app.dataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Disable writing.


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

                val spans = app.downloadCache.getCachedSpans(episode.url)
                loadingProgressSteps += spans.size
                binding.episodeProgress.progress = (100 / loadingProgressSteps)
                transcribe(spans)
            }
    }

    private fun startPlayer() {
        if (mediaItem == null || player == null) return

        val subtitleUri = Uri.fromFile(File(subtitleFilePath!!))
        Log.d("Player", "Got path to subtitles: $subtitleUri")

        Log.d("Player", "Existing media item URI: ${mediaItem?.localConfiguration?.uri}")

        val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(feed.language)
            .setSelectionFlags(SELECTION_FLAG_AUTOSELECT)
            .build()

        val subbedMedia = MediaItem
            .Builder()
            .setUri(mediaItem?.localConfiguration?.uri)
            .setSubtitleConfigurations(ImmutableList.of(subtitle))
            .build()

        binding.episodeProgress.visibility = View.GONE
        binding.exoPlayer.visibility = View.VISIBLE

        player?.apply {
            setMediaItem(subbedMedia)
            Log.d("MediaPlayer", "ready to play  >>>>>>>>>>>>")
            seekTo(currentItem, playbackPosition)
            prepare()
        }

        setDefaultArtwork()
        binding.exoPlayer.subtitleView?.setFractionalTextSize(FRACTIONAL_TEXT_SIZE)
        player?.playWhenReady = true
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            currentItem = exoPlayer.currentMediaItemIndex
            exoPlayer.release()
        }
        player = null
    }

    private fun transcribe(cacheSpans: NavigableSet<CacheSpan>) {
        val cachedChunks = cacheSpans.mapIndexed { index, span ->
            Log.d(TAG,
                "Cached span at index $index has file ${span.file?.name} position ${span.position} is cached? ${span.isCached} is hole? ${span.isHoleSpan} open-ended? ${span.isOpenEnded}")
            span.file!!.absolutePath
        }
        // First check if this episode has already been transcribed
        val localSubtitlePath = Utils.getSubtitlePathForCachePath(cachedChunks[0])

        // TODO: also check to see if there is already a job to transcribe this episode

        val fileStreamPath = requireContext().getFileStreamPath(localSubtitlePath)
        if (fileStreamPath.exists()) {
            Log.d(TAG, "Episode already transcribed; using existing subtitles")
            subtitleFilePath = fileStreamPath.absolutePath
            startPlayer()
            return
        }

        val workManager = WorkManager.getInstance(requireContext())

        // cancel any other transcription jobs before attempting this one
        workManager.cancelAllWorkByTag("transcribe")
        workManager.cancelAllWorkByTag("transcript_merge")
        workManager.pruneWork()

        // launch transcription workers in parallel
        val workers = cachedChunks.map {
            OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(workDataOf(AUDIO_FILE_PATH_PARAM to it))
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .addTag("transcribe")
                .addTag("transcribe_$localSubtitlePath") // TODO: move
                .build()
        }

        val mergeFileWorker = OneTimeWorkRequestBuilder<TranscriptMergeWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setInputMerger(ArrayCreatingInputMerger::class)
            .addTag("transcript_merge")
            .addTag("transcript_merge_$localSubtitlePath") // TODO: move
            .build()

        workManager.beginWith(workers).then(mergeFileWorker).enqueue()

        workManager.getWorkInfosByTagLiveData("transcribe_$localSubtitlePath")
            .observe(viewLifecycleOwner) { workInfo ->
                val completed = workInfo.count {
                    it.state == WorkInfo.State.SUCCEEDED
                } + 1
                binding.episodeProgress.progress = (100 / loadingProgressSteps) * completed
            }

        workManager
            .getWorkInfoByIdLiveData(mergeFileWorker.id)
            .observe(viewLifecycleOwner) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val subtitlePath = workInfo.outputData.getString(
                            SUBTITLE_FILE_PATH_PARAM
                        )
                        Log.d(TAG, "Transcription worker successfully wrote file $subtitlePath")
                        subtitleFilePath = subtitlePath
                        binding.episodeProgress.progress = 100
                        startPlayer()
                    }
                    WorkInfo.State.FAILED -> {
                        Log.e(TAG, "Transcription worker failed")
                    }
                    else -> {
                        Log.d(TAG, "Transcription worker moved to state ${workInfo?.state}")
                    }
                }
            }
    }
}
