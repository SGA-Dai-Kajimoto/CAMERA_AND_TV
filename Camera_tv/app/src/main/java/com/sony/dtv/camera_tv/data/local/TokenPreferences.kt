package com.sony.dtv.camera_tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Application Context から DataStore を生成するトップレベル拡張 */
private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "token_prefs")

/**
 * DataStore を使ってトークン情報を永続化するクラス。
 */
class TokenPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_APP_TYPE = stringPreferencesKey("app_type")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_ACCESS_TOKEN_TTL = longPreferencesKey("access_token_ttl")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_REFRESH_TOKEN_TTL = longPreferencesKey("refresh_token_ttl")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_ACCOUNT = stringPreferencesKey("account")
        // 最後に注入した local.properties 由来の refresh_token。
        // シードが変わったか（＝トークン更新されたか）の判定に使う。
        private val KEY_SEED_MARKER = stringPreferencesKey("seed_refresh_token_marker")

        const val DEFAULT_BASE_URL = "https://ws.dev3.imagingedge.sony.net"
        const val DEFAULT_APP_TYPE = "_trial_"

        /** Context から TokenPreferences を生成するファクトリ */
        fun create(context: Context): TokenPreferences =
            TokenPreferences(context.applicationContext.tokenDataStore)
    }

    val baseUrl: Flow<String> = dataStore.data.map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }
    val appType: Flow<String> = dataStore.data.map { it[KEY_APP_TYPE] ?: DEFAULT_APP_TYPE }
    val accessToken: Flow<String> = dataStore.data.map { it[KEY_ACCESS_TOKEN] ?: "" }
    val accessTokenTtl: Flow<Long> = dataStore.data.map { it[KEY_ACCESS_TOKEN_TTL] ?: 0L }
    val refreshToken: Flow<String> = dataStore.data.map { it[KEY_REFRESH_TOKEN] ?: "" }
    val refreshTokenTtl: Flow<Long> = dataStore.data.map { it[KEY_REFRESH_TOKEN_TTL] ?: 0L }
    val userId: Flow<String> = dataStore.data.map { it[KEY_USER_ID] ?: "" }
    val account: Flow<String> = dataStore.data.map { it[KEY_ACCOUNT] ?: "" }

    suspend fun saveBaseUrl(value: String) { dataStore.edit { it[KEY_BASE_URL] = value } }
    suspend fun saveAppType(value: String) { dataStore.edit { it[KEY_APP_TYPE] = value } }
    suspend fun saveAccessToken(value: String) { dataStore.edit { it[KEY_ACCESS_TOKEN] = value } }
    suspend fun saveAccessTokenTtl(value: Long) { dataStore.edit { it[KEY_ACCESS_TOKEN_TTL] = value } }
    suspend fun saveRefreshToken(value: String) { dataStore.edit { it[KEY_REFRESH_TOKEN] = value } }
    suspend fun saveRefreshTokenTtl(value: Long) { dataStore.edit { it[KEY_REFRESH_TOKEN_TTL] = value } }
    suspend fun saveUserId(value: String) { dataStore.edit { it[KEY_USER_ID] = value } }
    suspend fun saveAccount(value: String) { dataStore.edit { it[KEY_ACCOUNT] = value } }

    /**
     * local.properties 由来のシードトークンを注入する。
     *
     * 挙動:
     * - シードの refresh_token が前回注入時と異なる場合（初回 or local.properties 更新後）
     *   だけトークンを書き込み、TTL は 0（未知）にリセットする。
     * - 同じ場合は書き込まず、リフレッシュでローテーションされた最新トークンを保持する。
     *
     * これにより、local.properties でトークンを更新すると即座に反映され、
     * 変更が無ければ毎起動で古いシードに戻してしまうことを防ぐ。
     */
    suspend fun seedTokens(
        baseUrl: String,
        appType: String,
        accessToken: String,
        refreshToken: String,
    ) {
        dataStore.edit { prefs ->
            // baseUrl / appType は設定値なので常に最新へ更新
            prefs[KEY_BASE_URL] = baseUrl
            prefs[KEY_APP_TYPE] = appType

            // シードの refresh_token が変わったときだけ注入する
            if (refreshToken.isNotEmpty() && prefs[KEY_SEED_MARKER] != refreshToken) {
                if (accessToken.isNotEmpty()) prefs[KEY_ACCESS_TOKEN] = accessToken
                prefs[KEY_REFRESH_TOKEN] = refreshToken
                // TTL は不明なので 0（未知）にリセット。実TTLはリフレッシュ成功時に保存される。
                prefs[KEY_ACCESS_TOKEN_TTL] = 0L
                prefs[KEY_REFRESH_TOKEN_TTL] = 0L
                prefs[KEY_SEED_MARKER] = refreshToken
            }
        }
    }

    /**
     * QR 認証（ペアリングサーバー）で取得したトークン一式を保存する。
     *
     * seedTokens とは独立して、取得したトークンをそのまま書き込む。
     * ここで KEY_SEED_MARKER も refresh_token に合わせて更新することで、
     * 次回起動時の seedTokens が local.properties の（空 or 古い）シードで
     * QR 取得トークンを上書きしないようにする。
     */
    suspend fun saveAuthTokens(
        baseUrl: String,
        appType: String,
        accessToken: String,
        accessTokenTtl: Long,
        refreshToken: String,
        refreshTokenTtl: Long,
    ) {
        dataStore.edit { prefs ->
            if (baseUrl.isNotEmpty()) prefs[KEY_BASE_URL] = baseUrl
            if (appType.isNotEmpty()) prefs[KEY_APP_TYPE] = appType
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_ACCESS_TOKEN_TTL] = accessTokenTtl
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_REFRESH_TOKEN_TTL] = refreshTokenTtl
            // seedTokens が QR 取得トークンを上書きしないようにマーカーを合わせる
            if (refreshToken.isNotEmpty()) prefs[KEY_SEED_MARKER] = refreshToken
        }
    }

    /** すべての認証情報を消去する（サインアウト／再認証用）。 */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_ACCESS_TOKEN_TTL)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN_TTL)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_ACCOUNT)
            prefs.remove(KEY_SEED_MARKER)
        }
    }
}
