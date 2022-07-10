package dev.banderkat.podtitles.models

import com.squareup.moshi.Json

data class GpodderSearchResult(
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    @Json(name = "logo_url")
    val logoUrl: String?,
    val subscribers: Int?,
    val website: String?
)
