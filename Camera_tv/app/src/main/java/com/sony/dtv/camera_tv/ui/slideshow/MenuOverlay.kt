package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.domain.ratingValue
import com.sony.dtv.camera_tv.ui.common.KeyHint
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.ThumbnailCell
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvShapes
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes

/**
 * メニュー項目。ボタンの並びとハンドラをデータで持ち、インデックスの直書きを避ける。
 *
 * ページ分けはしない。「その他」は中身が見えず、探す手間が増えるだけだった。
 * 見比べと選別結果は選別中にしか使わないので、選別画面側に置いてここからは外している。
 * 一覧はメニューから ↓ を押し続ければ開けるのでボタンを置かない。
 */
internal enum class MenuItem {
    Cull,
    Rating,
    Share,
    Delete,
    Date,
    Sort,
    Play,
    Tutorial,
    SignOut,
}

/** 項目ごとのアイコン。文字だけだと 10-foot UI では読み取りにくいため添える。 */
internal fun MenuItem.icon(uiState: SlideshowUiState): ImageVector = when (this) {
    MenuItem.Cull -> Icons.Filled.CheckCircle
    MenuItem.Date -> Icons.Filled.DateRange
    MenuItem.Sort -> Icons.Filled.List
    MenuItem.Play -> if (uiState.isPlaying) Icons.Filled.Clear else Icons.Filled.PlayArrow
    MenuItem.Rating -> Icons.Filled.Star
    MenuItem.Share -> Icons.Filled.Share
    MenuItem.Delete -> Icons.Filled.Delete
    MenuItem.Tutorial -> Icons.Filled.Info
    MenuItem.SignOut -> Icons.Filled.ExitToApp
}

/** 選んでいる間に横へ出す説明。ラベルを短く保つための逆側の手。 */
internal val MenuItem.detailRes: Int
    get() = when (this) {
        MenuItem.Cull -> R.string.menu_cull_detail
        MenuItem.Rating -> R.string.menu_rating_detail
        MenuItem.Share -> R.string.menu_share_detail
        MenuItem.Delete -> R.string.menu_delete_detail
        MenuItem.Date -> R.string.menu_date_detail
        MenuItem.Sort -> R.string.menu_sort_detail
        MenuItem.Play -> R.string.menu_play_detail
        MenuItem.Tutorial -> R.string.menu_tutorial_detail
        MenuItem.SignOut -> R.string.menu_sign_out_detail
    }

/** サムネイル帯のデコード要求解像度。 */
private const val STRIP_THUMBNAIL_PX = 220

private val MENU_ICON_SIZE = 20.dp
private val MENU_WIDTH = 190.dp
private val MENU_DETAIL_WIDTH = 320.dp

/** 9 項目を 1080p の縦幅（約 540dp）にオーバースキャン込みで収めるための高さ。 */
private val MENU_BUTTON_HEIGHT = 40.dp

/** フォーカス位置（ボタン列 / サムネイル帯）。 */
private enum class MenuFocusArea { Buttons, Thumbnails }

/**
 * 画面2: 写真の上に重ねる操作メニュー。
 *
 * ボタンは縦に並べる。横並びだと項目が増えたときに画面外へはみ出すうえ、
 * 10-foot UI では横に長い列より縦の列のほうが視線移動が少ない。
 */
@Composable
internal fun MenuOverlay(
    uiState: SlideshowUiState,
    actions: SlideshowActions,
    onSelect: (MenuItem) -> Unit,
    onOpenGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    var focusArea by remember { mutableStateOf(MenuFocusArea.Buttons) }
    var buttonIndex by remember { mutableIntStateOf(0) }
    var thumbIndex by remember { mutableIntStateOf(uiState.currentIndex) }
    val thumbListState = rememberLazyListState()

    val items = MenuItem.entries
    val contents = uiState.visibleContents

    LaunchedEffect(contents) { actions.loadThumbnails(contents) }

    LaunchedEffect(thumbIndex, focusArea) {
        if (focusArea == MenuFocusArea.Thumbnails && contents.isNotEmpty()) {
            thumbListState.animateScrollToItem(thumbIndex.coerceIn(0, contents.lastIndex))
        }
    }

    KeyInputSurface(
        onKey = { key ->
            when (key) {
                Key.DirectionUp -> {
                    when {
                        focusArea == MenuFocusArea.Thumbnails -> focusArea = MenuFocusArea.Buttons
                        buttonIndex > 0 -> buttonIndex--
                        else -> onDismiss()
                    }
                    true
                }

                Key.DirectionDown -> {
                    when {
                        focusArea == MenuFocusArea.Thumbnails -> onOpenGallery()
                        buttonIndex < items.lastIndex -> buttonIndex++
                        contents.isNotEmpty() -> {
                            focusArea = MenuFocusArea.Thumbnails
                            thumbIndex = uiState.currentIndex.coerceIn(0, contents.lastIndex)
                        }

                        else -> onOpenGallery()
                    }
                    true
                }

                Key.DirectionLeft -> {
                    if (focusArea == MenuFocusArea.Thumbnails) {
                        // 先頭でさらに ← ならボタン列へ戻る
                        if (thumbIndex == 0) focusArea = MenuFocusArea.Buttons
                        else thumbIndex--
                    }
                    true
                }

                Key.DirectionRight -> {
                    when {
                        focusArea == MenuFocusArea.Buttons && contents.isNotEmpty() -> {
                            focusArea = MenuFocusArea.Thumbnails
                            thumbIndex = uiState.currentIndex.coerceIn(0, contents.lastIndex)
                        }

                        focusArea == MenuFocusArea.Thumbnails ->
                            thumbIndex = (thumbIndex + 1).coerceAtMost(contents.lastIndex)
                    }
                    true
                }

                Key.Enter, Key.DirectionCenter -> {
                    if (focusArea == MenuFocusArea.Buttons) {
                        onSelect(items[buttonIndex])
                    } else {
                        actions.selectIndex(thumbIndex)
                        onDismiss()
                    }
                    true
                }

                Key.Back -> { onDismiss(); true }

                else -> false
            }
        },
    ) {
        // フォーカスしている側を手前に描く。帯とボタン列は画面左下で重なるので、
        // 固定の重なり順だと選んでいるサムネイルがボタンの下に隠れる
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(if (focusArea == MenuFocusArea.Thumbnails) 1f else 0f)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, TvColors.ScrimStrong)),
                )
                .padding(TvDimens.SpaceMd),
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
        ) {
            if (contents.isNotEmpty()) {
                LazyRow(
                    state = thumbListState,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        TvDimens.SpaceSm,
                        Alignment.CenterHorizontally,
                    ),
                ) {
                    itemsIndexed(contents, key = { _, content -> content.contentId }) { index, content ->
                        ThumbnailCell(
                            bytes = uiState.thumbnails[content.contentId],
                            index = index,
                            rating = content.ratingValue(),
                            isFocused = focusArea == MenuFocusArea.Thumbnails && index == thumbIndex,
                            isCurrent = index == uiState.currentIndex,
                            reqPx = STRIP_THUMBNAIL_PX,
                            modifier = Modifier.size(TvDimens.ThumbnailStripSize),
                        )
                    }
                }
            }

            KeyHint(
                text = stringResource(
                    if (focusArea == MenuFocusArea.Buttons) R.string.menu_hint
                    else R.string.menu_thumbnail_hint,
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        // 写真を隠しすぎないよう、メニューを置く左側だけを暗くする
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .zIndex(if (focusArea == MenuFocusArea.Buttons) 1f else 0f)
                .background(
                    Brush.horizontalGradient(listOf(TvColors.ScrimStrong, Color.Transparent)),
                )
                .padding(TvDimens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(MENU_WIDTH),
                verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
            ) {
                items.forEachIndexed { index, item ->
                    MenuButton(
                        label = item.label(uiState),
                        icon = item.icon(uiState),
                        isFocused = focusArea == MenuFocusArea.Buttons && index == buttonIndex,
                    )
                }
            }

            // ラベルは短くし、何が起きるかは選んでいる間だけ横に出す
            if (focusArea == MenuFocusArea.Buttons) {
                MenuDetail(item = items[buttonIndex], uiState = uiState)
            }
        }
    }
}

/** 選択中の項目の説明。写真の上に素で置くと読めないので、面を敷いてから文字を載せる。 */
@Composable
private fun MenuDetail(item: MenuItem, uiState: SlideshowUiState) {
    Column(
        modifier = Modifier
            .padding(start = TvDimens.SpaceMd)
            .width(MENU_DETAIL_WIDTH)
            .clip(TvShapes.Medium)
            .background(TvColors.Scrim)
            .padding(TvDimens.SpaceMd),
        verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
    ) {
        Text(
            text = item.label(uiState),
            color = TvColors.OnSurface,
            fontSize = TvTextSizes.Label,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(item.detailRes),
            color = TvColors.OnSurfaceMuted,
            fontSize = TvTextSizes.Body,
        )
    }
}

/**
 * メニューのボタン。
 *
 * 非フォーカス時も背景と枠を描いて「押せるもの」だと分かるようにする。
 * 背景が無いとテキストが写真の上に浮いているだけに見えてボタンだと気づけない。
 */
@Composable
private fun MenuButton(
    label: String,
    icon: ImageVector,
    isFocused: Boolean,
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.04f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "menuButtonScale",
    )
    val contentColor = if (isFocused) TvColors.Background else TvColors.OnSurface
    Row(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .height(MENU_BUTTON_HEIGHT)
            .clip(TvShapes.Pill)
            .background(if (isFocused) TvColors.Focus else TvColors.Surface)
            .border(
                width = if (isFocused) TvDimens.FocusBorder else 1.dp,
                color = if (isFocused) TvColors.Focus else TvColors.SurfaceVariant,
                shape = TvShapes.Pill,
            )
            .padding(horizontal = TvDimens.SpaceMd),
        horizontalArrangement = Arrangement.spacedBy(TvDimens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(MENU_ICON_SIZE),
        )
        Text(
            text = label,
            color = contentColor,
            fontSize = TvTextSizes.Body,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 状態によって表示が変わるボタンのラベルを解決する。 */
@Composable
private fun MenuItem.label(uiState: SlideshowUiState): String = when (this) {
    MenuItem.Cull -> stringResource(R.string.menu_cull)
    MenuItem.Tutorial -> stringResource(R.string.menu_tutorial)
    MenuItem.SignOut -> stringResource(R.string.menu_sign_out)
    MenuItem.Date -> stringResource(R.string.menu_date)
    MenuItem.Rating -> stringResource(R.string.menu_rating)
    MenuItem.Delete -> stringResource(R.string.menu_delete)
    MenuItem.Share -> stringResource(R.string.menu_share)
    MenuItem.Sort -> stringResource(R.string.menu_sort)
    MenuItem.Play -> stringResource(
        if (uiState.isPlaying) R.string.menu_pause else R.string.menu_play,
    )
}
