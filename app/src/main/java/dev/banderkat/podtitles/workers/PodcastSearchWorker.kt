package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.google.common.collect.ImmutableMap
import dev.banderkat.podtitles.network.GpodderSearchNetwork

const val PODCAST_QUERY_PARAM = "query"

const val TITLES_RESULT_KEY = "titles"
const val AUTHORS_RESULT_KEY = "authors"
const val URLS_RESULT_KEY = "urls"
const val LOGOS_RESULT_KEY = "logos"

class PodcastSearchWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "PodcastSearchWorker"
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        return try {
            val query = inputData.getString(PODCAST_QUERY_PARAM)
                ?: error("Missing PodcastFetchWorker parameter $PODCAST_URL_PARAM")
            Log.d(TAG, "going to query for podcasts that match $query")
            val results = GpodderSearchNetwork.searchResults.searchGpodderAsync(query).await()
            val titlesArray = results.map {
                it.title
            }.toTypedArray()
            val authorsArray = results.map {
                it.author
            }.toTypedArray()
            val urlsArray = results.map {
                it.url
            }.toTypedArray()
            val logosArray = results.map {
                it.logoUrl
            }.toTypedArray()
            results.forEach { result ->
                Log.d(TAG, "Search result: ${result.title} ${result.author} ${result.url} ${result.logoUrl}")
            }
            val data = Data(
                mutableMapOf(
                    TITLES_RESULT_KEY to titlesArray,
                    AUTHORS_RESULT_KEY to authorsArray,
                    URLS_RESULT_KEY to urlsArray,
                    LOGOS_RESULT_KEY to logosArray,
            )
            )
            Result.success(data)
        } catch (ex: Exception) {
            Log.e(TAG, "Podcast feed fetch failed", ex)
            Result.failure()
        }
    }
}
