package com.sony.dtv.camera_tv.data.remote

import com.sony.dtv.camera_tv.data.remote.dto.ContentListResponse
import com.sony.dtv.camera_tv.data.remote.dto.FolderListResponse
import com.sony.dtv.camera_tv.data.remote.dto.TokenResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Imaging Edge API インターフェース。
 * TV アプリは読み取り専用のため、一覧取得・バイナリ取得・認証のみを定義する。
 */
interface ImagingEdgeApi {

    @POST("api/v1/oauth2/token")
    suspend fun refreshToken(@Body body: Map<String, Any>): Response<TokenResponse>

    @GET("api/v1/user/me")
    suspend fun getUserMe(): Response<Map<String, Any>>

    @GET("api/v1/folders")
    suspend fun listFolders(): Response<FolderListResponse>

    @GET("api/v1/folders/{folderId}/contents")
    suspend fun listContents(
        @Path("folderId") folderId: String,
        @Query("order_by") orderBy: String? = "updated_date_desc",
        @Query("limit") limit: Int? = 300,
        @Query("start_from") startFrom: String? = null,
    ): Response<ContentListResponse>

    @Streaming
    @GET("api/v1/folders/{folderId}/contents/{contentId}/resources/{kind}/binary")
    suspend fun getContentBinary(
        @Path("folderId") folderId: String,
        @Path("contentId") contentId: String,
        @Path("kind") kind: String,
    ): Response<ResponseBody>
}
