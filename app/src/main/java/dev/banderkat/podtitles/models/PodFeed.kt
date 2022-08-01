package dev.banderkat.podtitles.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

const val FEED_TABLE_NAME = "feed"
/**
 * Holds the top-level RSS channel element properties for a feed, plus its URL
 */
@Parcelize
@Entity(tableName = FEED_TABLE_NAME)
data class PodFeed (
    @PrimaryKey
    val url: String, // not provided in the RSS, but may be updated from the RSS if changed
    val title: String,
    val description: String,
    @ColumnInfo(index = true)
    var displayOrder: Int, // not from the RSS; order to display it in feed list
    val image: String = "",
    val imageTitle: String = "",
    @ColumnInfo(index = true)
    val language: String = "",
    val category: String = "",
    val subCategory: String = "",
    val author: String = "",
    val link: String = "",
    val copyright: String = "",
    val newUrl: String = "",
    @ColumnInfo(defaultValue = "0")
    val ttl: Int = 0,
    val pubDate: String = "",
    @ColumnInfo(defaultValue = "false")
    val complete: Boolean = false
): Parcelable {
    override fun toString(): String {
        return "PodFeed (title: $title URL: $url)"
    }
}
