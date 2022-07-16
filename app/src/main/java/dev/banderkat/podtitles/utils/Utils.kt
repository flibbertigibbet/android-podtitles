package dev.banderkat.podtitles.utils

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import dev.banderkat.podtitles.R
import dev.banderkat.podtitles.workers.TranscribeWorker
import java.io.File

/**
 * Assorted helper utilities
 */
object Utils {
    private const val TAG = "Utils"
    private const val GLIDE_LOADER_STROKE_WIDTH = 5f
    private const val GLIDE_LOADER_CENTER_RADIUS = 30f

    fun getSubtitlePathForAudioCachePath(audioCachePath: String): String {
        return "${File(audioCachePath).nameWithoutExtension}${TranscribeWorker.SUBTITLE_FILE_EXTENSION}"
    }

    fun convertToHttps(url: String): String {
        return Uri.parse(url)
            .buildUpon()
            .scheme("https")
            .build()
            .toString()
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
