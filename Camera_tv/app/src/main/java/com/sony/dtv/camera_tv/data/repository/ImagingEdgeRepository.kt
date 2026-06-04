package com.sony.dtv.camera_tv.data.repository

import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.remote.ImagingEdgeApi
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
    // 内部ユーティリティ
    // ---------------------------------------------------------------- //

    private fun <T> Response<T>.requireSuccess() {
        if (!isSuccessful) {
            throw IllegalStateException("API error ${code()}: ${errorBody()?.string()}")
        }
    }
}
