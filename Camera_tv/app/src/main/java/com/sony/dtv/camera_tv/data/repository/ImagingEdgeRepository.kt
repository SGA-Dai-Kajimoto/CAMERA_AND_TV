package com.sony.dtv.camera_tv.data.repository

import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.remote.ApiException
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/**
 * Imaging Edge API へのアクセスを集約するリポジトリ。
 *
 * 方針:
 *  - すべての公開関数は [Result] を返し、例外を呼び出し側へ漏らさない。
 *  - 通信は [ioDispatcher] 上で実行する。
 *  - コルーチンのキャンセルは「失敗」ではないため [Result] に包まず再スローする。
 *  - タグ文字列などのドメイン規約はここでは扱わない（domain パッケージの責務）。
 */
class ImagingEdgeRepository(
    private val api: ImagingEdgeApi,
    private val tokenPreferences: TokenPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---------------------------------------------------------------- //
    // ユーザー情報
    // ---------------------------------------------------------------- //

    /** ログインユーザー情報を取得し、user_id / account をローカルに保存する。 */
    suspend fun getUserMe(): Result<Map<String, Any>> = apiCall {
        val body = api.getUserMe().requireBody()
        val userId = body["user_id"]?.toString().orEmpty()
        val account = body["account"]?.toString() ?: userId
        tokenPreferences.saveUserId(userId)
        if (account.isNotEmpty()) tokenPreferences.saveAccount(account)
        body
    }

    // ---------------------------------------------------------------- //
    // フォルダ / コンテンツ一覧
    // ---------------------------------------------------------------- //

    /** 削除済み（removed_date あり）を除いたフォルダ一覧を返す。 */
    suspend fun listFolders(): Result<List<Folder>> = apiCall {
        api.listFolders().requireBody().folders.filter { it.removedDate == null }
    }

    /**
     * 1フォルダ分のコンテンツ一覧を返す。
     * API レスポンスに folder_id が含まれないため、ここで各 Content に付与する。
     */
    suspend fun listContents(
        folderId: String,
        orderBy: String = ORDER_BY_UPDATED_DESC,
        limit: Int = MAX_PAGE_SIZE,
        startFrom: String? = null,
        filterBy: String? = null,
    ): Result<List<Content>> = apiCall {
        api.listContents(folderId, orderBy, limit, startFrom, filterBy)
            .requireBody()
            .contents
            .map { it.copy(folderId = folderId) }
    }

    /** 全フォルダのコンテンツを結合して返す。 */
    suspend fun listAllContents(): Result<List<Content>> = apiCall {
        listFolders().getOrThrow().flatMap { folder ->
            listContents(folder.folderId).getOrThrow()
        }
    }

    // ---------------------------------------------------------------- //
    // コンテンツリソース
    // ---------------------------------------------------------------- //

    /** 画像バイナリを取得する。kind は "original" / "thumbnail_400" など。 */
    suspend fun getContentBinary(
        folderId: String,
        contentId: String,
        kind: String = KIND_ORIGINAL,
    ): Result<ByteArray> = apiCall {
        api.getContentBinary(folderId, contentId, kind).requireBody().bytes()
    }

    /** 事前署名済みダウンロードURL（認証不要・有効期限600秒）を取得する。共有QR用。 */
    suspend fun getContentDownloadUrl(
        folderId: String,
        contentId: String,
        kind: String = KIND_ORIGINAL,
    ): Result<String> = apiCall {
        val body = api.getContentDownloadUrl(folderId, contentId, kind).requireBody()
        body["download_url"]?.toString()
            ?: throw IllegalStateException("download_url is missing for contentId=$contentId")
    }

    // ---------------------------------------------------------------- //
    // 更新系
    // ---------------------------------------------------------------- //

    /** コンテンツのタグを丸ごと置き換える。既存タグの引き継ぎは呼び出し側の責務。 */
    suspend fun setContentTags(
        folderId: String,
        contentId: String,
        tags: List<String>,
    ): Result<Unit> = apiCall {
        api.setContentTags(folderId, contentId, mapOf("tags" to tags)).requireSuccess()
    }

    /** コンテンツを削除する。 */
    suspend fun removeContents(
        folderId: String,
        contentIds: List<String>,
    ): Result<Unit> = apiCall {
        api.removeContents(folderId, mapOf("content_ids" to contentIds)).requireSuccess()
    }

    // ---------------------------------------------------------------- //
    // 内部ユーティリティ
    // ---------------------------------------------------------------- //

    /**
     * API 呼び出しの共通ラッパー。
     * IO ディスパッチャで実行し、結果を [Result] に包む。
     * ただし [CancellationException] は「失敗」ではないので再スローして伝播させる。
     */
    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
        withContext(ioDispatcher) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    private fun Response<*>.requireSuccess() {
        if (!isSuccessful) throw ApiException(code(), errorBody()?.string())
    }

    private fun <T : Any> Response<T>.requireBody(): T {
        requireSuccess()
        return body() ?: throw IllegalStateException("Response body is null (HTTP ${code()})")
    }

    companion object {
        const val ORDER_BY_UPDATED_DESC = "updated_date_desc"
        const val MAX_PAGE_SIZE = 300
        const val KIND_ORIGINAL = "original"
    }
}
