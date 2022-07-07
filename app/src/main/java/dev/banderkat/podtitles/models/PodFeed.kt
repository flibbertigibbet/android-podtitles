package dev.banderkat.podtitles.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Holds the top-level RSS channel element properties for a feed, plus its URL
 */
@Entity(tableName = "feed")
data class PodFeed (
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(index = true)
    val url: String, // not provided in the RSS, but may be updated from the RSS if changed
    val title: String,
    val description: String,
    val image: String?,
    val imageTitle: String?,
    @ColumnInfo(index = true)
    val language: String?,
    val category: String?,
    val author: String?,
    val link: String?,
    val copyright: String?,
    val ttl: Int = 0,
    val pubDate: String?,
    val complete: Boolean = false
)
