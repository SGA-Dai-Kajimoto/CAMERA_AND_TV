package com.sony.dtv.carmera_poc.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.sony.dtv.carmera_poc.data.model.Folder

data class FolderListResponse(
    @SerializedName("folders") val folders: List<Folder> = emptyList(),
)
