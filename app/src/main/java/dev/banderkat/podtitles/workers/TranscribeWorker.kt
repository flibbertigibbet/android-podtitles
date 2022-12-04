package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.banderkat.podtitles.models.WebVttCue
import dev.banderkat.podtitles.utils.Utils
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt


const val AUDIO_FILE_PATH_PARAM = "input_audio_path"
const val VOSK_MODEL_PATH_PARAM = "vosk_model_path"
const val SUBTITLE_FILE_PATH_PARAM = "output_ttml_path"

/**
 * Transcribe a cached audio chunk with Vosk and convert the results to a TTML subtitle file.
 */
@Suppress("TooGenericExceptionCaught")
class TranscribeWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "TranscribeWorker"
        const val PARALLELISM = 1
        const val FFMPEG_PARAMS = "-ac 1 -ar 16000 -f wav -y -hide_banner -loglevel error"
        const val SAMPLE_RATE = 16000.0f
        const val BUFFER_SIZE_SECONDS = 0.2f

        // https://www.unimelb.edu.au/accessibility/video-captioning/style-guide
        const val MAX_CHARS_PER_CAPTION = 37 * 2
    }

    private val cues = mutableListOf<WebVttCue>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO.limitedParallelism(PARALLELISM)) {
            try {
                val inputPath = inputData.getString(AUDIO_FILE_PATH_PARAM)
                    ?: error("Missing $TAG parameter $AUDIO_FILE_PATH_PARAM")

                val modelPath = inputData.getString(VOSK_MODEL_PATH_PARAM)
                    ?: error("Missing $TAG parameter $VOSK_MODEL_PATH_PARAM")

                // get the playback length of this audio chunk in milliseconds
                val duration = FFprobeKit
                    .getMediaInformation(inputPath)
                    .mediaInformation
                    .duration

                Log.d(TAG, "Audio chunk $inputPath has duration of $duration seconds")

                supervisorScope {
                    val outputPath = recognize(inputPath, modelPath)
                    Result.success(
                        Data.Builder().putString(SUBTITLE_FILE_PATH_PARAM, "$outputPath|$duration").build()
                    )
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Vosk transcription failed", ex)
                if (ex is CancellationException) throw ex // re-throw error to cancel
                Result.failure()
            }
        }
    }

    // based on:
    // https://github.com/alphacep/vosk-api/blob/master/android/lib/src/main/java/org/vosk/android/SpeechStreamService.java
    private fun recognize(inputFilePath: String, modelPath: String): String {
        // Vosk requires 16Hz mono PCM wav. Convert it
        val wavPath = convertFFmpeg(inputFilePath)

        val model = Model(modelPath)
        Recognizer(model, SAMPLE_RATE).use { recognizer ->
            recognizer.setWords(true) // include timestamps
            applicationContext.openFileInput(wavPath)
                .use { wavStream ->
                    val bufferSize = (SAMPLE_RATE * BUFFER_SIZE_SECONDS * 2).roundToInt()
                    val buffer = ByteArray(bufferSize)

                    do {
                        val numberRead = wavStream.read(buffer, 0, bufferSize)
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

        // delete wav file
        applicationContext.deleteFile(wavPath)
        return outputPath
    }

    private fun convertFFmpeg(inputFilePath: String): String {
        val wavFilePath = Utils.getWavPathForAudioCachePath(inputFilePath)
        val absWavPath = applicationContext.getFileStreamPath(wavFilePath).canonicalPath
        var ffmpegSession: FFmpegSession? = null
        try {
            Log.d(TAG, "Going to run ffmpeg with: -i $inputFilePath $FFMPEG_PARAMS $absWavPath")
            ffmpegSession = FFmpegKit.execute(
                "-i $inputFilePath $FFMPEG_PARAMS $absWavPath"
            )

            Log.d(
                TAG,
                "ffmpeg finished. return code: ${ffmpegSession?.returnCode} duration: ${ffmpegSession?.duration}"
            )

            if (ffmpegSession?.failStackTrace != null) {
                Log.e(TAG, "ffmpeg failed: ${ffmpegSession.failStackTrace}")
                throw IOException(ffmpegSession.failStackTrace)
            } else if (ffmpegSession?.returnCode?.isValueSuccess == false) {
                error("ffmpeg did not return success. return code: ${ffmpegSession.returnCode}")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to convert audio to wav", ex)
            ffmpegSession?.cancel()
        }

        return wavFilePath
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
