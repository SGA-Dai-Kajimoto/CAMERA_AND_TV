package com.sony.dtv.carmera_poc.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.sony.dtv.carmera_poc.data.model.Folder

/** メイン画面。600dp 以上は横並び、それ以下は Navigation 遷移。 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    navController: NavHostController,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val config = LocalConfiguration.current

    LaunchedEffect(Unit) { viewModel.loadFolders() }

    // エラーを Snackbar 表示
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── ステータスバー ──
        Text(
            text = if (uiState.loginUser.isNotEmpty()) {
                "ログイン中: ${uiState.loginUser}  |  ${uiState.statusMessage}"
            } else {
                uiState.statusMessage
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // ── レイアウト分岐 ──
        if (config.screenWidthDp >= 600) {
            // タブレット: 左右ペイン
            Row(modifier = Modifier.weight(1f)) {
                FolderPanel(
                    folders = uiState.folders,
                    selectedFolder = uiState.selectedFolder,
                    isLoading = uiState.isLoading,
                    onFolderClick = { viewModel.selectFolder(it) },
                    onCreateFolder = { viewModel.createFolder(it) },
                    onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
                    onDeleteFolder = { viewModel.deleteFolder(it) },
                    onReload = { viewModel.loadFolders() },
                    modifier = Modifier.weight(1f),
                )
                ContentPanel(
                    folder = uiState.selectedFolder,
                    contents = uiState.contents,
                    isLoading = uiState.isLoading,
                    onUpload = { folderId, fileName, fileBytes -> viewModel.uploadImage(folderId, fileName, fileBytes) },
                    onViewContent = { folderId, contentId ->
                        navController.navigate("imageviewer/$folderId/$contentId")
                    },
                    onDeleteContent = { folderId, contentId ->
                        viewModel.deleteContent(folderId, contentId)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            // スマートフォン: フォルダ一覧のみ表示（コンテンツは選択後に遷移）
            FolderPanel(
                folders = uiState.folders,
                selectedFolder = uiState.selectedFolder,
                isLoading = uiState.isLoading,
                onFolderClick = { folder ->
                    viewModel.selectFolder(folder)
                    navController.navigate("contents")
                },
                onCreateFolder = { viewModel.createFolder(it) },
                onRenameFolder = { id, name -> viewModel.renameFolder(id, name) },
                onDeleteFolder = { viewModel.deleteFolder(it) },
                onReload = { viewModel.loadFolders() },
                modifier = Modifier.weight(1f),
            )
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}

/** エラーダイアログ（汎用） */
@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("エラー") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
