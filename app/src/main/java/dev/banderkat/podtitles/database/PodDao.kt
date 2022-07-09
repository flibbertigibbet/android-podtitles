package dev.banderkat.podtitles.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.*
import dev.banderkat.podtitles.models.EPISODE_TABLE_NAME
import dev.banderkat.podtitles.models.FEED_TABLE_NAME
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized

const val DATABASE_NAME = "podtitles"

@Dao
interface  PodDao {
    @Query("SELECT * FROM $FEED_TABLE_NAME WHERE url = :url")
    fun getFeed(url: String): LiveData<PodFeed>

    @Query("SELECT * FROM $EPISODE_TABLE_NAME WHERE feedId = :feedUrl")
    fun getEpisodesForFeed(feedUrl: String): LiveData<List<PodEpisode>>

    @Query("SELECT * FROM $EPISODE_TABLE_NAME WHERE guid = :guid AND feedId = :feedUrl")
    fun getEpisode(feedUrl: String, guid: String): LiveData<PodEpisode?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addFeed(feed: PodFeed): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addEpisode(episode: PodEpisode): Long

    @Update
    fun updateFeed(feed: PodFeed)

    @Update
    fun updateEpisode(episode: PodEpisode)

    @Delete
    fun deleteFeed(feed: PodFeed)

    @Delete
    fun deleteEpisode(episode: PodEpisode)

    @Query("DELETE FROM $FEED_TABLE_NAME")
    fun deleteAllFeeds() // will also cascade to delete all episodes
}

@Database(
    entities=[PodFeed::class, PodEpisode::class],
    version=1,
    exportSchema = true
)
abstract class PodDatabase: RoomDatabase() {
    abstract val podDao: PodDao
}

private lateinit var INSTANCE: PodDatabase

@OptIn(InternalCoroutinesApi::class)
fun getDatabase(context: Context): PodDatabase {
    synchronized(PodDatabase::class.java) {
        if (!::INSTANCE.isInitialized) {
            INSTANCE = Room.databaseBuilder(
                context.applicationContext,
                PodDatabase::class.java,
                DATABASE_NAME
            ).build()
        }
    }
    return INSTANCE
}