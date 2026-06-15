package com.sony.dtv.camera_tv.ui.slideshow

import com.sony.dtv.camera_tv.data.model.Content
import com.sony.dtv.camera_tv.data.remote.dto.ContentListResponse
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var vmScope: CoroutineScope
    private lateinit var repository: ImagingEdgeRepository

    private val dummyBytes = byteArrayOf(1, 2, 3)
    private val contents = listOf(
        Content("c1", "Image 1", recordedDate = "2026-06-15T10:00:00.000+09:00"),
        Content("c2", "Image 2", recordedDate = "2026-06-15T11:00:00.000+09:00"),
        Content("c3", "Image 3", recordedDate = "2026-06-14T09:00:00.000+09:00"),
    )

    @Before
    fun setup() {
        vmScope = CoroutineScope(dispatcher + Job())
        repository = mockk()
    }

    @After
    fun tearDown() {
        vmScope.cancel()
    }

    private fun createViewModel(folderId: String = "folder1") =
        SlideshowViewModel(repository, folderId, vmScope)

    private fun mockListContentsPaged(
        contents: List<Content> = this.contents,
        lastItem: String? = null,
    ) {
        coEvery { repository.listContentsPaged(any(), any(), any(), any()) } returns
            Result.success(ContentListResponse(contents, lastItem))
    }

    // ---------------------------------------------------------------- //
    // 初期ロード
    // ---------------------------------------------------------------- //

    @Test
    fun `init loads contents and first image`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(contents, state.contents)
        assertEquals(0, state.currentIndex)
        assertArrayEquals(dummyBytes, state.currentImageBytes)
        assertFalse(state.isLoading)
    }

    @Test
    fun `init sets error when listContentsPaged fails`() {
        coEvery { repository.listContentsPaged(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("network error"))

        val viewModel = createViewModel()

        assertNotNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------- //
    // 日付グルーピング
    // ---------------------------------------------------------------- //

    @Test
    fun `groupedItems contains DateHeaders and ContentItems`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()

        val grouped = viewModel.uiState.value.groupedItems
        // 2日分のヘッダー + 3件のコンテンツ = 5アイテム
        assertEquals(5, grouped.size)
        assertTrue(grouped[0] is ContentListItem.DateHeader)
        assertTrue(grouped[1] is ContentListItem.ContentItem)
    }

    @Test
    fun `availableDates are sorted descending`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()

        val dates = viewModel.uiState.value.availableDates
        assertEquals(2, dates.size)
        assertEquals(LocalDate.of(2026, 6, 15), dates[0])
        assertEquals(LocalDate.of(2026, 6, 14), dates[1])
    }

    @Test
    fun `selectedDate defaults to latest date`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()

        assertEquals(LocalDate.of(2026, 6, 15), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `selectDate changes selected date and resets index`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()
        viewModel.nextImage() // index = 1

        viewModel.selectDate(LocalDate.of(2026, 6, 14))

        assertEquals(LocalDate.of(2026, 6, 14), viewModel.uiState.value.selectedDate)
        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `currentDateContents returns only contents for selected date`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()

        // 最新日付 (6/15) のコンテンツは c1, c2
        val dateContents = viewModel.currentDateContents()
        assertEquals(2, dateContents.size)
        assertEquals("c1", dateContents[0].contentId)
        assertEquals("c2", dateContents[1].contentId)
    }

    // ---------------------------------------------------------------- //
    // ページネーション
    // ---------------------------------------------------------------- //

    @Test
    fun `loadMoreContents appends new contents`() {
        val firstPage = listOf(
            Content("c1", "Image 1", recordedDate = "2026-06-15T10:00:00.000+09:00"),
        )
        val secondPage = listOf(
            Content("c4", "Image 4", recordedDate = "2026-06-13T08:00:00.000+09:00"),
        )

        coEvery { repository.listContentsPaged(any(), any(), any(), isNull()) } returns
            Result.success(ContentListResponse(firstPage, "c1"))
        coEvery { repository.listContentsPaged(any(), any(), any(), eq("c1")) } returns
            Result.success(ContentListResponse(secondPage, null))
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()
        assertEquals(1, viewModel.uiState.value.contents.size)

        viewModel.loadMoreContents()
        assertEquals(2, viewModel.uiState.value.contents.size)
        assertEquals(2, viewModel.uiState.value.availableDates.size)
    }

    // ---------------------------------------------------------------- //
    // nextImage / prevImage（日付グループ内）
    // ---------------------------------------------------------------- //

    @Test
    fun `nextImage advances within selected date group`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()
        // selectedDate = 6/15, contents in group: c1, c2
        assertEquals(0, viewModel.uiState.value.currentIndex)

        viewModel.nextImage()
        assertEquals(1, viewModel.uiState.value.currentIndex)

        // wrap around within group (size=2)
        viewModel.nextImage()
        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `prevImage wraps within selected date group`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()
        // selectedDate = 6/15, group size = 2
        // 0 → 1 (wrap)
        viewModel.prevImage()
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    // ---------------------------------------------------------------- //
    // togglePlayPause
    // ---------------------------------------------------------------- //

    @Test
    fun `togglePlayPause pauses and resumes slideshow`() {
        mockListContentsPaged()
        coEvery { repository.getContentBinary(any(), any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayPause()
        assertFalse(viewModel.uiState.value.isPlaying)

        viewModel.togglePlayPause()
        assertTrue(viewModel.uiState.value.isPlaying)
    }

    // ---------------------------------------------------------------- //
    // clearError
    // ---------------------------------------------------------------- //

    @Test
    fun `clearError removes error from uiState`() {
        coEvery { repository.listContentsPaged(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("error"))

        val viewModel = createViewModel()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }
}
