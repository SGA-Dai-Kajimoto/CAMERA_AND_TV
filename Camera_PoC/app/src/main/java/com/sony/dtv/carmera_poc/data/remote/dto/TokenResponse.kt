package com.sony.dtv.carmera_poc.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("access_token_ttl") val accessTokenTtl: Long = 0L,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("refresh_token_ttl") val refreshTokenTtl: Long? = null,
)
