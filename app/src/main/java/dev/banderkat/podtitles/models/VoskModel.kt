package dev.banderkat.podtitles.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import kotlinx.parcelize.Parcelize

const val VOSK_MODEL_TABLE_NAME = "vosk_model"

@Parcelize
@Entity(tableName = VOSK_MODEL_TABLE_NAME)
data class VoskModel(
    @ColumnInfo(index = true)
    val lang: String,
    @Json(name = "lang_text")
    @ColumnInfo(index = true)
    val langText: String,
    val md5: String,
    @PrimaryKey
    val name: String,
    @ColumnInfo(index = true)
    val obsolete: String,
    val size: Long,
    @Json(name = "size_text")
    val sizeText: String,
    @ColumnInfo(index = true)
    val type: String,
    val url: String,
    val version: String
) : Parcelable {
    override fun toString(): String {
        return "VoskModelSearchResult: ($name $version $sizeText)"
    }
}
