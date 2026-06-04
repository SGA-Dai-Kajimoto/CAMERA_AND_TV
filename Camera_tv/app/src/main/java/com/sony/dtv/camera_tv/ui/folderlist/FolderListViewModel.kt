package com.sony.dtv.camera_tv.ui.folderlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderListUiState(
    val isLoading: Boolean = false,
    val loginUser: String = "",
    val folders: List<Folder> = emptyList(),
    val error: String? = null,
)

class FolderListViewModel(
    private val repository: ImagingEdgeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderListUiState())
    val uiState: StateFlow<FolderListUiState> = _uiState.asStateFlow()

    init {
        loadFolders()
    }

    fun loadFolders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // ユーザー情報取得（失敗しても続行）
            repository.getUserMe()
                .onSuccess { info ->
                    _uiState.update { it.copy(loginUser = info["user_id"]?.toString() ?: "") }
                }

            repository.listFolders()
                .onSuccess { folders ->
                    _uiState.update { it.copy(isLoading = false, folders = folders) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        fun factory(repository: ImagingEdgeRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { FolderListViewModel(repository) }
            }
    }
}
