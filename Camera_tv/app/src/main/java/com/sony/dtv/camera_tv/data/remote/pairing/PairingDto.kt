package com.sony.dtv.camera_tv.data.remote.pairing

import com.google.gson.annotations.SerializedName

/** POST /device/authorize のリクエスト。TV が作ったチャレンジだけを渡す。 */
data class DeviceAuthorizeRequest(
    @SerializedName("code_challenge") val codeChallenge: String,
    @SerializedName("code_challenge_method") val codeChallengeMethod: String = Pkce.METHOD_S256,
    @SerializedName("device_id") val deviceId: String = "camera-tv",
)

/** POST /device/authorize のレスポンス。 */
data class DeviceAuthorizeResponse(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String = "",
    @SerializedName("verification_uri") val verificationUri: String = "",
    /** QR に載せる URL。user_code が埋まっているので番号入力が要らない。 */
    @SerializedName("verification_uri_complete") val verificationUriComplete: String = "",
    @SerializedName("expires_in") val expiresIn: Long = 0L,
    @SerializedName("interval") val interval: Long = 0L,
)

/** POST /device/code のリクエスト。 */
data class DeviceCodeRequest(
    @SerializedName("device_code") val deviceCode: String,
)

/**
 * POST /device/code のレスポンス。
 *
 * 中継サーバーから受け取るのは認可コードまで。トークン交換は TV が自分で行うので、
 * code_verifier もトークンも中継サーバーを通らない。
 */
data class DeviceCodeResponse(
    @SerializedName("auth_code") val authCode: String = "",
    @SerializedName("base_url") val baseUrl: String = "",
    @SerializedName("app_type") val appType: String = "",
)

/** AccountPF のトークン交換リクエスト。キー名はスノークケース（実測済み）。 */
data class TokenExchangeRequest(
    @SerializedName("app_type") val appType: String,
    @SerializedName("auth_code") val authCode: String,
    @SerializedName("code_verifier") val codeVerifier: String,
)

/** ペアリング完了時に受け取るトークン一式。 */
data class PairingTokens(
    @SerializedName("access_token") val accessToken: String = "",
    @SerializedName("access_token_ttl") val accessTokenTtl: Long = 0L,
    @SerializedName("refresh_token") val refreshToken: String = "",
    @SerializedName("refresh_token_ttl") val refreshTokenTtl: Long = 0L,
)

/** RFC 8628 に倣ったエラー応答（HTTP 400 で返る）。 */
data class DeviceErrorResponse(
    @SerializedName("error") val error: String = "",
    @SerializedName("error_description") val errorDescription: String? = null,
) {
    companion object {
        /** まだユーザーがブラウザで認証を終えていない。ポーリングを続ける。 */
        const val AUTHORIZATION_PENDING = "authorization_pending"

        /** ポーリングが速すぎる。間隔を広げて続ける。 */
        const val SLOW_DOWN = "slow_down"

        /** セッションの期限切れ。最初からやり直す。 */
        const val EXPIRED_TOKEN = "expired_token"

        /** device_code が無効、または既に消費済み。 */
        const val ACCESS_DENIED = "access_denied"
    }
}
