package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.database.getDatabase
import dev.banderkat.podtitles.network.VoskModelNetwork

class FetchVoskModelsWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "FetchVoskModelsWorker"
    }

    private val database = getDatabase(appContext)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Going to fetch Vosk transcription models")
            val okHttpClient = (appContext as PodTitlesApplication).okHttpClient
            val models = VoskModelNetwork(okHttpClient).voskModels.getVoskModelsAsync().await()
            database.podDao.deleteAllVoskModels()
            database.podDao.addVoskModels(models)
            Result.success()
        } catch (ex: Exception) {
            Log.e(TAG, "Vosk model fetch failed", ex)
            Result.failure()
        }
    }
}
