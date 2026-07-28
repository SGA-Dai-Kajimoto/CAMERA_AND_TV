package com.sony.dtv.camera_tv.data.remote.pairing

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * QR 認証ローカルサーバーの API 定義。
 * Sony API とは別サーバー・別 Retrofit インスタンスで、認証（Bearer）は不要。
 */
interface PairingApi {

    /** ペアリング開始。QR 化する pairing_url と、トークン取得用の device_secret を得る。 */
    @POST("pairing/start")
    suspend fun start(
        @Body body: Map<String, @JvmSuppressWildcards Any>,
    ): PairingStartResponse

    /**
     * トークン取得ポーリング。
     * status="pending" の間は待機し、"completed" でトークンを受け取る。
     * device_secret はヘッダで送り、TV 以外からの取得を防ぐ。
     */
    @GET("pairing/{sessionId}")
    suspend fun poll(
        @Path("sessionId") sessionId: String,
        @Header("X-Device-Secret") deviceSecret: String,
    ): PairingPollResponse
}
