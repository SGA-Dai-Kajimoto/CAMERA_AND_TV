package com.sony.dtv.camera_tv.ui.slideshow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.model.ratingValue
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * コンテンツの並び順。
 *  - DateDesc  : 日付グループ表示（既定）
 *  - RatingDesc: 全コンテンツを評価の高い順にフラット表示
 */
enum class SortMode { DateDesc, RatingDesc }

data class SlideshowUiState(
    val isLoading: Boolean = false,
    val contents: List<Content> = emptyList(),
    val groupedItems: List<ContentListItem> = emptyList(),
    val availableDates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val currentImageBytes: ByteArray? = null,
    val isImageLoading: Boolean = false,
    val error: String? = null,
    val thumbnails: Map<String, ByteArray> = emptyMap(),
    val shareUrl: String? = null,
    val isShareLoading: Boolean = false,
    val currentContentRating: Int = 0,
    val isRatingLoading: Boolean = false,
    val sortMode: SortMode = SortMode.DateDesc,
)

class SlideshowViewModel(
    private val repository: ImagingEdgeRepository,
    private val externalScope: CoroutineScope? = null,
) : ViewModel() {

    private val scope: CoroutineScope get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(SlideshowUiState())
    val uiState: StateFlow<SlideshowUiState> = _uiState.asStateFlow()

    private var autoAdvanceJob: Job? = null
    private var imageLoadJob: Job? = null
    private var loadedContentId: String? = null

    init {
        loadContents()
    }

    fun loadContents() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.listAllContents()
                .onSuccess { contents ->
                    val grouped = groupByDate(contents)
                    val dates = extractAvailableDates(contents)
                    val latestDate = dates.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            contents = contents,
                            groupedItems = grouped,
                            availableDates = dates,
                            selectedDate = latestDate,
                            currentIndex = 0,
                        )
                    }
                    if (contents.isNotEmpty()) {
                        loadCurrentImage()
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun loadMoreContents() {
        // All contents are loaded at once from all folders; no pagination needed.
    }

    fun selectDate(date: LocalDate) {
        _uiState.update {
            it.copy(
                selectedDate = date,
                currentIndex = 0,
                currentImageBytes = null,
                sortMode = SortMode.DateDesc,
            )
        }
        loadCurrentImage()
        restartAutoAdvance()
    }

    /**
     * 並び順（日付順／評価順）を切り替える。
     * 切り替え後は先頭にリセットして画像を再読み込みする。
     */
    fun toggleSortMode() {
        val next = when (_uiState.value.sortMode) {
            SortMode.DateDesc -> SortMode.RatingDesc
            SortMode.RatingDesc -> SortMode.DateDesc
        }
        _uiState.update {
            it.copy(sortMode = next, currentIndex = 0, currentImageBytes = null)
        }
        loadCurrentImage()
        restartAutoAdvance()
    }

    fun nextImage() {
        val currentDateContents = currentDateContents()
        val size = currentDateContents.size
        if (size == 0) return
        _uiState.update {
            it.copy(
                currentIndex = (it.currentIndex + 1) % size,
                currentImageBytes = null,
            )
        }
        loadCurrentImage()
        restartAutoAdvance()
    }

    fun prevImage() {
        val currentDateContents = currentDateContents()
        val size = currentDateContents.size
        if (size == 0) return
        _uiState.update {
            it.copy(
                currentIndex = (it.currentIndex - 1 + size) % size,
                currentImageBytes = null,
            )
        }
        loadCurrentImage()
        restartAutoAdvance()
    }

    fun togglePlayPause() {
        val isNowPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = isNowPlaying) }
        if (isNowPlaying) startAutoAdvance() else cancelAutoAdvance()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 現在の日付グループのサムネイルを読み込む。
     * 低解像度(thumbnail_400)を優先取得し、複数を並列で読み込んで表示を高速化する。
     */
    fun loadThumbnails() {
        val dateContents = currentDateContents()
        if (dateContents.isEmpty()) return
        scope.launch {
            val pending = dateContents.filter { !_uiState.value.thumbnails.containsKey(it.contentId) }
            pending.chunked(THUMBNAIL_CONCURRENCY).forEach { batch ->
                val results = batch.map { content ->
                    async { content.contentId to loadThumbnailBytes(content) }
                }.awaitAll()
                val newThumbs = _uiState.value.thumbnails.toMutableMap()
                var changed = false
                for ((id, bytes) in results) {
                    if (bytes != null) {
                        newThumbs[id] = bytes
                        changed = true
                    }
                }
                if (changed) _uiState.update { it.copy(thumbnails = newThumbs.toMap()) }
            }
        }
    }

    /**
     * 1コンテンツのサムネイルバイトを取得する。
     * レスポンス重視で低解像度から順に試行する:
     *   thumbnail_400(長辺400px) → thumbnail_1024(長辺1024px) → original。
     * すべて失敗したら null。
     */
    private suspend fun loadThumbnailBytes(content: Content): ByteArray? {
        for (kind in THUMBNAIL_KINDS) {
            repository.getContentBinary(content.folderId, content.contentId, kind = kind)
                .onSuccess { return it }
        }
        android.util.Log.e("SlideshowVM", "thumbnail load failed cid=${content.contentId} (tried ${THUMBNAIL_KINDS})")
        return null
    }

    /**
     * 指定インデックスのコンテンツに移動する。
     */
    fun selectIndex(index: Int) {
        val dateContents = currentDateContents()
        if (dateContents.isEmpty()) return
        val newIndex = index.coerceIn(0, dateContents.size - 1)
        _uiState.update { it.copy(currentIndex = newIndex, currentImageBytes = null) }
        loadCurrentImage()
        restartAutoAdvance()
    }

    /**
     * 現在表示中のコンテンツにお気に入りタグを付与する。
     */
    fun toggleFavorite() {
        val dateContents = currentDateContents()
        if (dateContents.isEmpty()) return
        val state = _uiState.value
        val index = state.currentIndex.coerceIn(0, dateContents.size - 1)
        val content = dateContents[index]
        scope.launch {
            repository.setContentTags(content.folderId, content.contentId, listOf("favorite:1"))
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    /**
     * 現在表示中のコンテンツを削除し、次の写真に自動遷移する。
     */
    fun deleteCurrentContent() {
        val dateContents = currentDateContents()
        if (dateContents.isEmpty()) return
        val state = _uiState.value
        val index = state.currentIndex.coerceIn(0, dateContents.size - 1)
        val content = dateContents[index]
        scope.launch {
            repository.removeContents(content.folderId, listOf(content.contentId))
                .onSuccess {
                    // 削除後にコンテンツリストを再構築
                    val newContents = state.contents.filter { it.contentId != content.contentId }
                    val grouped = groupByDate(newContents)
                    val dates = extractAvailableDates(newContents)
                    val selectedDate = if (dates.contains(state.selectedDate)) state.selectedDate else dates.firstOrNull()
                    val newDateContents = newContents.filter { extractDate(it) == selectedDate }
                    val newIndex = if (newDateContents.isEmpty()) 0
                        else state.currentIndex.coerceIn(0, newDateContents.size - 1)
                    _uiState.update {
                        it.copy(
                            contents = newContents,
                            groupedItems = grouped,
                            availableDates = dates,
                            selectedDate = selectedDate,
                            currentIndex = newIndex,
                            currentImageBytes = null,
                        )
                    }
                    if (newDateContents.isNotEmpty()) loadCurrentImage()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    /**
     * 現在表示中のコンテンツの共有用ダウンロードURL（事前署名済み・認証不要・600秒有効）を取得する。
     * 取得した URL は shareUrl に格納され、QRコード表示に使う。
     */
    fun requestShareUrl() {
        val dateContents = currentDateContents()
        if (dateContents.isEmpty()) return
        val state = _uiState.value
        val index = state.currentIndex.coerceIn(0, dateContents.size - 1)
        val content = dateContents[index]
        _uiState.update { it.copy(isShareLoading = true, shareUrl = null) }
        scope.launch {
            repository.getContentDownloadUrl(content.folderId, content.contentId, kind = "original")
                .onSuccess { url ->
                    _uiState.update { it.copy(isShareLoading = false, shareUrl = url) }
                }
                .onFailure { e ->
                    android.util.Log.e("SlideshowVM", "share url failed: ${e.message}")
                    _uiState.update { it.copy(isShareLoading = false, error = e.message) }
                }
        }
    }

    /**
     * 共有URL（QR）表示を閉じる。
     */
    fun clearShareUrl() {
        _uiState.update { it.copy(shareUrl = null, isShareLoading = false) }
    }

    /**
     * 現在選択中の日付グループに属するコンテンツ一覧を返す。
     */
    fun currentDateContents(): List<Content> {
        val state = _uiState.value
        if (state.sortMode == SortMode.RatingDesc) {
            // 評価の高い順（同評価は新しい日付順）で全コンテンツをフラット表示
            return state.contents.sortedWith(
                compareByDescending<Content> { it.ratingValue() }
                    .thenByDescending { extractDate(it)?.toEpochDay() ?: Long.MIN_VALUE }
            )
        }
        val selectedDate = state.selectedDate ?: return state.contents
        return state.contents.filter { extractDate(it) == selectedDate }
    }

    // ---------------------------------------------------------------- //
    // グルーピングロジック
    // ---------------------------------------------------------------- //

    private fun groupByDate(contents: List<Content>): List<ContentListItem> {
        val displayFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

        return contents
            .sortedByDescending { extractDate(it)?.toEpochDay() ?: Long.MIN_VALUE }
            .groupBy { extractDate(it) }
            .flatMap { (date, items) ->
                val header = ContentListItem.DateHeader(
                    date = date ?: LocalDate.MIN,
                    label = date?.format(displayFormatter) ?: "日付不明",
                )
                listOf(header) + items.map { ContentListItem.ContentItem(it) }
            }
    }

    private fun extractAvailableDates(contents: List<Content>): List<LocalDate> {
        return contents
            .mapNotNull { extractDate(it) }
            .distinct()
            .sortedDescending()
    }

    private fun extractDate(content: Content): LocalDate? {
        content.recordedDate?.let {
            return try {
                OffsetDateTime.parse(it).toLocalDate()
            } catch (_: Exception) { null }
        }
        content.recordedDateLocalTime?.let {
            return try {
                LocalDateTime.parse(it).toLocalDate()
            } catch (_: Exception) { null }
        }
        content.createdDate?.let {
            return try {
                OffsetDateTime.parse(it).toLocalDate()
            } catch (_: Exception) { null }
        }
        return null
    }

    // ---------------------------------------------------------------- //
    // Private helpers
    // ---------------------------------------------------------------- //

    private fun loadCurrentImage() {
        val dateContents = currentDateContents()
        if (dateContents.isEmpty()) return
        val state = _uiState.value
        val index = state.currentIndex.coerceIn(0, dateContents.size - 1)
        val content = dateContents[index]
        // Skip if already loaded or loading the same content
        if (content.contentId == loadedContentId && state.currentImageBytes != null) return
        imageLoadJob?.cancel()
        imageLoadJob = scope.launch {
            _uiState.update {
                it.copy(isImageLoading = true, currentContentRating = content.ratingValue())
            }
            repository.getContentBinary(content.folderId, content.contentId, kind = "original")
                .onSuccess { bytes ->
                    android.util.Log.d("SlideshowVM", "Image loaded: ${bytes.size} bytes, contentId=${content.contentId}")
                    loadedContentId = content.contentId
                    _uiState.update { it.copy(isImageLoading = false, currentImageBytes = bytes) }
                }
                .onFailure { e ->
                    android.util.Log.e("SlideshowVM", "Image load failed: ${e.message}")
                    _uiState.update { it.copy(isImageLoading = false, error = e.message) }
                }
        }
    }

    private fun startAutoAdvance() {
        cancelAutoAdvance()
        autoAdvanceJob = scope.launch {
            while (true) {
                delay(SLIDESHOW_INTERVAL_MS)
                val state = _uiState.value
                val dateContents = currentDateContents()
                if (state.isPlaying && dateContents.isNotEmpty()) {
                    val newIndex = (state.currentIndex + 1) % dateContents.size
                    _uiState.update { it.copy(currentIndex = newIndex, currentImageBytes = null) }
                    loadCurrentImage()
                }
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

    override fun onCleared() {
        super.onCleared()
        cancelAutoAdvance()
    }

    // ---------------------------------------------------------------- //
    // お気に入り（5段階評価）
    // ---------------------------------------------------------------- //

    /**
     * 現在表示中のコンテンツに5段階評価を設定する。
     * @param rating 評価（0〜5、0は評価なし＝rating タグを削除）
     */
    fun setCurrentContentRating(rating: Int) {
        val dateContents = currentDateContents()
        if (dateContents.isEmpty()) return
        val state = _uiState.value
        val index = state.currentIndex.coerceIn(0, dateContents.size - 1)
        val content = dateContents[index]
        val clamped = rating.coerceIn(0, 5)

        // 既存タグから rating:* を除去し、1..5 のときのみ rating:N を付与する（0 は評価なし）
        val newTags = content.tags.orEmpty().filterNot { it.startsWith("rating:") } +
            if (clamped in 1..5) listOf("rating:$clamped") else emptyList()

        _uiState.update { it.copy(isRatingLoading = true) }
        scope.launch {
            repository.setContentTags(content.folderId, content.contentId, newTags)
                .onSuccess {
                    // ローカルの tags を更新して即時反映（★バッジ・評価順ソートに反映）
                    val updatedContents = _uiState.value.contents.map { c ->
                        if (c.contentId == content.contentId) c.copy(tags = newTags) else c
                    }
                    _uiState.update {
                        it.copy(
                            isRatingLoading = false,
                            currentContentRating = clamped,
                            contents = updatedContents,
                            groupedItems = groupByDate(updatedContents),
                        )
                    }
                    // 評価順表示では再ソート後も同じ写真を選択し続ける
                    val newIndex = currentDateContents().indexOfFirst { it.contentId == content.contentId }
                    if (newIndex >= 0) {
                        _uiState.update { it.copy(currentIndex = newIndex) }
                    }
                    android.util.Log.d("SlideshowVM", "Rating set: $clamped for contentId=${content.contentId}")
                }
                .onFailure { e ->
                    android.util.Log.e("SlideshowVM", "Rating failed: ${e.message}")
                    _uiState.update { it.copy(isRatingLoading = false, error = e.message) }
                }
        }
    }

    /**
     * 高評価（4〜5）のコンテンツのみを表示するフィルタを適用する。
     */
    fun filterByHighRating() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // 全フォルダから評価4以上のコンテンツを取得（最初のフォルダから）
            val folders = repository.listFolders().getOrNull() ?: emptyList()
            val firstFolder = folders.firstOrNull()
            if (firstFolder != null) {
                repository.listContentsWithRatingFilter(firstFolder.folderId, minRating = 4)
                    .onSuccess { contents ->
                        val grouped = groupByDate(contents)
                        val dates = extractAvailableDates(contents)
                        val latestDate = dates.firstOrNull()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                contents = contents,
                                groupedItems = grouped,
                                availableDates = dates,
                                selectedDate = latestDate,
                                currentIndex = 0,
                            )
                        }
                        if (contents.isNotEmpty()) {
                            loadCurrentImage()
                        }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
            }
        }
    }

    companion object {
        const val SLIDESHOW_INTERVAL_MS = 5000L
        const val THUMBNAIL_CONCURRENCY = 4

        // サムネイルに使う解像度。レスポンス重視で低解像度から順に試す。
        //   thumbnail_400 : 長辺400px / quality80 / Exif除去 / 自動回転（最小・最速）
        //   thumbnail_1024: 長辺1024px（thumbnail_400 が無い場合のフォールバック）
        //   original      : 上記が無い場合の最終フォールバック
        val THUMBNAIL_KINDS = listOf("thumbnail_400", "thumbnail_1024", "original")

        fun factory(
            repository: ImagingEdgeRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SlideshowViewModel(repository) }
        }
    }
}
