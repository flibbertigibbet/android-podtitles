package dev.banderkat.podtitles.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

const val EPISODE_TABLE_NAME = "episode"
/**
 * Holds the RSS child elements of a channel (modeled in PodFeed)
 */
@Entity(
    tableName = EPISODE_TABLE_NAME,
    primaryKeys = ["feedId", "guid"],
    foreignKeys = [ForeignKey(
    entity = PodFeed::class,
    parentColumns = arrayOf("url"),
    childColumns = arrayOf("feedId"),
    onDelete = ForeignKey.CASCADE
)])
@Parcelize
data class PodEpisode constructor(
    @ColumnInfo(index = true)
    val guid: String, // should be same as URL if GUID not provided in feed
    @ColumnInfo(index = true)
    var feedId: String, // ID of the parent PodFeed
    val url: String,
    val title: String,
    val mediaType: String,
    val size: Int,
    val link: String = "",
    val description: String = "",
    val duration: String = "",
    val pubDate: String = "",
    val image: String = "",
    val category: String = "",
    val episode: Int = 0,
    val season: Int = 0,
    val episodeType: String = ""
): Parcelable {
    override fun toString(): String {
        return "PodEpisode (GUID: $guid title: $title)"
    }
}