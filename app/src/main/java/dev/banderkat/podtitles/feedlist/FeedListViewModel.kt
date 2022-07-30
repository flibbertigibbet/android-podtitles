package dev.banderkat.podtitles.feedlist

import android.app.Application
import android.text.format.Formatter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.workers.TranscriptMergeWorker.Companion.SUBTITLE_FILE_EXTENSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedListViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "SearchResultVM"
    }

    private val app: PodTitlesApplication = application as PodTitlesApplication
    private val database = getDatabase(application)

    val feeds = database.podDao.getAllFeeds()

    fun getFeedByUrl(url: String) = database.podDao.getFeed(url)

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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
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
