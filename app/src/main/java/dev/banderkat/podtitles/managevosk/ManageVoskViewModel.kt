package dev.banderkat.podtitles.managevosk

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.work.*
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.search.SearchViewModel
import dev.banderkat.podtitles.workers.FetchVoskModelsWorker
import dev.banderkat.podtitles.workers.VOSK_MODEL_URL_PARAM
import dev.banderkat.podtitles.workers.VoskModelDownloadWorker

class ManageVoskViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "ManageVoskVM"
        const val FETCH_VOSK_MODELS_TAG = "fetch_vosk_models"
        const val FETCH_VOSK_UNIQUE_TAG = "fetch_vosk_unique"
        const val DOWNLOAD_VOSK_MODEL_TAG = "download_vosk_model"
    }

    private val database = getDatabase(application)
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean>
        get() = _isLoading
    private val workManager: WorkManager = WorkManager.getInstance(application.applicationContext)
    private var downloadWorker: LiveData<WorkInfo>? = null
    private val downloadObserver = Observer<WorkInfo> { workInfo ->
        when (workInfo?.state) {
            WorkInfo.State.SUCCEEDED -> {
                Log.d(SearchViewModel.TAG, "Successfully downloaded Vosk model")
                _isLoading.postValue(false)
            }
            WorkInfo.State.FAILED -> {
                // TODO: display error state to user
                Log.e(SearchViewModel.TAG, "Vosk model download failed")
                _isLoading.postValue(false)
            }
            else -> {
                Log.d(
                    SearchViewModel.TAG,
                    "Vosk model downloader moved to state ${workInfo?.state}"
                )
            }
        }
    }

    override fun onCleared() {
        workManager.cancelAllWorkByTag(SearchViewModel.SEARCH_WORK_TAG)
        downloadWorker?.removeObserver(downloadObserver)
        super.onCleared()
    }

    fun getDownloadableVoskModels(downloadedModels: String) = database.podDao
        .getDownloadableVoskModels(downloadedModels)

    fun getDownloadedVoskModels(downloadedModels: String) = database.podDao
        .getDownloadedVoskModels(downloadedModels)

    fun fetchVoskModels() {
        Log.d(TAG, "Go fetch Vosk transcription models")
        workManager.cancelAllWorkByTag(FETCH_VOSK_MODELS_TAG)

        val fetchRequest = OneTimeWorkRequestBuilder<FetchVoskModelsWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag(FETCH_VOSK_MODELS_TAG)
            .build()

        workManager
            .beginUniqueWork(
                FETCH_VOSK_UNIQUE_TAG,
                ExistingWorkPolicy.REPLACE,
                fetchRequest
            ).enqueue()
    }

    fun downloadVoskModel(url: String) {
        Log.d(TAG, "Going to download Vosk model at $url")
        val downloadRequest = OneTimeWorkRequestBuilder<VoskModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .setInputData(workDataOf(VOSK_MODEL_URL_PARAM to url))
            .addTag(DOWNLOAD_VOSK_MODEL_TAG)
            .build()

        workManager.enqueue(downloadRequest)
        downloadWorker = workManager.getWorkInfoByIdLiveData(downloadRequest.id)
        downloadWorker?.observeForever(downloadObserver)
    }

    class Factory(val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SearchViewModel(app) as T
            }
            throw IllegalArgumentException("Unable to construct view model")
        }
    }
}
