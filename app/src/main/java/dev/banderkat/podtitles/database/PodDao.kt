package dev.banderkat.podtitles.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.banderkat.podtitles.models.PodEpisode
import dev.banderkat.podtitles.models.PodFeed
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized

const val DATABASE_NAME = "podtitles"

@Dao
interface  PodDao {

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