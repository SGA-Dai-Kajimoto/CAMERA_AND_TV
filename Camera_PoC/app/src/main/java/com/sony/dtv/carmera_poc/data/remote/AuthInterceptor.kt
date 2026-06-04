package com.sony.dtv.carmera_poc.data.remote

import com.sony.dtv.carmera_poc.data.local.TokenPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * OkHttp インターセプター。
 * - 全リクエストに Authorization: Bearer <access_token> を付与する
 * - 401 レスポンス時は POST /api/v1/oauth2/token でリフレッシュし再試行する
 */
class AuthInterceptor(
    private val tokenPreferences: TokenPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = runBlocking { tokenPreferences.accessToken.first() }
        val request = chain.request().withBearerToken(accessToken)
        val response = chain.proceed(request)

        if (response.code == 401) {
            response.close()
            val refreshed = runBlocking { tryRefreshToken() }
            if (refreshed != null) {
                return chain.proceed(chain.request().withBearerToken(refreshed))
            }
        }
        return response
    }

    /**
     * refresh_token を使って access_token を更新する。
     * @return 新しい access_token。失敗時は null。
     */
    private suspend fun tryRefreshToken(): String? {
        return try {
            val baseUrl = tokenPreferences.baseUrl.first()
            val appType = tokenPreferences.appType.first()
            val refreshToken = tokenPreferences.refreshToken.first()
            val refreshTokenTtl = tokenPreferences.refreshTokenTtl.first()

            // リフレッシュ用に interceptor なしの OkHttpClient を使用（無限ループ防止）
            val tempRetrofit = buildRetrofitWithoutAuth(baseUrl)
            val api = tempRetrofit.create(ImagingEdgeApi::class.java)

            val body = mapOf(
                "app_type" to appType,
                "refresh_token" to refreshToken,
                "refresh_token_ttl" to refreshTokenTtl,
            )
            val result = api.refreshToken(body)
            if (result.isSuccessful) {
                val tokenResponse = result.body()!!
                tokenPreferences.saveAccessToken(tokenResponse.accessToken)
                tokenPreferences.saveAccessTokenTtl(tokenResponse.accessTokenTtl)
                tokenResponse.refreshToken?.let { tokenPreferences.saveRefreshToken(it) }
                tokenResponse.refreshTokenTtl?.let { tokenPreferences.saveRefreshTokenTtl(it) }
                tokenResponse.accessToken
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun Request.withBearerToken(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    companion object {
        fun buildRetrofitWithoutAuth(baseUrl: String): Retrofit {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl.trimEnd('/') + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }
}
