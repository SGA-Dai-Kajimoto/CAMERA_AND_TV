package com.sony.dtv.carmera_poc.ui.main

import com.sony.dtv.carmera_poc.data.model.Content
import com.sony.dtv.carmera_poc.data.model.Folder
import com.sony.dtv.carmera_poc.data.repository.ImagingEdgeRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
class MainViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ImagingEdgeRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
        viewModel = MainViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------- //
    // loadFolders
    // ---------------------------------------------------------------- //

    @Test
    fun `loadFolders updates folders in uiState`() = runTest {
        val folders = listOf(
            Folder("f1", "Folder A", null),
            Folder("f2", "Folder B", null),
        )
        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "u1", "account" to "acc1"))
        coEvery { repository.listFolders() } returns Result.success(folders)

        viewModel.loadFolders()

        assertEquals(folders, viewModel.uiState.value.folders)
    }

    @Test
    fun `loadFolders sets loginUser from getUserMe`() = runTest {
        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "user123", "account" to "acc"))
        coEvery { repository.listFolders() } returns Result.success(emptyList())

        viewModel.loadFolders()

        assertEquals("user123", viewModel.uiState.value.loginUser)
    }

    @Test
    fun `loadFolders sets error when getUserMe fails`() = runTest {
        coEvery { repository.getUserMe() } returns Result.failure(RuntimeException("auth error"))

        viewModel.loadFolders()

        assertNotNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------- //
    // selectFolder
    // ---------------------------------------------------------------- //

    @Test
    fun `selectFolder updates selectedFolder and contents`() = runTest {
        val folder = Folder("f1", "Folder A", null)
        val contents = listOf(
            Content("c1", "image001.jpg", "image001.jpg"),
            Content("c2", "image002.arw", "image002.arw"),
        )
        coEvery { repository.listContents("f1") } returns Result.success(contents)

        viewModel.selectFolder(folder)

        assertEquals(folder, viewModel.uiState.value.selectedFolder)
        assertEquals(contents, viewModel.uiState.value.contents)
    }

    @Test
    fun `selectFolder sets error when listContents fails`() = runTest {
        val folder = Folder("f1", "Folder A", null)
        coEvery { repository.listContents("f1") } returns Result.failure(RuntimeException("network error"))

        viewModel.selectFolder(folder)

        assertNotNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------- //
    // createFolder
    // ---------------------------------------------------------------- //

    @Test
    fun `createFolder re-fetches folder list after success`() = runTest {
        val initialFolders = listOf(Folder("f1", "Old Folder", null))
        val updatedFolders = listOf(Folder("f1", "Old Folder", null), Folder("f2", "New Folder", null))

        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "u1", "account" to "acc"))
        coEvery { repository.listFolders() } returnsMany listOf(
            Result.success(initialFolders),
            Result.success(updatedFolders),
        )
        coEvery { repository.createFolder("New Folder") } returns Result.success(mapOf("folder_id" to "f2"))

        viewModel.loadFolders()
        viewModel.createFolder("New Folder")

        assertEquals(updatedFolders, viewModel.uiState.value.folders)
        coVerify(exactly = 1) { repository.createFolder("New Folder") }
    }

    @Test
    fun `createFolder sets error on failure`() = runTest {
        coEvery { repository.createFolder(any()) } returns Result.failure(RuntimeException("server error"))

        viewModel.createFolder("Bad Folder")

        assertNotNull(viewModel.uiState.value.error)
    }

    // ---------------------------------------------------------------- //
    // renameFolder / deleteFolder
    // ---------------------------------------------------------------- //

    @Test
    fun `renameFolder calls repository with correct arguments`() = runTest {
        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "u1", "account" to "acc"))
        coEvery { repository.listFolders() } returns Result.success(emptyList())
        coEvery { repository.renameFolder("f1", "New Name") } returns Result.success(emptyMap())

        viewModel.renameFolder("f1", "New Name")

        coVerify { repository.renameFolder("f1", "New Name") }
    }

    @Test
    fun `deleteFolder calls repository and refreshes list`() = runTest {
        coEvery { repository.getUserMe() } returns Result.success(mapOf("user_id" to "u1", "account" to "acc"))
        coEvery { repository.listFolders() } returns Result.success(emptyList())
        coEvery { repository.deleteFolder("f1") } returns Result.success(Unit)

        viewModel.deleteFolder("f1")

        coVerify { repository.deleteFolder("f1") }
    }

    // ---------------------------------------------------------------- //
    // error clear
    // ---------------------------------------------------------------- //

    @Test
    fun `clearError sets error to null`() = runTest {
        coEvery { repository.getUserMe() } returns Result.failure(RuntimeException("error"))
        viewModel.loadFolders()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()

        assertNull(viewModel.uiState.value.error)
    }
}
