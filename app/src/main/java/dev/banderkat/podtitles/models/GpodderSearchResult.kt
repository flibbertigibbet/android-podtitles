package dev.banderkat.podtitles.models

data class GpodderSearchResult(
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    val logoUrl: String?,
    val subscribers: Int?,
    val website: String?
)
