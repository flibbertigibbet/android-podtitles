package dev.banderkat.podtitles.episode

import android.app.Application
import androidx.lifecycle.*
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.models.PodFeed
import dev.banderkat.podtitles.search.SearchResultViewModel

class EpisodeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "EpisodeViewModel"
    }

    private val database = getDatabase(application)

    class Factory(val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(EpisodeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return EpisodeViewModel(app) as T
            }
            throw IllegalArgumentException("Unable to construct view model")
        }
    }
}