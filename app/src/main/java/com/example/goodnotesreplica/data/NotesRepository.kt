package com.example.goodnotesreplica.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.min

/**
 * 노트북, 페이지, 내보내기 기록을 관리하는 파일 시스템 기반의 저장소입니다.
 *
 * @property context 파일 및 콘텐츠 접근을 위한 애플리케이션 컨텍스트
 */
class NotesRepository(private val context: Context) {
    /** 모든 노트북 폴더를 포함하는 루트 디렉토리 */
    private val rootDir = File(context.filesDir, "notebooks").apply {
        if (!exists()) {
            mkdirs()
        }
    }
    /** 폴더 메타데이터를 저장하는 영구 라이브러리 인덱스 파일 */
    private val libraryFile = File(rootDir, "library.json")
    /** 내보내기 기록 로그 파일 */
    private val exportHistoryFile = File(context.filesDir, "exports/history.json")
    /** IO 작업을 위한 디스패처 */
    private val ioDispatcher = Dispatchers.IO
    /** CPU 바운드 렌더링 및 레이아웃 작업을 위한 디스패처 */
    private val cpuDispatcher = Dispatchers.Default

    /**
     * 마지막 업데이트 시간순으로 정렬된 노트북 목록을 반환합니다.
     */
    suspend fun listNotebooks(): List<Notebook> = withContext(ioDispatcher) {
        rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readNotebook(File(it, "notebook.json")) }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    /**
     * 이름순으로 정렬된 모든 폴더 목록을 반환합니다.
     */
    suspend fun listFolders(): List<Folder> = withContext(ioDispatcher) {
        readFolders().sortedBy { it.name.lowercase() }
    }

    /**
     * 주어진 이름으로 새 폴더를 생성합니다.
     */
    suspend fun createFolder(name: String): Folder = withContext(ioDispatcher) {
        val trimmed = name.trim().ifEmpty { "Untitled Folder" }
        val now = System.currentTimeMillis()
        val folder = Folder(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = now,
            updatedAt = now,
        )
        val folders = readFolders() + folder
        writeFolders(folders)
        folder
    }

    /**
     * 폴더 이름과 타임스탬프를 업데이트합니다.
     */
    suspend fun renameFolder(folder: Folder, name: String): Folder = withContext(ioDispatcher) {
        val trimmed = name.trim().ifEmpty { folder.name }
        val updated = folder.copy(name = trimmed, updatedAt = System.currentTimeMillis())
        val folders = readFolders().map { if (it.id == folder.id) updated else it }
        writeFolders(folders)
        updated
    }

    /**
     * 폴더를 삭제하고 해당 폴더를 참조하던 노트북의 연결을 해제합니다.
     */
    suspend fun deleteFolder(folderId: String) = withContext(ioDispatcher) {
        val updatedFolders = readFolders().filterNot { it.id == folderId }
        writeFolders(updatedFolders)
        val notebooks = listNotebooks().map { notebook ->
            if (notebook.folderId == folderId) notebook.copy(folderId = null) else notebook
        }
        notebooks.forEach { writeNotebook(File(rootDir, "${it.id}/notebook.json"), it) }
    }

    /**
     * 선택적 폴더 아래에 새 노트북을 생성합니다.
     */
    suspend fun createNotebook(title: String, folderId: String? = null): Notebook = withContext(ioDispatcher) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val notebook = Notebook(
            id = id,
            title = title.trim().ifEmpty { "Untitled Notebook" },
            createdAt = now,
            updatedAt = now,
            pageCount = 0,
            coverColor = pickCoverColor(),
            folderId = folderId,
            tags = emptyList(),
        )
        val notebookDir = File(rootDir, id).apply { mkdirs() }
        File(notebookDir, "pages").mkdirs()
        File(notebookDir, "assets").mkdirs()
        writeNotebook(File(notebookDir, "notebook.json"), notebook)
        notebook
    }

    /**
     * 노트북의 이름을 변경하고 타임스탬프를 업데이트합니다.
     */
    suspend fun renameNotebook(notebook: Notebook, newTitle: String): Notebook = withContext(ioDispatcher) {
        val updated = notebook.copy(
            title = newTitle.trim().ifEmpty { notebook.title },
            updatedAt = System.currentTimeMillis(),
        )
        writeNotebook(File(rootDir, "${notebook.id}/notebook.json"), updated)
        updated
    }

    /**
     * 노트북을 다른 폴더로 이동합니다 (또는 폴더 없음으로 설정).
     */
    suspend fun updateNotebookFolder(notebook: Notebook, folderId: String?): Notebook = withContext(ioDispatcher) {
        val updated = notebook.copy(
            folderId = folderId,
            updatedAt = System.currentTimeMillis(),
        )
        writeNotebook(File(rootDir, "${notebook.id}/notebook.json"), updated)
        updated
    }

    /**
     * 노트북의 태그 목록을 업데이트합니다.
     */
    suspend fun updateNotebookTags(notebook: Notebook, tags: List<String>): Notebook = withContext(ioDispatcher) {
        val updated = notebook.copy(
            tags = normalizeTags(tags),
            updatedAt = System.currentTimeMillis(),
        )
        writeNotebook(File(rootDir, "${notebook.id}/notebook.json"), updated)
        updated
    }

    /**
     * 노트북과 해당 디스크 폴더를 삭제합니다.
     */
    suspend fun deleteNotebook(notebookId: String) = withContext(ioDispatcher) {
        val notebookDir = File(rootDir, notebookId)
        notebookDir.deleteRecursively()
    }

    /**
     * 노트북의 모든 페이지를 반환합니다.
     */
    suspend fun listPages(notebookId: String): List<Page> = withContext(ioDispatcher) {
        val pagesDir = File(File(rootDir, notebookId), "pages")
        if (!pagesDir.exists()) return@withContext emptyList()
        pagesDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { readPage(it) }
            ?.sortedBy { it.index }
            ?: emptyList()
    }

    /**
     * ID로 단일 페이지를 로드합니다.
     */
    suspend fun loadPage(notebookId: String, pageId: String): Page? = withContext(ioDispatcher) {
        readPage(pageFile(notebookId, pageId))
    }

    /**
     * 노트북에 새 빈 페이지를 생성합니다.
     */
    suspend fun createPage(
        notebookId: String,
        paperStyle: PaperStyle,
        backgroundPath: String? = null,
        aspectRatio: Float = DEFAULT_PAGE_RATIO,
    ): Page = withContext(ioDispatcher) {
        val pages = listPages(notebookId)
        val nextIndex = (pages.maxOfOrNull { it.index } ?: 0) + 1
        val page = Page(
            id = UUID.randomUUID().toString(),
            index = nextIndex,
            paperStyle = paperStyle,
            strokes = emptyList(),
            textItems = emptyList(),
            imageItems = emptyList(),
            backgroundPath = backgroundPath,
            aspectRatio = aspectRatio,
            sourcePdfPath = null,
            sourcePdfPageIndex = null,
            updatedAt = System.currentTimeMillis(),
        )
        writePage(pageFile(notebookId, page.id), page)
        updateNotebookCounts(notebookId, pages.size + 1)
        page
    }

    /**
     * 이미지를 노트북 에셋으로 복사하고 파일 경로를 반환합니다.
     */
    suspend fun importImage(notebookId: String, uri: Uri): String? = withContext(ioDispatcher) {
        val assetsDir = File(File(rootDir, notebookId), "assets").apply { mkdirs() }
        val extension = guessExtension(uri)
        val file = File(assetsDir, "img_${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
        input.use { stream ->
            file.outputStream().use { output ->
                stream.copyTo(output)
            }
        }
        file.absolutePath
    }

    /**
     * PDF를 배경 페이지로 가져와 노트북에 추가합니다.
     */
    suspend fun importPdfAsPages(notebookId: String, uri: Uri): List<Page> = withContext(ioDispatcher) {
        val assetsDir = File(File(rootDir, notebookId), "assets").apply { mkdirs() }
        val pdfFile = File(assetsDir, "source_${System.currentTimeMillis()}_${UUID.randomUUID()}.pdf")
        val pdfCopied = copyUriToFile(uri, pdfFile)
        if (!pdfCopied) return@withContext emptyList()
        val existingPages = listPages(notebookId)
        val startIndex = (existingPages.maxOfOrNull { it.index } ?: 0)
        val pages = mutableListOf<Page>()
        val descriptor = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                val stamp = System.currentTimeMillis()
                val maxSide = 1600f
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { pdfPage ->
                        val ratio = pdfPage.width.toFloat() / pdfPage.height.toFloat()
                        val scale = min(1f, maxSide / maxOf(pdfPage.width, pdfPage.height).toFloat())
                        val width = (pdfPage.width * scale).toInt().coerceAtLeast(1)
                        val height = (pdfPage.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        val matrix = Matrix().apply { setScale(scale, scale) }
                        pdfPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val file = File(assetsDir, "pdf_${stamp}_$i.png")
                        file.outputStream().use { output ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                        }
                        val page = Page(
                            id = UUID.randomUUID().toString(),
                            index = startIndex + i + 1,
                            paperStyle = PaperStyle.BLANK,
                            strokes = emptyList(),
                            textItems = emptyList(),
                            imageItems = emptyList(),
                            backgroundPath = file.absolutePath,
                            aspectRatio = ratio,
                            sourcePdfPath = pdfFile.absolutePath,
                            sourcePdfPageIndex = i,
                            updatedAt = System.currentTimeMillis(),
                        )
                        writePage(pageFile(notebookId, page.id), page)
                        pages.add(page)
                    }
                }
            }
        }
        updateNotebookCounts(notebookId, existingPages.size + pages.size)
        pages
    }

    /**
     * 최신 페이지 내용과 노트북 타임스탬프를 저장합니다.
     */
    suspend fun savePage(notebookId: String, page: Page) = withContext(ioDispatcher) {
        writePage(pageFile(notebookId, page.id), page)
        updateNotebookTimestamp(notebookId)
    }

    /**
     * 페이지를 앱 저장소에 PNG 파일로 내보냅니다.
     */
    suspend fun exportPageAsPng(page: Page, notebook: Notebook): File? = withContext(ioDispatcher) {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val bitmap = withContext(cpuDispatcher) {
            renderPageBitmap(page, EXPORT_WIDTH)
        }
        val file = File(exportDir, "page_${page.index}_${System.currentTimeMillis()}.png")
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        recordExport(
            buildExportRecord(
                page = page,
                notebook = notebook,
                type = ExportType.PNG,
                targetKind = ExportTargetKind.FILE,
                target = file.absolutePath,
            )
        )
        file
    }

    /**
     * 페이지를 앱 저장소에 PDF 파일로 내보냅니다.
     */
    suspend fun exportPageAsPdf(page: Page, notebook: Notebook): File? = withContext(ioDispatcher) {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val bitmap = withContext(cpuDispatcher) {
            renderPageBitmap(page, EXPORT_WIDTH)
        }
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
        val pdfPage = document.startPage(pageInfo)
        pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
        document.finishPage(pdfPage)
        val file = File(exportDir, "page_${page.index}_${System.currentTimeMillis()}.pdf")
        file.outputStream().use { output ->
            document.writeTo(output)
        }
        document.close()
        recordExport(
            buildExportRecord(
                page = page,
                notebook = notebook,
                type = ExportType.PDF,
                targetKind = ExportTargetKind.FILE,
                target = file.absolutePath,
            )
        )
        file
    }

    /**
     * 호출자가 제공한 문서 URI로 페이지를 내보냅니다.
     */
    suspend fun exportPageToUri(
        page: Page,
        notebook: Notebook,
        type: ExportType,
        uri: Uri,
    ): Boolean = withContext(ioDispatcher) {
        val resolver = context.contentResolver
        val output = resolver.openOutputStream(uri) ?: return@withContext false
        output.use { stream ->
            when (type) {
                ExportType.PNG -> {
                    val bitmap = withContext(cpuDispatcher) {
                        renderPageBitmap(page, EXPORT_WIDTH)
                    }
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                ExportType.PDF -> {
                    val bitmap = withContext(cpuDispatcher) {
                        renderPageBitmap(page, EXPORT_WIDTH)
                    }
                    val document = PdfDocument()
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
                    val pdfPage = document.startPage(pageInfo)
                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(pdfPage)
                    document.writeTo(stream)
                    document.close()
                }
            }
        }
        recordExport(
            buildExportRecord(
                page = page,
                notebook = notebook,
                type = type,
                targetKind = ExportTargetKind.URI,
                target = uri.toString(),
            )
        )
        true
    }

    /**
     * 최신순으로 정렬된 내보내기 기록을 반환합니다.
     */
    suspend fun listExportHistory(): List<ExportRecord> = withContext(ioDispatcher) {
        readExportHistory().sortedByDescending { it.createdAt }
    }

    /**
     * PDF 기반 페이지 배경을 다시 렌더링하고 업데이트된 페이지를 반환합니다.
     */
    suspend fun rerenderPdfPage(page: Page): Page? = withContext(ioDispatcher) {
        val source = page.sourcePdfPath ?: return@withContext null
        val pageIndex = page.sourcePdfPageIndex ?: return@withContext null
        val pdfFile = File(source)
        if (!pdfFile.exists()) return@withContext null
        val outputDir = pdfFile.parentFile ?: return@withContext null
        val rendered = withContext(cpuDispatcher) {
            renderPdfPageToPng(pdfFile, pageIndex, outputDir)
        } ?: return@withContext null
        page.copy(
            backgroundPath = rendered.path,
            aspectRatio = rendered.aspectRatio,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 제목, 태그 또는 페이지 텍스트 내용으로 노트북을 검색합니다.
     */
    suspend fun searchNotebooks(query: String): List<Notebook> = withContext(ioDispatcher) {
        val term = query.trim().lowercase()
        if (term.isEmpty()) return@withContext listNotebooks()
        val notebooks = listNotebooks()
        notebooks.filter { notebook ->
            if (notebook.title.lowercase().contains(term)) return@filter true
            if (notebook.tags.any { it.lowercase().contains(term) }) return@filter true
            val pages = listPages(notebook.id)
            pages.any { page ->
                page.textItems.any { it.text.lowercase().contains(term) }
            }
        }
    }

    private fun pageFile(notebookId: String, pageId: String): File {
        val pagesDir = File(File(rootDir, notebookId), "pages").apply { mkdirs() }
        return File(pagesDir, "$pageId.json")
    }

    private fun readNotebook(file: File): Notebook? {
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            Notebook(
                id = json.getString("id"),
                title = json.getString("title"),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt"),
                pageCount = json.optInt("pageCount", 0),
                coverColor = json.optInt("coverColor", 0xFF1C7C7D.toInt()),
                folderId = json.optString("folderId", "").takeIf { it.isNotBlank() },
                tags = json.optJSONArray("tags")?.let { tagsFromJson(it) } ?: emptyList(),
            )
        }.getOrNull()
    }

    private fun writeNotebook(file: File, notebook: Notebook) {
        val json = JSONObject()
            .put("id", notebook.id)
            .put("title", notebook.title)
            .put("createdAt", notebook.createdAt)
            .put("updatedAt", notebook.updatedAt)
            .put("pageCount", notebook.pageCount)
            .put("coverColor", notebook.coverColor)
            .put("folderId", notebook.folderId)
            .put("tags", JSONArray(notebook.tags))
        file.writeText(json.toString())
    }

    private fun readPage(file: File): Page? {
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            Page(
                id = json.getString("id"),
                index = json.getInt("index"),
                paperStyle = PaperStyle.valueOf(json.optString("paperStyle", PaperStyle.BLANK.name)),
                strokes = json.optJSONArray("strokes")?.let { strokesFromJson(it) } ?: emptyList(),
                textItems = json.optJSONArray("textItems")?.let { textItemsFromJson(it) } ?: emptyList(),
                imageItems = json.optJSONArray("imageItems")?.let { imageItemsFromJson(it) } ?: emptyList(),
                backgroundPath = json.optString("backgroundPath", "").takeIf { it.isNotBlank() },
                aspectRatio = json.optDouble("aspectRatio", DEFAULT_PAGE_RATIO.toDouble()).toFloat(),
                sourcePdfPath = json.optString("sourcePdfPath", "").takeIf { it.isNotBlank() },
                sourcePdfPageIndex = json.optInt("sourcePdfPageIndex", -1).takeIf { it >= 0 },
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            )
        }.getOrNull()
    }

    private fun writePage(file: File, page: Page) {
        val json = JSONObject()
            .put("id", page.id)
            .put("index", page.index)
            .put("paperStyle", page.paperStyle.name)
            .put("updatedAt", page.updatedAt)
            .put("strokes", strokesToJson(page.strokes))
            .put("textItems", textItemsToJson(page.textItems))
            .put("imageItems", imageItemsToJson(page.imageItems))
            .put("backgroundPath", page.backgroundPath)
            .put("aspectRatio", page.aspectRatio)
            .put("sourcePdfPath", page.sourcePdfPath)
            .put("sourcePdfPageIndex", page.sourcePdfPageIndex)
        file.writeText(json.toString())
    }

    private fun strokesToJson(strokes: List<Stroke>): JSONArray {
        val array = JSONArray()
        strokes.forEach { stroke ->
            val points = JSONArray()
            stroke.points.forEach { point ->
                points.put(JSONObject().put("x", point.x).put("y", point.y))
            }
            array.put(
                JSONObject()
                    .put("id", stroke.id)
                    .put("tool", stroke.tool.name)
                    .put("color", stroke.color)
                    .put("widthDp", stroke.widthDp)
                    .put("points", points)
            )
        }
        return array
    }

    private fun strokesFromJson(array: JSONArray): List<Stroke> {
        val result = mutableListOf<Stroke>()
        for (i in 0 until array.length()) {
            val strokeJson = array.optJSONObject(i) ?: continue
            val pointsArray = strokeJson.optJSONArray("points") ?: JSONArray()
            val points = mutableListOf<StrokePoint>()
            for (j in 0 until pointsArray.length()) {
                val pointJson = pointsArray.optJSONObject(j) ?: continue
                points.add(
                    StrokePoint(
                        x = pointJson.optDouble("x", 0.0).toFloat(),
                        y = pointJson.optDouble("y", 0.0).toFloat(),
                    )
                )
            }
            result.add(
                Stroke(
                    id = strokeJson.optString("id", UUID.randomUUID().toString()),
                    tool = ToolType.valueOf(strokeJson.optString("tool", ToolType.PEN.name)),
                    color = strokeJson.optInt("color", 0xFF2E2B24.toInt()),
                    widthDp = strokeJson.optDouble("widthDp", 2.5).toFloat(),
                    points = points,
                )
            )
        }
        return result
    }

    private fun textItemsToJson(items: List<TextItem>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("text", item.text)
                    .put("x", item.x)
                    .put("y", item.y)
                    .put("width", item.width)
                    .put("height", item.height)
                    .put("fontSizeSp", item.fontSizeSp)
                    .put("color", item.color)
                    .put("rotation", item.rotation)
            )
        }
        return array
    }

    private fun textItemsFromJson(array: JSONArray): List<TextItem> {
        val result = mutableListOf<TextItem>()
        for (i in 0 until array.length()) {
            val itemJson = array.optJSONObject(i) ?: continue
            result.add(
                TextItem(
                    id = itemJson.optString("id", UUID.randomUUID().toString()),
                    text = itemJson.optString("text", ""),
                    x = itemJson.optDouble("x", 0.1).toFloat(),
                    y = itemJson.optDouble("y", 0.1).toFloat(),
                    width = itemJson.optDouble("width", 0.4).toFloat(),
                    height = itemJson.optDouble("height", 0.1).toFloat(),
                    fontSizeSp = itemJson.optDouble("fontSizeSp", 18.0).toFloat(),
                    color = itemJson.optInt("color", 0xFF2E2B24.toInt()),
                    rotation = itemJson.optDouble("rotation", 0.0).toFloat(),
                )
            )
        }
        return result
    }

    private fun imageItemsToJson(items: List<ImageItem>): JSONArray {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("path", item.path)
                    .put("x", item.x)
                    .put("y", item.y)
                    .put("width", item.width)
                    .put("height", item.height)
                    .put("rotation", item.rotation)
            )
        }
        return array
    }

    private fun imageItemsFromJson(array: JSONArray): List<ImageItem> {
        val result = mutableListOf<ImageItem>()
        for (i in 0 until array.length()) {
            val itemJson = array.optJSONObject(i) ?: continue
            result.add(
                ImageItem(
                    id = itemJson.optString("id", UUID.randomUUID().toString()),
                    path = itemJson.optString("path", ""),
                    x = itemJson.optDouble("x", 0.1).toFloat(),
                    y = itemJson.optDouble("y", 0.1).toFloat(),
                    width = itemJson.optDouble("width", 0.4).toFloat(),
                    height = itemJson.optDouble("height", 0.3).toFloat(),
                    rotation = itemJson.optDouble("rotation", 0.0).toFloat(),
                )
            )
        }
        return result
    }

    private fun buildExportRecord(
        page: Page,
        notebook: Notebook,
        type: ExportType,
        targetKind: ExportTargetKind,
        target: String,
    ): ExportRecord {
        return ExportRecord(
            id = UUID.randomUUID().toString(),
            notebookId = notebook.id,
            notebookTitle = notebook.title,
            pageId = page.id,
            pageIndex = page.index,
            exportType = type,
            targetKind = targetKind,
            target = target,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun recordExport(record: ExportRecord) {
        val records = readExportHistory().toMutableList()
        records.add(record)
        val trimmed = if (records.size > EXPORT_HISTORY_LIMIT) {
            records.takeLast(EXPORT_HISTORY_LIMIT)
        } else {
            records
        }
        writeExportHistory(trimmed)
    }

    private fun readExportHistory(): List<ExportRecord> {
        if (!exportHistoryFile.exists()) return emptyList()
        return runCatching {
            val json = JSONObject(exportHistoryFile.readText())
            val array = json.optJSONArray("exports") ?: JSONArray()
            val result = mutableListOf<ExportRecord>()
            for (i in 0 until array.length()) {
                val recordJson = array.optJSONObject(i) ?: continue
                val type = runCatching {
                    ExportType.valueOf(recordJson.optString("exportType", ExportType.PNG.name))
                }.getOrElse { ExportType.PNG }
                val targetKind = runCatching {
                    ExportTargetKind.valueOf(recordJson.optString("targetKind", ExportTargetKind.FILE.name))
                }.getOrElse { ExportTargetKind.FILE }
                result.add(
                    ExportRecord(
                        id = recordJson.optString("id", UUID.randomUUID().toString()),
                        notebookId = recordJson.optString("notebookId", ""),
                        notebookTitle = recordJson.optString("notebookTitle", ""),
                        pageId = recordJson.optString("pageId", ""),
                        pageIndex = recordJson.optInt("pageIndex", 0),
                        exportType = type,
                        targetKind = targetKind,
                        target = recordJson.optString("target", ""),
                        createdAt = recordJson.optLong("createdAt", System.currentTimeMillis()),
                    )
                )
            }
            result
        }.getOrNull() ?: emptyList()
    }

    private fun writeExportHistory(records: List<ExportRecord>) {
        exportHistoryFile.parentFile?.mkdirs()
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("notebookId", record.notebookId)
                    .put("notebookTitle", record.notebookTitle)
                    .put("pageId", record.pageId)
                    .put("pageIndex", record.pageIndex)
                    .put("exportType", record.exportType.name)
                    .put("targetKind", record.targetKind.name)
                    .put("target", record.target)
                    .put("createdAt", record.createdAt)
            )
        }
        exportHistoryFile.writeText(JSONObject().put("exports", array).toString())
    }

    private fun updateNotebookTimestamp(notebookId: String) {
        val file = File(rootDir, "$notebookId/notebook.json")
        val notebook = readNotebook(file) ?: return
        writeNotebook(
            file,
            notebook.copy(updatedAt = System.currentTimeMillis())
        )
    }

    private fun updateNotebookCounts(notebookId: String, pageCount: Int) {
        val file = File(rootDir, "$notebookId/notebook.json")
        val notebook = readNotebook(file) ?: return
        writeNotebook(
            file,
            notebook.copy(
                updatedAt = System.currentTimeMillis(),
                pageCount = pageCount,
            )
        )
    }

    private fun readFolders(): List<Folder> {
        if (!libraryFile.exists()) return emptyList()
        return runCatching {
            val json = JSONObject(libraryFile.readText())
            val array = json.optJSONArray("folders") ?: JSONArray()
            val result = mutableListOf<Folder>()
            for (i in 0 until array.length()) {
                val folderJson = array.optJSONObject(i) ?: continue
                result.add(
                    Folder(
                        id = folderJson.getString("id"),
                        name = folderJson.getString("name"),
                        createdAt = folderJson.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = folderJson.optLong("updatedAt", System.currentTimeMillis()),
                    )
                )
            }
            result
        }.getOrNull() ?: emptyList()
    }

    private fun writeFolders(folders: List<Folder>) {
        val array = JSONArray()
        folders.forEach { folder ->
            array.put(
                JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("createdAt", folder.createdAt)
                    .put("updatedAt", folder.updatedAt)
            )
        }
        libraryFile.writeText(JSONObject().put("folders", array).toString())
    }

    private fun tagsFromJson(array: JSONArray): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty()) {
                result.add(value)
            }
        }
        return result
    }

    private fun normalizeTags(tags: List<String>): List<String> {
        return tags.mapNotNull { it.trim().ifEmpty { null } }.distinct()
    }

    private fun guessExtension(uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: return "jpg"
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
    }

    private fun copyUriToFile(uri: Uri, file: File): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        }.getOrDefault(false)
    }

    private data class RenderedPage(
        val path: String,
        val aspectRatio: Float,
    )

    private fun renderPdfPageToPng(
        pdfFile: File,
        pageIndex: Int,
        outputDir: File,
    ): RenderedPage? {
        if (!pdfFile.exists()) return null
        val descriptor = android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { pdfPage ->
                    val maxSide = 1600f
                    val ratio = pdfPage.width.toFloat() / pdfPage.height.toFloat()
                    val scale = min(1f, maxSide / maxOf(pdfPage.width, pdfPage.height).toFloat())
                    val width = (pdfPage.width * scale).toInt().coerceAtLeast(1)
                    val height = (pdfPage.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val matrix = Matrix().apply { setScale(scale, scale) }
                    pdfPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val file = File(outputDir, "pdf_${System.currentTimeMillis()}_${UUID.randomUUID()}.png")
                    file.outputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                    }
                    return RenderedPage(file.absolutePath, ratio)
                }
            }
        }
        return null
    }

    private fun renderPageBitmap(page: Page, targetWidth: Int): Bitmap {
        val ratio = if (page.aspectRatio > 0f) page.aspectRatio else DEFAULT_PAGE_RATIO
        val width = targetWidth
        val height = (targetWidth / ratio).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val backgroundPath = page.backgroundPath
        if (!backgroundPath.isNullOrBlank()) {
            val backgroundFile = File(backgroundPath)
            if (backgroundFile.exists()) {
                val backgroundBitmap = android.graphics.BitmapFactory.decodeFile(backgroundFile.absolutePath)
                if (backgroundBitmap != null) {
                    val src = android.graphics.Rect(0, 0, backgroundBitmap.width, backgroundBitmap.height)
                    val dst = android.graphics.Rect(0, 0, width, height)
                    canvas.drawBitmap(backgroundBitmap, src, dst, null)
                }
            }
        } else {
            drawPaper(canvas, width, height, page.paperStyle)
        }

        val density = width / 360f
        drawImages(canvas, page.imageItems, width, height)
        drawStrokes(canvas, page.strokes, width, height, density)
        drawTextItems(canvas, page.textItems, width, height, density)
        return bitmap
    }

    private fun drawPaper(canvas: Canvas, width: Int, height: Int, paperStyle: PaperStyle) {
        val ink = android.graphics.Color.parseColor("#B4AD9F")
        val soft = android.graphics.Color.parseColor("#DDD6C8")
        val spacing = 32f
        val margin = 44f
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = 1f
            color = soft
        }
        when (paperStyle) {
            PaperStyle.BLANK -> Unit
            PaperStyle.LINED -> {
                var y = spacing
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
                    y += spacing
                }
                val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                    color = ink
                    alpha = 160
                }
                canvas.drawLine(margin, 0f, margin, height.toFloat(), marginPaint)
            }
            PaperStyle.GRID -> {
                var x = spacing
                while (x < width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
                    x += spacing
                }
                var y = spacing
                while (y < height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
                    y += spacing
                }
            }
            PaperStyle.DOT -> {
                val radius = 1.4f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = soft }
                var x = spacing
                while (x < width) {
                    var y = spacing
                    while (y < height) {
                        canvas.drawCircle(x, y, radius, paint)
                        y += spacing
                    }
                    x += spacing
                }
            }
        }
    }

    private fun drawStrokes(
        canvas: Canvas,
        strokes: List<Stroke>,
        width: Int,
        height: Int,
        density: Float,
    ) {
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val path = Path()
            val first = stroke.points.first()
            path.moveTo(first.x * width, first.y * height)
            stroke.points.drop(1).forEach { point ->
                path.lineTo(point.x * width, point.y * height)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = stroke.color
                strokeWidth = stroke.widthDp * density
                if (stroke.tool == ToolType.HIGHLIGHTER) {
                    alpha = 100
                }
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawTextItems(
        canvas: Canvas,
        items: List<TextItem>,
        width: Int,
        height: Int,
        density: Float,
    ) {
        items.forEach { item ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.color
                textSize = item.fontSizeSp * density
                typeface = Typeface.SERIF
            }
            val content = if (item.text.isEmpty()) " " else item.text
            val boxWidth = (item.width * width).coerceAtLeast(1f)
            val boxHeight = item.height * height
            val startX = item.x * width
            val startY = item.y * height
            val centerX = startX + boxWidth / 2f
            val centerY = startY + boxHeight / 2f
            val layout = StaticLayout.Builder
                .obtain(content, 0, content.length, paint, boxWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            canvas.save()
            if (item.rotation != 0f) {
                canvas.rotate(item.rotation, centerX, centerY)
            }
            canvas.translate(startX, startY)
            if (boxHeight > 0f) {
                canvas.clipRect(0f, 0f, boxWidth, boxHeight)
            }
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawImages(
        canvas: Canvas,
        items: List<ImageItem>,
        width: Int,
        height: Int,
    ) {
        items.forEach { item ->
            val file = File(item.path)
            if (!file.exists()) return@forEach
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
            val left = item.x * width
            val top = item.y * height
            val right = left + item.width * width
            val bottom = top + item.height * height
            val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
            val dst = android.graphics.RectF(left, top, right, bottom)
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f
            canvas.save()
            if (item.rotation != 0f) {
                canvas.rotate(item.rotation, centerX, centerY)
            }
            canvas.drawBitmap(bitmap, src, dst, null)
            canvas.restore()
        }
    }

    private fun pickCoverColor(): Int {
        val palette = listOf(
            0xFF1C7C7D.toInt(),
            0xFFE07A5F.toInt(),
            0xFF3D405B.toInt(),
            0xFF81B29A.toInt(),
            0xFFF2CC8F.toInt(),
        )
        return palette.random()
    }

    companion object {
        private const val DEFAULT_PAGE_RATIO = 0.72f
        private const val EXPORT_WIDTH = 1600
        private const val EXPORT_HISTORY_LIMIT = 200
    }
}
