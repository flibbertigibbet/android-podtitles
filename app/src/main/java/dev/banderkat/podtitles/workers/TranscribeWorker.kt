package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.Session
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.banderkat.podtitles.models.WebVttCue
import dev.banderkat.podtitles.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.FileInputStream
import java.io.IOException
import kotlin.math.roundToInt


const val AUDIO_FILE_PATH_PARAM = "input_audio_path"
const val SUBTITLE_FILE_PATH_PARAM = "output_ttml_path"

/**
 * Transcribe a cached audio chunk with Vosk and convert the results to a TTML subtitle file.
 */
@Suppress("TooGenericExceptionCaught")
class TranscribeWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "TranscribeWorker"
        const val PARALLELISM = 2
        const val INTERMEDIATE_RESULTS_FILE_EXTENSION = ".json"
        const val FFMPEG_PARAMS = "-ac 1 -ar 16000 -f wav -y -hide_banner -loglevel error"
        const val SAMPLE_RATE = 16000.0f
        const val BUFFER_SIZE_SECONDS = 0.2f
        const val VOSK_MODEL_ASSET = "model-en-us" // TODO: support other language models
        const val VOSK_MODEL_NAME = "model"

        // https://www.unimelb.edu.au/accessibility/video-captioning/style-guide
        const val MAX_CHARS_PER_CAPTION = 37 * 2
    }

    private var outputPipe: String? = null
    private var ffmpegSession: Session? = null

    private val cues = mutableListOf<WebVttCue>()
    private val model = Model(
        StorageService.sync(
            applicationContext,
            VOSK_MODEL_ASSET,
            VOSK_MODEL_NAME
        )
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.Default.limitedParallelism(PARALLELISM)) {
            try {
                val inputPath = inputData.getString(AUDIO_FILE_PATH_PARAM)
                    ?: error("Missing $TAG parameter $AUDIO_FILE_PATH_PARAM")

                // get the playback length of this audio chunk in milliseconds
                val duration = FFprobeKit
                    .getMediaInformation(inputPath)
                    .mediaInformation
                    .duration

                Log.d(TAG, "Audio chunk $inputPath has duration of $duration seconds")
                val outputPath = recognize(inputPath)

                Result.success(
                    Data(mapOf(SUBTITLE_FILE_PATH_PARAM to "$outputPath|$duration"))
                )
            } catch (ex: Exception) {
                Log.e(TAG, "Vosk transcription failed", ex)
                Result.failure()
            } finally {
                ffmpegSession?.cancel()
                if (!outputPipe.isNullOrEmpty()) {
                    FFmpegKitConfig.closeFFmpegPipe(outputPipe)
                }

                ffmpegSession = null
                outputPipe = null
            }
        }
    }

    // based on:
    // https://github.com/alphacep/vosk-api/blob/master/android/lib/src/main/java/org/vosk/android/SpeechStreamService.java
    private fun recognize(inputFilePath: String): String {
        Recognizer(model, SAMPLE_RATE).use { recognizer ->
            recognizer.setWords(true) // include timestamps

            // Vosk requires 16Hz mono PCM wav. Pipe input file through ffmpeg to convert it
            pipeFFmpeg(inputFilePath)

            FileInputStream(outputPipe).use { ffmpegStream ->
                val bufferSize = (SAMPLE_RATE * BUFFER_SIZE_SECONDS * 2).roundToInt()
                val buffer = ByteArray(bufferSize)

                do {
                    val numberRead = ffmpegStream.read(buffer, 0, bufferSize)
                    val isSilence = recognizer.acceptWaveForm(buffer, bufferSize)
                    // ignore partial results (when silence not found)
                    if (isSilence) {
                        handleResult(recognizer.result)
                    }
                } while (numberRead >= 0)
            }
        }

        // output file path is the same as the input, but with a different extension
        val outputPath = Utils.getIntermediateResultsPathForAudioCachePath(inputFilePath)
        handleFinalResult(outputPath)
        return outputPath
    }

    private fun pipeFFmpeg(inputFilePath: String) {
        outputPipe = FFmpegKitConfig.registerNewFFmpegPipe(applicationContext)
        ffmpegSession = FFmpegKit.executeAsync(
            "-i $inputFilePath $FFMPEG_PARAMS $outputPipe"
        ) {
            Log.d(
                TAG,
                "ffmpeg finished. return code: ${it.returnCode} duration: ${it.duration}"
            )

            if (it.failStackTrace != null) {
                Log.e(TAG, "ffmpeg failed: ${it.failStackTrace}")
                throw IOException(it.failStackTrace)
            } else if (!it.returnCode.isValueSuccess) {
                error("ffmpeg did not return success. return code: ${it.returnCode}")
            }
        }
    }

    private fun handleResult(hypothesis: String?) {
        if (hypothesis.isNullOrEmpty()) return

        val json = JSONObject(hypothesis)
        if (json.getString("text").isNullOrEmpty()) return

        val resultJson = json.getJSONArray("result")

        // iterate through the words in the result, building cues with a max length
        val firstWord = resultJson.getJSONObject(0)
        var cueStart: Double = firstWord.getDouble("start")
        var cueEnd: Double = firstWord.getDouble("end")
        val cueText = StringBuilder("${firstWord.getString("word")} ")

        for (i in 1 until resultJson.length()) {
            val wordObj = resultJson.getJSONObject(i)
            val word = wordObj.getString("word")
            if ((cueText.length + word.length) > MAX_CHARS_PER_CAPTION) {
                cues.add(WebVttCue(cueStart, cueEnd, cueText.trimEnd().toString()))
                cueText.clear()
                cueStart = wordObj.getDouble("start")
            }
            cueText.append("$word ")
            cueEnd = wordObj.getDouble("end")
        }

        // write out last cue
        cues.add(WebVttCue(cueStart, cueEnd, cueText.trimEnd().toString()))
    }

    private fun handleFinalResult(outputFilePath: String) {
        // write complete subtitles for this chunk to file
        applicationContext.openFileOutput(outputFilePath, Context.MODE_PRIVATE)
            .use { fileOutputStream ->
                fileOutputStream.writer().use { writer ->
                    writer.write(getJsonResult())
                }
            }
    }

    private fun getJsonResult(): String {
        val cueListType = Types.newParameterizedType(List::class.java, WebVttCue::class.java)
        val jsonAdapter: JsonAdapter<List<WebVttCue>> = Moshi.Builder().build().adapter(cueListType)
        return jsonAdapter.toJson(cues)
    }
}
