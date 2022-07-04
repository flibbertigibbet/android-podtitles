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
import androidx.lifecycle.lifecycleScope
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
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.Session
import com.google.common.collect.ImmutableList
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.databinding.FragmentHomeBinding
import dev.banderkat.podtitles.player.DOWNLOAD_FINISHED_ACTION
import dev.banderkat.podtitles.player.PodTitlesDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.ErrorListener
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

const val MEDIA_URI = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
const val FFMPEG_PARAMS = "-ac 1 -ar 16000"
const val VOSK_HZ = 16000.0f
const val VOSK_MODEL_ASSET = "model-en-us"
const val VOSK_MODEL_NAME = "model"
const val SUBTITLE_FILE_NAME = "subtitles.ttml"

class HomeFragment : Fragment(), RecognitionListener {

    private var _binding: FragmentHomeBinding? = null

    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var currentItem = 0
    private var playbackPosition = 0L

    private var mediaItem: MediaItem? = null
    private var ffmpegSession: Session? = null

    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var voskSpeechStreamService: SpeechStreamService? = null

    private val xmlDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private var subtitleDocument: Document? = xmlDocumentBuilder.newDocument()
    private var ttmlParent: Element? = null
    private var resultsCounter = 0

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

        initializeVosk()
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

        voskSpeechStreamService?.stop()
        voskSpeechStreamService = null
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
                    val outputFilePath = inputFilePath.trimEnd(*".mp3".toCharArray()) + ".wav"
                    convertToWav(inputFilePath, outputFilePath)
                }
            }
    }

    private fun startPlayer() {

        if (mediaItem == null || player == null) return

        val absPath = requireContext().getFileStreamPath(SUBTITLE_FILE_NAME).absolutePath
        Log.d("Player", "Absolute path to subtitles: $absPath")
        val subtitleUri = Uri.fromFile(File(absPath))
        Log.d("Player", "Got path to subtitles: $subtitleUri")



        requireContext().openFileInput(SUBTITLE_FILE_NAME).use { stream ->
            val text = stream.bufferedReader().use {
                it.readText()
            }

            Log.d("Player", "Read from subtitles file: ${text}")
        }

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

    private fun convertToWav(inputFilePath: String, outputFilePath: String) {
        FFmpegKitConfig.clearSessions()

        // Vosk requires 16Hz mono PCM wav
        val ffmpegSession = FFmpegKit.executeAsync(
            "-i $inputFilePath $FFMPEG_PARAMS $outputFilePath"
        ) {
            Log.d(
                "ffmpeg",
                "ffmpeg finished. return code: ${it.returnCode} duration: ${it.duration}"
            )

            if (it.failStackTrace != null) {
                Log.e("ffmpeg", "ffmpeg failed. ${it.failStackTrace}")
            } else {
                lifecycleScope.launch {
                    recognizeVosk(outputFilePath)
                }
            }
        }
    }

    private fun initializeVosk() {
        try {
            val assetPath = "$VOSK_MODEL_ASSET"
            val outputPath = StorageService.sync(requireContext(), assetPath, VOSK_MODEL_NAME)
            voskModel = Model(outputPath)
            Log.d("Vosk", "Vosk model unpacked successfully")
        } catch (e: IOException) {
            Log.e("Vosk", "Failed to unpack model", e)
            voskModel = null
        }
    }

    private suspend fun recognizeVosk(inputFilePath: String) = withContext(Dispatchers.IO) {
        // see TTML format docs: https://www.w3.org/TR/ttml2/
        subtitleDocument = xmlDocumentBuilder.newDocument()
        subtitleDocument?.apply {
            val tt = createElement("tt")
            val ttAttr = createAttribute("xmlns")
            ttAttr.value = "http://www.w3.org/ns/ttml"
            val body = createElement("body")
            val regionAttr = createAttribute("region")
            regionAttr.value = "subtitleArea"
            body.setAttributeNode(regionAttr)
            tt.appendChild(body)
            appendChild(tt)

            ttmlParent = createElement("div")
            body.appendChild(ttmlParent)
        }

        Log.d("Vosk", "creating Vosk recognizer")
        voskRecognizer = Recognizer(voskModel, VOSK_HZ)
        voskRecognizer?.setWords(true) // include timestamps on results
        val fileStream = FileInputStream(inputFilePath)
        voskSpeechStreamService = SpeechStreamService(voskRecognizer, fileStream, VOSK_HZ)
        Log.d("Vosk", "starting Vosk stream service")
        voskSpeechStreamService?.start(this@HomeFragment)
    }

    // Vosk listener methods
    override fun onPartialResult(hypothesis: String?) {
        Log.d("Vosk", "partial result: $hypothesis")
    }

    override fun onResult(hypothesis: String?) {
        Log.d("Vosk", "result: $hypothesis")
        if (hypothesis.isNullOrEmpty()) return

        val json = JSONObject(hypothesis)
        val resultText = json.getString("text")
        if (resultText.isNullOrEmpty()) return

        resultsCounter++
        val resultJson = json.getJSONArray("result")
        subtitleDocument?.apply {
            val paragraph = createElement("p")
            val idAttr = createAttribute("xml:id")
            idAttr.value = "subtitle$resultsCounter"
            paragraph.setAttributeNode(idAttr)
            val beginAttr = createAttribute("begin")
            val endAttr = createAttribute("end")
            val firstWord = resultJson.getJSONObject(0)
            val lastWord = resultJson.getJSONObject(resultJson.length() - 1)
            val startStr = String.format("%.2f", firstWord.get("start"))
            val endStr = String.format("%.2f", lastWord.get("end"))
            beginAttr.value = "${startStr}s"
            endAttr.value = "${endStr}s"
            paragraph.setAttributeNode(beginAttr)
            paragraph.setAttributeNode(endAttr)
            paragraph.appendChild(createTextNode(resultText))
            ttmlParent?.appendChild(paragraph)
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        Log.d("Vosk", "final result: $hypothesis")

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.errorListener = object : ErrorListener {
            override fun warning(p0: TransformerException?) {
                Log.w("Vosk", "transformer warning", p0)
            }

            override fun error(p0: TransformerException?) {
                Log.e("Vosk", "transformer error", p0)
            }

            override fun fatalError(p0: TransformerException?) {
                Log.e("Vosk", "transformer fatal error", p0)
            }
        }

        // val app = requireActivity().application as PodTitlesApplication
        // FIXME

        requireContext().openFileOutput(SUBTITLE_FILE_NAME, Context.MODE_PRIVATE).use {
            val writer = StreamResult(StringWriter())

            val result = StreamResult(it)
            val domSource = DOMSource(subtitleDocument)

            transformer.transform(domSource, writer)

            val xmlStr = writer.writer.toString()
            Log.d("Vosk", "generated subtitle doc:")
            Log.d("Vosk", xmlStr)

            transformer.transform(domSource, result)
        }

        startPlayer()
    }

    override fun onError(exception: Exception?) {
        Log.e("Vosk", "Vosk processing error", exception)
    }

    override fun onTimeout() {
        Log.e("Vosk", "Vosk timed out")
    }
}
