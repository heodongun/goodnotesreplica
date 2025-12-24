package com.example.goodnotesreplica.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goodnotesreplica.data.Folder
import com.example.goodnotesreplica.data.Notebook

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    notebooks: List<Notebook>,
    folders: List<Folder>,
    selectedFolderId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateNotebook: (String, String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Folder, String) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    onOpenNotebook: (Notebook) -> Unit,
    onDeleteNotebook: (Notebook) -> Unit,
    onMoveNotebook: (Notebook, String?) -> Unit,
    onEditTags: (Notebook, List<String>) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val columns = when {
        configuration.screenWidthDp >= 900 -> 3
        configuration.screenWidthDp >= 600 -> 2
        else -> 1
    }

    var showNotebookDialog by remember { mutableStateOf(false) }
    var notebookTitle by remember { mutableStateOf("") }

    var showFolderDialog by remember { mutableStateOf(false) }
    var folderDialogTitle by remember { mutableStateOf("새 폴더") }
    var folderDialogValue by remember { mutableStateOf("") }
    var folderDialogTarget by remember { mutableStateOf<Folder?>(null) }

    var folderDeleteTarget by remember { mutableStateOf<Folder?>(null) }
    var moveNotebookTarget by remember { mutableStateOf<Notebook?>(null) }
    var moveFolderId by remember { mutableStateOf<String?>(null) }

    var tagNotebookTarget by remember { mutableStateOf<Notebook?>(null) }
    var tagInput by remember { mutableStateOf("") }

    val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }

    val background = Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            ) {
                HeaderSection(
                    notebookCount = notebooks.size,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    onSelectFolder = onSelectFolder,
                    onCreateFolder = {
                        folderDialogTitle = "새 폴더"
                        folderDialogValue = ""
                        folderDialogTarget = null
                        showFolderDialog = true
                    },
                    onRenameFolder = {
                        if (selectedFolder != null) {
                            folderDialogTitle = "폴더 이름 변경"
                            folderDialogValue = selectedFolder.name
                            folderDialogTarget = selectedFolder
                            showFolderDialog = true
                        }
                    },
                    onDeleteFolder = {
                        if (selectedFolder != null) {
                            folderDeleteTarget = selectedFolder
                        }
                    },
                    onCreateNotebook = { showNotebookDialog = true },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(visible = true, enter = fadeIn()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(notebooks, key = { it.id }) { notebook ->
                        NotebookCard(
                            notebook = notebook,
                            onOpen = { onOpenNotebook(notebook) },
                            onDelete = { onDeleteNotebook(notebook) },
                            onMove = {
                                moveNotebookTarget = notebook
                                moveFolderId = notebook.folderId
                            },
                            onEditTags = {
                                tagNotebookTarget = notebook
                                tagInput = notebook.tags.joinToString(", ")
                            },
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .aspectRatio(1.18f),
                        )
                    }
                }
            }
        }
    }

    if (showNotebookDialog) {
        AlertDialog(
            onDismissRequest = { showNotebookDialog = false },
            title = { Text(text = "새 노트북") },
            text = {
                Column {
                    Text(
                        text = "제목을 입력하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = notebookTitle,
                        onValueChange = { notebookTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("예: 아이디어 스케치") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateNotebook(notebookTitle, selectedFolderId)
                        notebookTitle = ""
                        showNotebookDialog = false
                    },
                    enabled = notebookTitle.isNotBlank(),
                ) {
                    Text("만들기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotebookDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text(folderDialogTitle) },
            text = {
                OutlinedTextField(
                    value = folderDialogValue,
                    onValueChange = { folderDialogValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = folderDialogValue.isNotBlank(),
                    onClick = {
                        val target = folderDialogTarget
                        if (target == null) {
                            onCreateFolder(folderDialogValue)
                        } else {
                            onRenameFolder(target, folderDialogValue)
                        }
                        showFolderDialog = false
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

    if (folderDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { folderDeleteTarget = null },
            title = { Text("폴더 삭제") },
            text = { Text("선택한 폴더를 삭제할까요? 노트북은 '전체'로 이동됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        folderDeleteTarget?.let { onDeleteFolder(it) }
                        folderDeleteTarget = null
                        onSelectFolder(null)
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderDeleteTarget = null }) {
                    Text("취소")
                }
            },
        )
    }

    if (moveNotebookTarget != null) {
        AlertDialog(
            onDismissRequest = { moveNotebookTarget = null },
            title = { Text("폴더 이동") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderOption(
                        label = "전체",
                        selected = moveFolderId == null,
                        onSelect = { moveFolderId = null },
                    )
                    folders.forEach { folder ->
                        FolderOption(
                            label = folder.name,
                            selected = moveFolderId == folder.id,
                            onSelect = { moveFolderId = folder.id },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        moveNotebookTarget?.let { onMoveNotebook(it, moveFolderId) }
                        moveNotebookTarget = null
                    }
                ) {
                    Text("이동")
                }
            },
            dismissButton = {
                TextButton(onClick = { moveNotebookTarget = null }) {
                    Text("취소")
                }
            },
        )
    }

    if (tagNotebookTarget != null) {
        AlertDialog(
            onDismissRequest = { tagNotebookTarget = null },
            title = { Text("태그 편집") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("쉼표로 구분하여 입력하세요.")
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("예: 디자인, 아이디어") },
                        singleLine = false,
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val tags = tagInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        tagNotebookTarget?.let { onEditTags(it, tags) }
                        tagNotebookTarget = null
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagNotebookTarget = null }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun HeaderSection(
    notebookCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    folders: List<Folder>,
    selectedFolderId: String?,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: () -> Unit,
    onDeleteFolder: () -> Unit,
    onCreateNotebook: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Paper Studio",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "노트북 ${notebookCount}개",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onCreateNotebook) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("새 노트")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("노트 제목, 태그, 본문 검색") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FolderChip(
                label = "전체",
                selected = selectedFolderId == null,
                onClick = { onSelectFolder(null) },
            )
            folders.forEach { folder ->
                FolderChip(
                    label = folder.name,
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelectFolder(folder.id) },
                )
            }
            IconButton(onClick = onCreateFolder) {
                Icon(imageVector = Icons.Default.Folder, contentDescription = null)
            }
            if (selectedFolderId != null) {
                IconButton(onClick = onRenameFolder) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                }
                IconButton(onClick = onDeleteFolder) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotebookCard(
    notebook: Notebook,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onEditTags: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val baseColor = Color(notebook.coverColor)
    val gradient = Brush.linearGradient(
        listOf(
            baseColor.copy(alpha = 0.22f),
            baseColor.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.surface,
        )
    )

    Card(
        onClick = onOpen,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp, 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(baseColor)
                    )
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("폴더 이동") },
                            onClick = {
                                menuExpanded = false
                                onMove()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.MoveToInbox,
                                    contentDescription = null,
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("태그 편집") },
                            onClick = {
                                menuExpanded = false
                                onEditTags()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("삭제") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                )
                            }
                        )
                    }
                }
                Column {
                    Text(
                        text = notebook.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "페이지 ${notebook.pageCount}장",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (notebook.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            notebook.tags.take(3).forEach { tag ->
                                TagChip(label = tag)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
