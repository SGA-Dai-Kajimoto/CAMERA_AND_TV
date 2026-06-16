package com.sony.dtv.camera_tv.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TokenPreferencesTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var tokenPreferences: TokenPreferences

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_prefs.preferences_pb") }
        )
        tokenPreferences = TokenPreferences(dataStore)
    }

    @Test
    fun `saveAccessToken and read back returns same value`() = testScope.runTest {
        tokenPreferences.saveAccessToken("my_access_token_123")
        assertEquals("my_access_token_123", tokenPreferences.accessToken.first())
    }

    @Test
    fun `saveRefreshToken and read back returns same value`() = testScope.runTest {
        tokenPreferences.saveRefreshToken("my_refresh_token_abc")
        assertEquals("my_refresh_token_abc", tokenPreferences.refreshToken.first())
    }

    @Test
    fun `saveUserId and saveAccount persist independently`() = testScope.runTest {
        tokenPreferences.saveUserId("user_42")
        tokenPreferences.saveAccount("account_abc")
        assertEquals("user_42", tokenPreferences.userId.first())
        assertEquals("account_abc", tokenPreferences.account.first())
    }

    @Test
    fun `default values are empty strings`() = testScope.runTest {
        assertEquals("", tokenPreferences.accessToken.first())
        assertEquals("", tokenPreferences.refreshToken.first())
        assertEquals("", tokenPreferences.userId.first())
        assertEquals("", tokenPreferences.account.first())
    }

    @Test
    fun `default baseUrl is dev3`() = testScope.runTest {
        assertEquals(TokenPreferences.DEFAULT_BASE_URL, tokenPreferences.baseUrl.first())
    }

    @Test
    fun `initIfEmpty writes values only when not present`() = testScope.runTest {
        // 1回目: 書き込まれる
        tokenPreferences.initIfEmpty(
            baseUrl = TokenPreferences.DEFAULT_BASE_URL,
            appType = "_trial_",
            accessToken = "token_A",
            accessTokenTtl = 3600L,
            refreshToken = "refresh_A",
            refreshTokenTtl = 86400L,
        )
        assertEquals("token_A", tokenPreferences.accessToken.first())

        // 2回目: 上書きされない
        tokenPreferences.initIfEmpty(
            baseUrl = TokenPreferences.DEFAULT_BASE_URL,
            appType = "_trial_",
            accessToken = "token_B",
            accessTokenTtl = 3600L,
            refreshToken = "refresh_B",
            refreshTokenTtl = 86400L,
        )
        assertEquals("token_A", tokenPreferences.accessToken.first())
    }
}
