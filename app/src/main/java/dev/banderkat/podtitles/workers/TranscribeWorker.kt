package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import dev.banderkat.podtitles.utils.Utils
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

const val AUDIO_FILE_PATH_PARAM = "input_audio_path"
const val SUBTITLE_FILE_PATH_PARAM = "output_ttml_path"
const val CHUNK_DURATION_PARAM = "chunk_duration_s"
const val CHUNK_POSITION_PARAM = "chunk_position"
const val WEBVTT_FILE_HEADER = "WEBVTT \n\n"

/**
 * Transcribe a cached audio chunk with Vosk and convert the results to a TTML subtitle file.
 */
@Suppress("TooGenericExceptionCaught")
class TranscribeWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    companion object {
        const val TAG = "TranscribeWorker"
        const val FFMPEG_PARAMS = "-ac 1 -ar 16000 -f wav -y"
        const val SAMPLE_RATE = 16000.0f
        const val BUFFER_SIZE_SECONDS = 0.2f
        const val VOSK_MODEL_ASSET = "model-en-us" // TODO: support other language models
        const val VOSK_MODEL_NAME = "model"

        // see WebVTT docs: https://developer.mozilla.org/en-US/docs/Web/API/WebVTT_API
        const val SUBTITLE_FILE_EXTENSION = ".vtt"
        const val WEBVTT_DURATION_FORMAT = "HH:mm:ss.SSS"
        const val MS_PER_SEC = 1000

        // https://www.unimelb.edu.au/accessibility/video-captioning/style-guide
        const val MAX_CHARS_PER_CAPTION = 37 * 2
    }

    // Using SDF to format durations, which is fine for values < 24 hrs
    private val durationFormatter = SimpleDateFormat(WEBVTT_DURATION_FORMAT, Locale.getDefault())
    private var subtitles = StringBuilder(WEBVTT_FILE_HEADER)
    private var outputPipe: String? = null
    private val model = Model(
        StorageService.sync(
            applicationContext,
            VOSK_MODEL_ASSET,
            VOSK_MODEL_NAME
        )
    )

    override fun doWork(): Result {
        return try {
            val inputPath = inputData.getString(AUDIO_FILE_PATH_PARAM)
                ?: error("Missing $TAG parameter $AUDIO_FILE_PATH_PARAM")
            val chunkPosition = inputData.getLong(CHUNK_POSITION_PARAM, -1)
            if (chunkPosition < 0) error("Missing $TAG parameter $CHUNK_POSITION_PARAM")

            // get the playback length of this audio chunk in milliseconds
            val duration = FFprobeKit
                .getMediaInformation(inputPath)
                .mediaInformation
                .duration

            Log.d(TAG, "Audio chunk $inputPath has duration of $duration s")
            val durationSeconds = duration.toDouble()

            Log.d(TAG, "going to transcribe audio from $inputPath")
            val outputPath = recognize(inputPath)

            Result.success(
                Data(
                    mapOf(
                        SUBTITLE_FILE_PATH_PARAM to outputPath,
                        CHUNK_POSITION_PARAM to chunkPosition,
                        CHUNK_DURATION_PARAM to durationSeconds
                    )
                )
            )
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
        val recognizer = Recognizer(model, SAMPLE_RATE)
        recognizer.setWords(true) // include timestamps

        // Vosk requires 16Hz mono PCM wav. Pipe input file through ffmpeg to convert it
        pipeFFmpeg(inputFilePath)

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
        val outputPath = Utils.getSubtitlePathForAudioCachePath(inputFilePath)
        handleFinalResult(recognizer.finalResult, outputPath)
        return outputPath
    }

    private fun pipeFFmpeg(inputFilePath: String) {
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

    private fun handleResult(hypothesis: String?) {
        Log.d(TAG, "Vosk result: $hypothesis")
        if (hypothesis.isNullOrEmpty()) return

        val json = JSONObject(hypothesis)
        if (json.getString("text").isNullOrEmpty()) return

        val resultJson = json.getJSONArray("result")

        // iterate through the words in the result, building cues with a max length
        val firstWord = resultJson.getJSONObject(0)
        var cueStart: Double = firstWord.getDouble("start")
        var cueEnd: Double = firstWord.getDouble("end")
        val cueText = StringBuilder("${firstWord.getString("word")} ")

        fun addCue() {
            val now = TimeZone.getDefault().rawOffset.toLong()
            val startDate = Date((cueStart * MS_PER_SEC).toLong() - now)
            val endDate = Date((cueEnd * MS_PER_SEC).toLong() - now)
            val startTiming = durationFormatter.format(startDate)
            val endTiming = durationFormatter.format(endDate)
            subtitles.appendLine("$startTiming --> $endTiming")
            subtitles.appendLine(cueText.trimEnd())
            subtitles.appendLine()
        }

        for (i in 1 until resultJson.length()) {
            val wordObj = resultJson.getJSONObject(i)
            val word = wordObj.getString("word")
            if ((cueText.length + word.length) > MAX_CHARS_PER_CAPTION) {
                addCue()
                cueText.clear()
                cueStart = wordObj.getDouble("start")
            }
            cueText.append("$word ")
            cueEnd = wordObj.getDouble("end")
        }

        // write out last cue
        addCue()
    }

    private fun handleFinalResult(hypothesis: String?, outputFilePath: String) {
        Log.d(TAG, "Vosk final result: $hypothesis")

        // write subtitles to file
        applicationContext.openFileOutput(outputFilePath, Context.MODE_PRIVATE)
            .use { fileOutputStream ->
                fileOutputStream.writer().use { writer ->
                    writer.write(subtitles.toString())
                }
            }
    }
}
