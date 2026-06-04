package com.sony.dtv.camera_tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val tokenPreferences: TokenPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val baseUrl = tokenPreferences.baseUrl.first()
            val accessToken = tokenPreferences.accessToken.first()
            val refreshToken = tokenPreferences.refreshToken.first()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                )
            }
        }
    }

    fun saveSettings(
        baseUrl: String,
        accessToken: String,
        refreshToken: String,
    ) {
        if (accessToken.isBlank()) {
            _uiState.update { it.copy(error = "Access token は必須です") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isSaved = false) }
            try {
                val trimmedBaseUrl = baseUrl.trimEnd('/').ifEmpty { TokenPreferences.DEFAULT_BASE_URL }
                tokenPreferences.saveBaseUrl(trimmedBaseUrl)
                tokenPreferences.saveAccessToken(accessToken.trim())
                tokenPreferences.saveRefreshToken(refreshToken.trim())
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "保存に失敗しました") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }

    companion object {
        fun factory(tokenPreferences: TokenPreferences): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(tokenPreferences) }
        }
    }
}
