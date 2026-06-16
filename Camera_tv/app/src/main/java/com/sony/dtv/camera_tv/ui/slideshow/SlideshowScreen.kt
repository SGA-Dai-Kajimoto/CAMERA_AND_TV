package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
                        MenuOverlay(
                            uiState = uiState,
                            viewModel = viewModel,
                            onDateSelect = { screenMode = ScreenMode.DateSelect },
                            onFavorite = {
                                viewModel.toggleFavorite()
                                toastMessage = "Registered as favorite"
                                screenMode = ScreenMode.Photo
                            },
                            onDelete = { showDeleteConfirm = true },
                            onDismiss = { screenMode = ScreenMode.Photo },
                        )
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

private enum class ScreenMode { Photo, Menu, DateSelect }

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
        val dateContents = uiState.contents.filter { content ->
            uiState.selectedDate == null || extractDateFromContent(content) == uiState.selectedDate
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
        val bitmap = remember(bytes) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
                                buttonIndex = (buttonIndex + 1).coerceAtMost(2)
                            } else {
                                thumbIndex = (thumbIndex + 1).coerceAtMost(dateContents.size - 1)
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (focusArea == 0) {
                                focusArea = 1
                                thumbIndex = uiState.currentIndex.coerceIn(0, dateContents.size - 1)
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
                        if (thumbBytes != null) {
                            val bitmap = remember(thumbBytes) {
                                BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Thumbnail ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = Color.Gray,
                                fontSize = 12.sp,
                            )
                        }
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