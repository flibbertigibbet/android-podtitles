package dev.banderkat.podtitles.models

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import kotlinx.parcelize.Parcelize

const val SEARCH_RESULT_TABLE_NAME = "gpodder_search_result"

@Parcelize
@Entity(tableName = SEARCH_RESULT_TABLE_NAME)
data class GpodderSearchResult(
    @PrimaryKey
    val url: String,
    val title: String,
    val author: String?,
    val description: String?,
    @Json(name = "logo_url")
    val logoUrl: String?,
    @ColumnInfo(index = true)
    val subscribers: Int?,
    val website: String?
) : Parcelable {
    override fun toString(): String {
        return "GpodderSearchResult ($title by $author at $url)"
    }
}
