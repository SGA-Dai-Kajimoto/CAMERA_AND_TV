package com.sony.dtv.camera_tv.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sony.dtv.camera_tv.data.local.TokenPreferences
import com.sony.dtv.camera_tv.data.remote.pairing.PairingApi
import com.sony.dtv.camera_tv.ui.common.generateQrBitmap

/**
 * デバイスフロー認証画面。
 *
 * QR を PC のブラウザで開くとログイン画面へ飛び、ログインが終わると
 * この画面が自動で切り替わる。6桁の番号は QR を読めないとき用のフォールバック。
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
            else -> AuthCode(
                userCode = state.userCode!!,
                verificationUri = state.verificationUri,
            )
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
private fun AuthCode(userCode: String, verificationUri: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (verificationUri != null) {
                QrPanel(url = verificationUri)
                Spacer(Modifier.height(20.dp))
            }
            Text(
                "確認コード",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatCode(userCode),
                fontSize = 44.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

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
                "1. PC のブラウザで左の QR コードの URL を開く\n" +
                    "2. Creators Cloud にログインする\n" +
                    "3. 「テレビに戻ってください」と表示されたら完了\n" +
                    "4. この画面は自動的に切り替わります",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (verificationUri != null) {
                Text(
                    verificationUri,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

/** QR は白背景でないと読み取り精度が落ちるため、白いパネルに載せる。 */
@Composable
private fun QrPanel(url: String) {
    val bitmap = remember(url) { generateQrBitmap(url, QR_SIZE_PX) }
    Box(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "ログイン用QRコード",
                modifier = Modifier.size(QR_DISPLAY_SIZE),
            )
        } else {
            Text("QRを生成できませんでした", fontSize = 16.sp, color = Color.Black)
        }
    }
}

private const val QR_SIZE_PX = 640
private val QR_DISPLAY_SIZE = 280.dp

/** 読み上げやすいように半分で区切る（"BCDF-GHJK"）。 */
private fun formatCode(code: String): String =
    if (code.length >= 6 && code.length % 2 == 0) {
        val half = code.length / 2
        "${code.substring(0, half)}-${code.substring(half)}"
    } else {
        code
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
