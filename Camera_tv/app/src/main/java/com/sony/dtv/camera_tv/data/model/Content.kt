package com.sony.dtv.camera_tv.data.model

import com.google.gson.annotations.SerializedName

data class Content(
    @SerializedName("content_id") val contentId: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("recorded_date") val recordedDate: String? = null,
    @SerializedName("recorded_date_local_time") val recordedDateLocalTime: String? = null,
    @SerializedName("created_date") val createdDate: String? = null,
    @SerializedName("updated_date") val updatedDate: String? = null,
    @SerializedName("tags") val tags: List<String>? = null,
    // API response does not include folder_id; set after fetch
    @Transient val folderId: String = "",
)

/**
 * tags に含まれる "rating:N"（N=1..5）から評価値を取り出す。
 * 複数ある場合は最大値、未設定なら 0 を返す。
 */
fun Content.ratingValue(): Int =
    tags
        ?.mapNotNull { tag ->
            if (tag.startsWith("rating:")) tag.substringAfter("rating:").toIntOrNull() else null
        }
        ?.maxOrNull()
        ?: 0
