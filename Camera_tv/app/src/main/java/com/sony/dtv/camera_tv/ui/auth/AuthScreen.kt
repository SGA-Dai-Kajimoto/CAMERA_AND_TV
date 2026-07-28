package com.sony.dtv.camera_tv.ui.auth

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.pairing.PairingApi
import com.sony.dtv.camera_tv.ui.common.generateQrBitmap

/**
 * QR コード認証画面。
 * スマホでQRを読み取り Creators Cloud にログインすると、トークンがTVへ渡され認証完了する。
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
            state.isLoading || state.pairingUrl == null -> AuthLoading()
            else -> AuthQr(pairingUrl = state.pairingUrl!!)
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
private fun AuthQr(pairingUrl: String) {
    val qrBitmap = remember(pairingUrl) { generateQrBitmap(pairingUrl, 720) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        // QR コード
        Box(
            modifier = Modifier.size(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "認証用QRコード",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("QRコードを生成できませんでした", color = MaterialTheme.colorScheme.error)
            }
        }

        // 手順説明
        Column(
            modifier = Modifier.width(520.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "スマートフォンでログイン",
                fontSize = 30.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "1. スマホのカメラで左のQRコードを読み取る\n" +
                    "2. 表示されたページで Creators Cloud にログイン\n" +
                    "3. ログインが完了すると自動的にこの画面が切り替わります",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "ログイン待機中…",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
