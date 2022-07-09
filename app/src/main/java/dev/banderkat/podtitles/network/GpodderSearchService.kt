package dev.banderkat.podtitles.network

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.banderkat.podtitles.models.GpodderSearchResult
import kotlinx.coroutines.Deferred
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface GpodderSearchService {
    // https://gpoddernet.readthedocs.io/en/latest/api/reference/directory.html#podcast-search
    @GET("search.json")
    fun searchGpodderAsync(
        @Query("q") query: String
    ): Deferred<List<GpodderSearchResult>>
}

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

object GpodderSearchNetwork {
    private const val baseUrl = "https://gpodder.net/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .build()

    val searchResults: GpodderSearchService = retrofit.create(GpodderSearchService::class.java)
}
