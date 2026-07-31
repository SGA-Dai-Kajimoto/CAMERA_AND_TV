package com.sony.dtv.camera_tv.ui.auth

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.pairing.PairingApi

/**
 * 番号ペアリング認証画面。
 * PC で Creators Cloud にログインし、ブックマークレットでこの番号を入力すると、
 * トークンがローカルサーバー経由でTVへ渡され認証完了する。
 */
@Composable
fun AuthScreen(
    pairingApi: PairingApi,
    tokenPreferences: TokenPreferences,
    onAuthenticated: () -> Unit,
) {
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(pairingApi, tokenPreferences),
    )
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.error != null -> AuthError(
                message = state.error!!,
                onRetry = { viewModel.startPairing() },
            )
            state.isLoading || state.userCode == null -> AuthLoading()
            else -> AuthCode(userCode = state.userCode!!)
        }
    }
}

@Composable
private fun AuthLoading() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text("認証を準備しています…", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun AuthCode(userCode: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        // 6桁コード
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "確認番号",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                formatCode(userCode),
                fontSize = 88.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // 手順説明
        Column(
            modifier = Modifier.width(560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "PC でログインして認証",
                fontSize = 30.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "1. PC のブラウザで Creators Cloud にログイン\n" +
                    "2. ブックマーク「TVへトークン送信」をクリック\n" +
                    "3. 左の確認番号を入力する\n" +
                    "4. 送信されると自動的にこの画面が切り替わります",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "送信待機中…",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 6桁を "123 456" のように3桁ずつ区切って読みやすくする。 */
private fun formatCode(code: String): String =
    if (code.length == 6) "${code.substring(0, 3)} ${code.substring(3)}" else code

@Composable
private fun AuthError(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            message,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.focusable(),
        ) {
            Text("もう一度試す", fontSize = 20.sp)
        }
    }
}
