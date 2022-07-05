package dev.banderkat.podtitles.episode

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class EpisodeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is episode Fragment"
    }
    val text: LiveData<String> = _text
}