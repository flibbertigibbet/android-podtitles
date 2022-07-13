package dev.banderkat.podtitles.feeddetails

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodEpisode

class FeedDetailsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "SearchResultVM"
    }

    private val database = getDatabase(application)

    fun getEpisodes(feedUrl: String): LiveData<List<PodEpisode>> {
        return database.podDao.getEpisodesForFeed(feedUrl)
    }

    class Factory(val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FeedDetailsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FeedDetailsViewModel(app) as T
            }
            throw IllegalArgumentException("Unable to construct view model")
        }
    }
}
