package com.sony.dtv.camera_tv.data.repository

import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
import com.sony.dtv.camera_tv.data.remote.dto.ContentListResponse
import com.sony.dtv.camera_tv.data.remote.dto.FolderListResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ImagingEdgeRepositoryTest {

    private lateinit var api: ImagingEdgeApi
    private lateinit var tokenPrefs: TokenPreferences
    private lateinit var repository: ImagingEdgeRepository

    @Before
    fun setUp() {
        api = mockk()
        tokenPrefs = mockk()
        every { tokenPrefs.accessToken } returns flowOf("test_access_token")
        every { tokenPrefs.refreshToken } returns flowOf("test_refresh_token")
        every { tokenPrefs.userId } returns flowOf("user123")
        every { tokenPrefs.account } returns flowOf("account123")
        every { tokenPrefs.appType } returns flowOf("_trial_")
        every { tokenPrefs.baseUrl } returns flowOf(TokenPreferences.DEFAULT_BASE_URL)
        repository = ImagingEdgeRepository(api, tokenPrefs)
    }

    @Test
    fun `getUserMe saves userId and returns map`() = runTest {
        // Given
        val userJson = mapOf<String, Any>("user_id" to "8857511748018", "account" to "acc_001")
        coEvery { api.getUserMe() } returns Response.success(userJson)
        coEvery { tokenPrefs.saveUserId(any()) } returns Unit
        coEvery { tokenPrefs.saveAccount(any()) } returns Unit

        // When
        val result = repository.getUserMe()

        // Then
        assertTrue(result.isSuccess)
        assertEquals("8857511748018", result.getOrNull()?.get("user_id"))
    }

    @Test
    fun `listFolders excludes folders with removed_date`() = runTest {
        // Given
        val folders = listOf(
            Folder(folderId = "f-001", displayName = "Active Folder", removedDate = null),
            Folder(folderId = "f-002", displayName = "Deleted Folder", removedDate = "2026-01-01"),
            Folder(folderId = "f-003", displayName = "Another Active", removedDate = null),
        )
        coEvery { api.listFolders() } returns Response.success(FolderListResponse(folders))

        // When
        val result = repository.listFolders()

        // Then
        assertTrue(result.isSuccess)
        val activeFolders = result.getOrNull()!!
        assertEquals(2, activeFolders.size)
        assertTrue(activeFolders.none { it.removedDate != null })
    }

    @Test
    fun `listContents returns content list for folderId`() = runTest {
        // Given
        val contents = listOf(
            Content(contentId = "c-001", displayName = "photo1.jpg", filename = "photo1.jpg"),
            Content(contentId = "c-002", displayName = "photo2.jpg", filename = "photo2.jpg"),
        )
        coEvery { api.listContents("f-001") } returns Response.success(ContentListResponse(contents))

        // When
        val result = repository.listContents("f-001")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()!!.size)
        assertEquals("c-001", result.getOrNull()!![0].contentId)
    }

    @Test
    fun `getContentBinary returns byte array on success`() = runTest {
        // Given
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) // PNG magic bytes
        val responseBody = bytes.toResponseBody()
        coEvery { api.getContentBinary("f-001", "c-001", "original") } returns
            Response.success(responseBody)

        // When
        val result = repository.getContentBinary("f-001", "c-001", "original")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(4, result.getOrNull()!!.size)
    }

    @Test
    fun `getUserMe returns failure on API error`() = runTest {
        // Given
        coEvery { api.getUserMe() } returns Response.error(
            401,
            "Unauthorized".toResponseBody(),
        )

        // When
        val result = repository.getUserMe()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }

    @Test
    fun `listFolders returns failure on API error`() = runTest {
        // Given
        coEvery { api.listFolders() } returns Response.error(
            500,
            "Internal Server Error".toResponseBody(),
        )

        // When
        val result = repository.listFolders()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }
}
