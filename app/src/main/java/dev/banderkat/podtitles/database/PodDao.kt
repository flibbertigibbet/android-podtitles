package dev.banderkat.podtitles.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.paging.PagingSource
import androidx.room.*
import dev.banderkat.podtitles.models.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized

const val DATABASE_NAME = "podtitles"

@Dao
interface PodDao {
    @Query("SELECT * FROM $FEED_TABLE_NAME WHERE url = :url")
    fun getFeed(url: String): LiveData<PodFeed?>

    @Query("SELECT * FROM $FEED_TABLE_NAME ORDER BY displayOrder ASC")
    fun getAllFeeds(): LiveData<List<PodFeed>>

    @Query("SELECT MAX(displayOrder) from $FEED_TABLE_NAME")
    fun getMaxFeedDisplayOrder(): LiveData<Int?>

    @Query("SELECT guid, title, duration, pubDate FROM $EPISODE_TABLE_NAME WHERE feedId = :feedUrl ORDER BY pubDate DESC")
    fun getEpisodePagesForFeed(feedUrl: String): PagingSource<Int, PodEpisodeItem>

    @Query("SELECT * FROM $EPISODE_TABLE_NAME WHERE feedId = :feedUrl")
    fun getEpisodesForFeed(feedUrl: String): LiveData<List<PodEpisode>>

    @Query("SELECT * FROM $EPISODE_TABLE_NAME WHERE guid = :guid AND feedId = :feedUrl")
    fun getEpisode(feedUrl: String, guid: String): LiveData<PodEpisode?>

    @Query("SELECT * FROM $SEARCH_RESULT_TABLE_NAME ORDER BY subscribers DESC")
    fun getSearchResults(): LiveData<List<GpodderSearchResult>>

    @Query("SELECT * FROM $VOSK_MODEL_TABLE_NAME WHERE type = 'small' AND obsolete = 'false' ORDER BY langText ASC")
    fun getVoskModels(): LiveData<List<VoskModel>>

    @Query("SELECT * FROM $VOSK_MODEL_TABLE_NAME WHERE name = :name")
    fun getVoskModel(name: String): LiveData<VoskModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addFeed(feed: PodFeed): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addEpisode(episode: PodEpisode): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addEpisodes(episodes: List<PodEpisode>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun addGpodderResults(results: List<GpodderSearchResult>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addVoskModels(models: List<VoskModel>)

    @Update
    fun updateFeed(feed: PodFeed)

    @Update
    fun updateEpisode(episode: PodEpisode)

    @Update
    fun updateVoskModel(model: VoskModel)

    @Delete
    fun deleteFeed(feed: PodFeed)

    @Delete
    fun deleteEpisode(episode: PodEpisode)

    @Query("DELETE FROM $EPISODE_TABLE_NAME WHERE feedId = :feedUrl")
    fun deleteAllEpisodesForFeed(feedUrl: String)

    @Delete
    fun deleteVoskModel(voskModel: VoskModel)

    @Query("DELETE FROM $FEED_TABLE_NAME")
    fun deleteAllFeeds() // will also cascade to delete all episodes

    @Query("DELETE FROM $SEARCH_RESULT_TABLE_NAME")
    fun deleteAllSearchResults()

    @Query("DELETE FROM $VOSK_MODEL_TABLE_NAME")
    fun deleteAllVoskModels()
}

@Database(
    entities = [
        PodFeed::class,
        PodEpisode::class,
        GpodderSearchResult::class,
        VoskModel::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PodDatabase : RoomDatabase() {
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