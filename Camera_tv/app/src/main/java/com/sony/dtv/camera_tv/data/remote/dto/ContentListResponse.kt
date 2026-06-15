package com.sony.dtv.camera_tv.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.sony.dtv.camera_tv.data.model.Content

data class ContentListResponse(
    @SerializedName("contents") val contents: List<Content> = emptyList(),
    @SerializedName("last_item") val lastItem: String? = null,
)
