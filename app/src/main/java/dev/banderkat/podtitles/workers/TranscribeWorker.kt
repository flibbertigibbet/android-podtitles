package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.google.common.collect.ImmutableMap
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
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
import kotlin.math.roundToInt

const val TAG = "TranscribeWorker"
const val FFMPEG_PARAMS = "-ac 1 -ar 16000 -f wav -y"
const val SAMPLE_RATE = 16000.0f
const val BUFFER_SIZE_SECONDS = 0.2f
const val VOSK_MODEL_ASSET = "model-en-us"
const val VOSK_MODEL_NAME = "model"
const val SUBTITLE_FILE_EXTENSION = ".ttml"
const val AUDIO_FILE_PATH_PARAM = "input_audio_path"
const val SUBTITLE_FILE_PATH_PARAM = "output_ttml_path"

/**
 * Transcribe a cached audio chunk with Vosk and convert the results to a TTML subtitle file.
 */
@Suppress("TooGenericExceptionCaught")
class TranscribeWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    private val xmlDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private var subtitleDocument: Document? = xmlDocumentBuilder.newDocument()
    private var ttmlParent: Element? = null
    private var resultsCounter = 0
    private var outputPipe: String? = null

    override fun doWork(): Result {
        return try {
            val inputPath = inputData.getString(AUDIO_FILE_PATH_PARAM)
                ?: error("Missing TranscribeWorker parameter $AUDIO_FILE_PATH_PARAM")
            Log.d(TAG, "going to transcribe audio from $inputPath")
            createSubtitleDocument()
            val outputPath = recognize(inputPath)
            Result.success(Data(ImmutableMap.of(SUBTITLE_FILE_PATH_PARAM, outputPath)))
        } catch (ex: Exception) {
            Log.e(TAG, "Vosk transcription failed", ex)
            Result.failure()
        } finally {
            if (!outputPipe.isNullOrEmpty()) {
                FFmpegKitConfig.closeFFmpegPipe(outputPipe)
            }
        }
    }

    // based on:
    // https://github.com/alphacep/vosk-api/blob/master/android/lib/src/main/java/org/vosk/android/SpeechStreamService.java
    private fun recognize(inputFilePath: String): String {
        val model =
            Model(StorageService.sync(applicationContext, VOSK_MODEL_ASSET, VOSK_MODEL_NAME))
        val recognizer = Recognizer(model, SAMPLE_RATE)
        recognizer.setWords(true) // include timestamps

        // Vosk requires 16Hz mono PCM wav. Pipe input file through ffmpeg to convert it
        pipeFFMPEG(inputFilePath)

        val inputStream = FileInputStream(outputPipe)
        val bufferSize = (SAMPLE_RATE * BUFFER_SIZE_SECONDS * 2).roundToInt()
        val buffer = ByteArray(bufferSize)

        do {
            val numberRead = inputStream.read(buffer, 0, bufferSize)
            val isSilence = recognizer.acceptWaveForm(buffer, bufferSize)
            // ignore partial results (when silence not found)
            if (isSilence) {
                handleResult(recognizer.result)
            }
        } while (numberRead >= 0)

        // output file path is the same as the input, but with the subtitle extension
        val inputFile = File(inputFilePath)
        val outputFilePath = "${inputFile.nameWithoutExtension}${SUBTITLE_FILE_EXTENSION}"

        handleFinalResult(recognizer.finalResult, outputFilePath)
        return applicationContext.getFileStreamPath(outputFilePath).absolutePath
    }

    private fun pipeFFMPEG(inputFilePath: String) {
        outputPipe = FFmpegKitConfig.registerNewFFmpegPipe(applicationContext)
        FFmpegKit.executeAsync(
            "-i $inputFilePath $FFMPEG_PARAMS $outputPipe"
        ) {
            Log.d(
                TAG,
                "ffmpeg finished. return code: ${it.returnCode} duration: ${it.duration}"
            )

            if (it.failStackTrace != null) {
                Log.e(TAG, "ffmpeg failed: ${it.failStackTrace}")
                throw IOException(it.failStackTrace)
            }
        }
    }

    private fun createSubtitleDocument() {
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
    }

    private fun handleResult(hypothesis: String?) {
        Log.d(TAG, "Vosk result: $hypothesis")
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

    private fun handleFinalResult(hypothesis: String?, outputFilePath: String) {
        Log.d(TAG, "Vosk final result: $hypothesis")

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.errorListener = object : ErrorListener {
            override fun warning(warning: TransformerException?) {
                Log.w(TAG, "XML transformer warning", warning)
            }

            override fun error(ex: TransformerException?) {
                Log.e(TAG, "XML transformer error", ex)
            }

            override fun fatalError(ex: TransformerException?) {
                Log.e(TAG, "XML transformer fatal error", ex)
                if (ex != null) throw ex
            }
        }

        // write subtitles to file
        applicationContext.openFileOutput(outputFilePath, Context.MODE_PRIVATE).use {
            // TODO: remove debug logging
            val writer = StreamResult(StringWriter())

            val result = StreamResult(it)
            val domSource = DOMSource(subtitleDocument)

            transformer.transform(domSource, writer)

            val xmlStr = writer.writer.toString()
            Log.d("Vosk", "generated subtitle doc:")
            Log.d("Vosk", xmlStr)

            transformer.transform(domSource, result)
        }
    }
}