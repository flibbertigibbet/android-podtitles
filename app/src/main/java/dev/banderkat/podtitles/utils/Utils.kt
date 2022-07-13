package dev.banderkat.podtitles.utils

import android.net.Uri

/**
 * Assorted helper utilities
 */
object Utils {
    fun convertToHttps(url: String): String {
        return Uri.parse(url)
            .buildUpon()
            .scheme("https")
            .build()
            .toString()
    }
}