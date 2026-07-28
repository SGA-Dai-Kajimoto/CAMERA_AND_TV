package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import com.sony.dtv.camera_tv.data.model.ratingValue
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * TV画面メインComposable。
 * 3つの画面状態を管理:
 *  - Photo: フルスクリーン写真表示(画面1)
 *  - Menu: 操作メニューオーバーレイ(画面2)
 *  - DateSelect: 日付選択画面
 */
@Composable
fun SlideshowScreen(
    repository: ImagingEdgeRepository,
) {
    val viewModel = viewModel<SlideshowViewModel>(
        factory = SlideshowViewModel.factory(repository),
    )
    val uiState by viewModel.uiState.collectAsState()

    var screenMode by remember { mutableStateOf(ScreenMode.Photo) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val photoFocusRequester = remember { FocusRequester() }

    // 起動時にフォーカスを要求
    LaunchedEffect(screenMode) {
        if (screenMode == ScreenMode.Photo) {
            try { photoFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Toastメッセージ自動消去
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2000)
            toastMessage = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }

            uiState.error != null -> {
                Text(
                    text = "Error: ${uiState.error}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            uiState.contents.isEmpty() -> {
                Text(
                    text = "No photos in this folder",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            else -> {
                when (screenMode) {
                    ScreenMode.Photo -> {
                        PhotoScreen(
                            uiState = uiState,
                            focusRequester = photoFocusRequester,
                            onPrev = { viewModel.prevImage() },
                            onNext = { viewModel.nextImage() },
                            onShowMenu = { screenMode = ScreenMode.Menu },
                            onBack = { /* no previous screen */ },
                        )
                    }

                    ScreenMode.Menu -> {
                        PhotoBackground(uiState = uiState)
                        var showRatingDialog by remember { mutableStateOf(false) }
                        MenuOverlay(
                            uiState = uiState,
                            viewModel = viewModel,
                            onDateSelect = { screenMode = ScreenMode.DateSelect },
                            onFavorite = { showRatingDialog = true },
                            onDelete = { showDeleteConfirm = true },
                            onShare = { viewModel.requestShareUrl() },
                            onSort = {
                                viewModel.toggleSortMode()
                                toastMessage = if (uiState.sortMode == SortMode.DateDesc) {
                                    "Sorted by rating"
                                } else {
                                    "Sorted by date"
                                }
                            },
                            onOpenGallery = { screenMode = ScreenMode.Gallery },
                            onDismiss = { screenMode = ScreenMode.Photo },
                        )
                        if (showRatingDialog) {
                            RatingDialog(
                                currentRating = uiState.currentContentRating,
                                isLoading = uiState.isRatingLoading,
                                onRatingSelected = { rating ->
                                    viewModel.setCurrentContentRating(rating)
                                    showRatingDialog = false
                                    toastMessage = if (rating == 0) "Rating cleared" else "Rated as $rating stars"
                                },
                                onDismiss = { showRatingDialog = false },
                            )
                        }
                        if (showDeleteConfirm) {
                            DeleteConfirmOverlay(
                                onConfirm = {
                                    viewModel.deleteCurrentContent()
                                    toastMessage = "Deleted"
                                    showDeleteConfirm = false
                                    screenMode = ScreenMode.Photo
                                },
                                onCancel = {
                                    showDeleteConfirm = false
                                },
                            )
                        }
                        if (uiState.shareUrl != null || uiState.isShareLoading) {
                            ShareOverlay(
                                url = uiState.shareUrl,
                                isLoading = uiState.isShareLoading,
                                onClose = { viewModel.clearShareUrl() },
                            )
                        }
                    }

                    ScreenMode.DateSelect -> {
                        DateSelectScreen(
                            dates = uiState.availableDates,
                            selectedDate = uiState.selectedDate,
                            onDateSelected = { date ->
                                viewModel.selectDate(date)
                                screenMode = ScreenMode.Photo
                            },
                            onBack = { screenMode = ScreenMode.Menu },
                        )
                    }

                    ScreenMode.Gallery -> {
                        GalleryScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onSelect = { index ->
                                viewModel.selectIndex(index)
                                screenMode = ScreenMode.Photo
                            },
                            onBack = { screenMode = ScreenMode.Menu },
                        )
                    }
                }
            }
        }

        // Toast
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        ) {
            Text(
                text = toastMessage ?: "",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .background(Color.DarkGray.copy(alpha = 0.85f))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

private enum class ScreenMode { Photo, Menu, DateSelect, Gallery }

// ============================================================
// Screen 1: Fullscreen photo display
// ============================================================

@Composable
private fun PhotoScreen(
    uiState: SlideshowUiState,
    focusRequester: FocusRequester,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onShowMenu: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> { onPrev(); true }
                        Key.DirectionRight -> { onNext(); true }
                        Key.DirectionDown, Key.Enter, Key.DirectionCenter -> { onShowMenu(); true }
                        Key.DirectionUp, Key.Back -> { onBack(); true }
                        else -> false
                    }
                } else false
            },
    ) {
        PhotoBackground(uiState = uiState)

        if (uiState.isImageLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Position indicator
        val dateContents = if (uiState.sortMode == SortMode.RatingDesc) {
            uiState.contents
        } else {
            uiState.contents.filter { content ->
                uiState.selectedDate == null || extractDateFromContent(content) == uiState.selectedDate
            }
        }
        if (dateContents.isNotEmpty()) {
            Text(
                text = "${uiState.currentIndex + 1} / ${dateContents.size}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // Selected date display
        uiState.selectedDate?.let { date ->
            Text(
                text = date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PhotoBackground(uiState: SlideshowUiState) {
    val bytes = uiState.currentImageBytes
    if (bytes != null) {
        // フルサイズ画像は Canvas の最大サイズを超えるため、画面サイズ相当までダウンサンプルする
        val bitmap = remember(bytes) {
            decodeSampledBitmap(bytes, FULLSCREEN_MAX_PX)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ============================================================
// Screen 2: Menu overlay
// ============================================================

@Composable
private fun MenuOverlay(
    uiState: SlideshowUiState,
    viewModel: SlideshowViewModel,
    onDateSelect: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onSort: () -> Unit,
    onOpenGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 0=buttons area, 1=thumbnail strip
    var focusArea by remember { mutableIntStateOf(0) }
    var buttonIndex by remember { mutableIntStateOf(0) }
    var thumbIndex by remember { mutableIntStateOf(uiState.currentIndex) }
    val menuFocusRequester = remember { FocusRequester() }
    val thumbListState = rememberLazyListState()

    val dateContents = viewModel.currentDateContents()

    // Load thumbnails when menu is shown
    LaunchedEffect(Unit) {
        viewModel.loadThumbnails()
        try { menuFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // Scroll to focused thumbnail
    LaunchedEffect(thumbIndex) {
        if (focusArea == 1 && dateContents.isNotEmpty()) {
            thumbListState.animateScrollToItem(thumbIndex.coerceIn(0, dateContents.size - 1))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray.copy(alpha = 0.5f))
            .focusRequester(menuFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (focusArea == 0) {
                                buttonIndex = (buttonIndex - 1).coerceAtLeast(0)
                            } else {
                                thumbIndex = (thumbIndex - 1).coerceAtLeast(0)
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (focusArea == 0) {
                                buttonIndex = (buttonIndex + 1).coerceAtMost(4)
                            } else {
                                thumbIndex = (thumbIndex + 1).coerceAtMost(dateContents.size - 1)
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (focusArea == 0) {
                                focusArea = 1
                                thumbIndex = uiState.currentIndex.coerceIn(0, dateContents.size - 1)
                            } else {
                                // サムネイル帯でさらに下 → 全画面ギャラリーへ
                                onOpenGallery()
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            if (focusArea == 1) {
                                focusArea = 0
                            } else {
                                onDismiss()
                            }
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            if (focusArea == 0) {
                                when (buttonIndex) {
                                    0 -> onDateSelect()
                                    1 -> onFavorite()
                                    2 -> onDelete()
                                    3 -> onShare()
                                    4 -> onSort()
                                }
                            } else {
                                // Select thumbnail → jump to that image
                                viewModel.selectIndex(thumbIndex)
                                onDismiss()
                            }
                            true
                        }
                        Key.Back -> { onDismiss(); true }
                        else -> false
                    }
                } else false
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(vertical = 12.dp),
        ) {
            // Button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MenuButton(label = "Date", isFocused = focusArea == 0 && buttonIndex == 0, onClick = onDateSelect)
                Spacer(modifier = Modifier.width(24.dp))
                MenuButton(label = "Favorite", isFocused = focusArea == 0 && buttonIndex == 1, onClick = onFavorite)
                Spacer(modifier = Modifier.width(24.dp))
                MenuButton(label = "Delete", isFocused = focusArea == 0 && buttonIndex == 2, onClick = onDelete)
                Spacer(modifier = Modifier.width(24.dp))
                MenuButton(label = "Share", isFocused = focusArea == 0 && buttonIndex == 3, onClick = onShare)
                Spacer(modifier = Modifier.width(24.dp))
                MenuButton(
                    label = if (uiState.sortMode == SortMode.RatingDesc) "Sort: Rating" else "Sort: Date",
                    isFocused = focusArea == 0 && buttonIndex == 4,
                    onClick = onSort,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Thumbnail strip (centered)
            LazyRow(
                state = thumbListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            ) {
                itemsIndexed(dateContents) { index, content ->
                    val isFocused = focusArea == 1 && index == thumbIndex
                    val isCurrentImage = index == uiState.currentIndex
                    val thumbBytes = uiState.thumbnails[content.contentId]

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(
                                width = if (isFocused) 3.dp else if (isCurrentImage) 2.dp else 0.dp,
                                color = when {
                                    isFocused -> Color.White
                                    isCurrentImage -> Color(0xFF4488FF)
                                    else -> Color.Transparent
                                },
                            )
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center,
                    ) {
                        ThumbnailInner(bytes = thumbBytes, index = index, reqPx = 160)
                        RatingBadge(content.ratingValue())
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuButton(
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color.White else Color.Gray.copy(alpha = 0.6f),
            contentColor = if (isFocused) Color.Black else Color.White,
        ),
        modifier = Modifier.height(48.dp),
    ) {
        Text(text = label, fontSize = 16.sp)
    }
}

// ============================================================
// Screen 3: Fullscreen thumbnail gallery (grid)
// ============================================================

@Composable
private fun GalleryScreen(
    uiState: SlideshowUiState,
    viewModel: SlideshowViewModel,
    onSelect: (index: Int) -> Unit,
    onBack: () -> Unit,
) {
    val dateContents = viewModel.currentDateContents()

    // 列数（少ない=大きいサムネイル / 多い=小さいサムネイル）
    var columns by remember { mutableIntStateOf(5) }
    var selectedIndex by remember { mutableIntStateOf(uiState.currentIndex.coerceIn(0, (dateContents.size - 1).coerceAtLeast(0))) }
    val gridState = rememberLazyGridState()
    val galleryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.loadThumbnails()
        try { galleryFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // 選択位置・列数が変わったらスクロール追従
    LaunchedEffect(selectedIndex, columns) {
        if (dateContents.isNotEmpty()) {
            gridState.animateScrollToItem(selectedIndex.coerceIn(0, dateContents.size - 1))
        }
    }

    // 列数からセルの必要解像度を概算（メモリ節約のためダウンサンプル）
    val reqPx = (1600 / columns).coerceIn(120, 480)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(galleryFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                val size = dateContents.size
                if (size == 0) {
                    if (keyEvent.key == Key.Back || keyEvent.key == Key.DirectionUp) { onBack(); return@onKeyEvent true }
                    return@onKeyEvent false
                }
                when (keyEvent.key) {
                    Key.DirectionLeft -> { selectedIndex = (selectedIndex - 1).coerceAtLeast(0); true }
                    Key.DirectionRight -> { selectedIndex = (selectedIndex + 1).coerceAtMost(size - 1); true }
                    Key.DirectionUp -> {
                        if (selectedIndex < columns) {
                            onBack()
                        } else {
                            selectedIndex = (selectedIndex - columns).coerceAtLeast(0)
                        }
                        true
                    }
                    Key.DirectionDown -> {
                        selectedIndex = (selectedIndex + columns).coerceAtMost(size - 1)
                        true
                    }
                    // CH+ : サムネイルを大きく（列を減らす）
                    Key.ChannelUp -> { columns = (columns - 1).coerceAtLeast(MIN_GALLERY_COLUMNS); true }
                    // CH- : サムネイルを小さく（列を増やす）
                    Key.ChannelDown -> { columns = (columns + 1).coerceAtMost(MAX_GALLERY_COLUMNS); true }
                    Key.Enter, Key.DirectionCenter -> { onSelect(selectedIndex); true }
                    Key.Back -> { onBack(); true }
                    else -> false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ヘッダー（操作ヒント + 件数）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selectedIndex + 1} / ${dateContents.size}",
                    color = Color.White,
                    fontSize = 16.sp,
                )
                Text(
                    text = "CH+/− : サイズ変更   決定 : 表示   戻る : メニュー",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            ) {
                gridItemsIndexed(dateContents) { index, content ->
                    val isSelected = index == selectedIndex
                    val isCurrent = index == uiState.currentIndex
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .border(
                                width = if (isSelected) 4.dp else if (isCurrent) 2.dp else 0.dp,
                                color = when {
                                    isSelected -> Color.White
                                    isCurrent -> Color(0xFF4488FF)
                                    else -> Color.Transparent
                                },
                            )
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center,
                    ) {
                        ThumbnailInner(
                            bytes = uiState.thumbnails[content.contentId],
                            index = index,
                            reqPx = reqPx,
                        )
                        RatingBadge(content.ratingValue())
                    }
                }
            }
        }
    }
}

private const val MIN_GALLERY_COLUMNS = 3
private const val MAX_GALLERY_COLUMNS = 8

/** 全画面表示用にダウンサンプルする際の最大辺ピクセル（4K TV 相当）。 */
private const val FULLSCREEN_MAX_PX = 2160

/**
 * サムネイル1枚の中身を描画する。
 * バイトがあればダウンサンプルしてデコードし画像表示、無ければ番号を表示する。
 */
@Composable
private fun ThumbnailInner(bytes: ByteArray?, index: Int, reqPx: Int) {
    if (bytes != null) {
        val bitmap = remember(bytes, reqPx) { decodeSampledBitmap(bytes, reqPx) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Thumbnail ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
    }
    Text(
        text = "${index + 1}",
        color = Color.Gray,
        fontSize = 12.sp,
    )
}

/**
 * サムネイルに重ねて表示する評価バッジ（★N）。
 * 評価未設定（0）の場合は何も表示しない。
 */
@Composable
private fun BoxScope.RatingBadge(rating: Int) {
    if (rating <= 0) return
    Row(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(2.dp)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "★$rating",
            color = Color(0xFFFFD700),
            fontSize = 11.sp,
        )
    }
}

/**
 * メモリ節約のため、要求サイズ(reqPx)に合わせて inSampleSize でダウンサンプルしてデコードする。
 */
private fun decodeSampledBitmap(bytes: ByteArray, reqPx: Int): android.graphics.Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim > 0 && maxDim / sample > reqPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: Exception) {
        null
    }
}

// ============================================================
// Delete confirmation overlay
// ============================================================

@Composable
private fun DeleteConfirmOverlay(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var focusedIndex by remember { mutableIntStateOf(1) }
    val confirmFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { confirmFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .focusRequester(confirmFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> { focusedIndex = 0; true }
                        Key.DirectionRight -> { focusedIndex = 1; true }
                        Key.Enter, Key.DirectionCenter -> {
                            if (focusedIndex == 0) onConfirm() else onCancel()
                            true
                        }
                        Key.Back -> { onCancel(); true }
                        else -> false
                    }
                } else false
            },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Delete this photo?",
                color = Color.White,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (focusedIndex == 0) Color.Red else Color.Gray,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Delete")
                }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (focusedIndex == 1) Color.White else Color.Gray,
                        contentColor = if (focusedIndex == 1) Color.Black else Color.White,
                    ),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ============================================================
// Share overlay (QR code)
// ============================================================

/**
 * 共有用QRコードを表示するオーバーレイ。
 * download_url（事前署名済み・有効期限600秒）をQR化し、スマホで読み取ってダウンロードできる。
 */
@Composable
private fun ShareOverlay(
    url: String?,
    isLoading: Boolean,
    onClose: () -> Unit,
) {
    val shareFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { shareFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .focusRequester(shareFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Back, Key.Enter, Key.DirectionCenter -> { onClose(); true }
                        else -> false
                    }
                } else false
            },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "スマホで読み取って画像をダウンロード",
                color = Color.White,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))

            val qrBitmap = remember(url) { url?.let { generateQrBitmap(it, 600) } }
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = Color.Black)
                    qrBitmap != null -> Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Share QR code",
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Text(
                        text = "QRコードを生成できませんでした",
                        color = Color.Black,
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "URLの有効期限は約10分です   決定/戻る : 閉じる",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * 文字列から QR コードの Bitmap を生成する（ZXing、オフライン動作）。
 */
private fun generateQrBitmap(text: String, sizePx: Int): android.graphics.Bitmap? {
    return try {
        val hints = mapOf(com.google.zxing.EncodeHintType.MARGIN to 1)
        val matrix = com.google.zxing.qrcode.QRCodeWriter()
            .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] =
                    if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    } catch (_: Exception) {
        null
    }
}

// ============================================================
// Date selection screen (Calendar)
// ============================================================

@Composable
private fun DateSelectScreen(
    dates: List<LocalDate>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val availableDatesSet = remember(dates) { dates.toSet() }
    val initialMonth = selectedDate?.let { YearMonth.from(it) }
        ?: dates.firstOrNull()?.let { YearMonth.from(it) }
        ?: YearMonth.now()

    var currentMonth by remember { mutableStateOf(initialMonth) }
    // focusedRow: 0=month header area, 1..6=calendar weeks
    var focusedRow by remember { mutableIntStateOf(1) }
    var focusedCol by remember { mutableIntStateOf(0) }

    val calendarFocusRequester = remember { FocusRequester() }

    // Build the grid for current month
    val firstDayOfMonth = currentMonth.atDay(1)
    val daysInMonth = currentMonth.lengthOfMonth()
    // Monday=1 .. Sunday=7, column offset (0-based, Mon=0)
    val startDayOfWeek = (firstDayOfMonth.dayOfWeek.value - 1) // 0=Mon
    // Grid: 6 rows x 7 cols, each cell = day number or 0 (empty)
    val grid = remember(currentMonth) {
        val g = Array(6) { IntArray(7) }
        for (day in 1..daysInMonth) {
            val cellIndex = startDayOfWeek + day - 1
            val row = cellIndex / 7
            val col = cellIndex % 7
            g[row][col] = day
        }
        g
    }

    // Resolve focused date
    val focusedDay = if (focusedRow in 1..6) {
        val day = grid[focusedRow - 1][focusedCol]
        if (day > 0) currentMonth.atDay(day) else null
    } else null

    // Initialize focus to selectedDate or first available date in month
    LaunchedEffect(currentMonth) {
        val targetDate = if (selectedDate != null && YearMonth.from(selectedDate) == currentMonth) {
            selectedDate
        } else {
            // Find first available date in this month
            dates.filter { YearMonth.from(it) == currentMonth }.minOrNull()
        }
        if (targetDate != null) {
            val day = targetDate.dayOfMonth
            val cellIndex = startDayOfWeek + day - 1
            focusedRow = (cellIndex / 7) + 1
            focusedCol = cellIndex % 7
        } else {
            focusedRow = 1
            focusedCol = 0
        }
    }

    LaunchedEffect(Unit) {
        try { calendarFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .focusRequester(calendarFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (focusedRow == 0) {
                                // Move to previous month
                                currentMonth = currentMonth.minusMonths(1)
                            } else {
                                val newCol = focusedCol - 1
                                if (newCol >= 0) focusedCol = newCol
                                else {
                                    // Wrap to previous row
                                    val newRow = focusedRow - 1
                                    if (newRow >= 1) {
                                        focusedRow = newRow
                                        focusedCol = 6
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (focusedRow == 0) {
                                // Move to next month
                                currentMonth = currentMonth.plusMonths(1)
                            } else {
                                val newCol = focusedCol + 1
                                if (newCol <= 6) focusedCol = newCol
                                else {
                                    // Wrap to next row
                                    val newRow = focusedRow + 1
                                    if (newRow <= 6) {
                                        focusedRow = newRow
                                        focusedCol = 0
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            val newRow = focusedRow - 1
                            if (newRow >= 0) focusedRow = newRow
                            true
                        }
                        Key.DirectionDown -> {
                            val newRow = focusedRow + 1
                            if (newRow <= 6) focusedRow = newRow
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            if (focusedRow == 0) {
                                // No action on month header
                            } else {
                                val day = grid.getOrNull(focusedRow - 1)?.getOrNull(focusedCol) ?: 0
                                if (day > 0) {
                                    val date = currentMonth.atDay(day)
                                    if (date in availableDatesSet) {
                                        onDateSelected(date)
                                    }
                                }
                            }
                            true
                        }
                        Key.Back -> { onBack(); true }
                        else -> false
                    }
                } else false
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Month navigation header
            val monthHeaderFocused = focusedRow == 0
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (monthHeaderFocused) Color.DarkGray else Color.Transparent)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\u25C0",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 24.dp),
                )
                Text(
                    text = "${currentMonth.year}年${currentMonth.monthValue}月",
                    color = Color.White,
                    fontSize = 24.sp,
                )
                Text(
                    text = "\u25B6",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 24.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Day-of-week header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val dayNames = listOf("月", "火", "水", "木", "金", "土", "日")
                dayNames.forEach { name ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = name,
                            color = Color.Gray,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar grid
            for (row in 0 until 6) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (col in 0 until 7) {
                        val day = grid[row][col]
                        val date = if (day > 0) currentMonth.atDay(day) else null
                        val hasImages = date != null && date in availableDatesSet
                        val isFocused = focusedRow == row + 1 && focusedCol == col
                        val isSelected = date == selectedDate

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .padding(2.dp)
                                .background(
                                    when {
                                        isFocused && hasImages -> Color.White
                                        isFocused -> Color.Gray.copy(alpha = 0.5f)
                                        isSelected -> Color(0xFF4488FF).copy(alpha = 0.6f)
                                        hasImages -> Color(0xFF4488FF).copy(alpha = 0.3f)
                                        else -> Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day > 0) {
                                Text(
                                    text = day.toString(),
                                    color = when {
                                        isFocused && hasImages -> Color.Black
                                        hasImages -> Color.White
                                        else -> Color.Gray.copy(alpha = 0.4f)
                                    },
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(16.dp).height(16.dp).background(Color(0xFF4488FF).copy(alpha = 0.3f)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "画像あり", color = Color.Gray, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(16.dp).height(16.dp).background(Color.Gray.copy(alpha = 0.4f)))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "画像なし", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

// ============================================================
// Utility
// ============================================================

private fun extractDateFromContent(content: com.sony.dtv.camera_tv.data.model.Content): LocalDate? {
    return try {
        content.recordedDateLocalTime?.let {
            java.time.LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
        } ?: content.createdDate?.let {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate()
        }
    } catch (_: Exception) {
        null
    }
}

// ============================================================
// Rating dialog (5-star rating selection)
// ============================================================

/**
 * 5段階評価選択ダイアログ。
 * リモコンで左右キー（⬅️/➡️）で星を選択、決定キー（⏎）で確定。
 */
@Composable
private fun RatingDialog(
    currentRating: Int = 0,
    isLoading: Boolean = false,
    onRatingSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedRating by remember { mutableIntStateOf(currentRating.coerceIn(0, 5)) }
    val ratingFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { ratingFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .focusRequester(ratingFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            selectedRating = (selectedRating - 1).coerceAtLeast(0)
                            true
                        }
                        Key.DirectionRight -> {
                            selectedRating = (selectedRating + 1).coerceAtMost(5)
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            if (!isLoading) {
                                onRatingSelected(selectedRating)
                            }
                            true
                        }
                        Key.Back -> { onDismiss(); true }
                        else -> false
                    }
                } else false
            },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.8f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "お気に入り評価",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            // 現在の選択値（0=評価なし）
            Text(
                text = if (selectedRating == 0) "評価なし" else "★ × $selectedRating",
                color = Color.White,
                fontSize = 20.sp,
            )

            // Star rating display (0=なし, 1-5)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 評価なし（クリア）ボタン
                val isClearFocused = selectedRating == 0
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = if (isClearFocused) Color(0xFFFFD700) else Color.Gray,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                        .border(
                            width = if (isClearFocused) 3.dp else 1.dp,
                            color = if (isClearFocused) Color.White else Color.DarkGray,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✕",
                        color = if (isClearFocused) Color.Black else Color.LightGray,
                        fontSize = 32.sp,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))

                repeat(5) { index ->
                    val rating = index + 1
                    val isFocused = rating == selectedRating
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = if (isFocused)
                                    Color(0xFFFFD700) // Gold for focused
                                else if (rating <= selectedRating)
                                    Color(0xFFFFAA00) // Orange for selected
                                else
                                    Color.Gray, // Gray for unselected
                                shape = androidx.compose.foundation.shape.CircleShape,
                            )
                            .border(
                                width = if (isFocused) 3.dp else 1.dp,
                                color = if (isFocused) Color.White else Color.DarkGray,
                                shape = androidx.compose.foundation.shape.CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "★",
                            color = if (rating <= selectedRating) Color.White else Color.LightGray,
                            fontSize = 40.sp,
                        )
                    }
                    if (index < 4) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            // Help text
            Text(
                text = "←/→キー: 選択（左端✕で評価なし）   決定キー: 確定   戻るキー: キャンセル",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}