package com.sony.dtv.camera_tv.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.gson.Gson
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceAuthorizeRequest
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceCodeRequest
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceCodeResponse
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceErrorResponse
import com.sony.dtv.camera_tv.data.remote.pairing.PairingApi
import com.sony.dtv.camera_tv.data.remote.pairing.TokenExchangeRequest
import com.sony.dtv.camera_tv.data.remote.pairing.Pkce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Response

/** 認証画面の状態。 */
data class AuthUiState(
    val isLoading: Boolean = true,
    val userCode: String? = null,
    /** QR に載せる URL。番号入力なしでログイン画面まで飛べる。 */
    val verificationUri: String? = null,
    val expiresInSec: Long = 0L,
    val isAuthenticated: Boolean = false,
    val error: String? = null,
)

/**
 * PKCE ベースの擬似デバイスフローの制御。
 *
 *  1. code_verifier / code_challenge を生成し、challenge だけをサーバーへ送る
 *  2. device_code・user_code・QR 用 URL を受け取って画面表示
 *  3. POST /device/code をポーリングして auth_code を受け取る
 *  4. **TV 自身が** AccountPF でトークンに交換して保存する
 *
 * **code_verifier はこの ViewModel のメモリ上にしか持たない。**
 * 中継サーバーへも送らないので、サーバーが auth_code を握ってもトークン化できない。
 * プロセスが死んだら最初からやり直す。
 */
class AuthViewModel(
    private val pairingApi: PairingApi,
    private val tokenPreferences: TokenPreferences,
    externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    /** メモリ上にのみ保持する PKCE の verifier。永続化しない。 */
    private var codeVerifier: String? = null

    init {
        startPairing()
    }

    /** ペアリングを開始（初回およびリトライ時）。 */
    fun startPairing() {
        _uiState.update { AuthUiState(isLoading = true) }
        scope.launch {
            val pkce = Pkce.generate()
            codeVerifier = pkce.verifier

            val start = try {
                pairingApi.authorize(DeviceAuthorizeRequest(codeChallenge = pkce.challenge))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "サーバーに接続できませんでした: ${e.message ?: "不明なエラー"}",
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    userCode = start.userCode,
                    verificationUri = start.verificationUriComplete.ifEmpty { start.verificationUri },
                    expiresInSec = start.expiresIn,
                    error = null,
                )
            }
            // TV の画面を見て打ち直さなくて済むよう、logcat にも出す
            Log.i(TAG, "pairing code=${start.userCode} url=${start.verificationUriComplete}")
            pollForToken(start.deviceCode, pkce.verifier, start.expiresIn, start.interval)
        }
    }

    /**
     * 認可コードが受け取れるまでポーリングする。
     * 実時計ではなくポーリング回数で期限を判定するため、テストで仮想時間が使える。
     */
    private suspend fun pollForToken(
        deviceCode: String,
        verifier: String,
        expiresInSec: Long,
        intervalSec: Long,
    ) {
        var intervalMs = intervalSec.coerceAtLeast(1L) * 1000
        val totalMs = expiresInSec.coerceAtLeast(MIN_SESSION_SEC) * 1000
        val maxAttempts = (totalMs / intervalMs).toInt().coerceAtLeast(1)

        repeat(maxAttempts) {
            delay(intervalMs)

            val response = try {
                pairingApi.authCode(DeviceCodeRequest(deviceCode))
            } catch (_: Exception) {
                _uiState.update { it.copy(error = "サーバーとの通信に失敗しました。") }
                return
            }

            if (response.isSuccessful) {
                val granted = response.body()
                if (granted == null || granted.authCode.isEmpty()) {
                    _uiState.update { it.copy(error = "認可コードを受け取れませんでした。") }
                    return
                }
                exchangeAndSave(granted, verifier)
                return
            }

            when (val error = parseError(response)) {
                DeviceErrorResponse.AUTHORIZATION_PENDING -> Unit
                // サーバーが速すぎると判断したので間隔を広げる
                DeviceErrorResponse.SLOW_DOWN -> intervalMs += SLOW_DOWN_STEP_MS
                DeviceErrorResponse.EXPIRED_TOKEN -> {
                    _uiState.update { it.copy(error = "時間切れです。もう一度お試しください。") }
                    return
                }

                DeviceErrorResponse.ACCESS_DENIED -> {
                    _uiState.update { it.copy(error = "認証が拒否されました。もう一度お試しください。") }
                    return
                }

                else -> {
                    _uiState.update { it.copy(error = "認証に失敗しました（$error）。") }
                    return
                }
            }
        }
        _uiState.update { it.copy(error = "時間切れです。もう一度お試しください。") }
    }

    /**
     * 認可コードをトークンに交換して保存する。
     * 中継サーバーではなく AccountPF を直接叩くので、verifier もトークンも LAN に出ない。
     */
    private suspend fun exchangeAndSave(granted: DeviceCodeResponse, verifier: String) {
        val baseUrl = granted.baseUrl.trimEnd('/')
        if (baseUrl.isEmpty()) {
            _uiState.update { it.copy(error = "接続先が分かりませんでした。") }
            return
        }

        val response = try {
            pairingApi.exchangeToken(
                url = "$baseUrl/api/v1/oauth2/token",
                body = TokenExchangeRequest(
                    appType = granted.appType,
                    authCode = granted.authCode,
                    codeVerifier = verifier,
                ),
            )
        } catch (_: Exception) {
            _uiState.update { it.copy(error = "トークンの取得に失敗しました。") }
            return
        }

        val tokens = response.body()
        if (!response.isSuccessful || tokens == null || tokens.accessToken.isEmpty()) {
            _uiState.update { it.copy(error = "トークンの取得に失敗しました。") }
            return
        }

        tokenPreferences.saveAuthTokens(
            baseUrl = baseUrl,
            appType = granted.appType,
            accessToken = tokens.accessToken,
            accessTokenTtl = tokens.accessTokenTtl,
            refreshToken = tokens.refreshToken,
            refreshTokenTtl = tokens.refreshTokenTtl,
        )
        codeVerifier = null
        _uiState.update { it.copy(isAuthenticated = true, error = null) }
    }

    /** エラーボディから RFC 8628 形式のエラーコードを取り出す。読めなければ空文字。 */
    private fun parseError(response: Response<DeviceCodeResponse>): String =
        runCatching {
            val body = response.errorBody()?.string().orEmpty()
            gson.fromJson(body, DeviceErrorResponse::class.java)?.error.orEmpty()
        }.getOrDefault("")

    override fun onCleared() {
        super.onCleared()
        codeVerifier = null
    }

    companion object {
        private const val TAG = "AuthVM"

        /** サーバーが極端に短い expires_in を返しても、最低これだけは待つ。 */
        private const val MIN_SESSION_SEC = 60L

        /** slow_down を受けたときに広げるポーリング間隔。 */
        private const val SLOW_DOWN_STEP_MS = 2000L

        fun factory(
            pairingApi: PairingApi,
            tokenPreferences: TokenPreferences,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(pairingApi, tokenPreferences) }
        }
    }
}
