package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.domain.CullFilter
import com.sony.dtv.camera_tv.domain.ratingValue
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.ThumbnailCell
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

private const val REVIEW_COLUMNS = 5
private const val REVIEW_THUMBNAIL_PX = 400

/** フォーカス位置。上端からさらに上を押すとタブ行へ抜ける。 */
private enum class ReviewFocus { Tabs, Grid }

/**
 * 画面6: 選別結果の見直し。
 *
 * 採用と見送りを行き来して、判定をやり直せるようにする。
 * 選別中は迷ったら送れる代わりに、後からいつでも取り消せることが前提になっている。
 */
@Composable
internal fun CullReviewScreen(
    uiState: SlideshowUiState,
    actions: SlideshowActions,
    onBack: () -> Unit,
) {
    val contents = uiState.cullReviewContents
    // 開いた直後はまず「どちらを見ているか」を示す。いきなりグリッドに入ると
    // 採用と見送りのどちらの一覧なのか分からない
    var focus by remember { mutableStateOf(ReviewFocus.Tabs) }
    var selectedIndex by remember(uiState.cullReviewFilter) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    // 未判定に戻すと一覧から消えるので、選択位置が末尾を越える
    LaunchedEffect(contents.size) {
        selectedIndex = selectedIndex.coerceIn(0, (contents.size - 1).coerceAtLeast(0))
    }

    // 空の側でグリッドにフォーカスが残ると、どのキーも効かない行き止まりになる
    LaunchedEffect(contents.isEmpty()) {
        if (contents.isEmpty()) focus = ReviewFocus.Tabs
    }

    LaunchedEffect(uiState.cullReviewContents) {
        actions.loadThumbnails(uiState.cullReviewContents)
    }

    LaunchedEffect(selectedIndex, contents.size) {
        if (contents.isNotEmpty()) {
            gridState.animateScrollToItem(selectedIndex.coerceIn(0, contents.lastIndex))
        }
    }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.Background),
        onKey = { key ->
            when {
                key == Key.Back -> { onBack(); true }

                focus == ReviewFocus.Tabs -> when (key) {
                    Key.DirectionLeft -> { actions.setCullReviewFilter(CullFilter.Picked); true }
                    Key.DirectionRight -> { actions.setCullReviewFilter(CullFilter.Skipped); true }
                    // 空の側へは降りられない
                    Key.DirectionDown -> {
                        if (contents.isNotEmpty()) focus = ReviewFocus.Grid
                        true
                    }

                    Key.DirectionUp -> { onBack(); true }
                    else -> false
                }

                contents.isEmpty() -> when (key) {
                    Key.DirectionUp -> { focus = ReviewFocus.Tabs; true }
                    else -> false
                }

                else -> when (key) {
                    Key.DirectionLeft -> {
                        selectedIndex = (selectedIndex - 1).coerceAtLeast(0); true
                    }

                    Key.DirectionRight -> {
                        selectedIndex = (selectedIndex + 1).coerceAtMost(contents.lastIndex); true
                    }

                    Key.DirectionUp -> {
                        if (selectedIndex < REVIEW_COLUMNS) focus = ReviewFocus.Tabs
                        else selectedIndex -= REVIEW_COLUMNS
                        true
                    }

                    Key.DirectionDown -> {
                        selectedIndex =
                            (selectedIndex + REVIEW_COLUMNS).coerceAtMost(contents.lastIndex)
                        true
                    }

                    // 決定で反対側へ。CH- で未判定に戻す。
                    // どちらも一覧が縮むので、古い selectedIndex での直取りはしない
                    Key.Enter, Key.DirectionCenter -> {
                        contents.getOrNull(selectedIndex)
                            ?.let { actions.flipCullJudgement(it.contentId) }
                        true
                    }

                    Key.ChannelDown -> {
                        contents.getOrNull(selectedIndex)
                            ?.let { actions.revertCullJudgement(it.contentId) }
                        true
                    }

                    else -> false
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReviewTabs(
                filter = uiState.cullReviewFilter,
                pickedCount = uiState.pickedContents.size,
                skippedCount = uiState.skippedContents.size,
                isFocused = focus == ReviewFocus.Tabs,
            )

            if (contents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.cull_review_empty),
                        color = TvColors.OnSurfaceMuted,
                        fontSize = TvTextSizes.Body,
                    )
                }
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(REVIEW_COLUMNS),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
                verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
                contentPadding = PaddingValues(
                    horizontal = TvDimens.SpaceLg,
                    vertical = TvDimens.SpaceMd,
                ),
            ) {
                itemsIndexed(contents, key = { _, content -> content.contentId }) { index, content ->
                    ThumbnailCell(
                        bytes = uiState.thumbnails[content.contentId],
                        index = index,
                        rating = content.ratingValue(),
                        isFocused = focus == ReviewFocus.Grid && index == selectedIndex,
                        isCurrent = false,
                        reqPx = REVIEW_THUMBNAIL_PX,
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewTabs(
    filter: CullFilter,
    pickedCount: Int,
    skippedCount: Int,
    isFocused: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvColors.Surface)
            .padding(horizontal = TvDimens.SpaceLg, vertical = TvDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReviewTab(
                label = stringResource(R.string.cull_review_picked, pickedCount),
                isSelected = filter == CullFilter.Picked,
                isFocused = isFocused,
                accent = TvColors.Pick,
            )
            ReviewTab(
                label = stringResource(R.string.cull_review_skipped, skippedCount),
                isSelected = filter == CullFilter.Skipped,
                isFocused = isFocused,
                accent = TvColors.OnSurfaceMuted,
            )
        }
        KeyHint(
            text = stringResource(
                if (isFocused) R.string.cull_review_tab_hint else R.string.cull_review_hint,
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun ReviewTab(
    label: String,
    isSelected: Boolean,
    isFocused: Boolean,
    accent: androidx.compose.ui.graphics.Color,
) {
    val background = when {
        isSelected && isFocused -> TvColors.Focus
        isSelected -> TvColors.SurfaceVariant
        else -> TvColors.Background
    }
    Box(
        modifier = Modifier
            .height(TvDimens.MenuButtonHeight)
            .clip(TvShapes.Pill)
            .background(background)
            .padding(horizontal = TvDimens.SpaceLg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                isSelected && isFocused -> TvColors.Background
                isSelected -> accent
                else -> TvColors.OnSurfaceDisabled
            },
            fontSize = TvTextSizes.Label,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
