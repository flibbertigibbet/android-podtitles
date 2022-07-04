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
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback
import com.arthenica.ffmpegkit.ReturnCode
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.databinding.FragmentHomeBinding
import dev.banderkat.podtitles.player.DOWNLOAD_FINISHED_ACTION
import dev.banderkat.podtitles.player.PodTitlesDownloadService

const val MEDIA_URI = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var currentItem = 0
    private var playbackPosition = 0L

    private var mediaItem: MediaItem? = null

    private val downloadCompleteBroadcast: BroadcastReceiver = object: BroadcastReceiver() {
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
        requireActivity().registerReceiver(downloadCompleteBroadcast, IntentFilter(DOWNLOAD_FINISHED_ACTION))
        sendDownloadRequest()
        // initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        // if (player == null) initializePlayer()
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
            .setUpstreamDataSourceFactory(app.httpDataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Disable writing.


        player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(requireContext())
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .build()
            .also { exoPlayer ->
                binding.exoPlayer.player = exoPlayer
                //val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(""))
                //    .build()
                //val mediaItem = MediaItem
                  //  .Builder()
                  //  .setMediaId(MEDIA_URI)
                  //  .setUri(MEDIA_URI)
                    //.setSubtitleConfigurations(ImmutableList.of(subtitle))
                    //.build()

                val wavFiles = app.downloadCache.getCachedSpans(MEDIA_URI).map {
                    val inputFilePath = it.file!!.absolutePath
                    val outputFilePath = inputFilePath.trimEnd(*".mp3".toCharArray()) + ".wav"
                    convertToWav(inputFilePath, outputFilePath)
                    return@map outputFilePath
                }

                
                if (mediaItem != null) exoPlayer.setMediaItem(mediaItem!!)
                Log.d("MediaPlayer", "have ${exoPlayer.mediaItemCount} item(s) to play >>>>>>>>")
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(currentItem, playbackPosition)
                exoPlayer.prepare()
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

    private fun convertToWav(inputFilePath: String, outputFilePath: String) {
        FFmpegKitConfig.clearSessions()

        //val inputPipe = FFmpegKitConfig.registerNewFFmpegPipe(requireContext())
        //val outputPipe = FFmpegKitConfig.registerNewFFmpegPipe(requireContext())

        val session = FFmpegKit.executeAsync("-i $inputFilePath -ac 1 -ar 16000 $outputFilePath"
        ) { // FIXME: complete callback
            Log.d(
                "ffmpeg",
                "ffmpeg finished. return code: ${it.returnCode} duration: ${it.duration}"
            )
            if (it.failStackTrace != null) {
                Log.e("ffmpeg", "ffmpeg failed. ${it.failStackTrace}")
            }
        }
    }
}
