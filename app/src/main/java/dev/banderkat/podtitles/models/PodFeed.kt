package dev.banderkat.podtitles.models

/**
 * Holds the top-level RSS channel element properties for a feed, plus its URL
 */
data class PodFeed (
    val id: Int,
    val url: String, // not provided in the RSS, but may be updated by the RSS
    val title: String,
    val description: String,
    val image: String?,
    val imageTitle: String?,
    val language: String?,
    val category: String?,
    val author: String?,
    val link: String?,
    val copyright: String?,
    val ttl: Int = 0,
    val pubDate: String?,
    val complete: Boolean = false
)
