package com.sony.dtv.carmera_poc.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UploadSessionResponse(
    @SerializedName("upload_id") val uploadId: String,
    @SerializedName("upload_url") val uploadUrl: String,
)
