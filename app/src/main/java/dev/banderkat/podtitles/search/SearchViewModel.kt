package dev.banderkat.podtitles.search

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.work.*
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.workers.PODCAST_QUERY_PARAM
import dev.banderkat.podtitles.workers.PodcastSearchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "SearchViewModel"
        const val SEARCH_WORK_TAG = "podtitles_search"
        const val SEARCH_DEBOUNCE_MS = 500L
    }

    private val database = getDatabase(application)
    private val workManager: WorkManager = WorkManager.getInstance(application.applicationContext)
    private var searchWorkers: LiveData<WorkInfo>? = null
    private val searchObserver = Observer<WorkInfo> { workInfo ->
        when (workInfo?.state) {
            WorkInfo.State.SUCCEEDED -> {
                Log.d(TAG, "Successfully got search results")
                _isLoading.postValue(false)
            }
            WorkInfo.State.FAILED -> {
                // TODO: display error state to user
                Log.e(TAG, "Search worker failed")
                _isLoading.postValue(false)
            }
            else -> {
                Log.d(TAG, "Search worker moved to state ${workInfo?.state}")
            }
        }
    }

    val searchResults = database.podDao.getSearchResults()

    private val _searchQuery = MutableLiveData("")
    private val _isLoading = MutableLiveData(false)

    /**
     * Store the last issued query to restore state on navigating back from viewing a result
     */
    val searchQuery: LiveData<String>
        get() = _searchQuery

    val isLoading: LiveData<Boolean>
        get() = _isLoading

    init {
        clearSearch()
    }

    override fun onCleared() {
        workManager.cancelAllWorkByTag(SEARCH_WORK_TAG)
        searchWorkers?.removeObserver(searchObserver)
        super.onCleared()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.postValue(query)
        if (query.isBlank()) clearSearch() else searchPodcasts(query)
    }

    private fun clearSearch() {
        viewModelScope.launch(Dispatchers.IO) {
            database.podDao.deleteAllSearchResults()
        }
    }

    private fun searchPodcasts(query: String) {
        Log.d(TAG, "Go search for podcasts with $query")
        _isLoading.postValue(true)

        // cancel any previous searches
        workManager.cancelAllWorkByTag(SEARCH_WORK_TAG)

        val searchRequest = OneTimeWorkRequestBuilder<PodcastSearchWorker>()
            .setInitialDelay(SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS) // debounce
            .setInputData(workDataOf(PODCAST_QUERY_PARAM to query))
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag(SEARCH_WORK_TAG)
            .build()

        workManager.enqueue(searchRequest)
        val searchWorkers = workManager.getWorkInfoByIdLiveData(searchRequest.id)
        searchWorkers.observeForever(searchObserver)
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