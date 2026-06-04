package com.sony.dtv.carmera_poc.data.repository

import com.sony.dtv.carmera_poc.data.local.TokenPreferences
import com.sony.dtv.carmera_poc.data.model.Content
import com.sony.dtv.carmera_poc.data.model.Folder
import com.sony.dtv.carmera_poc.data.remote.ImagingEdgeApi
import com.sony.dtv.carmera_poc.data.remote.dto.ContentListResponse
import com.sony.dtv.carmera_poc.data.remote.dto.FolderListResponse
import com.sony.dtv.carmera_poc.data.remote.dto.TokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
        every { tokenPrefs.baseUrl } returns flowOf("https://ws.dev.imagingedge.sony.net")
        repository = ImagingEdgeRepository(api, tokenPrefs)
    }

    @Test
    fun `getUserMe returns user_id from JSON response`() = runTest {
        // Given
        val userJson = mapOf("user_id" to "8857511748018", "account" to "acc_001")
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
        val response = FolderListResponse(folders = folders)
        coEvery { api.listFolders() } returns Response.success(response)

        // When
        val result = repository.listFolders()

        // Then
        assertTrue(result.isSuccess)
        val activeFolders = result.getOrNull()!!
        assertEquals(2, activeFolders.size)
        assertTrue(activeFolders.none { it.removedDate != null })
    }

    @Test
    fun `createFolder sends correct request body`() = runTest {
        // Given
        val bodySlot = slot<Map<String, String>>()
        coEvery { api.createFolder(capture(bodySlot)) } returns Response.success(
            mapOf("folder_id" to "f-abc123")
        )
        coEvery { tokenPrefs.appType } returns flowOf("_trial_")

        // When
        val result = repository.createFolder("My Folder")

        // Then
        assertTrue(result.isSuccess)
        assertEquals("My Folder", bodySlot.captured["display_name"])
        assertEquals("_trial_", bodySlot.captured["app_id"])
        assertTrue(bodySlot.captured.containsKey("utc_offset"))
    }

    @Test
    fun `renameFolder sends correct endpoint and body`() = runTest {
        // Given
        val bodySlot = slot<Map<String, String>>()
        coEvery { api.renameFolder("f-001", capture(bodySlot)) } returns Response.success(
            mapOf("folder_id" to "f-001")
        )

        // When
        val result = repository.renameFolder("f-001", "New Name")

        // Then
        assertTrue(result.isSuccess)
        assertEquals("New Name", bodySlot.captured["display_name"])
        coVerify(exactly = 1) { api.renameFolder("f-001", any()) }
    }

    @Test
    fun `deleteFolder calls correct endpoint`() = runTest {
        // Given
        coEvery { api.deleteFolder("f-001") } returns Response.success(Unit)

        // When
        val result = repository.deleteFolder("f-001")

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { api.deleteFolder("f-001") }
    }

    @Test
    fun `listContents returns contents for folder`() = runTest {
        // Given
        val contents = listOf(
            Content(contentId = "c-001", displayName = "image001.jpg", filename = "image001.jpg"),
            Content(contentId = "c-002", displayName = "image002.arw", filename = "image002.arw"),
        )
        val response = ContentListResponse(contents = contents)
        coEvery { api.listContents("f-001") } returns Response.success(response)

        // When
        val result = repository.listContents("f-001")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()!!.size)
    }

    @Test
    fun `deleteContent sends correct content_ids array`() = runTest {
        // Given
        val bodySlot = slot<Map<String, List<String>>>()
        coEvery { api.deleteContent("f-001", capture(bodySlot)) } returns Response.success(
            mapOf("deleted_content_ids" to listOf("c-001"))
        )

        // When
        val result = repository.deleteContent("f-001", "c-001")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(listOf("c-001"), bodySlot.captured["content_ids"])
    }
}
