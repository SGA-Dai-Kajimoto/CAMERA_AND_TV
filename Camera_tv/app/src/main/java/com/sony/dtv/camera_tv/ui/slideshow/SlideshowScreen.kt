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
import androidx.compose.foundation.layout.width
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
import coil.compose.AsyncImage
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * TV画面 メインComposable。
 * 3つの画面状態を管理:
 *  - Photo: フルスクリーン写真表示（画面1）
 *  - Menu: 操作メニューオーバーレイ（画面2）
 *  - DateSelect: 日付選択画面
 */
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
                    text = "エラー: ${uiState.error}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            uiState.contents.isEmpty() -> {
                Text(
                    text = "このフォルダに写真はありません",
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
                            onBack = onBack,
                        )
                    }

                    ScreenMode.Menu -> {
                        PhotoBackground(uiState = uiState)
                        MenuOverlay(
                            onDateSelect = { screenMode = ScreenMode.DateSelect },
                            onFavorite = {
                                viewModel.toggleFavorite()
                                toastMessage = "お気に入りに登録しました"
                                screenMode = ScreenMode.Photo
                            },
                            onDelete = { showDeleteConfirm = true },
                            onDismiss = { screenMode = ScreenMode.Photo },
                        )
                        if (showDeleteConfirm) {
                            DeleteConfirmOverlay(
                                onConfirm = {
                                    viewModel.deleteCurrentContent()
                                    toastMessage = "削除しました"
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

        // Toast表示
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
// 画面1: フルスクリーン写真表示
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
                        Key.Back -> { onBack(); true }
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

        // 現在位置インジケーター
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

        // 選択中の日付表示
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
    if (uiState.currentImageBytes != null) {
        AsyncImage(
            model = uiState.currentImageBytes,
            contentDescription = "写真",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ============================================================
// 画面2: 操作メニューオーバーレイ
// ============================================================

@Composable
private fun MenuOverlay(
    onDateSelect: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var focusedIndex by remember { mutableIntStateOf(0) }
    val menuFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { menuFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .focusRequester(menuFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.DirectionRight -> {
                            focusedIndex = (focusedIndex + 1).coerceAtMost(2)
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            when (focusedIndex) {
                                0 -> onDateSelect()
                                1 -> onFavorite()
                                2 -> onDelete()
                            }
                            true
                        }
                        Key.Back -> { onDismiss(); true }
                        else -> false
                    }
                } else false
            },
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuButton(label = "日付選択", isFocused = focusedIndex == 0, onClick = onDateSelect)
            Spacer(modifier = Modifier.width(24.dp))
            MenuButton(label = "お気に入り", isFocused = focusedIndex == 1, onClick = onFavorite)
            Spacer(modifier = Modifier.width(24.dp))
            MenuButton(label = "削除", isFocused = focusedIndex == 2, onClick = onDelete)
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
// 削除確認オーバーレイ
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
                text = "この写真を削除しますか？",
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
                    Text("削除する")
                }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (focusedIndex == 1) Color.White else Color.Gray,
                        contentColor = if (focusedIndex == 1) Color.Black else Color.White,
                    ),
                ) {
                    Text("キャンセル")
                }
            }
        }
    }
}

// ============================================================
// 日付選択画面
// ============================================================

@Composable
private fun DateSelectScreen(
    dates: List<LocalDate>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    var focusedIndex by remember { mutableIntStateOf(dates.indexOf(selectedDate).coerceAtLeast(0)) }
    val dateFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try { dateFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .focusRequester(dateFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                            true
                        }
                        Key.DirectionDown -> {
                            focusedIndex = (focusedIndex + 1).coerceAtMost(dates.size - 1)
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            if (dates.isNotEmpty()) onDateSelected(dates[focusedIndex])
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
        ) {
            Text(
                text = "日付を選択",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                dates.forEachIndexed { index, date ->
                    val isFocused = index == focusedIndex
                    val isSelected = date == selectedDate
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    isFocused -> Color.White
                                    isSelected -> Color.Gray.copy(alpha = 0.4f)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                            color = if (isFocused) Color.Black else Color.White,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// ユーティリティ
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
