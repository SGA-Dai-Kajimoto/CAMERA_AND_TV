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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.domain.ratingValue
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.InfoChip
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.ThumbnailCell
import com.sony.dtv.camera_tv.ui.common.rememberFullscreenReqPx
import com.sony.dtv.camera_tv.ui.common.rememberSampledBitmap
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
    LaunchedEffect(candidates) { actions.loadThumbnails(candidates) }
    LaunchedEffect(uiState.compareIndex) {
        stripState.animateScrollToItem(uiState.compareIndex.coerceIn(0, candidates.lastIndex))
    }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.Background),
        onKey = { key ->
            when (key) {
                Key.DirectionLeft -> { actions.compareMoveBy(-1); true }
                Key.DirectionRight -> { actions.compareMoveBy(1); true }
                Key.Enter, Key.DirectionCenter -> { actions.toggleCompareSelection(); true }
                Key.ChannelUp, Key.ChannelDown -> { actions.toggleCompareLayout(); true }
                Key.DirectionUp -> { actions.comparePick(); true }
                Key.DirectionDown -> { actions.compareSkip(); true }
                Key.Back -> { onExit(); true }
                else -> false
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState.compareLayout) {
                    CompareLayout.Stack -> StackedCompare(uiState)
                    CompareLayout.SideBySide -> SideBySideCompare(uiState)
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
            text = stringResource(R.string.compare_hint),
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
private fun StackedCompare(uiState: SlideshowUiState) {
    val picked = uiState.comparePicked

    Box(modifier = Modifier.fillMaxSize()) {
        picked.forEachIndexed { index, content ->
            CompareImage(
                bytes = uiState.compareImages[content.contentId],
                isFocused = false,
                showBorder = false,
                alpha = 1f / (index + 1),
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
private fun SideBySideCompare(uiState: SlideshowUiState) {
    Row(
        modifier = Modifier.fillMaxSize().padding(TvDimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
    ) {
        uiState.comparePicked.forEach { content ->
            val isFocused = content.contentId == uiState.compareContent?.contentId
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CompareImage(
                        bytes = uiState.compareImages[content.contentId],
                        isFocused = isFocused,
                        showBorder = true,
                    )
                }
                CompareCaption(content = content, isFocused = isFocused)
            }
        }
    }
}

@Composable
private fun CompareImage(
    bytes: ByteArray?,
    isFocused: Boolean,
    showBorder: Boolean,
    alpha: Float = 1f,
) {
    val reqPx = rememberFullscreenReqPx()
    val bitmap = rememberSampledBitmap(bytes, reqPx)
    Box(
        modifier = Modifier
            .fillMaxSize()
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
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.photo_content_description),
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
