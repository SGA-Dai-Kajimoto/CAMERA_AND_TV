package com.sony.dtv.carmera_poc.data.remote

import com.sony.dtv.carmera_poc.data.remote.dto.ContentListResponse
import com.sony.dtv.carmera_poc.data.remote.dto.FolderListResponse
import com.sony.dtv.carmera_poc.data.remote.dto.TokenResponse
import com.sony.dtv.carmera_poc.data.remote.dto.UploadSessionResponse
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ImagingEdgeApi {

    @POST("api/v1/oauth2/token")
    suspend fun refreshToken(@Body body: Map<String, Any>): Response<TokenResponse>

    @GET("api/v1/user/me")
    suspend fun getUserMe(): Response<Map<String, Any>>

    @GET("api/v1/folders")
    suspend fun listFolders(): Response<FolderListResponse>

    @POST("api/v1/folders")
    suspend fun createFolder(@Body body: Map<String, String>): Response<Map<String, String>>

    @PUT("api/v1/folders/{folderId}")
    suspend fun renameFolder(
        @Path("folderId") folderId: String,
        @Body body: Map<String, String>,
    ): Response<Map<String, String>>

    @DELETE("api/v1/folders/{folderId}")
    suspend fun deleteFolder(@Path("folderId") folderId: String): Response<Unit>

    @GET("api/v1/folders/{folderId}/contents")
    suspend fun listContents(@Path("folderId") folderId: String): Response<ContentListResponse>

    @Streaming
    @GET("api/v1/folders/{folderId}/contents/{contentId}/resources/{kind}/binary")
    suspend fun getContentBinary(
        @Path("folderId") folderId: String,
        @Path("contentId") contentId: String,
        @Path("kind") kind: String = "original",
    ): Response<ResponseBody>

    @POST("api/v1/folders/{folderId}/contents:remove")
    suspend fun deleteContent(
        @Path("folderId") folderId: String,
        @Body body: Map<String, List<String>>,
    ): Response<Map<String, List<String>>>

    @POST("api/v1/folders/{folderId}/upload")
    suspend fun startUploadSession(
        @Path("folderId") folderId: String,
        @Body body: Map<String, String>,
    ): Response<UploadSessionResponse>

    @PUT
    suspend fun putUploadBinary(
        @retrofit2.http.Url url: String,
        @Body body: RequestBody,
    ): Response<Unit>

    @POST("api/v1/folders/{folderId}/contents")
    suspend fun registerContent(
        @Path("folderId") folderId: String,
        @Query("wait_time") waitTime: String = "10s",
        @Body body: Map<String, Any>,
    ): Response<Map<String, String>>
}
