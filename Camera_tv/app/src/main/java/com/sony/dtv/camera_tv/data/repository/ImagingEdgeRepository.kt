package com.sony.dtv.camera_tv.data.repository

import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
import com.sony.dtv.camera_tv.data.remote.dto.ContentListResponse
import retrofit2.Response

/**
 * Imaging Edge API の読み取り操作を提供するリポジトリ。
 * TV アプリはコンテンツを閲覧するだけのため、書き込み系 API は持たない。
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
    // フォルダ一覧
    // ---------------------------------------------------------------- //

    suspend fun listFolders(): Result<List<Folder>> = runCatching {
        val response = api.listFolders()
        response.requireSuccess()
        response.body()!!.folders.filter { it.removedDate == null }
    }

    // ---------------------------------------------------------------- //
    // コンテンツ一覧
    // ---------------------------------------------------------------- //

    suspend fun listContents(folderId: String): Result<List<Content>> = runCatching {
        val response = api.listContents(folderId)
        response.requireSuccess()
        response.body()!!.contents
    }

    /**
     * コンテンツ一覧を取得する（ページネーション・ソート対応）。
     * @param folderId フォルダID
     * @param orderBy ソート順。"updated_date_desc" or "updated_date_asc"
     * @param limit 取得件数上限（1〜300）
     * @param startFrom ページネーション用。前回レスポンスの lastItem を指定
     * @return ContentListResponse（contents + lastItem）
     */
    suspend fun listContentsPaged(
        folderId: String,
        orderBy: String = "updated_date_desc",
        limit: Int = 300,
        startFrom: String? = null,
    ): Result<ContentListResponse> = runCatching {
        val response = api.listContents(folderId, orderBy, limit, startFrom)
        response.requireSuccess()
        response.body()!!
    }

    // ---------------------------------------------------------------- //
    // コンテンツバイナリ取得（スライドショー用）
    // ---------------------------------------------------------------- //

    suspend fun getContentBinary(
        folderId: String,
        contentId: String,
        kind: String = "original",
    ): Result<ByteArray> = runCatching {
        val response = api.getContentBinary(folderId, contentId, kind)
        response.requireSuccess()
        response.body()!!.bytes()
    }

    // ---------------------------------------------------------------- //
    // お気に入り（タグ操作）
    // ---------------------------------------------------------------- //

    suspend fun setContentTags(
        folderId: String,
        contentId: String,
        tags: List<String>,
    ): Result<Unit> = runCatching {
        val body = mapOf("tags" to tags)
        val response = api.setContentTags(folderId, contentId, body)
        response.requireSuccess()
    }

    // ---------------------------------------------------------------- //
    // コンテンツ削除
    // ---------------------------------------------------------------- //

    suspend fun removeContents(
        folderId: String,
        contentIds: List<String>,
    ): Result<Unit> = runCatching {
        val body = mapOf("content_ids" to contentIds)
        val response = api.removeContents(folderId, body)
        response.requireSuccess()
    }

    // ---------------------------------------------------------------- //
    // 内部ユーティリティ
    // ---------------------------------------------------------------- //

    private fun <T> Response<T>.requireSuccess() {
        if (!isSuccessful) {
            throw IllegalStateException("API error ${code()}: ${errorBody()?.string()}")
        }
    }
}
