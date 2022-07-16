package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.common.collect.ImmutableMap
import java.io.File

/**
 * Transcribe a cached audio chunk with Vosk and convert the results to a TTML subtitle file.
 */
@Suppress("TooGenericExceptionCaught")
class TranscriptMergeWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    companion object {
        const val TAG = "TranscriptMergeWorker"
    }

    override fun doWork(): Result {
        return try {
            val inputPaths = inputData.getStringArray(SUBTITLE_FILE_PATH_PARAM)
                ?: error("Missing $TAG parameter $SUBTITLE_FILE_PATH_PARAM")
            Log.d(TAG, "going to merge transcripts from $inputPaths")
            for (path in inputPaths) {
                Log.d(TAG, "Merge got path $path")
            }

            val outputPath = mergeTranscripts(inputPaths)
            Result.success(Data(ImmutableMap.of(SUBTITLE_FILE_PATH_PARAM, outputPath)))
        } catch (ex: Exception) {
            Log.e(TAG, "Transcription file merge failed", ex)
            Result.failure()
        }
    }

    /**
     * Merges together the TTML subscript files from each of the cached audio chunks of a podcast.
     *
     * @param transcripts Paths to the TTML files to merge
     * @return Path to the merged file
     */
    private fun mergeTranscripts(transcripts: Array<String>): String {
        if (transcripts.isEmpty()) error("Missing transcript files to merge")
        if (transcripts.size == 1) return transcripts[0] // only one file; nothing to merge

        Log.d(TAG, "Got ${transcripts.size} transcripts to merge")
        // append the transcripts for additional chunks to the transcript file for the first chunk
        applicationContext.openFileOutput(transcripts[0], Context.MODE_APPEND).writer()
            .use { writer ->
                for (i in 1 until transcripts.size) {
                    applicationContext.openFileInput(transcripts[i]).reader().use { reader ->
                        writer.append(reader.readText().removePrefix(WEBVTT_FILE_HEADER))
                    }
                }
            }

        for (i in 1 until transcripts.size) {
            // clean up partial transcripts
            File(transcripts[i]).delete()
        }
        return applicationContext.getFileStreamPath(transcripts[0]).absolutePath
    }
}
