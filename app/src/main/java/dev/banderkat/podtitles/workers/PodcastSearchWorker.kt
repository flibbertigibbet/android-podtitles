package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.banderkat.podtitles.network.GpodderSearchNetwork

const val PODCAST_QUERY_PARAM = "query"

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
            results.forEach { result ->
                Log.d(TAG, "Search result: ${result.title} ${result.author} ${result.url}")
            }
            Result.success()
        } catch (ex: Exception) {
            Log.e(TAG, "Podcast feed fetch failed", ex)
            Result.failure()
        }
    }
}
