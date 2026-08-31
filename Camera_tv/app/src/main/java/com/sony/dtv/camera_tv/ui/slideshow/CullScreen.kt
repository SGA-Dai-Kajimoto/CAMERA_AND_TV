package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.domain.ContentCulling
import com.sony.dtv.camera_tv.domain.ContentRating
import com.sony.dtv.camera_tv.domain.ratingValue
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.InfoChip
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.TvActionButton
import com.sony.dtv.camera_tv.ui.common.ZoomedRegion
import com.sony.dtv.camera_tv.ui.common.rememberFullscreenReqPx
import com.sony.dtv.camera_tv.ui.common.rememberSampledBitmap
import com.sony.dtv.camera_tv.ui.common.rememberZoomedRegion
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes
import java.time.format.DateTimeFormatter

/** ミニマップの幅。全体像のどこを見ているかが分かればよいので小さくてよい。 */
private val MINIMAP_WIDTH = 200.dp

private val CULL_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E)")

/**
 * 画面5: 選別。未判定の写真を 1 枚ずつ「採用 / 見送り」で捌く。
 *
 * ← → は判定せずに候補を行き来する。拡大したまま候補を切り替えられるようにしてあり、
 * 同じ座標・等倍で画像が入れ替わることで、連写どうしの差が「ちらつき」として見える。
 *
 * 上下キーの意味が分かるよう、採用は画面の上端、見送りは下端に常時ラベルを出す。
 */
@Composable
internal fun CullScreen(
    uiState: SlideshowUiState,
    actions: SlideshowActions,
    onExit: () -> Unit,
    onReview: () -> Unit,
    onCompare: () -> Unit,
) {
    if (uiState.isCullComplete) {
        CullCompleteScreen(
            pickedCount = uiState.pickedContents.size,
            skippedCount = uiState.skippedContents.size,
            onExit = onExit,
            onReview = onReview,
        )
        return
    }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val zoomed = rememberZoomedRegion(
        bytes = uiState.zoomImageBytes.takeIf { uiState.isZoomed },
        viewportWidth = viewport.width,
        viewportHeight = viewport.height,
        magnification = uiState.zoomMagnification,
        centerX = uiState.zoomCenterX,
        centerY = uiState.zoomCenterY,
    )

    KeyInputSurface(
        modifier = Modifier
            .background(TvColors.Background)
            .onSizeChanged { viewport = it },
        onKey = { key ->
            // 拡大中は方向キーを表示位置の移動に使う。
            // 写真の送りと採用・見送りは拡大を解除してから行う。
            if (uiState.isZoomed) {
                when (key) {
                    Key.DirectionLeft -> { actions.panZoom(-1, 0); true }
                    Key.DirectionRight -> { actions.panZoom(1, 0); true }
                    Key.DirectionUp -> { actions.panZoom(0, -1); true }
                    Key.DirectionDown -> { actions.panZoom(0, 1); true }
                    Key.Enter, Key.DirectionCenter -> { actions.cycleZoom(); true }
                    Key.ChannelUp -> { onCompare(); true }
                    Key.Back -> { actions.zoomOff(); true }
                    else -> false
                }
            } else {
                when (key) {
                    Key.DirectionLeft -> { actions.cullPrev(); true }
                    Key.DirectionRight -> { actions.cullNext(); true }
                    Key.DirectionUp -> { actions.cullPick(); true }
                    Key.DirectionDown -> { actions.cullSkip(); true }
                    Key.Enter, Key.DirectionCenter -> { actions.cycleZoom(); true }
                    Key.ChannelUp -> { onCompare(); true }
                    Key.ChannelDown -> { onReview(); true }
                    Key.Back -> { onExit(); true }
                    else -> false
                }
            }
        },
    ) {
        if (uiState.isZoomed) {
            ZoomedImage(zoomed = zoomed, isLoading = uiState.isZoomLoading)
        } else {
            CullPreviewImage(uiState.currentImageBytes)
        }

        if (uiState.isImageLoading && !uiState.isZoomed) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = TvColors.OnSurface,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(TvDimens.SpaceLg),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm)) {
                InfoChip(
                    text = stringResource(
                        R.string.cull_progress,
                        uiState.cullIndex + 1,
                        uiState.cullQueue.size,
                    ),
                    color = TvColors.Accent,
                )
                // キューには判定済みも並ぶので、この写真がどちらなのかを常に示す
                val rating = uiState.cullContent?.ratingValue() ?: ContentRating.NONE
                if (rating != ContentRating.NONE) {
                    val picked = rating >= ContentCulling.PICK
                    InfoChip(
                        text = stringResource(
                            if (picked) R.string.cull_pick else R.string.cull_skip,
                        ),
                        color = if (picked) TvColors.Pick else TvColors.OnSurfaceMuted,
                    )
                }
                if (uiState.cullRemaining > 0) {
                    InfoChip(
                        text = stringResource(R.string.cull_unjudged, uiState.cullRemaining),
                        color = TvColors.OnSurfaceMuted,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm)) {
                InfoChip(
                    text = uiState.cullDate?.format(CULL_DATE_FORMATTER)
                        ?: stringResource(R.string.cull_scope_all),
                    color = TvColors.OnSurface,
                )
                if (uiState.isZoomed) {
                    InfoChip(
                        text = stringResource(
                            R.string.cull_zoom_badge,
                            (uiState.zoomMagnification * 100).toInt(),
                        ),
                        color = TvColors.Star,
                    )
                }
            }
        }

        KeyHint(
            text = stringResource(
                if (uiState.isZoomed) R.string.cull_hint_zoomed else R.string.cull_hint,
            ),
            modifier = Modifier.align(Alignment.BottomCenter).padding(TvDimens.SpaceLg),
        )

        if (zoomed != null) {
            ZoomMinimap(
                region = zoomed,
                modifier = Modifier.align(Alignment.BottomEnd).padding(TvDimens.SpaceLg),
            )
        }
    }
}

/** 選別中のプレビュー。全画面表示なので描画面の解像度までダウンサンプルする。 */
@Composable
private fun CullPreviewImage(bytes: ByteArray?) {
    val reqPx = rememberFullscreenReqPx()
    Crossfade(
        targetState = bytes,
        animationSpec = tween(durationMillis = 160),
        label = "cullCrossfade",
        modifier = Modifier.fillMaxSize(),
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

/**
 * 拡大表示。原寸画像から必要な矩形だけを切り出して描く。
 * 切り出す範囲は倍率に反比例するので、200% では元画像の 1/4 の幅だけが見える。
 */
@Composable
private fun ZoomedImage(zoomed: ZoomedRegion?, isLoading: Boolean) {
    Box(modifier = Modifier.fillMaxSize().background(TvColors.Background)) {
        if (zoomed != null) {
            Image(
                bitmap = zoomed.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.cull_zoom_content_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = TvColors.OnSurface,
            )
        }
    }
}

/** 全体像のどこを拡大しているかを示すミニマップ。 */
@Composable
private fun ZoomMinimap(region: ZoomedRegion, modifier: Modifier = Modifier) {
    val aspect = region.sourceWidth.toFloat() / region.sourceHeight
    Box(
        modifier = modifier
            .width(MINIMAP_WIDTH)
            .aspectRatio(aspect)
            .clip(TvShapes.Small)
            .background(TvColors.Scrim),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = TvColors.OnSurfaceDisabled, style = Stroke(width = 2f))
            drawRect(
                color = TvColors.Star,
                topLeft = Offset(
                    region.visibleLeftRatio * size.width,
                    region.visibleTopRatio * size.height,
                ),
                size = Size(
                    region.visibleWidthRatio * size.width,
                    region.visibleHeightRatio * size.height,
                ),
                style = Stroke(width = 3f),
            )
        }
    }
}

/** 未判定を捌ききったときの完了表示。 */
@Composable
private fun CullCompleteScreen(
    pickedCount: Int,
    skippedCount: Int,
    onExit: () -> Unit,
    onReview: () -> Unit,
) {
    KeyInputSurface(
        modifier = Modifier.background(TvColors.Background),
        onKey = { key ->
            when (key) {
                Key.Enter, Key.DirectionCenter -> { onReview(); true }
                Key.Back -> { onExit(); true }
                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
        ) {
            Text(
                text = stringResource(R.string.cull_complete_title),
                color = TvColors.OnSurface,
                fontSize = TvTextSizes.Title,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd)) {
                InfoChip(
                    text = stringResource(R.string.cull_review_picked, pickedCount),
                    color = TvColors.Pick,
                )
                InfoChip(
                    text = stringResource(R.string.cull_review_skipped, skippedCount),
                    color = TvColors.OnSurfaceMuted,
                )
            }
            TvActionButton(label = stringResource(R.string.cull_complete_review))
        }
    }
}
