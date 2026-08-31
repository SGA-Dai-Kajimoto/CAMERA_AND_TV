package com.sony.dtv.camera_tv.ui.slideshow

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.remote.isCertificateFailure
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import com.sony.dtv.camera_tv.domain.ContentCulling
import com.sony.dtv.camera_tv.domain.ContentGrouping
import com.sony.dtv.camera_tv.domain.ContentListItem
import com.sony.dtv.camera_tv.domain.ContentRating
import com.sony.dtv.camera_tv.domain.CullFilter
import com.sony.dtv.camera_tv.domain.SortMode
import com.sony.dtv.camera_tv.domain.isJudged
import com.sony.dtv.camera_tv.domain.ratingValue
import com.sony.dtv.camera_tv.domain.recordedLocalDate
import com.sony.dtv.camera_tv.domain.tagsWithRating
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * スライドショー画面の状態。
 *
 * [visibleContents] は「並び順」と「選択日」から導出された、実際に画面に出るコンテンツ列。
 * UI 側で再フィルタしないよう、状態更新時に必ずここへ反映する。
 */
data class SlideshowUiState(
    val isLoading: Boolean = false,
    val contents: List<Content> = emptyList(),
    val groupedItems: List<ContentListItem> = emptyList(),
    val availableDates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val sortMode: SortMode = SortMode.DateDesc,
    val visibleContents: List<Content> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val currentImageBytes: ByteArray? = null,
    val isImageLoading: Boolean = false,
    val thumbnails: Map<String, ByteArray> = emptyMap(),
    val shareUrl: String? = null,
    val isShareLoading: Boolean = false,
    val isRatingLoading: Boolean = false,
    val isCulling: Boolean = false,
    val cullQueue: List<Content> = emptyList(),
    val cullIndex: Int = 0,
    /** 選別の対象日。null なら全期間。 */
    val cullDate: LocalDate? = null,
    val zoomStep: Int = 0,
    /** 拡大位置。元画像全体を 0.0〜1.0 とした割合で、0.5 が中央。 */
    val zoomCenterX: Float = 0.5f,
    val zoomCenterY: Float = 0.5f,
    val zoomImageBytes: ByteArray? = null,
    val isZoomLoading: Boolean = false,
    val cullReviewFilter: CullFilter = CullFilter.Picked,
    /** 比較モードの候補。空なら比較していない。 */
    val compareContents: List<Content> = emptyList(),
    val compareIndex: Int = 0,
    /** 見比べに含めるとチェックした写真の contentId。 */
    val compareSelected: Set<String> = emptySet(),
    val compareLayout: CompareLayout = CompareLayout.Stack,
    val compareImages: Map<String, ByteArray> = emptyMap(),
    val error: String? = null,

    /** 作業を中断させない軽い知らせ（トースト表示）。 */
    val notice: String? = null,
) {
    /** 現在表示中のコンテンツ。空なら null。 */
    val currentContent: Content? get() = visibleContents.getOrNull(currentIndex)

    /** 現在表示中のコンテンツの評価（0=評価なし）。 */
    val currentRating: Int get() = currentContent?.ratingValue() ?: ContentRating.NONE

    val hasContents: Boolean get() = contents.isNotEmpty()

    /**
     * 選別中のコンテンツ。キューを末尾まで進めたら null（＝完了）。
     *
     * キューは開始時に固定するので、判定してもキュー内の要素は古いタグのまま。
     * 表示は必ず [contents] 側の最新を引き当てる。
     */
    val cullContent: Content? get() = cullQueue.getOrNull(cullIndex)?.let { queued ->
        contents.firstOrNull { it.contentId == queued.contentId } ?: queued
    }

    /** 選別キューを最後まで処理し終えたか。 */
    val isCullComplete: Boolean get() = isCulling && cullIndex >= cullQueue.size

    /** 選別キューに残っている未判定の枚数。キューには判定済みも並んでいる。 */
    val cullRemaining: Int
        get() {
            val judgedIds = contents.filter { it.isJudged() }.map { it.contentId }.toSet()
            return cullQueue.count { it.contentId !in judgedIds }
        }

    /** 拡大表示中か。 */
    val isZoomed: Boolean get() = zoomStep > 0

    /** 現在の拡大倍率（画面ピクセル ÷ 元画像ピクセル）。1.0 が等倍。 */
    val zoomMagnification: Float
        get() = SlideshowViewModel.ZOOM_MAGNIFICATIONS.getOrElse(zoomStep) { 1f }

    /** 採用済みの写真。 */
    val pickedContents: List<Content> get() = ContentCulling.picked(contents)

    /** 見送り済みの写真。削除していないのでいつでも戻せる。 */
    val skippedContents: List<Content> get() = ContentCulling.skipped(contents)

    /** 見直し画面でいま表示している側の写真。 */
    val cullReviewContents: List<Content>
        get() = when (cullReviewFilter) {
            CullFilter.Picked -> pickedContents
            CullFilter.Skipped -> skippedContents
        }

    /** 比較モードで現在フォーカスしている写真。 */
    val compareContent: Content? get() = compareContents.getOrNull(compareIndex)

    val isComparing: Boolean get() = compareContents.isNotEmpty()

    /**
     * 実際に見比べる写真。
     * チェックが無いうちはフォーカス中の 1 枚だけを見せる（空白にしない）。
     */
    val comparePicked: List<Content>
        get() = compareContents.filter { it.contentId in compareSelected }
            .ifEmpty { listOfNotNull(compareContent) }
}

/** 比較モードの見せ方。 */
enum class CompareLayout {
    /** 同じ位置に重ねて切り替える。わずかな差が「ちらつき」として見える。 */
    Stack,

    /** 左右に並べる。構図の違いを見るとき向き。 */
    SideBySide,
}

/**
 * スライドショー画面の状態管理。
 *
 * 並び替え・グルーピング・評価タグの解釈は domain パッケージの純粋関数に委譲し、
 * ここでは「非同期取得」と「状態遷移」だけを担当する。
 */
class SlideshowViewModel(
    private val repository: ImagingEdgeRepository,
    externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(SlideshowUiState())
    val uiState: StateFlow<SlideshowUiState> = _uiState.asStateFlow()

    private var autoAdvanceJob: Job? = null
    private var imageLoadJob: Job? = null
    private var zoomLoadJob: Job? = null
    private var compareLoadJob: Job? = null
    private var tagSyncJob: Job? = null
    private var loadedContentId: String? = null

    /** 現在取得中のコンテンツ。同じ写真を二重で取りに行かないための目印。 */
    private var loadingContentId: String? = null

    /** 未送信のタグ更新。UI は先に進め、ここに残った分を順に送る。 */
    private val pendingTagUpdates = ArrayDeque<TagUpdate>()

    private data class TagUpdate(
        val folderId: String,
        val contentId: String,
        val tags: List<String>,
    )

    init {
        loadContents()
    }

    // ---------------------------------------------------------------- //
    // 読み込み
    // ---------------------------------------------------------------- //

    fun loadContents() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.listAllContents()
                .onSuccess { contents -> applyContents(contents) }
                .onFailure { e -> failWith("コンテンツの取得に失敗しました", e) }
        }
    }

    /** 取得したコンテンツ一覧を状態へ反映し、先頭の画像を読み込む。 */
    private fun applyContents(contents: List<Content>) {
        val dates = ContentGrouping.availableDates(contents)
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                contents = contents,
                groupedItems = ContentGrouping.groupByDate(contents),
                availableDates = dates,
                selectedDate = state.selectedDate?.takeIf { it in dates } ?: dates.firstOrNull(),
                currentIndex = 0,
                currentImageBytes = null,
            ).withVisibleContents()
        }
        loadCurrentImage()
    }

    // ---------------------------------------------------------------- //
    // ナビゲーション
    // ---------------------------------------------------------------- //

    fun selectDate(date: LocalDate) {
        updateAndReload { it.copy(selectedDate = date, sortMode = SortMode.DateDesc, currentIndex = 0) }
    }

    /** 並び順（日付順 ⇄ 評価順）を切り替え、先頭にリセットする。 */
    fun toggleSortMode() {
        updateAndReload { it.copy(sortMode = it.sortMode.toggled(), currentIndex = 0) }
    }

    /** 並び順を直接指定する（並び順ダイアログから）。 */
    fun setSortMode(mode: SortMode) {
        if (_uiState.value.sortMode == mode) return
        updateAndReload { it.copy(sortMode = mode, currentIndex = 0) }
    }

    fun nextImage() = moveBy(1)

    fun prevImage() = moveBy(-1)

    fun selectIndex(index: Int) {
        val size = _uiState.value.visibleContents.size
        if (size == 0) return
        updateAndReload { it.copy(currentIndex = index.coerceIn(0, size - 1)) }
    }

    private fun moveBy(delta: Int) {
        val size = _uiState.value.visibleContents.size
        if (size == 0) return
        updateAndReload { it.copy(currentIndex = ((it.currentIndex + delta) % size + size) % size) }
    }

    /** 自動再生の開始／停止を切り替える。 */
    fun togglePlayPause() {
        val playing = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = playing) }
        if (playing) startAutoAdvance() else cancelAutoAdvance()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    // ---------------------------------------------------------------- //
    // 評価 / 削除 / 共有
    // ---------------------------------------------------------------- //

    /**
     * 現在表示中のコンテンツに5段階評価を設定する。
     * 0 を渡すと評価タグを取り除く（評価以外のタグは保持される）。
     */
    fun setCurrentContentRating(rating: Int) {
        val content = _uiState.value.currentContent ?: return
        val newTags = content.tagsWithRating(rating)

        _uiState.update { it.copy(isRatingLoading = true) }
        scope.launch {
            repository.setContentTags(content.folderId, content.contentId, newTags)
                .onSuccess { applyLocalTagUpdate(content.contentId, newTags) }
                .onFailure { e ->
                    _uiState.update { it.copy(isRatingLoading = false) }
                    failWith("評価の保存に失敗しました", e)
                }
        }
    }

    /** サーバー更新成功後、ローカル状態にもタグを反映して即時に見た目を更新する。 */
    private fun applyLocalTagUpdate(contentId: String, newTags: List<String>) {
        _uiState.update { state ->
            val updated = state.contents.map { c ->
                if (c.contentId == contentId) c.copy(tags = newTags) else c
            }
            val next = state.copy(
                isRatingLoading = false,
                contents = updated,
                groupedItems = ContentGrouping.groupByDate(updated),
            ).withVisibleContents()
            // 評価順では並びが変わるため、同じ写真を選択し続ける
            val index = next.visibleContents.indexOfFirst { it.contentId == contentId }
            if (index >= 0) next.copy(currentIndex = index) else next
        }
    }

    /** 現在表示中のコンテンツを削除し、同じ位置の次の写真へ移る。 */
    fun deleteCurrentContent() {
        val content = _uiState.value.currentContent ?: return
        scope.launch {
            repository.removeContents(content.folderId, listOf(content.contentId))
                .onSuccess { removeLocally(content.contentId) }
                .onFailure { e -> failWith("削除に失敗しました", e) }
        }
    }

    private fun removeLocally(contentId: String) {
        _uiState.update { state ->
            val remaining = state.contents.filterNot { it.contentId == contentId }
            val dates = ContentGrouping.availableDates(remaining)
            val next = state.copy(
                contents = remaining,
                groupedItems = ContentGrouping.groupByDate(remaining),
                availableDates = dates,
                selectedDate = state.selectedDate?.takeIf { it in dates } ?: dates.firstOrNull(),
                currentImageBytes = null,
                thumbnails = state.thumbnails - contentId,
            ).withVisibleContents()
            next.copy(
                currentIndex = next.currentIndex.coerceIn(
                    0,
                    (next.visibleContents.size - 1).coerceAtLeast(0),
                ),
            )
        }
        loadedContentId = null
        loadCurrentImage()
    }

    /** 共有用のダウンロードURL（事前署名済み・約10分有効）を取得する。 */
    fun requestShareUrl() {
        val content = _uiState.value.currentContent ?: return
        _uiState.update { it.copy(isShareLoading = true, shareUrl = null) }
        scope.launch {
            repository.getContentDownloadUrl(content.folderId, content.contentId)
                .onSuccess { url -> _uiState.update { it.copy(isShareLoading = false, shareUrl = url) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isShareLoading = false) }
                    failWith("共有URLの取得に失敗しました", e)
                }
        }
    }

    fun clearShareUrl() {
        _uiState.update { it.copy(shareUrl = null, isShareLoading = false) }
    }

    // ---------------------------------------------------------------- //
    // 選別
    // ---------------------------------------------------------------- //

    /** 選別を開始する。[date] を渡すとその日に限定する。 */
    fun startCulling(date: LocalDate? = null) {
        val contents = _uiState.value.contents
        val queue = ContentCulling.queue(contents, date)
        val startIndex = ContentCulling.firstUnjudgedIndex(queue)
        Log.i(
            TAG,
            "startCulling date=$date all=${contents.size} " +
                "visible=${_uiState.value.visibleContents.size} queue=${queue.size} " +
                "startIndex=$startIndex judged=${contents.count { it.isJudged() }} " +
                "queueDates=${queue.groupingBy { it.recordedLocalDate() }.eachCount()}",
        )
        cancelAutoAdvance()
        _uiState.update {
            it.copy(
                isPlaying = false,
                isCulling = true,
                cullQueue = queue,
                cullIndex = startIndex,
                cullDate = date,
            ).clearedImages()
        }
        loadedContentId = null
        loadCullImage()
    }

    /** 選別の対象を「選択中の日」と「全期間」で切り替える。 */
    fun toggleCullScope() {
        val state = _uiState.value
        startCulling(if (state.cullDate == null) state.selectedDate else null)
    }

    /** 選別を終了して通常表示へ戻る。 */
    fun exitCulling() {
        zoomLoadJob?.cancel()
        _uiState.update {
            it.copy(
                isCulling = false,
                cullQueue = emptyList(),
                cullIndex = 0,
            ).clearedImages()
        }
        loadedContentId = null
        loadCurrentImage()
    }

    /** 採用。スライドショーに出す評価を付けて次へ進む。 */
    fun cullPick() = judgeCurrentCull(ContentCulling.PICK)

    /** 見送り。削除はせず、評価だけ下げて次へ進む。 */
    fun cullSkip() = judgeCurrentCull(ContentCulling.SKIP)

    /** 判定せずに次の候補へ。 */
    fun cullNext() = moveCullBy(1)

    /** 判定せずに前の候補へ。 */
    fun cullPrev() = moveCullBy(-1)

    /**
     * 評価を付けて次へ進む。
     * サーバー送信の完了は待たず、状態だけ先に進める（連打で操作が詰まらないようにするため）。
     */
    private fun judgeCurrentCull(rating: Int) {
        val content = _uiState.value.cullContent ?: return
        val newTags = content.tagsWithRating(rating)
        enqueueTagUpdate(content.folderId, content.contentId, newTags)

        _uiState.update { state ->
            val updated = state.contents.map { c ->
                if (c.contentId == content.contentId) c.copy(tags = newTags) else c
            }
            state.copy(
                contents = updated,
                groupedItems = ContentGrouping.groupByDate(updated),
                cullIndex = state.cullIndex + 1,
            ).clearedImages().withVisibleContents()
        }
        loadedContentId = null
        loadCullImage()
    }

    private fun moveCullBy(delta: Int) {
        val state = _uiState.value
        val size = state.cullQueue.size
        if (size == 0) return
        val next = (state.cullIndex + delta).coerceIn(0, size - 1)
        if (next == state.cullIndex) return
        _uiState.update { it.copy(cullIndex = next).clearedImages() }
        loadedContentId = null
        loadCullImage()
    }

    /**
     * 拡大を 解除 → 等倍 → 200% → 400% → 解除 と循環させる。
     * リモコンでは割り当てられるキーが少ないため、1 キーで完結させる。
     */
    fun cycleZoom() {
        val state = _uiState.value
        if (state.cullContent == null) return
        val next = (state.zoomStep + 1) % ZOOM_MAGNIFICATIONS.size
        // 倍率を変えるたび中央に戻すと見ていた場所を見失うので、位置は保持する
        _uiState.update { it.copy(zoomStep = next) }
        if (next > 0 && state.zoomImageBytes == null) loadZoomImage()
    }

    /** 拡大を解除する（拡大中の「戻る」キー用）。 */
    fun zoomOff() {
        if (!_uiState.value.isZoomed) return
        _uiState.update { it.copy(zoomStep = 0, zoomCenterX = 0.5f, zoomCenterY = 0.5f) }
    }

    /**
     * 拡大中の表示位置を動かす。割合なので、倍率が高いほど 1 回の移動量を小さくする。
     * こうしないと 400% では一押しで画面外まで飛んでしまう。
     */
    fun panZoom(dx: Int, dy: Int) {
        val state = _uiState.value
        if (!state.isZoomed) return
        val step = PAN_STEP_RATIO / state.zoomMagnification.coerceAtLeast(1f)
        _uiState.update {
            it.copy(
                zoomCenterX = (it.zoomCenterX + dx * step).coerceIn(0f, 1f),
                zoomCenterY = (it.zoomCenterY + dy * step).coerceIn(0f, 1f),
            )
        }
    }

    // ---------------------------------------------------------------- //
    // 比較
    // ---------------------------------------------------------------- //

    /**
     * 見比べを開く。現在表示中の一覧をそのまま候補にする。
     * どれを見比べるかはユーザーがチェックで選ぶので、枚数は制限しない。
     */
    fun startCompare() {
        val state = _uiState.value
        val source = if (state.isCulling) state.cullQueue else state.visibleContents
        val anchor = if (state.isCulling) state.cullIndex else state.currentIndex
        if (source.isEmpty()) return

        val current = source.getOrNull(anchor)
        _uiState.update {
            it.copy(
                compareContents = source,
                compareIndex = anchor.coerceIn(0, source.lastIndex),
                // 開いた写真は最初から選ばれている方が自然
                compareSelected = setOfNotNull(current?.contentId),
                compareImages = emptyMap(),
            )
        }
        loadCompareImages(listOfNotNull(current))
    }

    fun exitCompare() {
        compareLoadJob?.cancel()
        _uiState.update {
            it.copy(
                compareContents = emptyList(),
                compareSelected = emptySet(),
                compareImages = emptyMap(),
            )
        }
    }

    fun compareMoveBy(delta: Int) {
        val size = _uiState.value.compareContents.size
        if (size == 0) return
        val next = ((_uiState.value.compareIndex + delta) % size + size) % size
        _uiState.update { it.copy(compareIndex = next) }
        // フォーカスしたものはチェック前でも見せる必要がある
        loadCompareImages(listOfNotNull(_uiState.value.compareContent))
    }

    /** 見比べに含めるかを切り替える。 */
    fun toggleCompareSelection() {
        val content = _uiState.value.compareContent ?: return
        _uiState.update {
            val selected = if (content.contentId in it.compareSelected) {
                it.compareSelected - content.contentId
            } else {
                it.compareSelected + content.contentId
            }
            it.copy(compareSelected = selected)
        }
        loadCompareImages(_uiState.value.comparePicked)
    }

    fun toggleCompareLayout() {
        _uiState.update {
            it.copy(
                compareLayout = if (it.compareLayout == CompareLayout.Stack) {
                    CompareLayout.SideBySide
                } else {
                    CompareLayout.Stack
                },
            )
        }
    }

    /** 比較中の写真を採用する。残りを見送りにするのはやりすぎなのでしない。 */
    fun comparePick() {
        val content = _uiState.value.compareContent ?: return
        applyRating(content.contentId, ContentCulling.PICK)
    }

    fun compareSkip() {
        val content = _uiState.value.compareContent ?: return
        applyRating(content.contentId, ContentCulling.SKIP)
    }

    /** 比較は複数枚を同時に持つので、原寸ではなく軽い方を使う。 */
    private fun loadCompareImages(targets: List<Content>) {
        compareLoadJob?.cancel()
        compareLoadJob = scope.launch {
            targets.forEach { content ->
                if (_uiState.value.compareImages.containsKey(content.contentId)) return@forEach
                val bytes = CULL_PREVIEW_KINDS.firstNotNullOfOrNull { kind ->
                    repository.getContentBinary(content.folderId, content.contentId, kind).getOrNull()
                } ?: return@forEach
                _uiState.update { it.copy(compareImages = it.compareImages + (content.contentId to bytes)) }
            }
        }
    }

    // ---------------------------------------------------------------- //
    // 選別結果の見直し
    // ---------------------------------------------------------------- //

    /** 見直し画面で表示する側（採用 / 見送り）を切り替える。 */
    fun setCullReviewFilter(filter: CullFilter) {
        _uiState.update { it.copy(cullReviewFilter = filter) }
    }

    /** 判定を取り消して未判定に戻す。次の選別で再び出てくる。 */
    fun revertCullJudgement(contentId: String) = applyRating(contentId, ContentRating.NONE)

    /** 判定を反対側へ切り替える（採用 ⇄ 見送り）。 */
    fun flipCullJudgement(contentId: String) {
        val current = _uiState.value.contents.firstOrNull { it.contentId == contentId } ?: return
        val next =
            if (current.ratingValue() == ContentCulling.PICK) ContentCulling.SKIP
            else ContentCulling.PICK
        applyRating(contentId, next)
    }

    /** 楽観的に評価を変え、送信はキューに任せる。 */
    private fun applyRating(contentId: String, rating: Int) {
        val content = _uiState.value.contents.firstOrNull { it.contentId == contentId } ?: return
        val newTags = content.tagsWithRating(rating)
        enqueueTagUpdate(content.folderId, content.contentId, newTags)
        _uiState.update { state ->
            val updated = state.contents.map { c ->
                if (c.contentId == contentId) c.copy(tags = newTags) else c
            }
            state.copy(
                contents = updated,
                groupedItems = ContentGrouping.groupByDate(updated),
            ).withVisibleContents()
        }
    }

    /**
     * タグ更新をキューへ積み、単一のコルーチンで順に送信する。
     * 選別中は 1 キー押下ごとに更新が発生するため、直列化しないと順序が乱れる。
     */
    private fun enqueueTagUpdate(folderId: String, contentId: String, tags: List<String>) {
        pendingTagUpdates.addLast(TagUpdate(folderId, contentId, tags))
        if (tagSyncJob?.isActive == true) return
        tagSyncJob = scope.launch {
            while (true) {
                val update = pendingTagUpdates.removeFirstOrNull() ?: break
                repository.setContentTags(update.folderId, update.contentId, update.tags)
                    .onSuccess {
                        Log.i(TAG, "tag sync ok cid=${update.contentId} tags=${update.tags}")
                    }
                    .onFailure { e ->
                        // 黙って落とすと、画面上だけ判定済みに見えて再起動で全部未判定に戻る
                        Log.w(TAG, "tag sync failed cid=${update.contentId} tags=${update.tags}", e)
                        _uiState.update { it.copy(notice = "評価の保存に失敗しました") }
                    }
            }
        }
    }

    private fun loadZoomImage() {
        val content = _uiState.value.cullContent ?: return
        zoomLoadJob?.cancel()
        zoomLoadJob = scope.launch {
            _uiState.update { it.copy(isZoomLoading = true) }
            repository.getContentBinary(content.folderId, content.contentId)
                .onSuccess { bytes ->
                    Log.i(TAG, "zoom source cid=${content.contentId} bytes=${bytes.size}")
                    _uiState.update { it.copy(isZoomLoading = false, zoomImageBytes = bytes) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isZoomLoading = false, zoomStep = 0) }
                    failWith("原寸画像の読み込みに失敗しました", e)
                }
        }
    }

    /**
     * 選別中のプレビュー画像を読み込む。
     * 1 枚ごとに原寸を落とすと送りが止まるため、まず [CULL_PREVIEW_KINDS] の軽い方を試す。
     */
    private fun loadCullImage() {
        val content = _uiState.value.cullContent ?: return
        if (content.contentId == loadingContentId && imageLoadJob?.isActive == true) return

        imageLoadJob?.cancel()
        loadingContentId = content.contentId
        imageLoadJob = scope.launch {
            _uiState.update { it.copy(isImageLoading = true) }
            val bytes = CULL_PREVIEW_KINDS.firstNotNullOfOrNull { kind ->
                repository.getContentBinary(content.folderId, content.contentId, kind).getOrNull()
            }
            if (bytes == null) {
                _uiState.update { it.copy(isImageLoading = false) }
                Log.w(TAG, "cull preview unavailable cid=${content.contentId}")
            } else {
                loadedContentId = content.contentId
                _uiState.update { it.copy(isImageLoading = false, currentImageBytes = bytes) }
            }
            loadingContentId = null
        }
    }

    // ---------------------------------------------------------------- //
    // サムネイル
    // ---------------------------------------------------------------- //

    /**
     * 指定したコンテンツのサムネイルをまとめて読み込む。
     * 未取得のものだけを [THUMBNAIL_CONCURRENCY] 件ずつ並列取得し、バッチ単位で状態へ反映する。
     *
     * 選別キューや選別結果は表示中一覧と一致しないので、周りから対象を渡してもらう。
     */
    fun loadThumbnails(targets: List<Content> = _uiState.value.visibleContents) {
        if (targets.isEmpty()) return
        scope.launch {
            targets.filterNot { it.contentId in _uiState.value.thumbnails }
                .chunked(THUMBNAIL_CONCURRENCY)
                .forEach { batch ->
                    val loaded = coroutineScope {
                        batch.map { content ->
                            async { content.contentId to loadThumbnailBytes(content) }
                        }.awaitAll()
                    }.mapNotNull { (id, bytes) -> bytes?.let { id to it } }

                    if (loaded.isNotEmpty()) {
                        _uiState.update { it.copy(thumbnails = it.thumbnails + loaded) }
                    }
                }
        }
    }

    /**
     * 1件分のサムネイルを取得する。
     * レスポンス重視で低解像度から順に試し、すべて失敗したら null。
     */
    private suspend fun loadThumbnailBytes(content: Content): ByteArray? {
        for (kind in THUMBNAIL_KINDS) {
            repository.getContentBinary(content.folderId, content.contentId, kind)
                .onSuccess { return it }
        }
        Log.w(TAG, "thumbnail load failed cid=${content.contentId}")
        return null
    }

    // ---------------------------------------------------------------- //
    // 内部ヘルパー
    // ---------------------------------------------------------------- //

    /** 選択状態を変更し、画像の再読み込みと自動再生タイマーの再設定を行う。 */
    private fun updateAndReload(transform: (SlideshowUiState) -> SlideshowUiState) {
        val previousId = _uiState.value.currentContent?.contentId
        _uiState.update { state ->
            val next = transform(state).withVisibleContents()
            // 表示対象が変わっていなければ取得済みの画像をそのまま使う
            if (next.currentContent?.contentId == previousId) next else next.copy(currentImageBytes = null)
        }
        loadCurrentImage()
        restartAutoAdvance()
    }

    /** [SlideshowUiState.visibleContents] を並び順・選択日から再計算する。 */
    private fun SlideshowUiState.withVisibleContents(): SlideshowUiState =
        copy(visibleContents = ContentGrouping.visibleContents(contents, sortMode, selectedDate))

    /** 写真を切り替える際に、前の画像と拡大状態を落とす。 */
    private fun SlideshowUiState.clearedImages(): SlideshowUiState =
        copy(
            currentImageBytes = null,
            zoomImageBytes = null,
            zoomStep = 0,
            zoomCenterX = 0.5f,
            zoomCenterY = 0.5f,
            isZoomLoading = false,
        )

    private fun loadCurrentImage() {
        val state = _uiState.value
        val content = state.currentContent ?: return
        if (content.contentId == loadedContentId && state.currentImageBytes != null) return
        // 取得中に再度呼ばれても取り消さない。原寸は数秒かかるので捨てると丸ごと無駄になる
        if (content.contentId == loadingContentId && imageLoadJob?.isActive == true) return

        imageLoadJob?.cancel()
        loadingContentId = content.contentId
        imageLoadJob = scope.launch {
            _uiState.update { it.copy(isImageLoading = true) }
            repository.getContentBinary(content.folderId, content.contentId)
                .onSuccess { bytes ->
                    loadedContentId = content.contentId
                    _uiState.update { it.copy(isImageLoading = false, currentImageBytes = bytes) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isImageLoading = false) }
                    failWith("画像の読み込みに失敗しました", e)
                }
            loadingContentId = null
        }
    }

    private fun startAutoAdvance() {
        cancelAutoAdvance()
        autoAdvanceJob = scope.launch {
            while (true) {
                delay(SLIDESHOW_INTERVAL_MS)
                if (!_uiState.value.isPlaying) break
                moveBy(1)
            }
        }
    }

    private fun restartAutoAdvance() {
        if (_uiState.value.isPlaying) startAutoAdvance()
    }

    private fun cancelAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
    }

    private fun failWith(userMessage: String, cause: Throwable) {
        Log.e(TAG, "$userMessage: ${cause.message}", cause)
        val message = if (cause.isCertificateFailure()) {
            "$userMessage\n\nテレビの日付と時刻が合っているか確かめてください。" +
                "ずれているとサーバーの証明書を検証できず接続できません。"
        } else {
            userMessage
        }
        _uiState.update { it.copy(isLoading = false, error = message) }
    }

    override fun onCleared() {
        super.onCleared()
        cancelAutoAdvance()
        zoomLoadJob?.cancel()
    }

    companion object {
        private const val TAG = "SlideshowVM"

        const val SLIDESHOW_INTERVAL_MS = 5000L
        const val THUMBNAIL_CONCURRENCY = 4

        /**
         * サムネイルに使う解像度。レスポンス重視で低解像度から順に試す。
         *   thumbnail_400 : 長辺400px（最小・最速）
         *   thumbnail_1024: 長辺1024px（フォールバック）
         *   original      : 最終フォールバック
         */
        val THUMBNAIL_KINDS = listOf("thumbnail_400", "thumbnail_1024", "original")

        /**
         * 選別中のプレビューに使う解像度。
         * 全画面表示には thumbnail_1920 で十分で、原寸は拡大時にのみ取りに行く。
         */
        val CULL_PREVIEW_KINDS = listOf("thumbnail_1920", "original")

        /**
         * 拡大の段階。先頭の 0 は「拡大なし」を表す。
         * 値は「画面ピクセル ÷ 元画像ピクセル」で、1.0 が等倍（100%）。
         */
        val ZOOM_MAGNIFICATIONS = listOf(0f, 1f, 2f, 4f)

        /** 等倍時に方向キー 1 回で動く割合。倍率に反比例させて使う。 */
        const val PAN_STEP_RATIO = 0.12f

        fun factory(repository: ImagingEdgeRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SlideshowViewModel(repository) }
            }
    }
}
