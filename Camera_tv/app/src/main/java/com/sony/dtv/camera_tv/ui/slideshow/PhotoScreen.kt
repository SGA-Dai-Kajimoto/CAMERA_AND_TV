package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.ui.common.InfoChip
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.rememberFullscreenReqPx
import com.sony.dtv.camera_tv.ui.common.rememberSampledBitmap
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private val DATE_CHIP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd (E)")

/** 自動再生中に止め方を見せておく時間。 */
private const val PLAY_HINT_VISIBLE_MS = 4000L

/**
 * 画面1: フルスクリーン写真表示。
 * 左右で写真切替、決定／下でメニューを開く。
 *
 * 自動再生中は写真以外を一切描かない。鑑賞中に日付や枚数が出ていると
 * そちらに目が向き、大画面で写真を見るという体験を壊す。
 */
@Composable
internal fun PhotoScreen(
    uiState: SlideshowUiState,
    actions: SlideshowActions,
    onShowMenu: () -> Unit,
) {
    var playHintVisible by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.isPlaying) {
        playHintVisible = true
        delay(PLAY_HINT_VISIBLE_MS)
        playHintVisible = false
    }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.Background),
        onKey = { key ->
            when (key) {
                Key.DirectionLeft -> { actions.prev(); true }
                Key.DirectionRight -> { actions.next(); true }
                Key.DirectionDown, Key.Enter, Key.DirectionCenter -> {
                    if (uiState.isPlaying) actions.togglePlay() else onShowMenu()
                    true
                }

                Key.MediaPlayPause -> { actions.togglePlay(); true }
                // 再生中のみ戻るを消費する。常に取るとアプリを終了できなくなる
                Key.Back -> if (uiState.isPlaying) { actions.togglePlay(); true } else false
                else -> false
            }
        },
    ) {
        PhotoImage(uiState.currentImageBytes)

        if (uiState.isPlaying) {
            // 止め方だけは短く出す。これが無いと戻れなくなる
            AnimatedVisibility(
                visible = playHintVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TvDimens.SpaceLg),
            ) {
                KeyHint(text = stringResource(R.string.photo_play_hint))
            }
            return@KeyInputSurface
        }

        if (uiState.isImageLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = TvColors.OnSurface,
            )
        }

        // 上段: 日付と枚数
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(TvDimens.SpaceLg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm)) {
                uiState.selectedDate?.let { date ->
                    InfoChip(text = date.format(DATE_CHIP_FORMATTER))
                }
            }
            if (uiState.visibleContents.isNotEmpty()) {
                InfoChip(
                    text = stringResource(
                        R.string.photo_counter,
                        uiState.currentIndex + 1,
                        uiState.visibleContents.size,
                    ),
                )
            }
        }

        // 下段: 評価と操作ヒント
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(TvDimens.SpaceLg),
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
        ) {
            if (uiState.currentRating > 0) {
                InfoChip(
                    text = "★".repeat(uiState.currentRating),
                    color = TvColors.Star,
                )
            }
        }

        // 自動再生中でなければ常時出しておく。消えてしまうと、この画面から
        // 何ができるのかが分からなくなる
        KeyHint(
            text = stringResource(R.string.photo_hint),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = TvDimens.SpaceLg),
        )
    }
}

/**
 * 写真本体。切り替え時にクロスフェードして、TV でのちらつきを抑える。
 * 原寸は GPU テクスチャ上限とヒープを超えるため、描画面の解像度までダウンサンプルする。
 */
@Composable
internal fun PhotoImage(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val reqPx = rememberFullscreenReqPx()
    Crossfade(
        targetState = bytes,
        animationSpec = tween(durationMillis = 260),
        label = "photoCrossfade",
        modifier = modifier.fillMaxSize(),
    ) { target ->
        val bitmap = rememberSampledBitmap(target, reqPx)
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.photo_content_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(TvColors.Background))
        }
    }
}

/** 読み込み中・エラー・空状態の中央表示。 */
@Composable
internal fun StatusMessage(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(TvDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
    ) {
        Text(text = title, color = TvColors.OnSurface, fontSize = TvTextSizes.Title)
        if (detail != null) {
            Text(text = detail, color = TvColors.OnSurfaceMuted, fontSize = TvTextSizes.Body)
        }
    }
}

/** 画面下部に短時間表示する通知。 */
@Composable
internal fun TvToast(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Text(
            text = message.orEmpty(),
            color = TvColors.OnSurface,
            fontSize = TvTextSizes.Body,
            modifier = Modifier
                .background(TvColors.ScrimStrong, TvShapes.Pill)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}
