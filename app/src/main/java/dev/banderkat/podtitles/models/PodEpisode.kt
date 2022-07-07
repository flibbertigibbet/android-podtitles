package dev.banderkat.podtitles.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Holds the RSS child elements of a channel (modeled in PodFeed)
 */
@Entity("episode", foreignKeys = [ForeignKey(
    entity = PodFeed::class,
    parentColumns = arrayOf("id"),
    childColumns = arrayOf("feedId"),
    onDelete = ForeignKey.CASCADE
)])
data class PodEpisode constructor(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(index = true)
    val feedId: Int, // ID of the parent PodFeed
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
    val episode: Int?,
    val season: Int?,
    val episodeType: String?
)