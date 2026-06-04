package com.sony.dtv.camera_tv.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.sony.dtv.camera_tv.data.model.Folder

data class FolderListResponse(
    @SerializedName("folders") val folders: List<Folder> = emptyList(),
)
