package com.sony.dtv.carmera_poc.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
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
        // When
        tokenPreferences.saveAccessToken("my_access_token_123")

        // Then
        assertEquals("my_access_token_123", tokenPreferences.accessToken.first())
    }

    @Test
    fun `saveRefreshToken and read back returns same value`() = testScope.runTest {
        // When
        tokenPreferences.saveRefreshToken("my_refresh_token_abc")

        // Then
        assertEquals("my_refresh_token_abc", tokenPreferences.refreshToken.first())
    }

    @Test
    fun `saveUserId and saveAccount persist independently`() = testScope.runTest {
        // When
        tokenPreferences.saveUserId("user_42")
        tokenPreferences.saveAccount("account_abc")

        // Then
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
}
