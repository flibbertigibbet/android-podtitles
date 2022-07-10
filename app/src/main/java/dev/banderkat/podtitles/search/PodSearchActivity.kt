package dev.banderkat.podtitles.search

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import dev.banderkat.podtitles.databinding.ActivityPodcastSearchBinding

class PodSearchActivity : AppCompatActivity() {
    companion object {
        const val TAG = "PodSearchActivity"
    }

    private lateinit var binding: ActivityPodcastSearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.w(TAG, "Starting to create unused search activity")
        binding = ActivityPodcastSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Intent.ACTION_SEARCH == intent.action) {
            finish()
        }

        finish() // do not use this activity
    }
}