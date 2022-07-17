package dev.banderkat.podtitles

import android.app.Application
import android.os.StrictMode
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.work.Configuration
import dev.banderkat.podtitles.player.DOWNLOAD_CONTENT_DIRECTORY
import okhttp3.OkHttpClient
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.Executors

class PodTitlesApplication : Application(), Configuration.Provider {
    companion object {
        const val httpCacheDir = "http_cache"
        const val cacheMaxSize = 50L * 1024L * 1024L // 50 MiB
        const val maxThreads = 2
    }

    // ExoPlayer causing strict mode violations
    // init { StrictMode.enableDefaults() }

    // Customize WorkManager initialization to limit concurrency
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .setExecutor(Executors.newFixedThreadPool(maxThreads))
            .build()

    // Caching OkHttp client
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(
                okhttp3.Cache(
                    directory = File(cacheDir, httpCacheDir),
                    maxSize = cacheMaxSize
                )
            )
            .hostnameVerifier { _, _ -> true } // many feeds fail hostname verification
            .build()
    }

    // Cache management singletons for ExoPlayer
    val databaseProvider: DatabaseProvider by lazy {
        StandaloneDatabaseProvider(this)
    }

    val downloadCache: androidx.media3.datasource.cache.Cache by lazy {
        SimpleCache(
            File(this.filesDir, DOWNLOAD_CONTENT_DIRECTORY),
            NoOpCacheEvictor(),
            databaseProvider
        )
    }

    val dataSourceFactory: DefaultDataSource.Factory by lazy {
        val cookieManager = CookieManager()
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        CookieHandler.setDefault(cookieManager)
        return@lazy DefaultDataSource.Factory(this)
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            this,
            databaseProvider,
            downloadCache,
            dataSourceFactory,
            Runnable::run
        )
    }

    @Synchronized
    fun getDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                DefaultDataSource.Factory(
                    this, dataSourceFactory
                )
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}

