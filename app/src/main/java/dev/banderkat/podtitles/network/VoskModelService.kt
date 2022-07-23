package dev.banderkat.podtitles.network

import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.banderkat.podtitles.models.VoskModel
import kotlinx.coroutines.Deferred
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

interface VoskModelService {
    @GET("vosk/models/model-list.json")
    fun getVoskModelsAsync(): Deferred<List<VoskModel>>
}

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

class VoskModelNetwork(okHttpClient: OkHttpClient) {
    companion object {
        const val baseUrl = "https://alphacephei.com/"
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .addCallAdapterFactory(CoroutineCallAdapterFactory())
        .build()

    val voskModels: VoskModelService = retrofit.create(VoskModelService::class.java)
}
