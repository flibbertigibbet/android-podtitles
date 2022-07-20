package dev.banderkat.podtitles.models

data class PodEpisodeItem (
    val guid: String,
    val title: String,
    var duration: String,
    var pubDate: String
)
