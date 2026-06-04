package com.sony.dtv.carmera_poc.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sony.dtv.carmera_poc.data.local.TokenPreferences
import com.sony.dtv.carmera_poc.data.model.Content
import com.sony.dtv.carmera_poc.data.model.Folder
import com.sony.dtv.carmera_poc.data.remote.AuthInterceptor
import com.sony.dtv.carmera_poc.data.remote.ImagingEdgeApi
import com.sony.dtv.carmera_poc.data.repository.ImagingEdgeRepository
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class UiState(
    val isLoading: Boolean = false,
    val statusMessage: String = "",
    val loginUser: String = "",
    val folders: List<Folder> = emptyList(),
    val selectedFolder: Folder? = null,
    val contents: List<Content> = emptyList(),
    val error: String? = null,
)

class MainViewModel(
    private val repository: ImagingEdgeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ---------------------------------------------------------------- //
    // 初期化・フォルダ一覧
    // ---------------------------------------------------------------- //

    /** アプリ起動時 / 再読み込みボタン: getUserMe → listFolders */
    fun loadFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.getUserMe()
                .onSuccess { userInfo ->
                    val userId = userInfo["user_id"]?.toString() ?: ""
                    _uiState.update { it.copy(loginUser = userId) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    return@launch
                }

            repository.listFolders()
                .onSuccess { folders ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            folders = folders,
                            statusMessage = "${folders.size} フォルダ",
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    // ---------------------------------------------------------------- //
    // フォルダ選択
    // ---------------------------------------------------------------- //

    fun selectFolder(folder: Folder) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedFolder = folder, error = null) }
            repository.listContents(folder.folderId)
                .onSuccess { contents ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            contents = contents,
                            statusMessage = "${contents.size} コンテンツ",
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    // ---------------------------------------------------------------- //
    // フォルダ操作
    // ---------------------------------------------------------------- //

    fun createFolder(displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.createFolder(displayName)
                .onSuccess { refreshFolders("フォルダを作成しました") }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun renameFolder(folderId: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.renameFolder(folderId, displayName)
                .onSuccess { refreshFolders("フォルダ名を変更しました") }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.deleteFolder(folderId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            selectedFolder = if (state.selectedFolder?.folderId == folderId) null else state.selectedFolder,
                            contents = if (state.selectedFolder?.folderId == folderId) emptyList() else state.contents,
                        )
                    }
                    refreshFolders("フォルダを削除しました")
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    // ---------------------------------------------------------------- //
    // コンテンツ操作
    // ---------------------------------------------------------------- //

    fun uploadImage(folderId: String, fileName: String, fileBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "アップロード中...", error = null) }
            repository.uploadImage(folderId, fileName, fileBytes)
                .onSuccess { refreshContents(folderId, "アップロードしました") }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun deleteContent(folderId: String, contentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.deleteContent(folderId, contentId)
                .onSuccess { refreshContents(folderId, "コンテンツを削除しました") }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    // ---------------------------------------------------------------- //
    // エラークリア
    // ---------------------------------------------------------------- //

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ---------------------------------------------------------------- //
    // Private helpers
    // ---------------------------------------------------------------- //

    private suspend fun refreshFolders(message: String) {
        repository.listFolders()
            .onSuccess { folders ->
                _uiState.update {
                    it.copy(isLoading = false, folders = folders, statusMessage = message)
                }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    private suspend fun refreshContents(folderId: String, message: String) {
        repository.listContents(folderId)
            .onSuccess { contents ->
                _uiState.update {
                    it.copy(isLoading = false, contents = contents, statusMessage = message)
                }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    // ---------------------------------------------------------------- //
    // Factory
    // ---------------------------------------------------------------- //

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val tokenPrefs = TokenPreferences.create(context)
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
                val okHttpClient = OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(tokenPrefs))
                    .addInterceptor(logging)
                    .build()
                val baseUrl = kotlinx.coroutines.runBlocking {
                    tokenPrefs.baseUrl.first().ifEmpty { "https://api.imaging-edge.sony.net/" }
                }
                val gson = GsonBuilder().create()
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build()
                val api = retrofit.create(ImagingEdgeApi::class.java)
                MainViewModel(ImagingEdgeRepository(api, tokenPrefs))
            }
        }
    }
}
