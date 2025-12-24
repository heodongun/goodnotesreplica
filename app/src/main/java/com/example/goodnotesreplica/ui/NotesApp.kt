package com.example.goodnotesreplica.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.goodnotesreplica.data.ExportType
import com.example.goodnotesreplica.data.ExportRecord
import com.example.goodnotesreplica.data.Folder
import com.example.goodnotesreplica.data.Notebook
import com.example.goodnotesreplica.data.NotesRepository
import com.example.goodnotesreplica.data.Page
import com.example.goodnotesreplica.data.PaperStyle
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 앱의 화면 상태를 정의합니다.
 */
sealed interface Screen {
    /** 홈 화면 (노트북 목록) */
    data object Home : Screen
    /** 노트북 상세 화면 (페이지 목록) */
    data class NotebookDetail(val notebookId: String) : Screen
    /** 편집기 화면 (그리기 및 편집) */
    data class Editor(val notebookId: String, val pageId: String) : Screen
}

/**
 * 앱의 최상위 컴포저블입니다.
 * 내비게이션 및 전체 상태 관리를 담당합니다.
 */
@Composable
fun NotesApp() {
    val context = LocalContext.current
    val repository = remember { NotesRepository(context) }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 840

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var notebooks by remember { mutableStateOf<List<Notebook>>(emptyList()) }
    var visibleNotebooks by remember { mutableStateOf<List<Notebook>>(emptyList()) }
    var folders by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeNotebook by remember { mutableStateOf<Notebook?>(null) }
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var activePage by remember { mutableStateOf<Page?>(null) }

    LaunchedEffect(Unit) {
        coroutineScope {
            val notebooksDeferred = async { repository.listNotebooks() }
            val foldersDeferred = async { repository.listFolders() }
            notebooks = notebooksDeferred.await()
            folders = foldersDeferred.await()
            visibleNotebooks = notebooks
        }
    }

    LaunchedEffect(searchQuery, selectedFolderId, notebooks) {
        val base = if (searchQuery.isBlank()) {
            notebooks
        } else {
            repository.searchNotebooks(searchQuery)
        }
        visibleNotebooks = if (selectedFolderId == null) {
            base
        } else {
            base.filter { it.folderId == selectedFolderId }
        }
    }

    fun syncActiveNotebook(updated: List<Notebook>) {
        val active = activeNotebook ?: return
        val refreshed = updated.firstOrNull { it.id == active.id }
        if (refreshed != null) {
            activeNotebook = refreshed
        }
    }

    fun openNotebook(notebook: Notebook) {
        scope.launch {
            activeNotebook = notebook
            pages = repository.listPages(notebook.id)
            activePage = pages.firstOrNull()
            screen = Screen.NotebookDetail(notebook.id)
        }
    }

    fun openPage(page: Page) {
        scope.launch {
            val notebook = activeNotebook ?: return@launch
            activePage = repository.loadPage(notebook.id, page.id) ?: page
            screen = if (isExpanded) {
                Screen.NotebookDetail(notebook.id)
            } else {
                Screen.Editor(notebook.id, page.id)
            }
        }
    }

    fun createNotebook(title: String, folderId: String?) {
        scope.launch {
            val newNotebook = repository.createNotebook(title, folderId)
            notebooks = repository.listNotebooks()
            openNotebook(newNotebook)
        }
    }

    fun createFolder(name: String) {
        scope.launch {
            repository.createFolder(name)
            folders = repository.listFolders()
        }
    }

    fun renameFolder(folder: Folder, name: String) {
        scope.launch {
            repository.renameFolder(folder, name)
            folders = repository.listFolders()
        }
    }

    fun deleteFolder(folder: Folder) {
        scope.launch {
            repository.deleteFolder(folder.id)
            coroutineScope {
                val foldersDeferred = async { repository.listFolders() }
                val notebooksDeferred = async { repository.listNotebooks() }
                folders = foldersDeferred.await()
                notebooks = notebooksDeferred.await()
            }
            syncActiveNotebook(notebooks)
            if (selectedFolderId == folder.id) {
                selectedFolderId = null
            }
        }
    }

    fun createPage(style: PaperStyle) {
        scope.launch {
            val notebook = activeNotebook ?: return@launch
            val newPage = repository.createPage(notebook.id, style)
            pages = repository.listPages(notebook.id)
            activePage = newPage
            if (!isExpanded) {
                screen = Screen.Editor(notebook.id, newPage.id)
            }
        }
    }

    fun savePage(page: Page) {
        scope.launch {
            val notebook = activeNotebook ?: return@launch
            repository.savePage(notebook.id, page)
            activePage = page
            pages = pages.map { if (it.id == page.id) page else it }
            notebooks = repository.listNotebooks()
            syncActiveNotebook(notebooks)
        }
    }

    fun moveNotebook(notebook: Notebook, folderId: String?) {
        scope.launch {
            val updated = repository.updateNotebookFolder(notebook, folderId)
            notebooks = notebooks.map { if (it.id == updated.id) updated else it }
            syncActiveNotebook(notebooks)
        }
    }

    fun updateNotebookTags(notebook: Notebook, tags: List<String>) {
        scope.launch {
            val updated = repository.updateNotebookTags(notebook, tags)
            notebooks = notebooks.map { if (it.id == updated.id) updated else it }
            syncActiveNotebook(notebooks)
        }
    }

    fun deleteNotebook(notebook: Notebook) {
        scope.launch {
            repository.deleteNotebook(notebook.id)
            notebooks = repository.listNotebooks()
            activeNotebook = null
            pages = emptyList()
            activePage = null
            screen = Screen.Home
        }
    }

    fun renameNotebook(notebook: Notebook, title: String) {
        scope.launch {
            val updated = repository.renameNotebook(notebook, title)
            notebooks = notebooks.map { if (it.id == updated.id) updated else it }
            syncActiveNotebook(notebooks)
        }
    }

    suspend fun importImage(uri: android.net.Uri): String? {
        val notebook = activeNotebook ?: return null
        return repository.importImage(notebook.id, uri)
    }

    suspend fun exportPage(page: Page, type: ExportType): File? {
        val notebook = activeNotebook ?: return null
        return when (type) {
            ExportType.PNG -> repository.exportPageAsPng(page, notebook)
            ExportType.PDF -> repository.exportPageAsPdf(page, notebook)
        }
    }

    suspend fun exportPageToUri(page: Page, type: ExportType, uri: android.net.Uri): Boolean {
        val notebook = activeNotebook ?: return false
        return repository.exportPageToUri(page, notebook, type, uri)
    }

    suspend fun loadExportHistory(): List<ExportRecord> {
        return repository.listExportHistory()
    }

    suspend fun rerenderPdf(page: Page): Page? {
        val notebook = activeNotebook ?: return null
        val updated = repository.rerenderPdfPage(page) ?: return null
        repository.savePage(notebook.id, updated)
        pages = pages.map { if (it.id == updated.id) updated else it }
        activePage = updated
        notebooks = repository.listNotebooks()
        syncActiveNotebook(notebooks)
        return updated
    }

    suspend fun importPdf(uri: android.net.Uri) {
        val notebook = activeNotebook ?: return
        val newPages = repository.importPdfAsPages(notebook.id, uri)
        if (newPages.isNotEmpty()) {
            pages = repository.listPages(notebook.id)
            activePage = newPages.firstOrNull() ?: activePage
            if (!isExpanded) {
                screen = Screen.Editor(notebook.id, activePage?.id ?: newPages.first().id)
            }
            coroutineScope {
                val notebooksDeferred = async { repository.listNotebooks() }
                notebooks = notebooksDeferred.await()
                syncActiveNotebook(notebooks)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = screen) {
            Screen.Home -> HomeScreen(
                notebooks = visibleNotebooks,
                folders = folders,
                selectedFolderId = selectedFolderId,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSelectFolder = { selectedFolderId = it },
                onCreateNotebook = ::createNotebook,
                onCreateFolder = ::createFolder,
                onRenameFolder = ::renameFolder,
                onDeleteFolder = ::deleteFolder,
                onOpenNotebook = ::openNotebook,
                onDeleteNotebook = ::deleteNotebook,
                onMoveNotebook = ::moveNotebook,
                onEditTags = ::updateNotebookTags,
            )

            is Screen.NotebookDetail -> {
                val notebook = activeNotebook
                if (notebook == null) {
                    screen = Screen.Home
                } else {
                    NotebookScreen(
                        notebook = notebook,
                        pages = pages,
                        activePage = activePage,
                        isExpanded = isExpanded,
                        onBack = {
                            activePage = null
                            screen = Screen.Home
                        },
                        onCreatePage = ::createPage,
                        onOpenPage = ::openPage,
                        onSavePage = ::savePage,
                        onRenameNotebook = { title -> renameNotebook(notebook, title) },
                        onImportPdf = { uri -> importPdf(uri) },
                        onImportImage = { uri -> importImage(uri) },
                        onExportPage = { page, type -> exportPage(page, type) },
                        onExportToUri = { page, type, uri -> exportPageToUri(page, type, uri) },
                        onLoadExportHistory = ::loadExportHistory,
                        onRerenderPdf = { active -> rerenderPdf(active) },
                    )
                }
            }

            is Screen.Editor -> {
                val notebook = activeNotebook
                val page = activePage
                if (notebook == null || page == null) {
                    screen = Screen.Home
                } else {
                    EditorScreen(
                        notebook = notebook,
                        page = page,
                        onBack = {
                            screen = Screen.NotebookDetail(notebook.id)
                        },
                        onSavePage = ::savePage,
                        onImportImage = { uri -> importImage(uri) },
                        onExportPage = { active, type -> exportPage(active, type) },
                        onExportToUri = { active, type, uri -> exportPageToUri(active, type, uri) },
                        onLoadExportHistory = ::loadExportHistory,
                        onRerenderPdf = { active -> rerenderPdf(active) },
                    )
                }
            }
        }
    }
}
