package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.utils.PodcastFeedParser
import dev.banderkat.podtitles.utils.Utils

const val PODCAST_URL_PARAM = "url"
const val PODCAST_ORDER_PARAM = "order"

/**
 * Fetches the RSS for a podcast feed in the background, parses it, then stores it to the database.
 */
class PodcastFetchWorker(private val appContext: Context, workerParams: WorkerParameters) :
Worker(appContext, workerParams) {
    companion object {
        const val TAG = "PodcastFetchWorker"
    }

    @Suppress("TooGenericExceptionCaught")
    override fun doWork(): Result {
        return try {
            val url = inputData.getString(PODCAST_URL_PARAM)
                ?: error("Missing PodcastFetchWorker parameter $PODCAST_URL_PARAM")
            val displayOrder = inputData.getInt(PODCAST_ORDER_PARAM, -1)
            val httpsUrl = Utils.convertToHttps(url)
            Log.d(TAG, "going to fetch podcast feed from $httpsUrl")
            val okHttpClient = (appContext as PodTitlesApplication).okHttpClient
            PodcastFeedParser(applicationContext, okHttpClient, httpsUrl, displayOrder).fetchFeed()
            Result.success()
        } catch (ex: Exception) {
            Log.e(TAG, "Podcast feed fetch failed", ex)
            Result.failure()
        }
    }
}