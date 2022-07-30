package dev.banderkat.podtitles

import android.app.Application
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dev.banderkat.podtitles.player.DOWNLOAD_CONTENT_DIRECTORY
import okhttp3.OkHttpClient
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

class PodTitlesApplication : Application() {
    companion object {
        const val HTTP_CACHE_DIR = "http_cache"
        const val CACHE_MAX_SIZE = 50L * 1024L * 1024L // 50 MiB
        const val READ_TIMEOUT_SECONDS = 20L
        const val APP_UA = "Android Podtitles https://github.com/flibbertigibbet/android-podtitles"
    }

    // ExoPlayer causing strict mode violations
    // init { StrictMode.enableDefaults() }

    // Caching OkHttp client
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(
                okhttp3.Cache(
                    directory = File(cacheDir, HTTP_CACHE_DIR),
                    maxSize = CACHE_MAX_SIZE
                )
            )
            .hostnameVerifier { _, _ -> true } // many feeds fail hostname verification
            .addNetworkInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header("User-Agent", APP_UA)
                        .build()
                )
            }
            .build()
    }

    // Cache management singletons for ExoPlayer
    private val databaseProvider: DatabaseProvider by lazy {
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

