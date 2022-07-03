package dev.banderkat.podtitles

import android.app.Application
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dev.banderkat.podtitles.player.DOWNLOAD_CONTENT_DIRECTORY
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy

class PodTitlesApplication: Application() {

    val databaseProvider: DatabaseProvider by lazy {
        StandaloneDatabaseProvider(this)
    }

    val downloadCache: Cache by lazy {
        SimpleCache(
            File(this.filesDir, DOWNLOAD_CONTENT_DIRECTORY),
            NoOpCacheEvictor(),
            databaseProvider
        )
    }

    val httpDataSourceFactory: HttpDataSource.Factory by lazy {
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        CookieHandler.setDefault(cookieManager)
        return@lazy DefaultHttpDataSource.Factory()
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            this,
            databaseProvider,
            downloadCache,
            httpDataSourceFactory,
            Runnable::run
        )
    }

    @Synchronized
    fun getDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(
                this, httpDataSourceFactory)
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
