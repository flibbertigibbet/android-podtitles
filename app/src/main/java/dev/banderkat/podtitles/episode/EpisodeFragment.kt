package dev.banderkat.podtitles.episode

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.navigation.fragment.navArgs
import androidx.work.*
import com.google.common.collect.ImmutableList
import com.sun.jna.StringArray
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.databinding.FragmentEpisodeBinding
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.player.DOWNLOAD_FINISHED_ACTION
import dev.banderkat.podtitles.player.PodTitlesDownloadService
import dev.banderkat.podtitles.utils.Utils
import dev.banderkat.podtitles.workers.AUDIO_FILE_PATH_PARAM
import dev.banderkat.podtitles.workers.SUBTITLE_FILE_PATH_PARAM
import dev.banderkat.podtitles.workers.TranscribeWorker
import dev.banderkat.podtitles.workers.TranscriptMergeWorker
import java.io.File

// TODO: remove test URI
const val MEDIA_URI = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"

class EpisodeFragment : Fragment() {
    companion object {
        const val TAG = "EpisodeFragment"
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
    private var playWhenReady = true
    private var currentItem = 0
    private var playbackPosition = 0L

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

    private fun sendDownloadRequest() {

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

                val episodeCacheFiles = app.downloadCache.getCachedSpans(episode.url).map {
                    it.file!!.absolutePath
                }

                transcribe(episodeCacheFiles)
            }
    }

    private fun startPlayer() {
        if (mediaItem == null || player == null) return

        val subtitleUri = Uri.fromFile(File(subtitleFilePath!!))
        Log.d("Player", "Got path to subtitles: $subtitleUri")

        Log.d("Player", "Existing media item URI: ${mediaItem?.localConfiguration?.uri}")

        val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("en")
            .setSelectionFlags(SELECTION_FLAG_AUTOSELECT)
            .build()

        val subbedMedia = MediaItem
            .Builder()
            .setUri(mediaItem?.localConfiguration?.uri)
            .setSubtitleConfigurations(ImmutableList.of(subtitle))
            .build()

        player?.apply {
            setMediaItem(subbedMedia)
            Log.d("MediaPlayer", "ready to play  >>>>>>>>>>>>")
            playWhenReady = playWhenReady
            seekTo(currentItem, playbackPosition)
            prepare()
        }
    }

    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            currentItem = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.release()
        }
        player = null
    }

    private fun transcribe(inputFileCachePaths: List<String>) {
        // launch transcription workers in parallel

        // First check if this episode has already been transcribed
        val localSubtitlePath = Utils.getSubtitlePathForAudioCachePath(
            inputFileCachePaths[0]
        )

        val expectedSubtitlePath = requireContext()
            .applicationContext
            .getFileStreamPath(localSubtitlePath)
            .absolutePath

        if (File(expectedSubtitlePath).exists()) {
            Log.d(TAG, "Episode already transcribed; using existing subtitles")
            subtitleFilePath = expectedSubtitlePath
            startPlayer()
            return
        }

        val workers = inputFileCachePaths.map {
            OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(workDataOf(AUDIO_FILE_PATH_PARAM to it))
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .addTag("transcribe") // TODO: move
                .build()
        }

        val mergeFileWorker = OneTimeWorkRequestBuilder<TranscriptMergeWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setInputMerger(ArrayCreatingInputMerger::class)
            .addTag("transcript_merge") // TODO: move
            .build()

        val workManager = WorkManager.getInstance(requireContext())
        workManager.beginWith(workers).then(mergeFileWorker).enqueue()

        workManager
            .getWorkInfoByIdLiveData(mergeFileWorker.id)
            .observe(viewLifecycleOwner) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val subtitlePath = workInfo.outputData.getString(
                            SUBTITLE_FILE_PATH_PARAM
                        )
                        Log.d(
                            "EpisodeFragment",
                            "Transcription worker successfully wrote file $subtitlePath"
                        )
                        subtitleFilePath = subtitlePath
                        startPlayer()
                    }
                    WorkInfo.State.FAILED -> {
                        Log.e("EpisodeFragment", "Transcription worker failed")
                    }
                    else -> {
                        Log.d(
                            "EpisodeFragment",
                            "Transcription worker moved to state ${workInfo?.state}"
                        )
                    }
                }
            }
    }
}
