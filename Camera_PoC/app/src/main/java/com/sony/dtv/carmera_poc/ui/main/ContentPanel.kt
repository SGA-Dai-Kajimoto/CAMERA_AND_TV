package com.sony.dtv.carmera_poc.ui.main

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sony.dtv.carmera_poc.data.model.Content
import com.sony.dtv.carmera_poc.data.model.Folder
import com.sony.dtv.carmera_poc.ui.theme.ImagingEdgeTheme

@Composable
fun ContentPanel(
    folder: Folder?,
    contents: List<Content>,
    isLoading: Boolean,
    onUpload: (folderId: String, fileName: String, fileBytes: ByteArray) -> Unit,
    onViewContent: (folderId: String, contentId: String) -> Unit,
    onDeleteContent: (folderId: String, contentId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedContent by remember { mutableStateOf<Content?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null && folder != null) {
            // Uri からファイル名とバイト列を取得
            val fileName = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } ?: "upload.jpg"
            val fileBytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return@rememberLauncherForActivityResult
            onUpload(folder.folderId, fileName, fileBytes)
        }
    }

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = if (folder != null) "コンテンツ (${folder.displayName})" else "コンテンツ（フォルダを選択）",
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // コンテンツ一覧
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (folder == null) {
                Text(
                    "左のリストからフォルダを選択してください",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(contents) { content ->
                        ContentItem(
                            content = content,
                            isSelected = content.contentId == selectedContent?.contentId,
                            onClick = { selectedContent = content },
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
                onClick = { filePickerLauncher.launch("image/*") },
                enabled = folder != null,
                modifier = Modifier.weight(1f),
            ) { Text("アップロード") }
            Button(
                onClick = {
                    if (folder != null && selectedContent != null) {
                        onViewContent(folder.folderId, selectedContent!!.contentId)
                    }
                },
                enabled = folder != null && selectedContent != null,
                modifier = Modifier.weight(1f),
            ) { Text("表示") }
            Button(
                onClick = { if (selectedContent != null) showDeleteDialog = true },
                enabled = folder != null && selectedContent != null,
                modifier = Modifier.weight(1f),
            ) { Text("削除") }
        }
    }

    if (showDeleteDialog && selectedContent != null && folder != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("コンテンツ削除") },
            text = { Text("「${selectedContent!!.displayName}」を削除しますか？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteContent(folder.folderId, selectedContent!!.contentId)
                    selectedContent = null
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
private fun ContentItem(
    content: Content,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = content.displayName,
        color = textColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 500)
@Composable
private fun ContentPanelPreview() {
    ImagingEdgeTheme {
        ContentPanel(
            folder = Folder("f1", "Folder A"),
            contents = listOf(
                Content("c1", "image001.jpg", "image001.jpg"),
                Content("c2", "image002.arw", "image002.arw"),
            ),
            isLoading = false,
            onUpload = { _, _, _ -> },
            onViewContent = { _, _ -> },
            onDeleteContent = { _, _ -> },
        )
    }
}
