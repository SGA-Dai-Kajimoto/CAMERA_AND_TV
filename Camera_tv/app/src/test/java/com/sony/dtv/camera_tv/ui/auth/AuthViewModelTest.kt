package com.sony.dtv.camera_tv.ui.auth

import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceAuthorizeRequest
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceAuthorizeResponse
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceCodeRequest
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceCodeResponse
import com.sony.dtv.camera_tv.data.remote.pairing.DeviceErrorResponse
import com.sony.dtv.camera_tv.data.remote.pairing.PairingApi
import com.sony.dtv.camera_tv.data.remote.pairing.PairingTokens
import com.sony.dtv.camera_tv.data.remote.pairing.Pkce
import com.sony.dtv.camera_tv.data.remote.pairing.TokenExchangeRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private lateinit var pairingApi: PairingApi
    private lateinit var tokenPrefs: TokenPreferences

    private val authorizeResponse = DeviceAuthorizeResponse(
        deviceCode = "dc-1",
        userCode = "BCDFGHJK",
        verificationUri = "http://localhost:8000/device",
        verificationUriComplete = "http://localhost:8000/device?user_code=BCDFGHJK",
        expiresIn = 300,
        interval = 3,
    )

    private val granted = DeviceCodeResponse(
        authCode = "ac-1",
        baseUrl = "https://ws.example.net",
        appType = "_trial_",
    )

    private val tokens = PairingTokens(
        accessToken = "at",
        accessTokenTtl = 3600,
        refreshToken = "rt",
        refreshTokenTtl = 86400,
    )

    /** RFC 8628 形式のエラー応答（HTTP 400）。 */
    private fun errorResponse(code: String): Response<DeviceCodeResponse> = Response.error(
        400,
        """{"error":"$code"}""".toResponseBody("application/json".toMediaType()),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        pairingApi = mockk()
        tokenPrefs = mockk(relaxed = true)
        coEvery { pairingApi.authorize(any()) } returns authorizeResponse
        coEvery { pairingApi.exchangeToken(any(), any()) } returns Response.success(tokens)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AuthViewModel(pairingApi, tokenPrefs, scope)

    @Test
    fun `開始に成功するとコードとQR用URLが表示される`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returns
            errorResponse(DeviceErrorResponse.AUTHORIZATION_PENDING)
        val vm = createViewModel()

        advanceTimeBy(1)

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals("BCDFGHJK", state.userCode)
        assertEquals("http://localhost:8000/device?user_code=BCDFGHJK", state.verificationUri)
        assertEquals(300L, state.expiresInSec)
        assertNull(state.error)
    }

    @Test
    fun `S256 のチャレンジだけをサーバーへ送る`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returns
            errorResponse(DeviceErrorResponse.AUTHORIZATION_PENDING)
        val request = slot<DeviceAuthorizeRequest>()
        createViewModel()

        advanceTimeBy(1)

        coVerify { pairingApi.authorize(capture(request)) }
        assertEquals(Pkce.METHOD_S256, request.captured.codeChallengeMethod)
        assertTrue(request.captured.codeChallenge.isNotEmpty())
    }

    @Test
    fun `中継サーバーへは device_code しか送らない`() = runTest(dispatcher) {
        // code_verifier が中継サーバーを通ると、そこが侵害された時点でトークンを奪える
        val codeRequest = slot<DeviceCodeRequest>()
        coEvery { pairingApi.authCode(capture(codeRequest)) } returns Response.success(granted)
        createViewModel()

        advanceUntilIdle()

        assertEquals("dc-1", codeRequest.captured.deviceCode)
    }

    @Test
    fun `トークン交換は中継サーバーを通さず AccountPF を直接叩く`() = runTest(dispatcher) {
        val authorizeRequest = slot<DeviceAuthorizeRequest>()
        val url = slot<String>()
        val exchange = slot<TokenExchangeRequest>()
        coEvery { pairingApi.authCode(any()) } returns Response.success(granted)
        coEvery { pairingApi.exchangeToken(capture(url), capture(exchange)) } returns
            Response.success(tokens)
        createViewModel()

        advanceUntilIdle()

        coVerify { pairingApi.authorize(capture(authorizeRequest)) }
        assertEquals("https://ws.example.net/api/v1/oauth2/token", url.captured)
        assertEquals("ac-1", exchange.captured.authCode)
        assertEquals("_trial_", exchange.captured.appType)
        // 認可時のチャレンジと対になる verifier を送っている
        assertEquals(
            authorizeRequest.captured.codeChallenge,
            Pkce.challengeOf(exchange.captured.codeVerifier),
        )
    }

    @Test
    fun `開始に失敗するとエラーになる`() = runTest(dispatcher) {
        coEvery { pairingApi.authorize(any()) } throws IOException("connection refused")
        val vm = createViewModel()

        advanceTimeBy(1)

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertNull(state.userCode)
    }

    @Test
    fun `交換に成功するとトークンを保存し認証済みになる`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returnsMany listOf(
            errorResponse(DeviceErrorResponse.AUTHORIZATION_PENDING),
            Response.success(granted),
        )
        val vm = createViewModel()

        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAuthenticated)
        assertNull(vm.uiState.value.error)
        coVerify {
            tokenPrefs.saveAuthTokens(
                baseUrl = "https://ws.example.net",
                appType = "_trial_",
                accessToken = "at",
                accessTokenTtl = 3600,
                refreshToken = "rt",
                refreshTokenTtl = 86400,
            )
        }
    }

    @Test
    fun `認可コードが空ならエラーにする`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returns Response.success(DeviceCodeResponse())
        val vm = createViewModel()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNotNull(vm.uiState.value.error)
        coVerify(exactly = 0) { pairingApi.exchangeToken(any(), any()) }
    }

    @Test
    fun `トークン交換に失敗したらエラーにする`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returns Response.success(granted)
        coEvery { pairingApi.exchangeToken(any(), any()) } returns
            Response.error(401, """{"error":"invalid"}""".toResponseBody("application/json".toMediaType()))
        val vm = createViewModel()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNotNull(vm.uiState.value.error)
        coVerify(exactly = 0) {
            tokenPrefs.saveAuthTokens(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `トークン交換で通信例外が出てもエラーにして止まる`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returns Response.success(granted)
        coEvery { pairingApi.exchangeToken(any(), any()) } throws IOException("boom")
        val vm = createViewModel()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `slow_down では止まらずポーリングを続ける`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returnsMany listOf(
            errorResponse(DeviceErrorResponse.SLOW_DOWN),
            Response.success(granted),
        )
        val vm = createViewModel()

        advanceUntilIdle()

        assertTrue(vm.uiState.value.isAuthenticated)
        coVerify(exactly = 2) { pairingApi.authCode(any()) }
    }

    @Test
    fun `expired_token は即座に時間切れエラーにする`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returns
            errorResponse(DeviceErrorResponse.EXPIRED_TOKEN)
        val vm = createViewModel()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNotNull(vm.uiState.value.error)
        coVerify(exactly = 1) { pairingApi.authCode(any()) }
    }

    @Test
    fun `access_denied は即座にエラーにして止まる`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returns
            errorResponse(DeviceErrorResponse.ACCESS_DENIED)
        val vm = createViewModel()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNotNull(vm.uiState.value.error)
        coVerify(exactly = 1) { pairingApi.authCode(any()) }
    }

    @Test
    fun `通信例外が出たらエラーを出して止まる`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } throws IOException("boom")
        val vm = createViewModel()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNotNull(vm.uiState.value.error)
        coVerify(exactly = 1) { pairingApi.authCode(any()) }
    }

    @Test
    fun `pending のまま期限に達したら時間切れエラーになる`() = runTest(dispatcher) {
        coEvery { pairingApi.authCode(any()) } returns
            errorResponse(DeviceErrorResponse.AUTHORIZATION_PENDING)
        val vm = createViewModel()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `startPairing のリトライで状態がリセットされる`() = runTest(dispatcher) {
        coEvery { pairingApi.authorize(any()) } throws IOException("down")
        val vm = createViewModel()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.error)

        coEvery { pairingApi.authorize(any()) } returns authorizeResponse
        coEvery { pairingApi.authCode(any()) } returns
            errorResponse(DeviceErrorResponse.AUTHORIZATION_PENDING)
        vm.startPairing()
        advanceTimeBy(1)

        val state = vm.uiState.value
        assertNull(state.error)
        assertEquals("BCDFGHJK", state.userCode)
    }
}
