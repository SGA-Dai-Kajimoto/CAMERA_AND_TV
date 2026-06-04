package com.sony.dtv.carmera_poc.data.repository

import android.net.Uri
import com.sony.dtv.carmera_poc.data.local.TokenPreferences
import com.sony.dtv.carmera_poc.data.model.Content
import com.sony.dtv.carmera_poc.data.model.Folder
import com.sony.dtv.carmera_poc.data.remote.ImagingEdgeApi
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * ImagingEdge API の全操作を提供するリポジトリ。
 * PC 版の ApiClient に対応。
 */
class ImagingEdgeRepository(
    private val api: ImagingEdgeApi,
    private val tokenPreferences: TokenPreferences,
) {

    // ---------------------------------------------------------------- //
    // ユーザー情報
    // ---------------------------------------------------------------- //

    suspend fun getUserMe(): Result<Map<String, Any>> = runCatching {
        val response = api.getUserMe()
        response.requireSuccess()
        val body = response.body()!!
        val userId = body["user_id"]?.toString() ?: ""
        val account = body["account"]?.toString() ?: userId
        tokenPreferences.saveUserId(userId)
        if (account.isNotEmpty()) tokenPreferences.saveAccount(account)
        body
    }

    // ---------------------------------------------------------------- //
    // フォルダ操作
    // ---------------------------------------------------------------- //

    suspend fun listFolders(): Result<List<Folder>> = runCatching {
        val response = api.listFolders()
        response.requireSuccess()
        response.body()!!.folders.filter { it.removedDate == null }
    }

    suspend fun createFolder(displayName: String): Result<Map<String, String>> = runCatching {
        val appType = tokenPreferences.appType.first()
        val body = mapOf(
            "display_name" to displayName,
            "app_id" to appType,
            "utc_offset" to utcOffset(),
        )
        val response = api.createFolder(body)
        response.requireSuccess()
        response.body()!!
    }

    suspend fun renameFolder(folderId: String, displayName: String): Result<Map<String, String>> =
        runCatching {
            val body = mapOf("display_name" to displayName)
            val response = api.renameFolder(folderId, body)
            response.requireSuccess()
            response.body()!!
        }

    suspend fun deleteFolder(folderId: String): Result<Unit> = runCatching {
        val response = api.deleteFolder(folderId)
        response.requireSuccess()
    }

    // ---------------------------------------------------------------- //
    // コンテンツ操作
    // ---------------------------------------------------------------- //

    suspend fun listContents(folderId: String): Result<List<Content>> = runCatching {
        val response = api.listContents(folderId)
        response.requireSuccess()
        response.body()!!.contents
    }

    suspend fun getContentBinary(
        folderId: String,
        contentId: String,
        kind: String = "original",
    ): Result<ByteArray> = runCatching {
        val response = api.getContentBinary(folderId, contentId, kind)
        response.requireSuccess()
        response.body()!!.bytes()
    }

    suspend fun deleteContent(folderId: String, contentId: String): Result<Map<String, List<String>>> =
        runCatching {
            val body = mapOf("content_ids" to listOf(contentId))
            val response = api.deleteContent(folderId, body)
            response.requireSuccess()
            response.body()!!
        }

    // ---------------------------------------------------------------- //
    // アップロード（3ステップ）
    // ---------------------------------------------------------------- //

    /**
     * 画像アップロード:
     * [1] POST .../upload → upload_id / upload_url 取得
     * [2] PUT <upload_url> にバイナリ送信
     * [3] POST .../contents でフォルダへ登録（425 時は最大4回リトライ）
     */
    suspend fun uploadImage(
        folderId: String,
        fileName: String,
        fileBytes: ByteArray,
    ): Result<Map<String, String>> = runCatching {
        // [1] セッション開始
        val sessionResp = api.startUploadSession(
            folderId,
            mapOf("name" to fileName, "target" to "content/original/image"),
        )
        sessionResp.requireSuccess()
        val session = sessionResp.body()!!

        // [2] バイナリアップロード
        val mimeType = mimeType(fileName)
        val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val putResp = api.putUploadBinary(session.uploadUrl, requestBody)
        putResp.requireSuccess()

        // [3] コンテンツ登録（425 → リトライ）
        val registerBody = mapOf<String, Any>(
            "upload_id" to session.uploadId,
            "kind" to "original",
            "name" to fileName,
            "bytes" to fileBytes.size,
            "utc_offset" to utcOffset(),
        )
        var regResp = api.registerContent(folderId, "10s", registerBody)
        var attempt = 0
        while (regResp.code() == 425 && attempt < 3) {
            kotlinx.coroutines.delay(3_000)
            regResp = api.registerContent(folderId, "10s", registerBody)
            attempt++
        }
        regResp.requireSuccess()
        regResp.body()!!
    }

    // ---------------------------------------------------------------- //
    // ヘルパー
    // ---------------------------------------------------------------- //

    private fun utcOffset(): String {
        val offset = ZonedDateTime.now().offset.toString()
        // "+0900" → "+09:00"
        return if (offset.length == 5) "${offset.substring(0, 3)}:${offset.substring(3)}" else offset
    }

    private fun mimeType(fileName: String): String = when {
        fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
        fileName.endsWith(".png", true) -> "image/png"
        fileName.endsWith(".arw", true) -> "image/x-sony-arw"
        else -> "application/octet-stream"
    }

    private fun <T> retrofit2.Response<T>.requireSuccess() {
        if (!isSuccessful) {
            throw Exception("API error ${code()}: ${errorBody()?.string()}")
        }
    }
}
