package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.common.collect.ImmutableMap
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.banderkat.podtitles.models.AudioCacheChunk
import dev.banderkat.podtitles.models.WebVttCue
import dev.banderkat.podtitles.utils.Utils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Transcribe a cached audio chunk with Vosk and convert the results to a TTML subtitle file.
 */
@Suppress("TooGenericExceptionCaught")
class TranscriptMergeWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    companion object {
        const val TAG = "TranscriptMergeWorker"

        // see WebVTT docs: https://developer.mozilla.org/en-US/docs/Web/API/WebVTT_API
        const val SUBTITLE_FILE_EXTENSION = ".vtt"
        const val WEBVTT_FILE_HEADER = "WEBVTT \n\n"
        const val WEBVTT_DURATION_FORMAT = "HH:mm:ss.SSS"
        const val MS_PER_SEC = 1000
    }

    // Using SDF to format durations, which is fine for values < 24 hrs
    private val durationFormatter = SimpleDateFormat(WEBVTT_DURATION_FORMAT, Locale.getDefault())
    private var subtitles = StringBuilder(WEBVTT_FILE_HEADER)

    private val cueListType = Types.newParameterizedType(List::class.java, WebVttCue::class.java)
    private val jsonAdapter: JsonAdapter<List<WebVttCue>> =
        Moshi.Builder().build().adapter(cueListType)

    override fun doWork(): Result {
        return try {
            val transcriptPaths = inputData.getStringArray(SUBTITLE_FILE_PATH_PARAM)
                ?: error("Missing $TAG parameter $SUBTITLE_FILE_PATH_PARAM")
            val chunkPositions = inputData.getIntArray(CHUNK_POSITION_PARAM)
                ?: error("Missing $TAG parameter $CHUNK_POSITION_PARAM")
            val chunkDurations = inputData.getDoubleArray(CHUNK_DURATION_PARAM)
                ?: error("Missing $TAG parameter $CHUNK_DURATION_PARAM")

            Log.d(TAG, "going to merge ${transcriptPaths.size} transcripts")

            val chunks = chunkPositions.sorted().mapIndexed { index, position ->
                AudioCacheChunk(position, transcriptPaths[index], chunkDurations[index])
            }

            val outputPath = mergeTranscripts(chunks)
            Result.success(Data(ImmutableMap.of(SUBTITLE_FILE_PATH_PARAM, outputPath)))
        } catch (ex: Exception) {
            Log.e(TAG, "Transcription file merge failed", ex)
            Result.failure()
        }
    }

    /**
     * Merges together the subscript files from each of the cached audio chunks of a podcast.
     *
     * @param transcripts Paths to the subscript files to merge
     * @return Path to the merged file
     */
    private fun mergeTranscripts(transcripts: List<AudioCacheChunk>): String {
        if (transcripts.isEmpty()) error("Missing transcript files to merge")

        Log.d(TAG, "Got ${transcripts.size} transcripts to merge:")
        transcripts.forEach { Log.d(TAG, "Got transcript $it") }

        // relative path to subtitle file to output
        val outputPath = Utils.getSubtitlePathForCachePath(transcripts[0].filePath)

        var chunkStartTime = 0.0

        // generate the WebVTT transcript file from the JSON intermediate result files
        applicationContext.openFileOutput(outputPath, Context.MODE_PRIVATE).writer()
            .use { writer ->
                writer.append(WEBVTT_FILE_HEADER)
                transcripts.forEach { transcript ->
                    applicationContext.openFileInput(transcript.filePath).reader().use { reader ->
                        val cues = jsonAdapter.fromJson(reader.readText())
                        cues?.forEach { cue ->
                            writer.append(getWebVttCueText(cue, chunkStartTime))
                        }
                    }
                    chunkStartTime += transcript.duration!!
                }
            }

        // clean up intermediate results
        transcripts.forEach { applicationContext.deleteFile(it.filePath) }
        return applicationContext.getFileStreamPath(outputPath).absolutePath
    }

    private fun getWebVttCueText(cue: WebVttCue, chunkStartTime: Double): String {
        val chunkOffset = (chunkStartTime * MS_PER_SEC).toLong()
        val now = TimeZone.getDefault().rawOffset.toLong()
        val startDate = Date((cue.start * MS_PER_SEC).toLong() - now + chunkOffset)
        val endDate = Date((cue.end * MS_PER_SEC).toLong() - now + chunkOffset)
        val startTiming = durationFormatter.format(startDate)
        val endTiming = durationFormatter.format(endDate)
        return "$startTiming --> $endTiming\n${cue.text}\n\n"
    }
}
