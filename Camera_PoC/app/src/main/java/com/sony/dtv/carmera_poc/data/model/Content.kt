package com.sony.dtv.carmera_poc.data.model

import com.google.gson.annotations.SerializedName

data class Content(
    @SerializedName("content_id") val contentId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("filename") val filename: String? = null,
)
