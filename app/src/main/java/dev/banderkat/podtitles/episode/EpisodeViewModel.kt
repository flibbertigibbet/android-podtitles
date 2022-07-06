package dev.banderkat.podtitles.episode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class EpisodeViewModel : ViewModel() {
    companion object {
        const val TAG = "EpisodeVM"
    }

    private val _text = MutableLiveData<String>().apply {
        value = "This is the episode Fragment" // TODO: remove
    }
    val text: LiveData<String> = _text
}