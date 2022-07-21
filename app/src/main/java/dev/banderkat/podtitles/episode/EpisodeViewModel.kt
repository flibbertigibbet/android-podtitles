package dev.banderkat.podtitles.episode

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.*
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.workers.AUDIO_FILE_PATH_PARAM
import dev.banderkat.podtitles.workers.TranscribeWorker
import dev.banderkat.podtitles.workers.TranscriptMergeWorker
import java.util.concurrent.TimeUnit

const val TRANSCRIBE_JOB_TAG = "transcribe"
const val TRANSCRIPT_MERGE_JOB_TAG = "transcript_merge"
const val TRANSCRIBE_JOB_CHAIN_TAG = "transcribe_chain"

class EpisodeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "EpisodeViewModel"
        const val BACKOFF_DELAY_MINUTES = 5L
    }

    private val app: PodTitlesApplication = application as PodTitlesApplication
    private val workManager = WorkManager.getInstance(application.applicationContext)

    private val database = getDatabase(application)

    fun getEpisode(feedUrl: String, episodeGuid: String): LiveData<PodEpisode?> {
        return database.podDao.getEpisode(feedUrl, episodeGuid)
    }

    fun onDownloadCompleted(episodeUrl: String, subtitleFilePath: String) {
        transcribe(episodeUrl, subtitleFilePath)
    }

    private fun transcribe(episodeUrl: String, subtitleFilePath: String) {
        val spans = app.downloadCache.getCachedSpans(episodeUrl)
        val cachedChunks = spans.map { span -> span.file!!.absolutePath }

        // create transcript workers for each downloaded audio chunk
        val workers = cachedChunks.map {
            OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(workDataOf(AUDIO_FILE_PATH_PARAM to it))
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_MINUTES, // backoff delay default is 10 seconds
                    TimeUnit.MINUTES
                )
                .addTag(TRANSCRIBE_JOB_TAG)
                .addTag("${TRANSCRIBE_JOB_TAG}_$subtitleFilePath")
                .build()
        }

        val mergeFileWorker = OneTimeWorkRequestBuilder<TranscriptMergeWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setInputMerger(ArrayCreatingInputMerger::class)
            .addTag(TRANSCRIPT_MERGE_JOB_TAG)
            .addTag("${TRANSCRIPT_MERGE_JOB_TAG}_$subtitleFilePath")
            .build()

        // Use unique work to only run a single transcript job at a time, and queue the rest
        workManager.beginUniqueWork(
            TRANSCRIBE_JOB_CHAIN_TAG,
            ExistingWorkPolicy.APPEND_OR_REPLACE, // if previous job failed, start a new one
            workers
        ).then(mergeFileWorker).enqueue()

        Log.d(TAG, "Transcript work has been enqueued")
    }

    class Factory(val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EpisodeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return EpisodeViewModel(app) as T
            }
            throw IllegalArgumentException("Unable to construct view model")
        }
    }
}
