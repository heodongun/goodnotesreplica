package com.example.goodnotesreplica.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.goodnotesreplica.data.ExportRecord
import com.example.goodnotesreplica.data.ExportTargetKind
import com.example.goodnotesreplica.data.ExportType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportHistoryDialog(
    records: List<ExportRecord>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("내보내기 기록") },
        text = {
            if (records.isEmpty()) {
                Text("아직 내보낸 기록이 없습니다.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(records, key = { it.id }) { record ->
                        ExportHistoryRow(record = record)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
    )
}

@Composable
private fun ExportHistoryRow(record: ExportRecord) {
    val context = LocalContext.current
    val formatter = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
    val timestamp = remember(record.createdAt) {
        formatter.format(Date(record.createdAt))
    }
    val mimeType = when (record.exportType) {
        ExportType.PNG -> "image/png"
        ExportType.PDF -> "application/pdf"
    }
    val isFile = record.targetKind == ExportTargetKind.FILE
    val targetFile = if (isFile) File(record.target) else null
    val targetUri = if (!isFile) runCatching { Uri.parse(record.target) }.getOrNull() else null
    val targetLabel = when {
        targetFile != null -> targetFile.name
        targetUri?.lastPathSegment != null -> targetUri.lastPathSegment ?: record.target
        else -> record.target
    }
    val notebookTitle = record.notebookTitle.ifBlank { "Untitled Notebook" }
    val canOpenFile = targetFile?.exists() == true

    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "$notebookTitle • Page ${record.pageIndex}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${record.exportType.name} • $timestamp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = targetLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    canOpenFile -> {
                        TextButton(onClick = { openFile(context, targetFile!!, mimeType) }) {
                            Text("열기")
                        }
                        TextButton(onClick = { shareFile(context, targetFile!!, mimeType, "내보내기 공유") }) {
                            Text("공유")
                        }
                    }
                    targetUri != null -> {
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(targetUri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("열기")
                        }
                    }
                    else -> {
                        Text(
                            text = "대상을 찾을 수 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}
