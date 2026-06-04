package com.sony.dtv.camera_tv.ui.slideshow

import com.sony.dtv.camera_tv.data.model.Content
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

@OptIn(ExperimentalCoroutinesApi::class)
class SlideshowViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var vmScope: CoroutineScope
    private lateinit var repository: ImagingEdgeRepository

    private val dummyBytes = byteArrayOf(1, 2, 3)
    private val contents = listOf(
        Content("c1", "Image 1"),
        Content("c2", "Image 2"),
        Content("c3", "Image 3"),
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

    // ---------------------------------------------------------------- //
    // 初期ロード
    // ---------------------------------------------------------------- //

    @Test
    fun `init loads contents and first image`() {
        coEvery { repository.listContents(any()) } returns Result.success(contents)
        coEvery { repository.getContentBinary(any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals(contents, state.contents)
        assertEquals(0, state.currentIndex)
        assertArrayEquals(dummyBytes, state.currentImageBytes)
        assertFalse(state.isLoading)
    }

    @Test
    fun `init sets error when listContents fails`() {
        coEvery { repository.listContents(any()) } returns Result.failure(RuntimeException("network error"))

        val viewModel = createViewModel()

        assertNotNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------- //
    // nextImage / prevImage
    // ---------------------------------------------------------------- //

    @Test
    fun `nextImage advances currentIndex and wraps around`() {
        coEvery { repository.listContents(any()) } returns Result.success(contents)
        coEvery { repository.getContentBinary(any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()
        assertEquals(0, viewModel.uiState.value.currentIndex)

        // 0 → 1
        viewModel.nextImage()
        assertEquals(1, viewModel.uiState.value.currentIndex)

        // 1 → 2
        viewModel.nextImage()
        assertEquals(2, viewModel.uiState.value.currentIndex)

        // 2 → 0 (wrap around)
        viewModel.nextImage()
        assertEquals(0, viewModel.uiState.value.currentIndex)
    }

    @Test
    fun `prevImage decrements currentIndex and wraps around`() {
        coEvery { repository.listContents(any()) } returns Result.success(contents)
        coEvery { repository.getContentBinary(any(), any()) } returns Result.success(dummyBytes)

        val viewModel = createViewModel()
        assertEquals(0, viewModel.uiState.value.currentIndex)

        // 0 → 2 (wrap around)
        viewModel.prevImage()
        assertEquals(2, viewModel.uiState.value.currentIndex)

        // 2 → 1
        viewModel.prevImage()
        assertEquals(1, viewModel.uiState.value.currentIndex)
    }

    // ---------------------------------------------------------------- //
    // togglePlayPause
    // ---------------------------------------------------------------- //

    @Test
    fun `togglePlayPause pauses and resumes slideshow`() {
        coEvery { repository.listContents(any()) } returns Result.success(contents)
        coEvery { repository.getContentBinary(any(), any()) } returns Result.success(dummyBytes)

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
        coEvery { repository.listContents(any()) } returns Result.failure(RuntimeException("error"))

        val viewModel = createViewModel()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }
}
