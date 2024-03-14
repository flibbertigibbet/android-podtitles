package dev.banderkat.podtitles.managevosk

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.work.*
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.VoskModel
import dev.banderkat.podtitles.search.SearchViewModel
import dev.banderkat.podtitles.utils.Utils
import dev.banderkat.podtitles.workers.FetchVoskModelsWorker
import dev.banderkat.podtitles.workers.VOSK_MODEL_URL_PARAM
import dev.banderkat.podtitles.workers.VoskModelDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                fetchVoskModels() // re-fetch models to update status
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

    /**
     * Transforms the Vosk models fetched from the DB to set the field indicating if it is downloaded.
     */
    val voskModels = database.podDao.getVoskModels().map { models ->
        val downloadedVoskModels = Utils.getDownloadedVoskModels(application)
        models.map { model ->
            model.isDownloaded = model.name in downloadedVoskModels
            model
        }
    }

    /**
     * Deletes a model, then re-fetches the models, to refresh download status.
     * Models JSON file will be in HTTP cache.
     */
    fun deleteVoskModel(model: VoskModel) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.podDao.deleteVoskModel(model)
            }
            fetchVoskModels()
        }
    }

    override fun onCleared() {
        workManager.cancelAllWorkByTag(SearchViewModel.SEARCH_WORK_TAG)
        downloadWorker?.removeObserver(downloadObserver)
        super.onCleared()
    }

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
        _isLoading.postValue(true)
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
