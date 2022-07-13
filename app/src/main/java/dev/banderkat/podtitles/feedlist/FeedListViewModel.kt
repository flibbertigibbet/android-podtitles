package dev.banderkat.podtitles.feedlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodFeed

class FeedListViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "SearchResultVM"
    }

    private val database = getDatabase(application)

    val feeds = database.podDao.getAllFeeds()

    fun getFeed(feedUrl: String): LiveData<PodFeed?> {
        return database.podDao.getFeed(feedUrl)
    }

    class Factory(val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeedListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FeedListViewModel(app) as T
            }
            throw IllegalArgumentException("Unable to construct view model")
        }
    }
}
