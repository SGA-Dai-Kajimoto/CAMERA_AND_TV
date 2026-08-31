package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.domain.ratingValue
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.ThumbnailCell
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

private const val MIN_COLUMNS = 3
private const val MAX_COLUMNS = 8
private const val DEFAULT_COLUMNS = 5

/** 列数からセルのデコード解像度を概算する（列が多いほど小さく＝省メモリ）。 */
private fun thumbnailPxFor(columns: Int): Int = (1920 / columns).coerceIn(160, 520)

/**
 * 画面3: 全画面サムネイル一覧。
 * CH+ / CH− でサムネイルの大きさ（列数）を変更できる。
 */
@Composable
internal fun GalleryScreen(
    uiState: SlideshowUiState,
    actions: SlideshowActions,
    onSelect: (index: Int) -> Unit,
    onBack: () -> Unit,
) {
    val contents = uiState.visibleContents
    var columns by remember { mutableIntStateOf(DEFAULT_COLUMNS) }
    var selectedIndex by remember {
        mutableIntStateOf(uiState.currentIndex.coerceIn(0, (contents.size - 1).coerceAtLeast(0)))
    }
    val gridState = rememberLazyGridState()

    LaunchedEffect(uiState.visibleContents) { actions.loadThumbnails(uiState.visibleContents) }

    LaunchedEffect(selectedIndex, columns) {
        if (contents.isNotEmpty()) {
            gridState.animateScrollToItem(selectedIndex.coerceIn(0, contents.lastIndex))
        }
    }

    KeyInputSurface(
        modifier = Modifier.background(TvColors.Background),
        onKey = { key ->
            if (contents.isEmpty()) {
                return@KeyInputSurface when (key) {
                    Key.Back, Key.DirectionUp -> { onBack(); true }
                    else -> false
                }
            }
            when (key) {
                Key.DirectionLeft -> { selectedIndex = (selectedIndex - 1).coerceAtLeast(0); true }
                Key.DirectionRight -> { selectedIndex = (selectedIndex + 1).coerceAtMost(contents.lastIndex); true }
                Key.DirectionUp -> {
                    if (selectedIndex < columns) onBack()
                    else selectedIndex -= columns
                    true
                }

                Key.DirectionDown -> {
                    selectedIndex = (selectedIndex + columns).coerceAtMost(contents.lastIndex)
                    true
                }

                Key.ChannelUp -> { columns = (columns - 1).coerceAtLeast(MIN_COLUMNS); true }
                Key.ChannelDown -> { columns = (columns + 1).coerceAtMost(MAX_COLUMNS); true }
                Key.Enter, Key.DirectionCenter -> { onSelect(selectedIndex); true }
                Key.Back -> { onBack(); true }
                else -> false
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GalleryHeader(
                position = selectedIndex + 1,
                total = contents.size,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
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
                        isFocused = index == selectedIndex,
                        isCurrent = index == uiState.currentIndex,
                        reqPx = thumbnailPxFor(columns),
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
        }
        KeyHint(
            text = stringResource(R.string.gallery_hint),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(TvDimens.SpaceLg),
        )
    }
}

@Composable
private fun GalleryHeader(position: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TvColors.Surface)
            .padding(horizontal = TvDimens.SpaceLg, vertical = TvDimens.SpaceMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.photo_counter, position, total),
            color = TvColors.OnSurface,
            fontSize = TvTextSizes.Label,
            fontWeight = FontWeight.Bold,
        )
    }
}
