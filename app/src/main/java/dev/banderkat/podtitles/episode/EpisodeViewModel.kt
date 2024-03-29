package dev.banderkat.podtitles.episode

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.media3.common.MediaItem
import androidx.work.*
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.workers.AUDIO_FILE_PATH_PARAM
import dev.banderkat.podtitles.workers.TranscribeWorker
import dev.banderkat.podtitles.workers.TranscriptMergeWorker
import dev.banderkat.podtitles.workers.VOSK_MODEL_PATH_PARAM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    private val _transcriptionModel = MutableLiveData("")
    val transcriptionModel: LiveData<String>
        get() = _transcriptionModel

    private val _mediaItem = MutableLiveData<MediaItem?>(null)
    val mediaItem: LiveData<MediaItem?>
        get() = _mediaItem

    private val _subtitlePath = MutableLiveData("")
    val subtitlePath: LiveData<String>
        get() = _subtitlePath

    private val _isCancelling = MutableLiveData(false)
    val isCancelling: LiveData<Boolean>
        get() = _isCancelling

    fun getEpisode(feedUrl: String, episodeGuid: String): LiveData<PodEpisode?> {
        return database.podDao.getEpisode(feedUrl, episodeGuid)
    }

    fun setMediaItem(item: MediaItem?) {
        _mediaItem.value = item
    }

    fun setSubtitlePath(path: String) {
        _subtitlePath.value = path
    }

    fun setTranscriptionModel(model: String) {
        _transcriptionModel.value = model
    }

    fun deleteEpisode(episodeUrl: String) {
        Log.d(TAG, "Deleting subtitles and cached audio from $episodeUrl")
        val subtitles = _subtitlePath.value
        if (!subtitles.isNullOrEmpty()) {
            File(subtitles).delete()
        }
        app.downloadCache.removeResource(episodeUrl)
        _transcriptionModel.postValue("")
        _mediaItem.postValue(null)
        _subtitlePath.postValue("")
    }

    /**
     * Cancels ~all~ currently running transcription jobs
     */
    fun cancelTranscription() {
        _isCancelling.value = true
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                workManager.cancelUniqueWork(TRANSCRIBE_JOB_CHAIN_TAG).await()
                workManager.pruneWork().await()
                _isCancelling.postValue(false)
            }
        }
    }

    fun onDownloadCompleted(episodeUrl: String, voskModelPath: String) {
        transcribe(episodeUrl, voskModelPath)
    }

    private fun transcribe(episodeUrl: String, voskModelPath: String) {
        val spans = app.downloadCache.getCachedSpans(episodeUrl)
        val cachedChunks = spans.map { span -> span.file!!.absolutePath }

        // create transcript workers for each downloaded audio chunk
        val workers = cachedChunks.map {
            OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(
                    workDataOf(
                        AUDIO_FILE_PATH_PARAM to it,
                        VOSK_MODEL_PATH_PARAM to voskModelPath
                    )
                )
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_MINUTES, // backoff delay default is 10 seconds
                    TimeUnit.MINUTES
                )
                .addTag(TRANSCRIBE_JOB_TAG)
                .addTag("${TRANSCRIBE_JOB_TAG}_${subtitlePath.value}")
                .build()
        }

        val mergeFileWorker = OneTimeWorkRequestBuilder<TranscriptMergeWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setInputMerger(ArrayCreatingInputMerger::class)
            .addTag(TRANSCRIPT_MERGE_JOB_TAG)
            .addTag("${TRANSCRIPT_MERGE_JOB_TAG}_${subtitlePath.value}")
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
