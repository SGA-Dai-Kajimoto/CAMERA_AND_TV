package com.sony.dtv.camera_tv.data.remote.pairing

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * TV ペアリングサーバーの API 定義。
 * Sony API とは別サーバー・別 Retrofit インスタンスで、認証（Bearer）は不要。
 */
interface PairingApi {

    /** デバイス認可の開始。TV が作った code_challenge を登録し、表示用の番号と QR URL を得る。 */
    @POST("device/authorize")
    suspend fun authorize(@Body body: DeviceAuthorizeRequest): DeviceAuthorizeResponse

    /**
     * 認可コードの取得ポーリング。
     *
     * 未認証のうちは 400 + `authorization_pending` が返るため、エラーボディを読む必要がある。
     * そのため [Response] のまま受け取る。
     */
    @POST("device/code")
    suspend fun authCode(@Body body: DeviceCodeRequest): Response<DeviceCodeResponse>

    /**
     * AccountPF でトークンに交換する。**中継サーバーを通さない**。
     *
     * 交換先は中継サーバーが返す base_url なので、絶対 URL で叩く。
     */
    @POST
    suspend fun exchangeToken(
        @Url url: String,
        @Body body: TokenExchangeRequest,
    ): Response<PairingTokens>
}
