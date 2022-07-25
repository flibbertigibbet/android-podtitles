package dev.banderkat.podtitles.utils

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.*
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.workers.PODCAST_ORDER_PARAM
import dev.banderkat.podtitles.workers.PODCAST_URL_PARAM
import dev.banderkat.podtitles.workers.PodcastFetchWorker

/**
 * Helper to get the current maximum display order for any existing feed,
 * then go fetch and add a new feed to the end of the existing display ordering.
 */
class AddFeed(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val feedUrl: String,
    private val listener: (success: Boolean) -> Unit
) {
    companion object {
        const val TAG = "AddFeed"
        const val FETCH_WORK_TAG = "fetch_podcast"
        const val FETCH_UNIQUE_WORK_TAG = "fetch_podcast_unique"
    }

    private val database = getDatabase(context)

    private val maxValObserver = Observer<Int?> {
        fetchPodcast(it ?: 0)
    }

    init {
        database.podDao.getMaxFeedDisplayOrder().observe(lifecycleOwner, maxValObserver)
    }

    private fun fetchPodcast(displayOrder: Int) {
        database.podDao.getMaxFeedDisplayOrder().removeObserver(maxValObserver)

        val fetchPodRequest = OneTimeWorkRequestBuilder<PodcastFetchWorker>()
            .setInputData(
                workDataOf(
                    PODCAST_URL_PARAM to feedUrl,
                    PODCAST_ORDER_PARAM to displayOrder
                )
            )
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .addTag(FETCH_WORK_TAG)
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager
            .beginUniqueWork(
                FETCH_UNIQUE_WORK_TAG,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                fetchPodRequest
            ).enqueue()

        workManager
            .getWorkInfoByIdLiveData(fetchPodRequest.id)
            .observe(lifecycleOwner) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        Log.d(TAG, "Podcast fetcher successfully fetched feed at $feedUrl")
                        listener.invoke(true)
                    }
                    WorkInfo.State.FAILED -> {
                        Log.e(TAG, "Podcast fetch worker failed")
                        listener.invoke(false)
                    }
                    else -> {
                        Log.d(TAG, "Podcast fetch moved to state ${workInfo?.state}")
                    }
                }
            }
    }
}