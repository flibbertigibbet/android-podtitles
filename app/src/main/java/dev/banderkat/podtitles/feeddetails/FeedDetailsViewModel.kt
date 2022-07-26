package dev.banderkat.podtitles.feeddetails

import android.app.Application
import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FeedDetailsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "SearchResultVM"
        const val PAGE_SIZE = 20
        const val INITIAL_LOAD_SIZE = PAGE_SIZE * 3
    }

    private val app: PodTitlesApplication = application as PodTitlesApplication
    private val database = getDatabase(application)

    private val _feedDeleted = MutableLiveData(false)
    val feedDeleted: LiveData<Boolean>
        get() = _feedDeleted

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

    /**
     * Delete cached audio and generated subtitles for each episode in the feed, if present,
     * then delete the feed from the database.
     */
    fun deleteFeed(feed: PodFeed) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.podDao.getEpisodesForFeed(feed.url).value?.forEach { episode ->
                    if (episode.url in app.downloadCache.keys) {
                        val firstSpan = app.downloadCache.getCachedSpans(episode.url).first().file
                        val subtitlesPath = if (firstSpan != null) {
                            Utils.getSubtitles(app, firstSpan.path)
                        } else null
                        if (subtitlesPath != null) File(subtitlesPath).delete()
                        app.downloadCache.removeResource(episode.url)
                    }
                }

                database.podDao.deleteFeed(feed)
                _feedDeleted.postValue(true)
            }
        }
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
