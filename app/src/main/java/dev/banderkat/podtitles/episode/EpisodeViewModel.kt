package dev.banderkat.podtitles.episode

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.*
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.workers.AUDIO_FILE_PATH_PARAM
import dev.banderkat.podtitles.workers.TranscribeWorker
import dev.banderkat.podtitles.workers.TranscriptMergeWorker
import kotlinx.coroutines.launch

class EpisodeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "EpisodeViewModel"
    }

    private val database = getDatabase(application)
    private val app: PodTitlesApplication = application as PodTitlesApplication
    private val workManager = WorkManager.getInstance(application.applicationContext)

    fun onDownloadCompleted(episodeUrl: String, subtitleFilePath: String) {
        transcribe(episodeUrl, subtitleFilePath)
    }

    private fun transcribe(episodeUrl: String, subtitleFilePath: String) {
        val spans = app.downloadCache.getCachedSpans(episodeUrl)
        val cachedChunks = spans.mapIndexed { index, span ->
            Log.d(
                EpisodeFragment.TAG,
                "Cached span at index $index has file ${span.file?.name} position ${span.position} is cached? ${span.isCached} is hole? ${span.isHoleSpan} open-ended? ${span.isOpenEnded}"
            )
            span.file!!.absolutePath
        }

        /*
        // FIXME: how to prevent concurrent transcription jobs?
        // cancel any other transcription jobs before attempting this one
        workManager.cancelAllWorkByTag("transcribe").await()
        workManager.cancelAllWorkByTag("transcript_merge").await()
        workManager.pruneWork().await()
        Log.d(TAG, "Any other transcription jobs have been cancelled")
         */

        // launch transcription workers in parallel
        val workers = cachedChunks.map {
            OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(workDataOf(AUDIO_FILE_PATH_PARAM to it))
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .addTag("transcribe")
                .addTag("transcribe_$subtitleFilePath") // TODO: move
                .build()
        }

        val mergeFileWorker = OneTimeWorkRequestBuilder<TranscriptMergeWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setInputMerger(ArrayCreatingInputMerger::class)
            .addTag("transcript_merge")
            .addTag("transcript_merge_$subtitleFilePath") // TODO: move
            .build()

        workManager.beginWith(workers).then(mergeFileWorker).enqueue()
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