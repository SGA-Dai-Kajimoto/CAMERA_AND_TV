package com.sony.dtv.camera_tv.ui.slideshow

import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import com.sony.dtv.camera_tv.domain.ContentCulling
import com.sony.dtv.camera_tv.domain.ContentRating
import com.sony.dtv.camera_tv.domain.CullFilter
import com.sony.dtv.camera_tv.domain.SortMode
import com.sony.dtv.camera_tv.domain.ratingValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private lateinit var repository: ImagingEdgeRepository

    private val day1 = LocalDate.of(2026, 5, 1)
    private val day2 = LocalDate.of(2026, 5, 2)

    private fun content(id: String, date: String, rating: Int? = null) = Content(
        contentId = id,
        recordedDate = "${date}T12:00:00+09:00",
        tags = rating?.let { listOf(ContentRating.toTag(it)) },
        folderId = "f1",
    )

    /** day2: b(★5) / day1: a(★1), c(★3) */
    private val contents = listOf(
        content("a", "2026-05-01", rating = 1),
        content("b", "2026-05-02", rating = 5),
        content("c", "2026-05-01", rating = 3),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.listAllContents() } returns Result.success(contents)
        coEvery { repository.getContentBinary(any(), any(), any()) } returns
            Result.success(byteArrayOf(1, 2, 3))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = SlideshowViewModel(repository, scope)

    // ---------------------------------------------------------------- //
    // 初期読み込み
    // ---------------------------------------------------------------- //

    @Test
    fun `初期化でコンテンツを読み込み最新日を選択する`() = runTest(dispatcher) {
        val state = createViewModel().uiState.value

        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(3, state.contents.size)
        assertEquals(listOf(day2, day1), state.availableDates)
        assertEquals(day2, state.selectedDate)
        assertTrue(state.hasContents)
    }

    @Test
    fun `初期読み込み後に現在の画像が取得される`() = runTest(dispatcher) {
        val state = createViewModel().uiState.value

        assertNotNull(state.currentImageBytes)
        assertFalse(state.isImageLoading)
        coVerify { repository.getContentBinary("f1", "b", any()) }
    }

    @Test
    fun `読み込み失敗時はエラーメッセージが入る`() = runTest(dispatcher) {
        coEvery { repository.listAllContents() } returns Result.failure(IOException("boom"))

        val state = createViewModel().uiState.value

        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertFalse(state.hasContents)
    }

    @Test
    fun `clearError でエラーが消える`() = runTest(dispatcher) {
        coEvery { repository.listAllContents() } returns Result.failure(IOException("boom"))
        val vm = createViewModel()

        vm.clearError()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `コンテンツが空でもクラッシュしない`() = runTest(dispatcher) {
        coEvery { repository.listAllContents() } returns Result.success(emptyList())
        val vm = createViewModel()

        vm.nextImage()
        vm.prevImage()
        vm.selectIndex(3)

        val state = vm.uiState.value
        assertFalse(state.hasContents)
        assertNull(state.currentContent)
        assertEquals(ContentRating.NONE, state.currentRating)
    }

    // ---------------------------------------------------------------- //
    // 日付選択 / 並び替え
    // ---------------------------------------------------------------- //

    @Test
    fun `visibleContents は選択日で絞り込まれる`() = runTest(dispatcher) {
        val vm = createViewModel()
        assertEquals(listOf("b"), vm.uiState.value.visibleContents.map { it.contentId })

        vm.selectDate(day1)

        val state = vm.uiState.value
        assertEquals(day1, state.selectedDate)
        assertEquals(listOf("a", "c"), state.visibleContents.map { it.contentId })
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `toggleSortMode で評価順の全件表示になる`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.toggleSortMode()

        val state = vm.uiState.value
        assertEquals(SortMode.RatingDesc, state.sortMode)
        assertEquals(listOf("b", "c", "a"), state.visibleContents.map { it.contentId })
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `toggleSortMode をもう一度呼ぶと日付順に戻る`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.toggleSortMode()
        vm.toggleSortMode()

        assertEquals(SortMode.DateDesc, vm.uiState.value.sortMode)
        assertEquals(listOf("b"), vm.uiState.value.visibleContents.map { it.contentId })
    }

    // ---------------------------------------------------------------- //
    // ナビゲーション
    // ---------------------------------------------------------------- //

    @Test
    fun `next と prev は端で巻き戻る`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.selectDate(day1) // a, c の 2 件

        vm.nextImage()
        assertEquals(1, vm.uiState.value.currentIndex)

        vm.nextImage()
        assertEquals(0, vm.uiState.value.currentIndex)

        vm.prevImage()
        assertEquals(1, vm.uiState.value.currentIndex)
    }

    @Test
    fun `selectIndex は範囲内に丸められる`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.selectDate(day1)

        vm.selectIndex(99)
        assertEquals(1, vm.uiState.value.currentIndex)

        vm.selectIndex(-5)
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    @Test
    fun `togglePlayPause で再生状態が切り替わる`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.togglePlayPause()
        assertTrue(vm.uiState.value.isPlaying)

        vm.togglePlayPause()
        assertFalse(vm.uiState.value.isPlaying)
    }

    // ---------------------------------------------------------------- //
    // 評価
    // ---------------------------------------------------------------- //

    @Test
    fun `評価設定は既存タグを保持したままサーバーへ送られる`() = runTest(dispatcher) {
        val withTags = listOf(
            content("a", "2026-05-01").copy(tags = listOf("family", "rating:1")),
        )
        coEvery { repository.listAllContents() } returns Result.success(withTags)
        coEvery { repository.setContentTags(any(), any(), any()) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.setCurrentContentRating(4)

        coVerify { repository.setContentTags("f1", "a", listOf("family", "rating:4")) }
        assertEquals(4, vm.uiState.value.currentRating)
        assertFalse(vm.uiState.value.isRatingLoading)
    }

    @Test
    fun `評価 0 で評価タグが取り除かれる`() = runTest(dispatcher) {
        coEvery { repository.setContentTags(any(), any(), any()) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.setCurrentContentRating(0)

        // 空配列は API が 400 で拒否するので rating:0 を残す
        coVerify { repository.setContentTags("f1", "b", listOf("rating:0")) }
        assertEquals(ContentRating.NONE, vm.uiState.value.currentRating)
    }

    @Test
    fun `評価順のまま評価を変えても同じ写真を選択し続ける`() = runTest(dispatcher) {
        coEvery { repository.setContentTags(any(), any(), any()) } returns Result.success(Unit)
        val vm = createViewModel()
        vm.toggleSortMode() // b(5), c(3), a(1)

        vm.setCurrentContentRating(1) // b が最下位へ移動する

        val state = vm.uiState.value
        assertEquals("b", state.currentContent?.contentId)
        assertEquals(state.visibleContents.indexOfFirst { it.contentId == "b" }, state.currentIndex)
    }

    @Test
    fun `評価の保存に失敗したらエラーになりローディングも解除される`() = runTest(dispatcher) {
        coEvery { repository.setContentTags(any(), any(), any()) } returns
            Result.failure(IOException("boom"))
        val vm = createViewModel()

        vm.setCurrentContentRating(5)

        val state = vm.uiState.value
        assertNotNull(state.error)
        assertFalse(state.isRatingLoading)
    }

    // ---------------------------------------------------------------- //
    // 削除
    // ---------------------------------------------------------------- //

    @Test
    fun `削除に成功するとローカル状態からも取り除かれる`() = runTest(dispatcher) {
        coEvery { repository.removeContents(any(), any()) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.deleteCurrentContent()

        val state = vm.uiState.value
        coVerify { repository.removeContents("f1", listOf("b")) }
        assertEquals(listOf("a", "c"), state.contents.map { it.contentId })
        // 2026-05-02 のコンテンツが無くなるので選択日も更新される
        assertEquals(day1, state.selectedDate)
        assertEquals(listOf(day1), state.availableDates)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `最後の1件を削除しても currentIndex が範囲内に収まる`() = runTest(dispatcher) {
        coEvery { repository.listAllContents() } returns
            Result.success(listOf(content("only", "2026-05-01")))
        coEvery { repository.removeContents(any(), any()) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.deleteCurrentContent()

        val state = vm.uiState.value
        assertFalse(state.hasContents)
        assertEquals(0, state.currentIndex)
        assertNull(state.currentContent)
    }

    @Test
    fun `削除に失敗したらローカル状態は変わらない`() = runTest(dispatcher) {
        coEvery { repository.removeContents(any(), any()) } returns
            Result.failure(IOException("boom"))
        val vm = createViewModel()

        vm.deleteCurrentContent()

        assertEquals(3, vm.uiState.value.contents.size)
        assertNotNull(vm.uiState.value.error)
    }

    // ---------------------------------------------------------------- //
    // 共有
    // ---------------------------------------------------------------- //

    @Test
    fun `共有URLを取得して保持する`() = runTest(dispatcher) {
        coEvery { repository.getContentDownloadUrl(any(), any(), any()) } returns
            Result.success("https://example.com/a.jpg")
        val vm = createViewModel()

        vm.requestShareUrl()

        assertEquals("https://example.com/a.jpg", vm.uiState.value.shareUrl)
        assertFalse(vm.uiState.value.isShareLoading)
    }

    @Test
    fun `clearShareUrl で共有状態が初期化される`() = runTest(dispatcher) {
        coEvery { repository.getContentDownloadUrl(any(), any(), any()) } returns
            Result.success("https://example.com/a.jpg")
        val vm = createViewModel()
        vm.requestShareUrl()

        vm.clearShareUrl()

        assertNull(vm.uiState.value.shareUrl)
        assertFalse(vm.uiState.value.isShareLoading)
    }

    @Test
    fun `共有URLの取得に失敗したらエラーになる`() = runTest(dispatcher) {
        coEvery { repository.getContentDownloadUrl(any(), any(), any()) } returns
            Result.failure(IOException("boom"))
        val vm = createViewModel()

        vm.requestShareUrl()

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isShareLoading)
    }

    // ---------------------------------------------------------------- //
    // 画像の読み込み
    // ---------------------------------------------------------------- //

    @Test
    fun `取得中に同じ写真を要求しても取り直さない`() = runTest(dispatcher) {
        val vm = createViewModel()
        val before = vm.uiState.value.currentContent?.contentId

        // 同じ写真のまま何度も再読み込みを要求する
        vm.selectIndex(0)
        vm.selectIndex(0)
        vm.selectIndex(0)

        assertEquals(before, vm.uiState.value.currentContent?.contentId)
        coVerify(exactly = 1) { repository.getContentBinary("f1", "b", "original") }
    }

    @Test
    fun `別の写真へ移ると取得し直す`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.selectDate(day1)

        vm.nextImage()

        coVerify { repository.getContentBinary("f1", "a", "original") }
        coVerify { repository.getContentBinary("f1", "c", "original") }
    }

    @Test
    fun `日付を指定した選別はその日の未判定だけを対象にする`() = runTest(dispatcher) {
        // day2: n2 のみ / day1: n1a, n1b（judged は day2 で判定済み）
        val dated = listOf(
            content("n2", "2026-05-02"),
            content("judged", "2026-05-02", rating = ContentCulling.PICK),
            content("n1a", "2026-05-01"),
            content("n1b", "2026-05-01"),
        )
        coEvery { repository.listAllContents() } returns Result.success(dated)
        coEvery { repository.setContentTags(any(), any(), any()) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.startCulling(day1)

        val state = vm.uiState.value
        assertEquals(day1, state.cullDate)
        assertEquals(listOf("n1a", "n1b"), state.cullQueue.map { it.contentId })
    }

    @Test
    fun `日付を渡さない選別は全期間が対象になる`() = runTest(dispatcher) {
        val dated = listOf(
            content("n2", "2026-05-02"),
            content("n1", "2026-05-01"),
        )
        coEvery { repository.listAllContents() } returns Result.success(dated)
        val vm = createViewModel()

        vm.startCulling(null)

        assertNull(vm.uiState.value.cullDate)
        assertEquals(2, vm.uiState.value.cullQueue.size)
    }

    @Test
    fun `選別の対象は選択中の日と全期間で切り替えられる`() = runTest(dispatcher) {
        val dated = listOf(
            content("n2", "2026-05-02"),
            content("n1", "2026-05-01"),
        )
        coEvery { repository.listAllContents() } returns Result.success(dated)
        val vm = createViewModel()
        vm.selectDate(day1)

        vm.startCulling(day1)
        assertEquals(1, vm.uiState.value.cullQueue.size)

        vm.toggleCullScope()
        assertNull(vm.uiState.value.cullDate)
        assertEquals(2, vm.uiState.value.cullQueue.size)

        vm.toggleCullScope()
        assertEquals(day1, vm.uiState.value.cullDate)
        assertEquals(1, vm.uiState.value.cullQueue.size)
    }

    // ---------------------------------------------------------------- //
    // 拡大の移動
    // ---------------------------------------------------------------- //

    @Test
    fun `拡大していないときは移動しても位置が変わらない`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()

        vm.panZoom(1, 1)

        assertEquals(0.5f, vm.uiState.value.zoomCenterX, 0f)
        assertEquals(0.5f, vm.uiState.value.zoomCenterY, 0f)
    }

    @Test
    fun `拡大中は方向に応じて見る位置が動く`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        vm.cycleZoom()

        vm.panZoom(1, 0)
        assertTrue(vm.uiState.value.zoomCenterX > 0.5f)

        vm.panZoom(0, 1)
        assertTrue(vm.uiState.value.zoomCenterY > 0.5f)
    }

    @Test
    fun `倍率が高いほど一度の移動量が小さくなる`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()

        vm.cycleZoom() // 100%
        vm.panZoom(1, 0)
        val atX1 = vm.uiState.value.zoomCenterX - 0.5f

        vm.zoomOff()
        vm.cycleZoom() // 100%
        vm.cycleZoom() // 200%
        vm.panZoom(1, 0)
        val atX2 = vm.uiState.value.zoomCenterX - 0.5f

        assertTrue("$atX2 < $atX1", atX2 < atX1)
    }

    @Test
    fun `見る位置は 0 から 1 の範囲に収まる`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        vm.cycleZoom()

        repeat(30) { vm.panZoom(-1, -1) }

        assertEquals(0f, vm.uiState.value.zoomCenterX, 0f)
        assertEquals(0f, vm.uiState.value.zoomCenterY, 0f)
    }

    @Test
    fun `写真を切り替えると見る位置が中央に戻る`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        vm.cycleZoom()
        vm.panZoom(1, 1)

        vm.cullNext()

        assertEquals(0.5f, vm.uiState.value.zoomCenterX, 0f)
        assertEquals(0.5f, vm.uiState.value.zoomCenterY, 0f)
    }

    // ---------------------------------------------------------------- //
    // 見比べ
    // ---------------------------------------------------------------- //

    @Test
    fun `見比べは一覧の全部を候補にして現在の写真から始まる`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.startCompare()

        val state = vm.uiState.value
        assertTrue(state.isComparing)
        assertEquals(state.visibleContents.size, state.compareContents.size)
        assertEquals(
            state.visibleContents[state.currentIndex].contentId,
            state.compareContent?.contentId,
        )
    }

    @Test
    fun `見比べは枚数を制限せず選択を足し引きできる`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.startCompare()

        // 開いた写真は最初から選ばれている
        assertEquals(1, vm.uiState.value.compareSelected.size)

        // 候補すべてにチェックを付けられる（上限なし）
        val candidates = vm.uiState.value.compareContents
        repeat(candidates.size) {
            val id = vm.uiState.value.compareContent!!.contentId
            if (id !in vm.uiState.value.compareSelected) vm.toggleCompareSelection()
            vm.compareMoveBy(1)
        }
        assertEquals(candidates.size, vm.uiState.value.compareSelected.size)

        // もう一度押すと外れる
        val target = vm.uiState.value.compareContent!!.contentId
        vm.toggleCompareSelection()
        assertTrue(target !in vm.uiState.value.compareSelected)
    }

    @Test
    fun `選択が空のときは見ている1枚だけを表示する`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.startCompare()

        // 最初から選ばれている1枚を外して空にする
        vm.toggleCompareSelection()

        val state = vm.uiState.value
        assertTrue(state.compareSelected.isEmpty())
        assertEquals(1, state.comparePicked.size)
        assertEquals(state.compareContent?.contentId, state.comparePicked.first().contentId)
    }

    @Test
    fun `見比べは重ねと並べを切り替えられる`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.startCompare()

        assertEquals(CompareLayout.Stack, vm.uiState.value.compareLayout)

        vm.toggleCompareLayout()
        assertEquals(CompareLayout.SideBySide, vm.uiState.value.compareLayout)

        vm.toggleCompareLayout()
        assertEquals(CompareLayout.Stack, vm.uiState.value.compareLayout)
    }

    @Test
    fun `見比べ中の採用は評価を保存する`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCompare()
        val target = vm.uiState.value.compareContent!!.contentId

        vm.comparePick()

        assertEquals(
            ContentCulling.PICK,
            vm.uiState.value.contents.first { it.contentId == target }.ratingValue(),
        )
    }

    @Test
    fun `見比べを終えると状態が片付く`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.startCompare()

        vm.exitCompare()

        assertFalse(vm.uiState.value.isComparing)
        assertTrue(vm.uiState.value.compareImages.isEmpty())
    }

    // ---------------------------------------------------------------- //
    // 選別
    // ---------------------------------------------------------------- //

    /** 未判定2件（新しい順に n2, n1）＋ 判定済み1件。 */
    private val cullContents = listOf(
        content("n1", "2026-05-01"),
        content("judged", "2026-05-02", rating = ContentCulling.PICK),
        content("n2", "2026-05-03"),
    )

    private fun createCullViewModel(): SlideshowViewModel {
        coEvery { repository.listAllContents() } returns Result.success(cullContents)
        coEvery { repository.setContentTags(any(), any(), any()) } returns Result.success(Unit)
        return createViewModel()
    }

    @Test
    fun `選別は判定済みも含めて新しい順にキューへ積む`() = runTest(dispatcher) {
        val vm = createCullViewModel()

        vm.startCulling()

        val state = vm.uiState.value
        assertTrue(state.isCulling)
        assertEquals(listOf("n2", "judged", "n1"), state.cullQueue.map { it.contentId })
        // 開始位置は最初の未判定
        assertEquals("n2", state.cullContent?.contentId)
        assertEquals(2, state.cullRemaining)
    }

    @Test
    fun `先頭が判定済みなら未判定の位置から始まる`() = runTest(dispatcher) {
        coEvery { repository.listAllContents() } returns Result.success(
            listOf(
                content("old", "2026-05-01"),
                content("newJudged", "2026-05-03", rating = ContentCulling.PICK),
            ),
        )
        coEvery { repository.setContentTags(any(), any(), any()) } returns Result.success(Unit)
        val vm = createViewModel()

        vm.startCulling()

        assertEquals(listOf("newJudged", "old"), vm.uiState.value.cullQueue.map { it.contentId })
        assertEquals("old", vm.uiState.value.cullContent?.contentId)
    }

    @Test
    fun `採用すると評価3を保存して次の候補へ進む`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()

        vm.cullPick()

        coVerify { repository.setContentTags("f1", "n2", listOf("rating:3")) }
        assertEquals("judged", vm.uiState.value.cullContent?.contentId)
        assertEquals(
            ContentCulling.PICK,
            vm.uiState.value.contents.first { it.contentId == "n2" }.ratingValue(),
        )
    }

    @Test
    fun `見送りは削除せず評価1を保存する`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()

        vm.cullSkip()

        coVerify { repository.setContentTags("f1", "n2", listOf("rating:1")) }
        coVerify(exactly = 0) { repository.removeContents(any(), any()) }
        assertEquals(3, vm.uiState.value.contents.size)
    }

    @Test
    fun `左右キーでは評価を付けずに候補だけ移動する`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()

        vm.cullNext()

        assertEquals("judged", vm.uiState.value.cullContent?.contentId)
        coVerify(exactly = 0) { repository.setContentTags(any(), any(), any()) }

        vm.cullPrev()

        assertEquals("n2", vm.uiState.value.cullContent?.contentId)
    }

    @Test
    fun `キューを捌ききると完了状態になる`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()

        repeat(vm.uiState.value.cullQueue.size) { vm.cullPick() }

        val state = vm.uiState.value
        assertTrue(state.isCullComplete)
        assertNull(state.cullContent)
        assertEquals(0, state.cullRemaining)
    }

    @Test
    fun `タグ保存に失敗しても選別は止まらない`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        coEvery { repository.setContentTags(any(), any(), any()) } returns
            Result.failure(IOException("boom"))
        vm.startCulling()

        vm.cullPick()

        assertEquals("judged", vm.uiState.value.cullContent?.contentId)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `タグ保存に失敗したら黙らずに知らせる`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        coEvery { repository.setContentTags(any(), any(), any()) } returns
            Result.failure(IOException("boom"))
        vm.startCulling()

        vm.cullPick()

        // 全画面エラーにすると選別から追い出されるので notice で伝える
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.notice)
    }

    @Test
    fun `判定済みは残るが未判定の残りが減る`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        assertEquals(3, vm.uiState.value.cullQueue.size)
        assertEquals(2, vm.uiState.value.cullRemaining)

        vm.cullPick()
        vm.startCulling()

        // 枚数はそのままだが、未判定の残りと開始位置が進む
        assertEquals(3, vm.uiState.value.cullQueue.size)
        assertEquals(1, vm.uiState.value.cullRemaining)
        assertEquals("n1", vm.uiState.value.cullContent?.contentId)
    }

    @Test
    fun `選別を終了すると通常表示へ戻る`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()

        vm.exitCulling()

        val state = vm.uiState.value
        assertFalse(state.isCulling)
        assertTrue(state.cullQueue.isEmpty())
        assertFalse(state.isZoomed)
        assertNotNull(state.currentImageBytes)
    }

    @Test
    fun `選別中のプレビューは thumbnail_1920 を優先する`() = runTest(dispatcher) {
        val vm = createCullViewModel()

        vm.startCulling()

        coVerify { repository.getContentBinary("f1", "n2", "thumbnail_1920") }
    }

    @Test
    fun `拡大は等倍から段階的に上がり一周で解除される`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()

        vm.cycleZoom()
        assertTrue(vm.uiState.value.isZoomed)
        assertEquals(1f, vm.uiState.value.zoomMagnification, 0f)
        assertNotNull(vm.uiState.value.zoomImageBytes)
        coVerify { repository.getContentBinary("f1", "n2", "original") }

        vm.cycleZoom()
        assertEquals(2f, vm.uiState.value.zoomMagnification, 0f)

        vm.cycleZoom()
        assertEquals(4f, vm.uiState.value.zoomMagnification, 0f)

        vm.cycleZoom()
        assertFalse(vm.uiState.value.isZoomed)
    }

    @Test
    fun `原寸画像は拡大の初回のみ取得する`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        // n2 は初期表示で原寸を取得済みなので、まだ取得していない n1 で数える
        vm.cullNext()
        vm.cullNext()

        vm.cycleZoom()
        vm.cycleZoom()

        coVerify(exactly = 1) { repository.getContentBinary("f1", "n1", "original") }
    }

    @Test
    fun `候補を切り替えると拡大状態は解除される`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        vm.cycleZoom()

        vm.cullNext()

        assertFalse(vm.uiState.value.isZoomed)
        assertNull(vm.uiState.value.zoomImageBytes)
    }

    // ---------------------------------------------------------------- //
    // 選別結果の見直し
    // ---------------------------------------------------------------- //

    @Test
    fun `見直しは採用と見送りを切り替えて表示する`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        vm.cullPick() // n2 を採用
        vm.cullNext() // judged はそのまま送る
        vm.cullSkip() // n1 を見送り

        assertEquals(CullFilter.Picked, vm.uiState.value.cullReviewFilter)
        assertEquals(listOf("n2", "judged"), vm.uiState.value.cullReviewContents.map { it.contentId })

        vm.setCullReviewFilter(CullFilter.Skipped)

        assertEquals(listOf("n1"), vm.uiState.value.cullReviewContents.map { it.contentId })
    }

    @Test
    fun `判定を未判定に戻せる`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        vm.cullPick()

        vm.revertCullJudgement("n2")

        // 空配列は API が 400 で拒否するので rating:0 を送る
        coVerify { repository.setContentTags("f1", "n2", listOf("rating:0")) }
        assertEquals(
            ContentRating.NONE,
            vm.uiState.value.contents.first { it.contentId == "n2" }.ratingValue(),
        )
        // 未判定に戻ったので、次の選別で再び対象になる
        vm.exitCulling()
        vm.startCulling()
        assertTrue(vm.uiState.value.cullQueue.any { it.contentId == "n2" })
    }

    @Test
    fun `判定を反対側へ切り替えられる`() = runTest(dispatcher) {
        val vm = createCullViewModel()
        vm.startCulling()
        vm.cullSkip() // n2 を見送り

        vm.flipCullJudgement("n2")

        assertEquals(
            ContentCulling.PICK,
            vm.uiState.value.contents.first { it.contentId == "n2" }.ratingValue(),
        )

        vm.flipCullJudgement("n2")

        assertEquals(
            ContentCulling.SKIP,
            vm.uiState.value.contents.first { it.contentId == "n2" }.ratingValue(),
        )
    }

    @Test
    fun `存在しないコンテンツの判定変更は無視される`() = runTest(dispatcher) {
        val vm = createCullViewModel()

        vm.revertCullJudgement("missing")
        vm.flipCullJudgement("missing")

        coVerify(exactly = 0) { repository.setContentTags(any(), any(), any()) }
    }

    // ---------------------------------------------------------------- //
    // サムネイル
    // ---------------------------------------------------------------- //

    @Test
    fun `loadThumbnails は表示中コンテンツ分を取得する`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.selectDate(day1)

        vm.loadThumbnails()

        assertEquals(setOf("a", "c"), vm.uiState.value.thumbnails.keys)
    }

    @Test
    fun `サムネイルは低解像度が失敗したら次の kind にフォールバックする`() = runTest(dispatcher) {
        coEvery { repository.getContentBinary(any(), any(), "thumbnail_400") } returns
            Result.failure(IOException("no thumb"))
        coEvery { repository.getContentBinary(any(), any(), "thumbnail_1024") } returns
            Result.success(byteArrayOf(9))
        val vm = createViewModel()

        vm.loadThumbnails()

        assertEquals(setOf("b"), vm.uiState.value.thumbnails.keys)
        coVerify { repository.getContentBinary("f1", "b", "thumbnail_400") }
        coVerify { repository.getContentBinary("f1", "b", "thumbnail_1024") }
    }

    @Test
    fun `全ての kind が失敗したサムネイルは保持されない`() = runTest(dispatcher) {
        coEvery { repository.getContentBinary(any(), any(), any()) } returns
            Result.failure(IOException("no thumb"))
        val vm = createViewModel()

        vm.loadThumbnails()

        assertTrue(vm.uiState.value.thumbnails.isEmpty())
    }

    @Test
    fun `取得済みサムネイルは再取得しない`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.loadThumbnails()
        vm.loadThumbnails()

        coVerify(exactly = 1) { repository.getContentBinary("f1", "b", "thumbnail_400") }
    }
}
