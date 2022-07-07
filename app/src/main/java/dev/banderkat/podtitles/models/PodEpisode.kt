package dev.banderkat.podtitles.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import dev.banderkat.podtitles.utils.DynamicInitializer

const val EPISODE_TABLE_NAME = "episode"
/**
 * Holds the RSS child elements of a channel (modeled in PodFeed)
 */
@Entity(
    EPISODE_TABLE_NAME, foreignKeys = [ForeignKey(
    entity = PodFeed::class,
    parentColumns = arrayOf("id"),
    childColumns = arrayOf("feedId"),
    onDelete = ForeignKey.CASCADE
)])
data class PodEpisode constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(index = true)
    val feedId: Long, // ID of the parent PodFeed
    val title: String,
    @ColumnInfo(index = true)
    val url: String,
    val mediaType: String,
    val size: Int,
    val link: String?,
    val description: String?,
    val duration: String?,
    @ColumnInfo(index = true)
    val guid: String?,
    val pubDate: String?,
    val image: String?,
    val category: String?,
    val episode: Int?,
    val season: Int?,
    val episodeType: String?
) {
    override fun toString(): String {
        return "PodEpisode id: $id guid: $guid title: $title"
    }
}