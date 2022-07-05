package dev.banderkat.podtitles.ui.home

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
import androidx.work.*
import com.google.common.collect.ImmutableList
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.databinding.FragmentHomeBinding
import dev.banderkat.podtitles.player.DOWNLOAD_FINISHED_ACTION
import dev.banderkat.podtitles.player.PodTitlesDownloadService
import dev.banderkat.podtitles.workers.AUDIO_FILE_PATH_PARAM
import dev.banderkat.podtitles.workers.SUBTITLE_FILE_PATH_PARAM
import dev.banderkat.podtitles.workers.TranscribeWorker
import java.io.File

const val MEDIA_URI = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

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

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        homeViewModel.text.observe(viewLifecycleOwner) {
            // FIXME: remove
        }

        return root
    }

    override fun onStart() {
        super.onStart()
        requireActivity().registerReceiver(
            downloadCompleteBroadcast,
            IntentFilter(DOWNLOAD_FINISHED_ACTION)
        )

        sendDownloadRequest()

        // initializePlayer()
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
        val downloadRequest = DownloadRequest.Builder(
            MEDIA_URI, Uri.parse(MEDIA_URI)
        ).build()

        mediaItem = downloadRequest.toMediaItem()

        DownloadService.sendAddDownload(
            requireContext(),
            PodTitlesDownloadService::class.java,
            downloadRequest,
            false
        )
    }

    private fun initializePlayer() {

        val app = requireActivity().application as PodTitlesApplication
        val cacheDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(app.downloadCache)
            .setUpstreamDataSourceFactory(app.dataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Disable writing.


        player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(requireContext())
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .build()
            .also { exoPlayer ->
                binding.exoPlayer.player = exoPlayer

                exoPlayer.trackSelector?.parameters = TrackSelectionParameters
                    .getDefaults(requireContext())
                    .buildUpon()
                    .setPreferredTextLanguage("en") // will not display if language not set
                    .setTrackTypeDisabled(TRACK_TYPE_DEFAULT, true) // otherwise doubled
                    .build()

                app.downloadCache.getCachedSpans(MEDIA_URI).forEach {
                    val inputFilePath = it.file!!.absolutePath
                    Log.d("Player", "Input file path is: $inputFilePath")
                    transcribe(inputFilePath)
                }
            }
    }

    private fun startPlayer() {
        if (mediaItem == null || player == null) return

        val subtitleUri = Uri.fromFile(File(subtitleFilePath))
        Log.d("Player", "Got path to subtitles: $subtitleUri")

        Log.d("Player", "Existing media item URI: ${mediaItem?.localConfiguration?.uri}")

        val subtitle = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
            .setMimeType(MimeTypes.APPLICATION_TTML)
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

    private fun transcribe(inputFilePath: String) {
        // launch transcription worker
        val transcribeRequest = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setInputData(workDataOf(AUDIO_FILE_PATH_PARAM to inputFilePath))
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag("transcribe") // TODO: move
            .build()

        val workManager = WorkManager.getInstance(requireContext())
        workManager.enqueue(transcribeRequest)

        workManager
            .getWorkInfoByIdLiveData(transcribeRequest.id)
            .observe(viewLifecycleOwner) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val subtitlePath = workInfo.outputData.getString(
                            SUBTITLE_FILE_PATH_PARAM
                        )
                        Log.d(
                            "HomeFragment",
                            "Transcription worker successfully wrote file $subtitlePath"
                        )
                        subtitleFilePath = subtitlePath
                        startPlayer()
                    }
                    WorkInfo.State.FAILED -> {
                        Log.e("HomeFragment", "Transcription worker failed")
                    }
                    else -> {
                        Log.d(
                            "HomeFragment",
                            "Transcription worker moved to state ${workInfo?.state}"
                        )
                    }
                }
            }
    }
}
