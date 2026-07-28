package com.sony.dtv.camera_tv.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.pairing.PairingApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** QR 認証画面の状態。 */
data class AuthUiState(
    val isLoading: Boolean = true,
    val pairingUrl: String? = null,
    val expiresInSec: Long = 0L,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
)

/**
 * QR 認証のフロー制御。
 *  1. サーバーへ /pairing/start → pairing_url と device_secret を取得
 *  2. pairing_url を QR 化して表示（画面側）
 *  3. /pairing/{sessionId} を一定間隔でポーリング
 *  4. completed でトークンを保存し isAuthenticated=true
 */
class AuthViewModel(
    private val pairingApi: PairingApi,
    private val tokenPreferences: TokenPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        startPairing()
    }

    /** ペアリングを開始（初回およびリトライ時）。 */
    fun startPairing() {
        _uiState.update { AuthUiState(isLoading = true) }
        viewModelScope.launch {
            try {
                val start = pairingApi.start(mapOf("device_id" to DEVICE_ID))
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pairingUrl = start.pairingUrl,
                        expiresInSec = start.expiresIn,
                        error = null,
                    )
                }
                pollForToken(start.sessionId, start.deviceSecret, start.expiresIn)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "サーバーに接続できませんでした: ${e.message ?: "不明なエラー"}",
                    )
                }
            }
        }
    }

    /** completed になるまでトークン取得をポーリングする。 */
    private suspend fun pollForToken(sessionId: String, deviceSecret: String, expiresInSec: Long) {
        val deadline = System.currentTimeMillis() + (expiresInSec.coerceAtLeast(60) * 1000)
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val poll = try {
                pairingApi.poll(sessionId, deviceSecret)
            } catch (e: Exception) {
                // セッション期限切れ(410)などはリトライ導線を出す
                _uiState.update {
                    it.copy(error = "認証セッションが失効しました。もう一度お試しください。")
                }
                return
            }
            if (poll.status == "completed") {
                val tokens = poll.tokens
                if (tokens == null || tokens.accessToken.isEmpty()) {
                    _uiState.update { it.copy(error = "トークンの取得に失敗しました。") }
                    return
                }
                tokenPreferences.saveAuthTokens(
                    baseUrl = tokens.baseUrl,
                    appType = tokens.appType,
                    accessToken = tokens.accessToken,
                    accessTokenTtl = tokens.accessTokenTtl,
                    refreshToken = tokens.refreshToken,
                    refreshTokenTtl = tokens.refreshTokenTtl,
                )
                _uiState.update { it.copy(isAuthenticated = true, error = null) }
                return
            }
        }
        _uiState.update { it.copy(error = "時間切れです。もう一度お試しください。") }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2500L
        private const val DEVICE_ID = "camera-tv"

        fun factory(
            pairingApi: PairingApi,
            tokenPreferences: TokenPreferences,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(pairingApi, tokenPreferences) }
        }
    }
}
