package com.sony.dtv.camera_tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sony.dtv.camera_tv.data.local.TokenPreferences

@Composable
fun SettingsScreen(
    tokenPreferences: TokenPreferences,
    onSaved: () -> Unit,
) {
    val settingsViewModel = viewModel<SettingsViewModel>(
        factory = SettingsViewModel.factory(tokenPreferences),
    )
    val uiState by settingsViewModel.uiState.collectAsState()

    // 保存完了したらコールバック
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            settingsViewModel.resetSaved()
            onSaved()
        }
    }

    // 初期値が読み込まれたらフォームフィールドに反映する
    var baseUrl by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf("") }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) {
            if (baseUrl.isEmpty()) baseUrl = uiState.baseUrl
            if (accessToken.isEmpty()) accessToken = uiState.accessToken
            if (refreshToken.isEmpty()) refreshToken = uiState.refreshToken
        }
    }

    val accessTokenFocusRequester = remember { FocusRequester() }
    val refreshTokenFocusRequester = remember { FocusRequester() }
    val saveFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 64.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        // ヘッダー
        Text(
            text = "接続設定",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Imaging Edge Cloud に接続するための認証情報を入力してください。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Base URL
        SettingsField(
            label = "サーバー URL",
            value = baseUrl,
            onValueChange = { baseUrl = it },
            placeholder = TokenPreferences.DEFAULT_BASE_URL,
            imeAction = ImeAction.Next,
            onNext = { accessTokenFocusRequester.requestFocus() },
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Access Token
        SettingsField(
            label = "アクセストークン",
            value = accessToken,
            onValueChange = { accessToken = it },
            placeholder = "access_token を入力",
            isPassword = true,
            imeAction = ImeAction.Next,
            onNext = { refreshTokenFocusRequester.requestFocus() },
            modifier = Modifier.focusRequester(accessTokenFocusRequester),
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Refresh Token
        SettingsField(
            label = "リフレッシュトークン",
            value = refreshToken,
            onValueChange = { refreshToken = it },
            placeholder = "refresh_token を入力（任意）",
            isPassword = true,
            imeAction = ImeAction.Done,
            onNext = { saveFocusRequester.requestFocus() },
            modifier = Modifier.focusRequester(refreshTokenFocusRequester),
        )
        Spacer(modifier = Modifier.height(32.dp))

        // エラー表示
        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        // 保存ボタン
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.wrapContentWidth().padding(end = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    settingsViewModel.saveSettings(
                        baseUrl = baseUrl,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                    )
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.focusRequester(saveFocusRequester),
            ) {
                Text(text = "保存して接続")
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onNext() },
                onDone = { onNext() },
            ),
            modifier = modifier.fillMaxWidth(),
        )
    }
}
