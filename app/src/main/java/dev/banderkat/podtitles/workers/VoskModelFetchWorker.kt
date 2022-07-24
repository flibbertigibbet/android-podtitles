package dev.banderkat.podtitles.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.banderkat.podtitles.PodTitlesApplication
import dev.banderkat.podtitles.utils.Utils
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File

const val VOSK_MODEL_URL_PARAM = "url"

/**
 * Fetches the RSS for a podcast feed in the background, parses it, then stores it to the database.
 */
class VoskModelFetchWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    companion object {
        const val TAG = "VoskModelFetchWorker"
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        return try {
            val url = inputData.getString(VOSK_MODEL_URL_PARAM)
                ?: error("Missing $TAG parameter $VOSK_MODEL_URL_PARAM")
            Log.d(TAG, "going to fetch Vosk model from $url")
            fetchVoskModel(url)
            Result.success()
        } catch (ex: Exception) {
            Log.e(TAG, "Vosk model fetch failed", ex)
            Result.failure()
        }
    }

    private fun fetchVoskModel(url: String) {
        val okHttpClient = (appContext as PodTitlesApplication).okHttpClient
        val request: Request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                // create directory and/or file for downloaded model, as needed
                val outputFilePath = Utils.getVoskModelPathForUrl(appContext, url)
                val folder = File(appContext.cacheDir, Utils.VOSK_DIR)
                if (!folder.exists()) folder.mkdir()
                val file = File(outputFilePath)
                // replace any existing download with the same name
                if (file.exists()) file.delete()
                file.createNewFile()

                file.sink().buffer().use { sink -> sink.writeAll(response.body!!.source()) }
            } else {
                error("Vosk model request failed with HTTP code ${response.code}")
            }
        }
    }
}
