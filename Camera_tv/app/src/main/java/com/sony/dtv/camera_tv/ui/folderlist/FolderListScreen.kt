package com.sony.dtv.camera_tv.ui.folderlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sony.dtv.camera_tv.data.model.Folder
import com.sony.dtv.camera_tv.data.repository.ImagingEdgeRepository

@Composable
fun FolderListScreen(
    repository: ImagingEdgeRepository,
    onFolderSelected: (folderId: String) -> Unit,
) {
    val viewModel = viewModel<FolderListViewModel>(
        factory = FolderListViewModel.factory(repository),
    )
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ヘッダー
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (uiState.loginUser.isNotEmpty()) "ユーザー: ${uiState.loginUser}" else "Camera TV",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(4.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        // エラー表示
        if (uiState.error != null) {
            Text(
                text = "エラー: ${uiState.error}",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        // フォルダ一覧
        if (!uiState.isLoading && uiState.folders.isEmpty() && uiState.error == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "フォルダがありません",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            FolderList(
                folders = uiState.folders,
                onFolderClick = { folder -> onFolderSelected(folder.folderId) },
            )
        }
    }
}

@Composable
private fun FolderList(
    folders: List<Folder>,
    onFolderClick: (Folder) -> Unit,
) {
    val firstFocusRequester = remember { FocusRequester() }

    LaunchedEffect(folders.firstOrNull()?.folderId) {
        if (folders.isNotEmpty()) {
            try { firstFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        itemsIndexed(folders) { index, folder ->
            FolderItem(
                folder = folder,
                onClick = { onFolderClick(folder) },
                modifier = if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier,
            )
        }
    }
}

@Composable
private fun FolderItem(
    folder: Folder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = if (isFocused) {
        MaterialTheme.colorScheme.inversePrimary
    } else {
        MaterialTheme.colorScheme.surface
    }

    Text(
        text = folder.displayName,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(bgColor, shape = MaterialTheme.shapes.medium)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium)
                } else {
                    Modifier
                },
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
