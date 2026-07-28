package com.sony.dtv.camera_tv.data.remote.pairing

import com.google.gson.annotations.SerializedName

/** POST /pairing/start のレスポンス。 */
data class PairingStartResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("device_secret") val deviceSecret: String,
    @SerializedName("pairing_url") val pairingUrl: String,
    @SerializedName("expires_in") val expiresIn: Long = 0L,
)

/** GET /pairing/{session_id} のレスポンス。 */
data class PairingPollResponse(
    @SerializedName("status") val status: String,
    @SerializedName("tokens") val tokens: PairingTokens? = null,
)

/** ペアリング完了時に受け取るトークン一式。 */
data class PairingTokens(
    @SerializedName("base_url") val baseUrl: String = "",
    @SerializedName("app_type") val appType: String = "",
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("access_token_ttl") val accessTokenTtl: Long = 0L,
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("refresh_token_ttl") val refreshTokenTtl: Long = 0L,
)
