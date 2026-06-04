package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository

@Composable
fun SlideshowScreen(
    folderId: String,
    repository: ImagingEdgeRepository,
    onBack: () -> Unit,
) {
    val viewModel = viewModel<SlideshowViewModel>(
        factory = SlideshowViewModel.factory(repository, folderId),
        key = "slideshow_$folderId",
    )
    val uiState by viewModel.uiState.collectAsState()

    val focusRequester = remember { FocusRequester() }

    // スライドショー画面起動時にフォーカスを要求してリモコンキーを受け取れるようにする
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> { viewModel.prevImage(); true }
                        Key.DirectionRight -> { viewModel.nextImage(); true }
                        Key.Enter, Key.DirectionCenter -> { viewModel.togglePlayPause(); true }
                        Key.Back -> { onBack(); true }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        when {
            // 初期ロード中
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }

            // エラー
            uiState.error != null -> {
                Text(
                    text = "エラー: ${uiState.error}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            // コンテンツなし
            uiState.contents.isEmpty() -> {
                Text(
                    text = "このフォルダに写真はありません",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // 画像表示
            else -> {
                val context = LocalContext.current
                // 認証ヘッダ付き ImageRequestを生成。thumbnailUrl 変化時に自動再ロード
                val imageRequest = remember(uiState.thumbnailUrl, uiState.accessToken) {
                    ImageRequest.Builder(context)
                        .data(uiState.thumbnailUrl)
                        .addHeader("Authorization", "Bearer ${uiState.accessToken}")
                        .crossfade(true)
                        .build()
                }

                var painterState by remember(uiState.thumbnailUrl) {
                    mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = "スライドショー画像",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onState = { painterState = it },
                )

                // Coil ロード中オーバーレイ
                if (painterState is AsyncImagePainter.State.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White,
                    )
                }

                // 下部コントロールバー
                SlideshowControlBar(
                    currentIndex = uiState.currentIndex,
                    total = uiState.contents.size,
                    isPlaying = uiState.isPlaying,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun SlideshowControlBar(
    currentIndex: Int,
    total: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 操作ヒント
        Text(
            text = "◀ 前  ▶ 次  ● ${if (isPlaying) "一時停止" else "再生"}",
            color = Color.White,
            fontSize = 14.sp,
        )

        // 現在の位置
        Text(
            text = "${currentIndex + 1} / $total",
            color = Color.White,
            fontSize = 16.sp,
        )

        // 再生状態
        Text(
            text = if (isPlaying) "▶ 再生中" else "⏸ 停止中",
            color = if (isPlaying) Color.Green else Color.Yellow,
            fontSize = 14.sp,
        )
    }
}
