package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.network.GpodderSearchNetwork

const val PODCAST_QUERY_PARAM = "query"

class PodcastSearchWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "PodcastSearchWorker"
    }

    private val database = getDatabase(appContext)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        return try {
            val query = inputData.getString(PODCAST_QUERY_PARAM)
                ?: error("Missing PodcastFetchWorker parameter $PODCAST_URL_PARAM")
            Log.d(TAG, "going to query for podcasts that match $query")
            val results = GpodderSearchNetwork.searchResults.searchGpodderAsync(query).await()
            database.podDao.deleteAllSearchResults()
            database.podDao.addGpodderResults(results)
            Result.success()
        } catch (ex: Exception) {
            Log.e(TAG, "Podcast search failed", ex)
            Result.failure()
        }
    }
}
