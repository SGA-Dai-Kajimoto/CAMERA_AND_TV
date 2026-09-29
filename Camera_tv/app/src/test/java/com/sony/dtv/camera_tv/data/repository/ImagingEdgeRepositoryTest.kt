package com.sony.dtv.camera_tv.data.repository

import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.remote.ApiException
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
import com.sony.dtv.camera_tv.data.remote.dto.ContentListResponse
import com.sony.dtv.camera_tv.data.remote.dto.FolderListResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
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
        repository = ImagingEdgeRepository(api, tokenPrefs, UnconfinedTestDispatcher())
    }

    private fun folder(id: String, removedDate: String? = null) =
        Folder(folderId = id, displayName = id, removedDate = removedDate)

    private fun <T> errorResponse(code: Int, message: String): Response<T> =
        Response.error(code, message.toResponseBody())

    // ---------------------------------------------------------------- //
    // ユーザー情報
    // ---------------------------------------------------------------- //

    @Test
    fun `getUserMe は user_id と account を保存する`() = runTest {
        coEvery { api.getUserMe() } returns Response.success(
            mapOf<String, Any>("user_id" to "8857511748018", "account" to "acc_001"),
        )
        coEvery { tokenPrefs.saveUserId(any()) } returns Unit
        coEvery { tokenPrefs.saveAccount(any()) } returns Unit

        val result = repository.getUserMe()

        assertTrue(result.isSuccess)
        assertEquals("8857511748018", result.getOrNull()?.get("user_id"))
        coVerify { tokenPrefs.saveUserId("8857511748018") }
        coVerify { tokenPrefs.saveAccount("acc_001") }
    }

    @Test
    fun `getUserMe は account が無ければ user_id を代用する`() = runTest {
        coEvery { api.getUserMe() } returns Response.success(mapOf<String, Any>("user_id" to "u1"))
        coEvery { tokenPrefs.saveUserId(any()) } returns Unit
        coEvery { tokenPrefs.saveAccount(any()) } returns Unit

        repository.getUserMe()

        coVerify { tokenPrefs.saveAccount("u1") }
    }

    @Test
    fun `getUserMe は 401 で ApiException を返す`() = runTest {
        coEvery { api.getUserMe() } returns Response.error(401, "Unauthorized".toResponseBody())

        val result = repository.getUserMe()

        val error = result.exceptionOrNull()
        assertTrue(error is ApiException)
        assertEquals(401, (error as ApiException).code)
    }

    // ---------------------------------------------------------------- //
    // フォルダ / コンテンツ
    // ---------------------------------------------------------------- //

    @Test
    fun `listFolders は削除済みフォルダを除外する`() = runTest {
        coEvery { api.listFolders() } returns Response.success(
            FolderListResponse(
                listOf(
                    folder("f-001"),
                    folder("f-002", removedDate = "2026-01-01"),
                    folder("f-003"),
                ),
            ),
        )

        val folders = repository.listFolders().getOrNull()!!

        assertEquals(listOf("f-001", "f-003"), folders.map { it.folderId })
    }

    @Test
    fun `listFolders は 500 で ApiException を返す`() = runTest {
        coEvery { api.listFolders() } returns Response.error(500, "ISE".toResponseBody())

        val error = repository.listFolders().exceptionOrNull()

        assertEquals(500, (error as ApiException).code)
    }

    @Test
    fun `listContents は各コンテンツに folderId を付与する`() = runTest {
        coEvery { api.listContents("f-001", any(), any(), any(), any()) } returns Response.success(
            ContentListResponse(
                listOf(Content(contentId = "c-001"), Content(contentId = "c-002")),
            ),
        )

        val contents = repository.listContents("f-001").getOrNull()!!

        assertEquals(listOf("c-001", "c-002"), contents.map { it.contentId })
        assertTrue(contents.all { it.folderId == "f-001" })
    }

    @Test
    fun `listAllContents は全フォルダの結果を結合する`() = runTest {
        coEvery { api.listFolders() } returns Response.success(
            FolderListResponse(listOf(folder("f-001"), folder("f-002"))),
        )
        coEvery { api.listContents("f-001", any(), any(), any(), any()) } returns
            Response.success(ContentListResponse(listOf(Content(contentId = "c-001"))))
        coEvery { api.listContents("f-002", any(), any(), any(), any()) } returns
            Response.success(ContentListResponse(listOf(Content(contentId = "c-002"))))

        val contents = repository.listAllContents().getOrNull()!!

        assertEquals(listOf("c-001", "c-002"), contents.map { it.contentId })
        assertEquals(listOf("f-001", "f-002"), contents.map { it.folderId })
    }

    @Test
    fun `listAllContents は一部のフォルダが失敗したら失敗を返す`() = runTest {
        coEvery { api.listFolders() } returns Response.success(
            FolderListResponse(listOf(folder("f-001"))),
        )
        coEvery { api.listContents("f-001", any(), any(), any(), any()) } returns
            errorResponse(500, "ISE")

        val result = repository.listAllContents()

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as ApiException).code)
    }

    // ---------------------------------------------------------------- //
    // バイナリ / ダウンロードURL
    // ---------------------------------------------------------------- //

    @Test
    fun `getContentBinary はバイト列を返す`() = runTest {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        coEvery { api.getContentBinary("f-001", "c-001", "original") } returns
            Response.success(bytes.toResponseBody())

        val result = repository.getContentBinary("f-001", "c-001")

        assertTrue(result.isSuccess)
        assertEquals(4, result.getOrNull()!!.size)
    }

    @Test
    fun `getContentBinary は 404 で ApiException を返す`() = runTest {
        coEvery { api.getContentBinary(any(), any(), any()) } returns
            Response.error(404, "Not Found".toResponseBody())

        val error = repository.getContentBinary("f-001", "c-001").exceptionOrNull()

        assertEquals(404, (error as ApiException).code)
    }

    @Test
    fun `getContentDownloadUrl は download_url を取り出す`() = runTest {
        coEvery { api.getContentDownloadUrl("f-001", "c-001", "original") } returns
            Response.success(mapOf<String, Any>("download_url" to "https://example.com/a.jpg"))

        val result = repository.getContentDownloadUrl("f-001", "c-001")

        assertEquals("https://example.com/a.jpg", result.getOrNull())
    }

    @Test
    fun `getContentDownloadUrl は download_url が無ければ失敗する`() = runTest {
        coEvery { api.getContentDownloadUrl(any(), any(), any()) } returns
            Response.success(mapOf<String, Any>("expires_in" to 600))

        val result = repository.getContentDownloadUrl("f-001", "c-001")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    // ---------------------------------------------------------------- //
    // 更新系
    // ---------------------------------------------------------------- //

    @Test
    fun `setContentTags は tags キーで送信する`() = runTest {
        coEvery { api.setContentTags(any(), any(), any()) } returns Response.success(emptyMap())

        val result = repository.setContentTags("f-001", "c-001", listOf("family", "rating:3"))

        assertTrue(result.isSuccess)
        coVerify {
            api.setContentTags("f-001", "c-001", mapOf("tags" to listOf("family", "rating:3")))
        }
    }

    @Test
    fun `setContentTags は失敗時に ApiException を返す`() = runTest {
        coEvery { api.setContentTags(any(), any(), any()) } returns
            Response.error(400, "Bad Request".toResponseBody())

        val error = repository.setContentTags("f-001", "c-001", emptyList()).exceptionOrNull()

        assertEquals(400, (error as ApiException).code)
    }

    @Test
    fun `removeContents は content_ids キーで送信する`() = runTest {
        coEvery { api.removeContents(any(), any()) } returns Response.success(emptyMap())

        val result = repository.removeContents("f-001", listOf("c-001", "c-002"))

        assertTrue(result.isSuccess)
        coVerify {
            api.removeContents("f-001", mapOf("content_ids" to listOf("c-001", "c-002")))
        }
    }

    @Test
    fun `removeContents は失敗時に ApiException を返す`() = runTest {
        coEvery { api.removeContents(any(), any()) } returns
            Response.error(403, "Forbidden".toResponseBody())

        val error = repository.removeContents("f-001", listOf("c-001")).exceptionOrNull()

        assertEquals(403, (error as ApiException).code)
    }

    // ---------------------------------------------------------------- //
    // 例外の扱い
    // ---------------------------------------------------------------- //

    @Test
    fun `通信例外は Result の失敗として返る`() = runTest {
        coEvery { api.listFolders() } throws java.io.IOException("network down")

        val result = repository.listFolders()

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test(expected = CancellationException::class)
    fun `CancellationException は Result に包まず再スローする`() = runTest {
        coEvery { api.listFolders() } throws CancellationException("cancelled")

        repository.listFolders()
    }
}
