package dev.banderkat.podtitles.feeddetails

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.utils.Utils
import kotlinx.coroutines.flow.map

class FeedDetailsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "SearchResultVM"
        const val PAGE_SIZE = 20
        const val INITIAL_LOAD_SIZE = PAGE_SIZE * 3
    }

    private val database = getDatabase(application)

    fun getEpisodePages(feedUrl: String) = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            initialLoadSize = INITIAL_LOAD_SIZE,
            enablePlaceholders = true,
            maxSize = PagingConfig.Companion.MAX_SIZE_UNBOUNDED
        )
    ) {
        database.podDao.getEpisodePagesForFeed(feedUrl)
    }.flow.map {
        it.map { item ->
            item.duration = Utils.getFormattedDuration(item.duration)
            item.pubDate = Utils.getFormattedDate(item.pubDate)
            item
        }
    }.cachedIn(viewModelScope)

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
