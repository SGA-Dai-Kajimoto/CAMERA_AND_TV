package com.sony.dtv.camera_tv.data.repository

import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
import com.sony.dtv.camera_tv.data.remote.dto.ContentListResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /**
     * 全フォルダのコンテンツを一括取得する。
     * 各 Content に folderId を付与して返す。
     */
    suspend fun listAllContents(): Result<List<Content>> = runCatching {
        val folders = listFolders().getOrThrow()
        folders.flatMap { folder ->
            val response = api.listContents(folder.folderId, "updated_date_desc", 300, null)
            response.requireSuccess()
            response.body()!!.contents.map { it.copy(folderId = folder.folderId) }
        }
    }

    // ---------------------------------------------------------------- //
    // コンテンツバイナリ取得（スライドショー用）
    // ---------------------------------------------------------------- //

    suspend fun getContentBinary(
        folderId: String,
        contentId: String,
        kind: String = "original",
    ): Result<ByteArray> = runCatching {
        withContext(Dispatchers.IO) {
            val response = api.getContentBinary(folderId, contentId, kind)
            response.requireSuccess()
            val body = response.body()
                ?: throw IllegalStateException("Response body is null for contentId=$contentId, kind=$kind")
            body.bytes()
        }
    }.rethrowCancellation()

    /**
     * コンテンツの事前署名済みダウンロードURLを取得する（共有/QR用）。
     * 返却URLは認証不要・有効期限600秒（10分）。
     */
    suspend fun getContentDownloadUrl(
        folderId: String,
        contentId: String,
        kind: String = "original",
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val response = api.getContentDownloadUrl(folderId, contentId, kind)
            response.requireSuccess()
            val body = response.body()
                ?: throw IllegalStateException("Response body is null for contentId=$contentId, kind=$kind")
            body["download_url"]?.toString()
                ?: throw IllegalStateException("download_url is missing for contentId=$contentId")
        }
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

    /**
     * コンテンツに5段階評価（1〜5）を設定する。
     * @param folderId フォルダID
     * @param contentId コンテンツID
     * @param rating 評価（1〜5）
     */
    suspend fun setContentRating(
        folderId: String,
        contentId: String,
        rating: Int,
    ): Result<Unit> = runCatching {
        require(rating in 1..5) { "Rating must be between 1 and 5" }
        setContentTags(folderId, contentId, listOf("rating:$rating")).getOrThrow()
    }

    /**
     * 指定された評価のコンテンツ一覧を取得する。
     * @param folderId フォルダID
     * @param minRating 最小評価（1〜5）
     * @param orderBy ソート順（"updated_date_desc" or "updated_date_asc"）
     * @param limit 取得件数（1〜300、デフォルト100）
     * @return 評価でフィルタされたコンテンツリスト
     */
    suspend fun listContentsWithRatingFilter(
        folderId: String,
        minRating: Int = 4,
        orderBy: String = "updated_date_desc",
        limit: Int = 300,
    ): Result<List<Content>> = runCatching {
        require(minRating in 1..5) { "Rating must be between 1 and 5" }
        val filterBy = "rating:$minRating"
        val response = api.listContents(folderId, orderBy, limit, null, filterBy)
        response.requireSuccess()
        response.body()!!.contents
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

    /**
     * runCatching がコルーチンのキャンセル例外（CancellationException）まで
     * 捕捉してしまうのを防ぐ。キャンセルは失敗ではないので再スローして正常に伝播させる。
     */
    private fun <T> Result<T>.rethrowCancellation(): Result<T> =
        onFailure { if (it is CancellationException) throw it }
}
