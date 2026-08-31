package com.sony.dtv.camera_tv.ui.slideshow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sony.dtv.camera_tv.R
import com.sony.dtv.camera_tv.data.local.UiPreferences
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import com.sony.dtv.camera_tv.domain.SortMode
import com.sony.dtv.camera_tv.domain.isJudged
import com.sony.dtv.camera_tv.ui.common.KeyInputSurface
import com.sony.dtv.camera_tv.ui.common.TvActionButton
import com.sony.dtv.camera_tv.ui.theme.TvColors
import com.sony.dtv.camera_tv.ui.theme.TvDimens
import com.sony.dtv.camera_tv.ui.theme.TvTextSizes
import com.sony.dtv.camera_tv.ui.tutorial.CoachScreen
import com.sony.dtv.camera_tv.ui.tutorial.CoachSignal
import com.sony.dtv.camera_tv.ui.tutorial.TutorialCoachOverlay
import com.sony.dtv.camera_tv.ui.tutorial.TutorialIntro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOAST_DURATION_MS = 2000L

/** 使い方案内の段階。 */
private enum class TutorialPhase { None, Intro, Coach }

/**
 * スライドショー画面のエントリポイント。
 * ViewModel の生成と [SlideshowActions] への束ね直しだけを行う。
 */
@Composable
fun SlideshowScreen(
    repository: ImagingEdgeRepository,
    onSignOut: () -> Unit = {},
) {
    val viewModel = viewModel<SlideshowViewModel>(
        factory = SlideshowViewModel.factory(repository),
    )
    val uiState by viewModel.uiState.collectAsState()

    val actions = remember(viewModel) {
        SlideshowActions(
            reload = viewModel::loadContents,
            next = viewModel::nextImage,
            prev = viewModel::prevImage,
            selectIndex = viewModel::selectIndex,
            selectDate = viewModel::selectDate,
            setSortMode = viewModel::setSortMode,
            togglePlay = viewModel::togglePlayPause,
            setRating = viewModel::setCurrentContentRating,
            delete = viewModel::deleteCurrentContent,
            requestShare = viewModel::requestShareUrl,
            closeShare = viewModel::clearShareUrl,
            loadThumbnails = viewModel::loadThumbnails,
            startCulling = viewModel::startCulling,
            exitCulling = viewModel::exitCulling,
            toggleCullScope = viewModel::toggleCullScope,
            cullPick = viewModel::cullPick,
            cullSkip = viewModel::cullSkip,
            cullNext = viewModel::cullNext,
            cullPrev = viewModel::cullPrev,
            cycleZoom = viewModel::cycleZoom,
            zoomOff = viewModel::zoomOff,
            panZoom = viewModel::panZoom,
            setCullReviewFilter = viewModel::setCullReviewFilter,
            flipCullJudgement = viewModel::flipCullJudgement,
            revertCullJudgement = viewModel::revertCullJudgement,
            startCompare = viewModel::startCompare,
            exitCompare = viewModel::exitCompare,
            compareMoveBy = viewModel::compareMoveBy,
            toggleCompareSelection = viewModel::toggleCompareSelection,
            toggleCompareLayout = viewModel::toggleCompareLayout,
            comparePick = viewModel::comparePick,
            compareSkip = viewModel::compareSkip,
            clearError = viewModel::clearError,
            clearNotice = viewModel::clearNotice,
        )
    }

    val context = LocalContext.current
    val uiPreferences = remember(context) { UiPreferences.create(context) }
    val tutorialSeen by uiPreferences.tutorialSeen.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(TutorialPhase.None) }

    LaunchedEffect(tutorialSeen) {
        if (!tutorialSeen) phase = TutorialPhase.Intro
    }

    // 導入カードは背後の画面と同時に出さない。KeyInputSurface 同士が
    // フォーカスを奪い合ってリモコン操作が届かなくなるため。
    if (phase == TutorialPhase.Intro) {
        TutorialIntro(
            // 途中で落ちても再度出ないよう、選んだ時点で既読にする
            onStart = {
                phase = TutorialPhase.Coach
                scope.launch { uiPreferences.setTutorialSeen() }
            },
            onSkip = {
                phase = TutorialPhase.None
                scope.launch { uiPreferences.setTutorialSeen() }
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        var screen by remember { mutableStateOf(ScreenMode.Photo) }

        SlideshowContent(
            uiState = uiState,
            actions = actions,
            onShowTutorial = { phase = TutorialPhase.Intro },
            onSignOut = onSignOut,
            coachActive = phase == TutorialPhase.Coach,
            onScreenModeChanged = { screen = it },
        )

        if (phase == TutorialPhase.Coach) {
            TutorialCoachOverlay(
                signal = CoachSignal(
                    screen = screen.toCoachScreen(),
                    photoIndex = uiState.currentIndex,
                    isCulling = uiState.isCulling,
                    isZoomed = uiState.isZoomed,
                    judgedCount = uiState.contents.count { it.isJudged() },
                ),
                onFinish = { phase = TutorialPhase.None },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = TvDimens.SpaceLg),
            )
        }
    }
}

/** 案内は写真画面と選別画面しか区別しない。 */
private fun ScreenMode.toCoachScreen(): CoachScreen = when (this) {
    ScreenMode.Photo -> CoachScreen.Photo
    ScreenMode.Cull -> CoachScreen.Cull
    else -> CoachScreen.Other
}

/**
 * 画面遷移とオーバーレイの出し分け。ViewModel には依存しない。
 */
@Composable
internal fun SlideshowContent(
    uiState: SlideshowUiState,
    actions: SlideshowActions,
    onShowTutorial: () -> Unit = {},
    onSignOut: () -> Unit = {},
    coachActive: Boolean = false,
    onScreenModeChanged: (ScreenMode) -> Unit = {},
) {
    var screenMode by remember { mutableStateOf(ScreenMode.Photo) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    // 案内は写真画面から始まる。メニューを開いたまま始めると
    // 「← → で写真を送る」と出ても左右キーはボタン移動に使われてしまう。
    LaunchedEffect(coachActive) {
        if (coachActive) screenMode = ScreenMode.Photo
    }

    LaunchedEffect(screenMode) { onScreenModeChanged(screenMode) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(TOAST_DURATION_MS)
            toastMessage = null
        }
    }

    LaunchedEffect(uiState.notice) {
        uiState.notice?.let {
            toastMessage = it
            actions.clearNotice()
        }
    }

    val context = LocalContext.current
    val sortedByDate = stringResource(R.string.toast_sorted_by_date)
    val sortedByRating = stringResource(R.string.toast_sorted_by_rating)
    val playStopped = stringResource(R.string.toast_play_stopped)
    val deletedMessage = stringResource(R.string.toast_deleted)
    val ratingCleared = stringResource(R.string.toast_rating_cleared)

    Box(modifier = Modifier.fillMaxSize().background(TvColors.Background)) {
        when {
            uiState.isLoading -> LoadingState(modifier = Modifier.align(Alignment.Center))

            uiState.error != null -> RetryableState(
                title = stringResource(R.string.error_title),
                detail = uiState.error,
                onRetry = actions.reload,
            )

            !uiState.hasContents -> RetryableState(
                title = stringResource(R.string.common_empty),
                onRetry = actions.reload,
            )

            else -> when (screenMode) {
                ScreenMode.Photo -> PhotoScreen(
                    uiState = uiState,
                    actions = actions,
                    onShowMenu = { screenMode = ScreenMode.Menu },
                )

                ScreenMode.Menu -> {
                    // メニュー中も写真は見えたままにする
                    PhotoImage(uiState.currentImageBytes)
                    MenuOverlay(
                        uiState = uiState,
                        actions = actions,
                        onSelect = { item ->
                            when (item) {
                                MenuItem.Cull -> {
                                    // 選択中の日を対象にする。全期間にしたいときは選別画面で CH+ で切り替える
                                    actions.startCulling(uiState.selectedDate)
                                    screenMode = ScreenMode.Cull
                                }

                                MenuItem.Sort -> showSortDialog = true
                                MenuItem.Date -> screenMode = ScreenMode.DateSelect
                                MenuItem.Rating -> showRatingDialog = true
                                MenuItem.Delete -> showDeleteConfirm = true
                                MenuItem.Share -> actions.requestShare()
                                MenuItem.Tutorial -> onShowTutorial()
                                MenuItem.SignOut -> showSignOutConfirm = true

                                MenuItem.Play -> {
                                    actions.togglePlay()
                                    // 鑑賞に集中できるよう、再生中は写真以外を出さない
                                    screenMode = ScreenMode.Photo
                                    toastMessage = if (uiState.isPlaying) playStopped else null
                                }
                            }
                        },
                        onOpenGallery = { screenMode = ScreenMode.Gallery },
                        onDismiss = { screenMode = ScreenMode.Photo },
                    )
                }

                ScreenMode.DateSelect -> DateSelectScreen(
                    dates = uiState.availableDates,
                    selectedDate = uiState.selectedDate,
                    onDateSelected = { date ->
                        actions.selectDate(date)
                        screenMode = ScreenMode.Photo
                    },
                    onBack = { screenMode = ScreenMode.Menu },
                )

                ScreenMode.Gallery -> GalleryScreen(
                    uiState = uiState,
                    actions = actions,
                    onSelect = { index ->
                        actions.selectIndex(index)
                        screenMode = ScreenMode.Photo
                    },
                    onBack = { screenMode = ScreenMode.Menu },
                )

                ScreenMode.Cull -> CullScreen(
                    uiState = uiState,
                    actions = actions,
                    onExit = {
                        actions.exitCulling()
                        screenMode = ScreenMode.Photo
                    },
                    onReview = { screenMode = ScreenMode.CullReview },
                    onCompare = {
                        actions.startCompare()
                        screenMode = ScreenMode.Compare
                    },
                )

                ScreenMode.Compare -> CompareScreen(
                    uiState = uiState,
                    actions = actions,
                    onExit = {
                        actions.exitCompare()
                        screenMode = if (uiState.isCulling) ScreenMode.Cull else ScreenMode.Photo
                    },
                )

                ScreenMode.CullReview -> CullReviewScreen(
                    uiState = uiState,
                    actions = actions,
                    onBack = { screenMode = ScreenMode.Cull },
                )
            }
        }

        // オーバーレイ（メニューの上に重ねる）
        if (showRatingDialog) {
            RatingDialog(
                currentRating = uiState.currentRating,
                isLoading = uiState.isRatingLoading,
                onRatingSelected = { rating ->
                    actions.setRating(rating)
                    showRatingDialog = false
                    toastMessage = if (rating == 0) {
                        ratingCleared
                    } else {
                        context.getString(R.string.toast_rating_set, rating)
                    }
                },
                onDismiss = { showRatingDialog = false },
            )
        }

        if (showDeleteConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.delete_title),
                message = stringResource(R.string.delete_message),
                confirmLabel = stringResource(R.string.delete_confirm),
                onConfirm = {
                    actions.delete()
                    showDeleteConfirm = false
                    screenMode = ScreenMode.Photo
                    toastMessage = deletedMessage
                },
                onCancel = { showDeleteConfirm = false },
            )
        }

        if (showSignOutConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.sign_out_title),
                message = stringResource(R.string.sign_out_message),
                confirmLabel = stringResource(R.string.sign_out_confirm),
                onConfirm = {
                    showSignOutConfirm = false
                    onSignOut()
                },
                onCancel = { showSignOutConfirm = false },
            )
        }

        if (showSortDialog) {
            SortDialog(
                current = uiState.sortMode,
                onSelected = { mode ->
                    actions.setSortMode(mode)
                    showSortDialog = false
                    screenMode = ScreenMode.Photo
                    toastMessage =
                        if (mode == SortMode.RatingDesc) sortedByRating else sortedByDate
                },
                onDismiss = { showSortDialog = false },
            )
        }

        if (uiState.shareUrl != null || uiState.isShareLoading) {
            ShareOverlay(
                url = uiState.shareUrl,
                isLoading = uiState.isShareLoading,
                onClose = actions.closeShare,
            )
        }

        TvToast(
            message = toastMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = TvDimens.ScreenPadding),
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
    ) {
        CircularProgressIndicator(color = TvColors.OnSurface)
        Text(
            text = stringResource(R.string.common_loading),
            color = TvColors.OnSurfaceMuted,
            fontSize = TvTextSizes.Body,
        )
    }
}

/**
 * エラー／空状態。決定キーで再読み込みできるようにして、リモコンだけで復帰できるようにする。
 */
@Composable
private fun RetryableState(
    title: String,
    detail: String? = null,
    onRetry: () -> Unit,
) {
    KeyInputSurface(
        onKey = { key ->
            when (key) {
                Key.Enter, Key.DirectionCenter -> { onRetry(); true }
                else -> false
            }
        },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TvDimens.SpaceMd),
        ) {
            StatusMessage(title = title, detail = detail)
            TvActionButton(label = stringResource(R.string.common_retry))
        }
    }
}
