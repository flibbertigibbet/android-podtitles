package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.utils.PodcastFeedParser

const val PODCAST_URL_PARAM = "url"
const val PODCAST_ORDER_PARAM = "order"

/**
 * Fetches the RSS for a podcast feed in the background, parses it, then stores it to the database.
 */
class PodcastFetchWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "PodcastFetchWorker"
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        return try {
            val url = inputData.getString(PODCAST_URL_PARAM)
                ?: error("Missing PodcastFetchWorker parameter $PODCAST_URL_PARAM")
            val displayOrder = inputData.getInt(PODCAST_ORDER_PARAM, -1)
            Log.d(TAG, "going to fetch podcast feed from $url")
            val okHttpClient = (appContext as PodTitlesApplication).okHttpClient
            PodcastFeedParser(applicationContext, okHttpClient, url, displayOrder).fetchFeed()
            Result.success()
        } catch (ex: Exception) {
            Log.e(TAG, "Podcast feed fetch failed", ex)
            Result.failure()
        }
    }
}
