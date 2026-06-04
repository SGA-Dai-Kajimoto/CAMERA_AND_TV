package com.sony.dtv.camera_tv.ui.settings

import com.sony.dtv.camera_tv.data.local.TokenPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var tokenPreferences: TokenPreferences

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        tokenPreferences = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun mockPreferences(
        baseUrl: String = "https://ws.dev3.imagingedge.sony.net",
        accessToken: String = "token123",
        refreshToken: String = "refresh456",
    ) {
        every { tokenPreferences.baseUrl } returns flowOf(baseUrl)
        every { tokenPreferences.accessToken } returns flowOf(accessToken)
        every { tokenPreferences.refreshToken } returns flowOf(refreshToken)
    }

    // ---------------------------------------------------------------- //
    // 初期ロード
    // ---------------------------------------------------------------- //

    @Test
    fun `init loads settings from preferences`() = runTest {
        mockPreferences(
            baseUrl = "https://example.com",
            accessToken = "mytoken",
            refreshToken = "myrefresh",
        )

        val viewModel = SettingsViewModel(tokenPreferences)

        val state = viewModel.uiState.value
        assertEquals("https://example.com", state.baseUrl)
        assertEquals("mytoken", state.accessToken)
        assertEquals("myrefresh", state.refreshToken)
        assertFalse(state.isLoading)
    }

    @Test
    fun `init with empty token reflects empty state`() = runTest {
        mockPreferences(accessToken = "", refreshToken = "")

        val viewModel = SettingsViewModel(tokenPreferences)

        assertEquals("", viewModel.uiState.value.accessToken)
    }

    // ---------------------------------------------------------------- //
    // saveSettings
    // ---------------------------------------------------------------- //

    @Test
    fun `saveSettings persists values and sets isSaved`() = runTest {
        mockPreferences()
        coEvery { tokenPreferences.saveBaseUrl(any()) } just Runs
        coEvery { tokenPreferences.saveAccessToken(any()) } just Runs
        coEvery { tokenPreferences.saveRefreshToken(any()) } just Runs

        val viewModel = SettingsViewModel(tokenPreferences)
        viewModel.saveSettings(
            baseUrl = "https://custom.example.com/",
            accessToken = "  newtoken  ",
            refreshToken = "newrefresh",
        )

        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertNull(state.error)

        // URL の末尾スラッシュが除去されること
        coVerify { tokenPreferences.saveBaseUrl("https://custom.example.com") }
        // トークンの前後スペースが除去されること
        coVerify { tokenPreferences.saveAccessToken("newtoken") }
        coVerify { tokenPreferences.saveRefreshToken("newrefresh") }
    }

    @Test
    fun `saveSettings with blank accessToken sets error and does not save`() = runTest {
        mockPreferences()

        val viewModel = SettingsViewModel(tokenPreferences)
        viewModel.saveSettings(
            baseUrl = "https://example.com",
            accessToken = "   ",
            refreshToken = "refresh",
        )

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertFalse(state.isSaved)
    }

    @Test
    fun `saveSettings with empty baseUrl uses DEFAULT_BASE_URL`() = runTest {
        mockPreferences()
        coEvery { tokenPreferences.saveBaseUrl(any()) } just Runs
        coEvery { tokenPreferences.saveAccessToken(any()) } just Runs
        coEvery { tokenPreferences.saveRefreshToken(any()) } just Runs

        val viewModel = SettingsViewModel(tokenPreferences)
        viewModel.saveSettings(
            baseUrl = "",
            accessToken = "token",
            refreshToken = "",
        )

        coVerify { tokenPreferences.saveBaseUrl(TokenPreferences.DEFAULT_BASE_URL) }
    }

    // ---------------------------------------------------------------- //
    // clearError / resetSaved
    // ---------------------------------------------------------------- //

    @Test
    fun `clearError removes error from uiState`() = runTest {
        mockPreferences()

        val viewModel = SettingsViewModel(tokenPreferences)
        // エラーをセットする
        viewModel.saveSettings("", "  ", "")
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `resetSaved clears isSaved flag`() = runTest {
        mockPreferences()
        coEvery { tokenPreferences.saveBaseUrl(any()) } just Runs
        coEvery { tokenPreferences.saveAccessToken(any()) } just Runs
        coEvery { tokenPreferences.saveRefreshToken(any()) } just Runs

        val viewModel = SettingsViewModel(tokenPreferences)
        viewModel.saveSettings("", "token", "")
        assertTrue(viewModel.uiState.value.isSaved)

        viewModel.resetSaved()
        assertFalse(viewModel.uiState.value.isSaved)
    }
}
