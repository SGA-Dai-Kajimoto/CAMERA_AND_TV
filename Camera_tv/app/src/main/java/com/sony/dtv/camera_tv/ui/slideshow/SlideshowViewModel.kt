package com.sony.dtv.camera_tv.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SlideshowUiState(
    val isLoading: Boolean = false,
    val contents: List<Content> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = true,
    val thumbnailUrl: String? = null,
    val accessToken: String = "",
    val error: String? = null,
)

class SlideshowViewModel(
    private val repository: ImagingEdgeRepository,
    private val folderId: String,
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(SlideshowUiState())
    val uiState: StateFlow<SlideshowUiState> = _uiState.asStateFlow()

    private var autoAdvanceJob: Job? = null
    private var baseUrl: String = ""

    init {
        loadContents()
    }

    fun loadContents() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.listContents(folderId)
                .onSuccess { contents ->
                    baseUrl = repository.getBaseUrl()
                    val accessToken = repository.getAccessToken()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            contents = contents,
                            currentIndex = 0,
                            thumbnailUrl = contents.firstOrNull()?.let { c -> thumbnailUrl(c) },
                            accessToken = accessToken,
                        )
                    }
                    if (contents.isNotEmpty()) startAutoAdvance()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun nextImage() {
        val state = _uiState.value
        val size = state.contents.size
        if (size == 0) return
        val newIndex = (state.currentIndex + 1) % size
        _uiState.update {
            it.copy(
                currentIndex = newIndex,
                thumbnailUrl = thumbnailUrl(state.contents[newIndex]),
            )
        }
        restartAutoAdvance()
    }

    fun prevImage() {
        val state = _uiState.value
        val size = state.contents.size
        if (size == 0) return
        val newIndex = (state.currentIndex - 1 + size) % size
        _uiState.update {
            it.copy(
                currentIndex = newIndex,
                thumbnailUrl = thumbnailUrl(state.contents[newIndex]),
            )
        }
        restartAutoAdvance()
    }

    fun togglePlayPause() {
        val isNowPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = isNowPlaying) }
        if (isNowPlaying) startAutoAdvance() else cancelAutoAdvance()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ---------------------------------------------------------------- //
    // Private helpers
    // ---------------------------------------------------------------- //

    private fun thumbnailUrl(content: Content): String =
        "$baseUrl/api/v1/folders/$folderId/contents/${content.contentId}/resources/thumbnail_1920/binary"

    private fun startAutoAdvance() {
        cancelAutoAdvance()
        autoAdvanceJob = scope.launch {
            while (true) {
                delay(SLIDESHOW_INTERVAL_MS)
                val state = _uiState.value
                if (state.isPlaying && state.contents.isNotEmpty()) {
                    val newIndex = (state.currentIndex + 1) % state.contents.size
                    _uiState.update {
                        it.copy(
                            currentIndex = newIndex,
                            thumbnailUrl = thumbnailUrl(state.contents[newIndex]),
                        )
                    }
                }
            }
        }
    }

    private fun restartAutoAdvance() {
        if (_uiState.value.isPlaying) startAutoAdvance()
    }

    private fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelAutoAdvance()
    }

    companion object {
        const val SLIDESHOW_INTERVAL_MS = 5000L

        fun factory(
            repository: ImagingEdgeRepository,
            folderId: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SlideshowViewModel(repository, folderId) }
        }
    }
}
