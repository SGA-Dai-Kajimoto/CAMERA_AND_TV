package com.sony.dtv.camera_tv.ui.folderlist

import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FolderListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ImagingEdgeRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------- //
    // loadFolders
    // ---------------------------------------------------------------- //

    @Test
    fun `init auto-loads folders on creation`() = runTest {
        val folders = listOf(Folder("f1", "Folder A", null), Folder("f2", "Folder B", null))
        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "u1"))
        coEvery { repository.listFolders() } returns Result.success(folders)

        val viewModel = FolderListViewModel(repository)

        assertEquals(folders, viewModel.uiState.value.folders)
    }

    @Test
    fun `loadFolders sets loginUser from getUserMe`() = runTest {
        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "user123"))
        coEvery { repository.listFolders() } returns Result.success(emptyList())

        val viewModel = FolderListViewModel(repository)

        assertEquals("user123", viewModel.uiState.value.loginUser)
    }

    @Test
    fun `loadFolders sets error when listFolders fails`() = runTest {
        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "u1"))
        coEvery { repository.listFolders() } returns Result.failure(RuntimeException("network error"))

        val viewModel = FolderListViewModel(repository)

        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadFolders continues even when getUserMe fails`() = runTest {
        val folders = listOf(Folder("f1", "Folder A", null))
        coEvery { repository.getUserMe() } returns Result.failure(RuntimeException("auth error"))
        coEvery { repository.listFolders() } returns Result.success(folders)

        val viewModel = FolderListViewModel(repository)

        assertEquals(folders, viewModel.uiState.value.folders)
    }

    @Test
    fun `explicit loadFolders refreshes state`() = runTest {
        val initial = listOf(Folder("f1", "Folder A", null))
        val updated = listOf(Folder("f1", "Folder A", null), Folder("f2", "Folder B", null))

        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "u1"))
        coEvery { repository.listFolders() } returns Result.success(initial)
        val viewModel = FolderListViewModel(repository)
        assertEquals(initial, viewModel.uiState.value.folders)

        coEvery { repository.listFolders() } returns Result.success(updated)
        viewModel.loadFolders()

        assertEquals(updated, viewModel.uiState.value.folders)
    }

    // ---------------------------------------------------------------- //
    // clearError
    // ---------------------------------------------------------------- //

    @Test
    fun `clearError removes error from uiState`() = runTest {
        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "u1"))
        coEvery { repository.listFolders() } returns Result.failure(RuntimeException("error"))
        val viewModel = FolderListViewModel(repository)
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }
}
