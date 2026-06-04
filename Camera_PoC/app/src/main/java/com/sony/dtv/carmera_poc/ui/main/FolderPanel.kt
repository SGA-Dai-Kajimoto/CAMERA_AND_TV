package com.sony.dtv.carmera_poc.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sony.dtv.carmera_poc.data.model.Folder
import com.sony.dtv.carmera_poc.ui.theme.ImagingEdgeTheme

@Composable
fun FolderPanel(
    folders: List<Folder>,
    selectedFolder: Folder?,
    isLoading: Boolean,
    onFolderClick: (Folder) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }

    // フォルダ一覧が読み込まれたら最初のアイテムにフォーカスを設定
    LaunchedEffect(folders.firstOrNull()?.folderId) {
        if (folders.isNotEmpty()) {
            try { firstItemFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Column(modifier = modifier.padding(8.dp)) {
        Text("フォルダ", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // フォルダ一覧
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && folders.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    itemsIndexed(folders) { index, folder ->
                        FolderItem(
                            folder = folder,
                            isSelected = folder.folderId == selectedFolder?.folderId,
                            onClick = { onFolderClick(folder) },
                            modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // 操作ボタン行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.weight(1f),
            ) { Text("新規") }
            Button(
                onClick = { if (selectedFolder != null) showRenameDialog = true },
                enabled = selectedFolder != null,
                modifier = Modifier.weight(1f),
            ) { Text("変更") }
            Button(
                onClick = { if (selectedFolder != null) showDeleteDialog = true },
                enabled = selectedFolder != null,
                modifier = Modifier.weight(1f),
            ) { Text("削除") }
        }
        Button(
            onClick = onReload,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("再読み込み") }
    }

    // ── ダイアログ ──
    if (showCreateDialog) {
        InputDialog(
            title = "フォルダ新規作成",
            label = "フォルダ名",
            initialValue = "",
            onConfirm = { name ->
                onCreateFolder(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    if (showRenameDialog && selectedFolder != null) {
        InputDialog(
            title = "フォルダ名変更",
            label = "新しいフォルダ名",
            initialValue = selectedFolder.displayName,
            onConfirm = { name ->
                onRenameFolder(selectedFolder.folderId, name)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog && selectedFolder != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("フォルダ削除") },
            text = { Text("「${selectedFolder.displayName}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFolder(selectedFolder.folderId)
                    showDeleteDialog = false
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun FolderItem(
    folder: Folder,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        isFocused -> MaterialTheme.colorScheme.inversePrimary
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    Text(
        text = folder.displayName,
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** テキスト入力ダイアログ（汎用） */
@Composable
fun InputDialog(
    title: String,
    label: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 500)
@Composable
private fun FolderPanelPreview() {
    ImagingEdgeTheme {
        FolderPanel(
            folders = listOf(
                Folder("f1", "Folder A"),
                Folder("f2", "Folder B"),
            ),
            selectedFolder = Folder("f1", "Folder A"),
            isLoading = false,
            onFolderClick = {},
            onCreateFolder = {},
            onRenameFolder = { _, _ -> },
            onDeleteFolder = {},
            onReload = {},
        )
    }
}
