package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.domain.ratingValue
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.InfoChip
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.ThumbnailCell
import com.sony.dtv.camera_tv.ui.common.ZoomMinimap
import com.sony.dtv.camera_tv.ui.common.ZoomedRegion
import com.sony.dtv.camera_tv.ui.common.rememberFullscreenReqPx
import com.sony.dtv.camera_tv.ui.common.rememberSampledBitmap
import com.sony.dtv.camera_tv.ui.common.rememberZoomedRegion
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

private const val STRIP_THUMBNAIL_PX = 220
private val CHECK_BADGE_SIZE = 30.dp

/**
 * 画面7: 見比べ。下の帯から比べたい写真をチェックで選び、上で見比べる。
 *
 * 見せ方が 2 つある。
 *  - 重ね  : 同じ位置に重ねて切り替える。位置が動かないので、わずかな差が見える
 *            連写のピントや目の開きを比べるのに向く
 *  - 並べ  : 横に並べる。1 枚あたりの面積は減るが、構図の違いは一目で分かる
 *
 * 人間は「並置した差」より「同じ場所での変化」の検出が得意なので、既定は重ね。
 * どちらが向くかは写真次第なので、選びながらいつでも切り替えられるようにしている。
 *
 * 拡大は選別画面と同じ仕組み（[rememberZoomedRegion]）を使い、倍率と位置を
 * 全枚に同時適用する。連写のピントを見比べるのが主な用途。
 */
@Composable
internal fun CompareScreen(
    uiState: SlideshowUiState,
    actions: SlideshowActions,
    onExit: () -> Unit,
) {
    val candidates = uiState.compareContents
    if (candidates.isEmpty()) {
        onExit()
        return
    }

    val stripState = rememberLazyListState()
    // ミニマップは 1 つだけ出す。全枚同じ位置を見ているので代表 1 枚で足りる
    var minimapRegion by remember { mutableStateOf<ZoomedRegion?>(null) }
    LaunchedEffect(candidates) { actions.loadThumbnails(candidates) }
    LaunchedEffect(uiState.compareIndex) {
        stripState.animateScrollToItem(uiState.compareIndex.coerceIn(0, candidates.lastIndex))
    }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.Background),
        onKey = { key ->
            // 拡大中は方向キーを全枚共通の「見る位置」の移動に使う。
            // 候補送りや採用・見送りは拡大を解除してから行う。
            if (uiState.isZoomed) {
                when (key) {
                    Key.DirectionLeft -> { actions.panZoom(-1, 0); true }
                    Key.DirectionRight -> { actions.panZoom(1, 0); true }
                    Key.DirectionUp -> { actions.panZoom(0, -1); true }
                    Key.DirectionDown -> { actions.panZoom(0, 1); true }
                    Key.Enter, Key.DirectionCenter, Key.ChannelDown -> { actions.cycleZoom(); true }
                    Key.ChannelUp -> { actions.toggleCompareLayout(); true }
                    Key.Back -> { actions.zoomOff(); true }
                    else -> false
                }
            } else {
                when (key) {
                    Key.DirectionLeft -> { actions.compareMoveBy(-1); true }
                    Key.DirectionRight -> { actions.compareMoveBy(1); true }
                    Key.Enter, Key.DirectionCenter -> { actions.toggleCompareSelection(); true }
                    Key.ChannelUp -> { actions.toggleCompareLayout(); true }
                    Key.ChannelDown -> { actions.cycleZoom(); true }
                    Key.DirectionUp -> { actions.comparePick(); true }
                    Key.DirectionDown -> { actions.compareSkip(); true }
                    Key.Back -> { onExit(); true }
                    else -> false
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState.compareLayout) {
                    CompareLayout.Stack -> StackedCompare(uiState) { minimapRegion = it }
                    CompareLayout.SideBySide -> SideBySideCompare(uiState) { minimapRegion = it }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(TvDimens.SpaceLg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    InfoChip(
                        text = stringResource(
                            R.string.compare_selected_count,
                            uiState.compareSelected.size,
                        ),
                        color = TvColors.Pick,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm)) {
                        if (uiState.isZoomed) {
                            InfoChip(
                                text = stringResource(
                                    R.string.cull_zoom_badge,
                                    (uiState.zoomMagnification * 100).toInt(),
                                ),
                                color = TvColors.Star,
                            )
                        }
                        InfoChip(
                            text = stringResource(
                                if (uiState.compareLayout == CompareLayout.Stack) {
                                    R.string.compare_layout_stack
                                } else {
                                    R.string.compare_layout_side
                                },
                            ),
                            color = TvColors.Star,
                        )
                    }
                }

                minimapRegion?.takeIf { uiState.isZoomed }?.let { region ->
                    ZoomMinimap(
                        region = region,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(TvDimens.SpaceLg),
                    )
                }
            }

            CandidateStrip(uiState = uiState, state = stripState)
        }
    }
}

/** 下の帯。チェックの有無が一目で分かるようにバッジを重ねる。 */
@Composable
private fun CandidateStrip(
    uiState: SlideshowUiState,
    state: androidx.compose.foundation.lazy.LazyListState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, TvColors.ScrimStrong)))
            .padding(TvDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
    ) {
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm, Alignment.CenterHorizontally),
        ) {
            itemsIndexed(
                uiState.compareContents,
                key = { _, content -> content.contentId },
            ) { index, content ->
                Box {
                    ThumbnailCell(
                        bytes = uiState.thumbnails[content.contentId],
                        index = index,
                        rating = content.ratingValue(),
                        isFocused = index == uiState.compareIndex,
                        isCurrent = false,
                        reqPx = STRIP_THUMBNAIL_PX,
                        modifier = Modifier.size(TvDimens.ThumbnailStripSize),
                    )
                    if (content.contentId in uiState.compareSelected) {
                        // ThumbnailCell はフォーカス中 zIndex=1 になるので、その上に置く
                        CheckBadge(
                            modifier = Modifier
                                .zIndex(2f)
                                .align(Alignment.TopEnd)
                                .padding(TvDimens.SpaceXs),
                        )
                    }
                }
            }
        }
        KeyHint(
            text = stringResource(
                if (uiState.isZoomed) R.string.compare_hint_zoomed else R.string.compare_hint,
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(CHECK_BADGE_SIZE).clip(CircleShape).background(TvColors.Pick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = TvColors.Background,
        )
    }
}

/**
 * 重ね表示。選んだ写真を同じ位置に半透明で重ねる。
 *
 * i 枚目の alpha を 1/(i+1) にすると、順に重ねた結果が全枚の単純平均になる。
 * こうすると位置がずれている部分だけが二重ににじんで見える。
 */
@Composable
private fun StackedCompare(uiState: SlideshowUiState, onRegion: (ZoomedRegion?) -> Unit) {
    val picked = uiState.comparePicked

    Box(modifier = Modifier.fillMaxSize()) {
        picked.forEachIndexed { index, content ->
            CompareImage(
                content = content,
                uiState = uiState,
                isFocused = false,
                showBorder = false,
                alpha = 1f / (index + 1),
                onRegion = if (index == 0) onRegion else null,
            )
        }
        uiState.compareContent?.let { focused ->
            CompareCaption(
                content = focused,
                isFocused = true,
                modifier = Modifier.align(Alignment.BottomStart).padding(TvDimens.SpaceLg),
            )
        }
    }
}

/** 並べ表示。チェックした枚数だけ横に並べる。 */
@Composable
private fun SideBySideCompare(uiState: SlideshowUiState, onRegion: (ZoomedRegion?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize().padding(TvDimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
    ) {
        uiState.comparePicked.forEachIndexed { index, content ->
            val isFocused = content.contentId == uiState.compareContent?.contentId
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CompareImage(
                        content = content,
                        uiState = uiState,
                        isFocused = isFocused,
                        showBorder = true,
                        onRegion = if (index == 0) onRegion else null,
                    )
                }
                CompareCaption(content = content, isFocused = isFocused)
            }
        }
    }
}

/**
 * 見比べ 1 枚分の描画。
 *
 * 拡大中は選別画面と同じように原寸から必要な矩形だけを切り出す。切り出し位置は
 * 元画像全体に対する割合なので、画素数が同じ連写なら全枚で同じ個所が並ぶ。
 * ビューポートは並べ表示では 1 枚分の幅になるので、ここで実寸を測って渡す。
 */
@Composable
private fun CompareImage(
    content: Content,
    uiState: SlideshowUiState,
    isFocused: Boolean,
    showBorder: Boolean,
    alpha: Float = 1f,
    onRegion: ((ZoomedRegion?) -> Unit)? = null,
) {
    val reqPx = rememberFullscreenReqPx()
    val bitmap = rememberSampledBitmap(uiState.compareImages[content.contentId], reqPx)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val zoomed = rememberZoomedRegion(
        bytes = uiState.compareZoomImages[content.contentId].takeIf { uiState.isZoomed },
        viewportWidth = viewport.width,
        viewportHeight = viewport.height,
        magnification = uiState.zoomMagnification,
        centerX = uiState.zoomCenterX,
        centerY = uiState.zoomCenterY,
    )
    LaunchedEffect(zoomed) { onRegion?.invoke(zoomed) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = if (isFocused) TvDimens.FocusBorder else 1.dp,
                        color = if (isFocused) TvColors.Focus else TvColors.SurfaceVariant,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val shown = if (uiState.isZoomed) zoomed?.bitmap else bitmap
        if (shown != null) {
            Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = stringResource(
                    if (uiState.isZoomed) {
                        R.string.cull_zoom_content_description
                    } else {
                        R.string.photo_content_description
                    },
                ),
                contentScale = ContentScale.Fit,
                alpha = alpha,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (alpha == 1f) {
            CircularProgressIndicator(color = TvColors.OnSurface)
        }
    }
}

@Composable
private fun CompareCaption(content: Content, isFocused: Boolean, modifier: Modifier = Modifier) {
    val rating = content.ratingValue()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = content.displayName ?: content.filename.orEmpty(),
            color = if (isFocused) TvColors.OnSurface else TvColors.OnSurfaceMuted,
            fontSize = TvTextSizes.Caption,
        )
        if (rating > 0) {
            InfoChip(
                text = stringResource(if (rating >= 3) R.string.cull_pick else R.string.cull_skip),
                color = if (rating >= 3) TvColors.Pick else TvColors.OnSurfaceMuted,
            )
        }
    }
}
