package com.sony.dtv.carmera_poc.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.sony.dtv.carmera_poc.data.model.Content

data class ContentListResponse(
    @SerializedName("contents") val contents: List<Content> = emptyList(),
)
