package dev.banderkat.podtitles.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.workers.TranscribeWorker
import dev.banderkat.podtitles.workers.TranscriptMergeWorker
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * Assorted helper utilities
 */
object Utils {
    private const val TAG = "Utils"
    private const val GLIDE_LOADER_STROKE_WIDTH = 5f
    private const val GLIDE_LOADER_CENTER_RADIUS = 30f
    private const val TIME_MULTIPLIER = 60

    private val utcTimeZone = TimeZone.getTimeZone("UTC")
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").apply {
        timeZone = utcTimeZone
    }
    private val dateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
    private val pubDateFormat = SimpleDateFormat(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        Locale.getDefault()
    ).apply {
        timeZone = utcTimeZone
    }

    /**
     * Convert RFC 822 formatted podcast publication dates to ISO 8601, for DB sorting.
     */
    fun getIsoDate(dateStr: String): String {
        return try {
            isoDateFormat.format(pubDateFormat.parse(dateStr)!!)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to parse podcast date $dateStr", ex)
            ""
        }
    }

    /**
     * Convert ISO 8601 dates from the DB to presentation format.
     */
    fun getFormattedDate(dateStr: String): String {
        return try {
            dateFormatter.format(isoDateFormat.parse(dateStr)!!)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to format date $dateStr for presentation", ex)
            dateStr
        }
    }

    /**
     * Parse episode durations, which may be in seconds, HH:mm:ss, or some other format.
     */
    fun getFormattedDuration(duration: String): String {
        return try {
            // first try to parse it as seconds (recommended in standard)
            duration.toInt().seconds.toString()
        } catch (ex: Exception) {
            try {
                // next try to parse it as a duration string
                val parts = duration.split(":")
                var seconds = parts.last().toInt()
                if (parts.size > 1) seconds += parts[parts.size - 2].toInt() * TIME_MULTIPLIER
                if (parts.size == 3) seconds += parts.first()
                    .toInt() * TIME_MULTIPLIER * TIME_MULTIPLIER
                seconds.seconds.toString()
            } catch (ex: Exception) {
                Log.w(TAG, "Failed to parse duration $duration", ex)
                // use it as-is
                duration
            }
        }
    }

    fun getSubtitlePathForCachePath(cachePath: String): String {
        return "${File(cachePath).nameWithoutExtension}${TranscriptMergeWorker.SUBTITLE_FILE_EXTENSION}"
    }

    fun getIntermediateResultsPathForAudioCachePath(audioCachePath: String): String {
        return "${File(audioCachePath).nameWithoutExtension}.json"
    }

    fun getWavPathForAudioCachePath(audioCachePath: String): String {
        return "${File(audioCachePath).absoluteFile.nameWithoutExtension}.wav"
    }

    fun convertToHttps(url: String): String {
        return if (url.isNotBlank()) {
            Uri.parse(url)
                .buildUpon()
                .scheme("https")
                .build()
                .toString()
        } else {
            ""
        }
    }

    fun loadLogo(logoUrl: String?, context: Context, imageView: ImageView) {
        if (logoUrl.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.ic_headphones)
        } else {
            val circularProgressDrawable = CircularProgressDrawable(context)
            circularProgressDrawable.strokeWidth = GLIDE_LOADER_STROKE_WIDTH
            circularProgressDrawable.centerRadius = GLIDE_LOADER_CENTER_RADIUS
            circularProgressDrawable.start()

            Glide.with(context)
                .load(logoUrl)
                .placeholder(circularProgressDrawable)
                .fitCenter()
                .error(R.drawable.ic_headphones)
                .into(imageView)
        }
    }
}
