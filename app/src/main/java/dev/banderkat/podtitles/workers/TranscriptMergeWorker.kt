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

/**
 * Transcribe a cached audio chunk with Vosk and convert the results to a TTML subtitle file.
 */
@Suppress("TooGenericExceptionCaught")
class TranscriptMergeWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    companion object {
        const val TAG = "TranscriptMergeWorker"
    }

    private val xmlDocumentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private var subtitleDocument: Document? = xmlDocumentBuilder.newDocument()


    override fun doWork(): Result {
        return try {
            val inputPaths = inputData.getStringArray(SUBTITLE_FILE_PATH_PARAM)
                ?: error("Missing $TAG parameter $SUBTITLE_FILE_PATH_PARAM")
            Log.d(TAG, "going to merge transcripts from $inputPaths")
            for (path in inputPaths) {
                Log.d(TAG, "Merge got path $path")
            }
            Result.success(Data(ImmutableMap.of(SUBTITLE_FILE_PATH_PARAM, inputPaths[0])))
            //Result.success()
        } catch (ex: Exception) {
            Log.e(TAG, "Vosk transcription failed", ex)
            Result.failure()
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

            //ttmlParent = createElement("div")
            //body.appendChild(ttmlParent)
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