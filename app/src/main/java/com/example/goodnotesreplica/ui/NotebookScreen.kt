package com.example.goodnotesreplica.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.goodnotesreplica.data.ExportType
import com.example.goodnotesreplica.data.ExportRecord
import com.example.goodnotesreplica.data.Notebook
import com.example.goodnotesreplica.data.Page
import com.example.goodnotesreplica.data.PaperStyle
import java.io.File
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NotebookScreen(
    notebook: Notebook,
    pages: List<Page>,
    activePage: Page?,
    isExpanded: Boolean,
    onBack: () -> Unit,
    onCreatePage: (PaperStyle) -> Unit,
    onOpenPage: (Page) -> Unit,
    onSavePage: (Page) -> Unit,
    onRenameNotebook: (String) -> Unit,
    onImportPdf: suspend (Uri) -> Unit,
    onImportImage: suspend (Uri) -> String?,
    onExportPage: suspend (Page, ExportType) -> File?,
    onExportToUri: suspend (Page, ExportType, Uri) -> Boolean,
    onLoadExportHistory: suspend () -> List<ExportRecord>,
    onRerenderPdf: suspend (Page) -> Page?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var exportExpanded by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(notebook.title) }
    var pendingImagePath by remember { mutableStateOf<String?>(null) }
    var exportHistoryOpen by remember { mutableStateOf(false) }
    var exportHistory by remember { mutableStateOf<List<ExportRecord>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { onImportPdf(uri) }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch { pendingImagePath = onImportImage(uri) }
        }
    }
    val savePngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val active = activePage ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val saved = onExportToUri(active, ExportType.PNG, uri)
                val message = if (saved) "PNG 저장 완료" else "PNG 저장 실패"
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val active = activePage ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val saved = onExportToUri(active, ExportType.PDF, uri)
                val message = if (saved) "PDF 저장 완료" else "PDF 저장 실패"
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val background = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = notebook.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "페이지 ${notebook.pageCount}장",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (activePage != null) {
                        IconButton(onClick = { exportExpanded = true }) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = exportExpanded,
                            onDismissRequest = { exportExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("내보내기 기록") },
                                onClick = {
                                    exportExpanded = false
                                    scope.launch {
                                        exportHistory = onLoadExportHistory()
                                        exportHistoryOpen = true
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("PNG 공유") },
                                onClick = {
                                    exportExpanded = false
                                    scope.launch {
                                        val file = onExportPage(activePage, ExportType.PNG)
                                        if (file != null) {
                                            shareFile(context, file, "image/png", "PNG 공유")
                                        } else {
                                            snackbarHostState.showSnackbar("PNG 내보내기 실패")
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("PNG 다운로드 저장") },
                                onClick = {
                                    exportExpanded = false
                                    val name = "PaperStudio_Page_${activePage.index}.png"
                                    savePngLauncher.launch(name)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("PDF 공유") },
                                onClick = {
                                    exportExpanded = false
                                    scope.launch {
                                    val file = onExportPage(activePage, ExportType.PDF)
                                    if (file != null) {
                                        shareFile(context, file, "application/pdf", "PDF 공유")
                                    } else {
                                        snackbarHostState.showSnackbar("PDF 내보내기 실패")
                                    }
                                }
                            },
                        )
                            DropdownMenuItem(
                                text = { Text("PDF 다운로드 저장") },
                                onClick = {
                                    exportExpanded = false
                                    val name = "PaperStudio_Page_${activePage.index}.pdf"
                                    savePdfLauncher.launch(name)
                                },
                            )
                            val sourcePdf = activePage.sourcePdfPath?.let { File(it) }
                            if (sourcePdf != null && sourcePdf.exists()) {
                                DropdownMenuItem(
                                    text = { Text("PDF 원본 보기") },
                                    onClick = {
                                        exportExpanded = false
                                        openFile(context, sourcePdf, "application/pdf")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("PDF 원본 공유") },
                                    onClick = {
                                        exportExpanded = false
                                        shareFile(context, sourcePdf, "application/pdf", "PDF 원본 공유")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("PDF 다시 렌더") },
                                    onClick = {
                                    exportExpanded = false
                                    scope.launch {
                                        val updated = onRerenderPdf(activePage)
                                        val message = if (updated != null) "PDF 다시 렌더 완료" else "PDF 다시 렌더 실패"
                                        snackbarHostState.showSnackbar(message)
                                    }
                                },
                            )
                        }
                    }
                    }
                    IconButton(onClick = { renameDialog = true }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        PaperStyle.values().forEach { style ->
                            DropdownMenuItem(
                                text = { Text(paperStyleLabel(style)) },
                                onClick = {
                                    menuExpanded = false
                                    onCreatePage(style)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("PDF 가져오기") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                pdfPicker.launch(arrayOf("application/pdf"))
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(innerPadding)
        ) {
            if (isExpanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    PageList(
                        pages = pages,
                        activePage = activePage,
                        onOpenPage = onOpenPage,
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .padding(16.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 16.dp, top = 16.dp, bottom = 16.dp),
                    ) {
                        if (activePage != null) {
                            EditorPanel(
                                page = activePage,
                                onSavePage = onSavePage,
                                onRequestImagePick = { imagePicker.launch("image/*") },
                                insertImagePath = pendingImagePath,
                                onConsumeImagePath = { pendingImagePath = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            EmptyEditor(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            } else {
                PageList(
                    pages = pages,
                    activePage = activePage,
                    onOpenPage = onOpenPage,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            }
        }
    }

    if (exportHistoryOpen) {
        ExportHistoryDialog(
            records = exportHistory,
            onDismiss = { exportHistoryOpen = false },
        )
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("노트북 이름 변경") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameNotebook(renameText)
                        renameDialog = false
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

}

@Composable
private fun PageList(
    pages: List<Page>,
    activePage: Page?,
    onOpenPage: (Page) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(pages, key = { it.id }) { page ->
            PageCard(
                page = page,
                isActive = activePage?.id == page.id,
                onClick = { onOpenPage(page) },
            )
        }
    }
}

@Composable
private fun PageCard(
    page: Page,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val ratio = if (page.aspectRatio > 0f) page.aspectRatio else 0.72f
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Page ${page.index}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                PageThumbnail(
                    page = page,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun EmptyEditor(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "페이지를 선택하거나 새로 추가하세요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
