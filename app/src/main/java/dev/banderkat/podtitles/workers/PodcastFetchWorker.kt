package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.banderkat.podtitles.utils.PodcastFeedParser

const val PODCAST_URL_PARAM = "url"

/**
 * Fetches the RSS for a podcast feed in the background, parses it, then stores it to the database.
 */
class PodcastFetchWorker(appContext: Context, workerParams: WorkerParameters) :
Worker(appContext, workerParams) {
    @Suppress("TooGenericExceptionCaught")
    override fun doWork(): Result {
        return try {
            val url = inputData.getString(PODCAST_URL_PARAM)
                ?: error("Missing PodcastFetchWorker parameter $PODCAST_URL_PARAM")
            Log.d(TAG, "going to fetch podcast feed from $url")
            PodcastFeedParser(applicationContext, url).fetchFeed()
            Result.success()
        } catch (ex: Exception) {
            Log.e(TAG, "Podcast feed fetch failed", ex)
            Result.failure()
        }
    }
}