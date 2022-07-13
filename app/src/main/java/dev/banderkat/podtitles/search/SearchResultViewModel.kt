package dev.banderkat.podtitles.search

import android.app.Application
import androidx.lifecycle.*
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchResultViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "SearchResultVM"
    }

    private val database = getDatabase(application)

    fun getFeed(feedUrl: String): LiveData<PodFeed?> {
        return database.podDao.getFeed(feedUrl)
    }

    fun removeFeed(feed: PodFeed) {
        viewModelScope.launch(Dispatchers.IO) {
            database.podDao.deleteFeed(feed)
        }
    }

    class Factory(val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchResultViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SearchResultViewModel(app) as T
            }
            throw IllegalArgumentException("Unable to construct view model")
        }
    }
}