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
     * 認証情報を一括で書き込む。既に値があれば上書きしない。
     * PoC の auth_info.json から初期値をロードする際に使用する。
     */
    suspend fun initIfEmpty(
        baseUrl: String,
        appType: String,
        accessToken: String,
        accessTokenTtl: Long,
        refreshToken: String,
        refreshTokenTtl: Long,
    ) {
        dataStore.edit { prefs ->
            if (!prefs.contains(KEY_BASE_URL)) prefs[KEY_BASE_URL] = baseUrl
            if (!prefs.contains(KEY_APP_TYPE)) prefs[KEY_APP_TYPE] = appType
            if (!prefs.contains(KEY_ACCESS_TOKEN)) prefs[KEY_ACCESS_TOKEN] = accessToken
            if (!prefs.contains(KEY_ACCESS_TOKEN_TTL)) prefs[KEY_ACCESS_TOKEN_TTL] = accessTokenTtl
            if (!prefs.contains(KEY_REFRESH_TOKEN)) prefs[KEY_REFRESH_TOKEN] = refreshToken
            if (!prefs.contains(KEY_REFRESH_TOKEN_TTL)) prefs[KEY_REFRESH_TOKEN_TTL] = refreshTokenTtl
        }
    }
}
