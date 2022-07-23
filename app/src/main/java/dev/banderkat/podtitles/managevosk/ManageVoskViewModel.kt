package dev.banderkat.podtitles.managevosk

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.search.SearchViewModel
import dev.banderkat.podtitles.workers.FetchVoskModelsWorker

class ManageVoskViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "ManageVoskVM"
        const val FETCH_VOSK_MODELS_TAG = "fetch_vosk_models"
        const val FETCH_VOSK_UNIQUE_TAG = "fetch_vosk_unique"
    }

    private val database = getDatabase(application)
    private val workManager: WorkManager = WorkManager.getInstance(application.applicationContext)

    val downloadableVoskModels = database.podDao.getDownloadableVoskModels()

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