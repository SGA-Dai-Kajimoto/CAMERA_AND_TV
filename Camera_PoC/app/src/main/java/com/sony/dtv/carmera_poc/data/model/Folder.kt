package com.sony.dtv.carmera_poc.data.model

import com.google.gson.annotations.SerializedName

data class Folder(
    @SerializedName("folder_id") val folderId: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("removed_date") val removedDate: String? = null,
)
