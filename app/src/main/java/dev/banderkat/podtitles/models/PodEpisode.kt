package dev.banderkat.podtitles.models

/**
 * Holds the RSS child elements of a channel (modeled in PodFeed)
 */
data class PodEpisode (
    val id: Int,
    val feedId: Int, // ID of the parent PodFeed
    val title: String,
    val url: String,
    val mediaType: String,
    val size: Int,
    val link: String?,
    val description: String?,
    val duration: String?,
    val guid: String?,
    val pubDate: String?,
    val image: String?,
    val episode: Int?,
    val season: Int?,
    val episodeType: String?
)