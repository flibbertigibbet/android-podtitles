package dev.banderkat.podtitles.feedlist

import android.app.Application
import android.text.format.Formatter
import androidx.lifecycle.*
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.workers.TranscriptMergeWorker.Companion.SUBTITLE_FILE_EXTENSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FeedListViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "SearchResultVM"
    }

    private val app: PodTitlesApplication = application as PodTitlesApplication
    private val database = getDatabase(application)

    val feeds = database.podDao.getAllFeeds()

    fun getTwoFeeds(feedOneUrl: String, feedTwoUrl: String): LiveData<Pair<PodFeed, PodFeed>> =
        object : MediatorLiveData<Pair<PodFeed, PodFeed>>() {
            var feedOne: PodFeed? = null
            var feedTwo: PodFeed? = null

            init {
                addSource(getFeedByUrl(feedOneUrl)) {
                    if (it != null) {
                        feedOne = it
                        this.removeSource(getFeedByUrl(feedOneUrl))
                    }
                    if (feedOne != null && feedTwo != null) value = Pair(feedOne!!, feedTwo!!)
                }

                addSource(getFeedByUrl(feedTwoUrl)) {
                    if (it != null) {
                        feedTwo = it
                        this.removeSource(getFeedByUrl(feedTwoUrl))
                    }
                    if (feedOne != null && feedTwo != null) value = Pair(feedOne!!, feedTwo!!)
                }
            }
        }

    fun getFeedByUrl(url: String) = database.podDao.getFeed(url)

    fun updateFeedPair(feeds: Pair<PodFeed, PodFeed>) {
        viewModelScope.launch(Dispatchers.IO) {
            database.podDao.updateFeed(feeds.first)
            database.podDao.updateFeed(feeds.second)
        }
    }

    fun getTranscriptsSize(): String = Formatter.formatShortFileSize(app,
        app.fileList().fold(0) { acc, appFile ->
            if (appFile.endsWith(SUBTITLE_FILE_EXTENSION, true)) {
                acc + app.getFileStreamPath(appFile).length()
            } else {
                acc
            }
        }
    )

    fun getDownloadCacheSize(): String = Formatter
        .formatShortFileSize(app, app.downloadCache.cacheSpace)

    fun deleteFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            app.downloadCache.apply {
                keys.forEach { cacheKey ->
                    removeResource(cacheKey)
                }
            }

            app.fileList().forEach { appFile ->
                if (appFile.endsWith(SUBTITLE_FILE_EXTENSION, true)) {
                    app.deleteFile(appFile)
                }
            }
        }

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
