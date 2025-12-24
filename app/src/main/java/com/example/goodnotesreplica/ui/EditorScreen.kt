package com.example.goodnotesreplica.ui

import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import com.example.goodnotesreplica.data.ExportType
import com.example.goodnotesreplica.data.ExportRecord
import com.example.goodnotesreplica.data.ImageItem
import com.example.goodnotesreplica.data.Notebook
import com.example.goodnotesreplica.data.Page
import com.example.goodnotesreplica.data.PaperStyle
import com.example.goodnotesreplica.data.Stroke
import com.example.goodnotesreplica.data.StrokePoint
import com.example.goodnotesreplica.data.TextItem
import com.example.goodnotesreplica.data.ToolType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 노트 편집을 위한 메인 화면 컴포저블입니다.
 * 툴바, 캔버스, 내보내기 메뉴 등을 포함합니다.
 *
 * @param notebook 편집 중인 노트북 정보
 * @param page 편집 중인 페이지 정보
 * @param onBack 뒤로 가기 콜백
 * @param onSavePage 페이지 저장 콜백
 * @param onImportImage 이미지 가져오기 콜백
 * @param onExportPage 페이지 파일 내보내기 콜백
 * @param onExportToUri URI로 페이지 내보내기 콜백
 * @param onLoadExportHistory 내보내기 기록 로드 콜백
 * @param onRerenderPdf PDF 다시 렌더링 콜백
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditorScreen(
    notebook: Notebook,
    page: Page,
    onBack: () -> Unit,
    onSavePage: (Page) -> Unit,
    onImportImage: suspend (Uri) -> String?,
    onExportPage: suspend (Page, ExportType) -> File?,
    onExportToUri: suspend (Page, ExportType, Uri) -> Boolean,
    onLoadExportHistory: suspend () -> List<ExportRecord>,
    onRerenderPdf: suspend (Page) -> Page?,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingImagePath by remember { mutableStateOf<String?>(null) }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var exportHistoryOpen by remember { mutableStateOf(false) }
    var exportHistory by remember { mutableStateOf<List<ExportRecord>>(emptyList()) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                pendingImagePath = onImportImage(uri)
            }
        }
    }
    val savePngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = onExportToUri(page, ExportType.PNG, uri)
                val message = if (saved) "PNG 저장 완료" else "PNG 저장 실패"
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    val savePdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = onExportToUri(page, ExportType.PDF, uri)
                val message = if (saved) "PDF 저장 완료" else "PDF 저장 실패"
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = notebook.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Page ${page.index}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { exportMenuExpanded = true }) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = exportMenuExpanded,
                        onDismissRequest = { exportMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("내보내기 기록") },
                            onClick = {
                                exportMenuExpanded = false
                                scope.launch {
                                    exportHistory = onLoadExportHistory()
                                    exportHistoryOpen = true
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("PNG 공유") },
                            onClick = {
                                exportMenuExpanded = false
                                scope.launch {
                                    val file = onExportPage(page, ExportType.PNG)
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
                                exportMenuExpanded = false
                                val name = "PaperStudio_Page_${page.index}.png"
                                savePngLauncher.launch(name)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("PDF 공유") },
                            onClick = {
                                exportMenuExpanded = false
                                scope.launch {
                                    val file = onExportPage(page, ExportType.PDF)
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
                                exportMenuExpanded = false
                                val name = "PaperStudio_Page_${page.index}.pdf"
                                savePdfLauncher.launch(name)
                            },
                        )
                        val sourcePdf = page.sourcePdfPath?.let { File(it) }
                        if (sourcePdf != null && sourcePdf.exists()) {
                            DropdownMenuItem(
                                text = { Text("PDF 원본 보기") },
                                onClick = {
                                    exportMenuExpanded = false
                                    openFile(context, sourcePdf, "application/pdf")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("PDF 원본 공유") },
                                onClick = {
                                    exportMenuExpanded = false
                                    shareFile(context, sourcePdf, "application/pdf", "PDF 원본 공유")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("PDF 다시 렌더") },
                                onClick = {
                                    exportMenuExpanded = false
                                    scope.launch {
                                        val updated = onRerenderPdf(page)
                                        val message = if (updated != null) "PDF 다시 렌더 완료" else "PDF 다시 렌더 실패"
                                        snackbarHostState.showSnackbar(message)
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    ComposeBrush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant,
                        )
                    )
                )
        ) {
            EditorPanel(
                page = page,
                onSavePage = onSavePage,
                onRequestImagePick = { imagePicker.launch("image/*") },
                insertImagePath = pendingImagePath,
                onConsumeImagePath = { pendingImagePath = null },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )
        }
    }

    if (exportHistoryOpen) {
        ExportHistoryDialog(
            records = exportHistory,
            onDismiss = { exportHistoryOpen = false },
        )
    }

}

/**
 * 캔버스와 툴바를 포함하는 실제 편집 패널입니다.
 *
 * @param page 편집할 페이지
 * @param onSavePage 페이지 저장 콜백
 * @param onRequestImagePick 이미지 선택 요청 콜백
 * @param insertImagePath 삽입할 이미지 경로 (선택된 경우)
 * @param onConsumeImagePath 이미지 경로 소비(처리 완료) 콜백
 * @param modifier 수정자
 */
@Composable
fun EditorPanel(
    page: Page,
    onSavePage: (Page) -> Unit,
    onRequestImagePick: () -> Unit,
    insertImagePath: String?,
    onConsumeImagePath: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var currentTool by remember { mutableStateOf(ToolType.PEN) }
    var strokeWidth by remember { mutableStateOf(3.5f) }
    var selectedColor by remember { mutableStateOf(Color(0xFF2E2B24)) }
    var paperStyle by remember(page.id) { mutableStateOf(page.paperStyle) }
    var backgroundPath by remember(page.id) { mutableStateOf(page.backgroundPath) }
    var canvasSize by remember(page.id) { mutableStateOf(IntSize.Zero) }
    var zoomScale by remember(page.id) { mutableStateOf(1f) }
    var zoomOffset by remember(page.id) { mutableStateOf(Offset.Zero) }

    val strokes = remember(page.id) { mutableStateListOf<Stroke>().apply { addAll(page.strokes) } }
    val textItems = remember(page.id) { mutableStateListOf<TextItem>().apply { addAll(page.textItems) } }
    val imageItems = remember(page.id) { mutableStateListOf<ImageItem>().apply { addAll(page.imageItems) } }

    val undoStack = remember(page.id) { mutableStateListOf<EditAction>() }
    val redoStack = remember(page.id) { mutableStateListOf<EditAction>() }

    var selectionState by remember(page.id) { mutableStateOf(SelectionState()) }
    var moveSnapshot by remember(page.id) { mutableStateOf<PageSnapshot?>(null) }
    var transformState by remember(page.id) { mutableStateOf<TransformState?>(null) }
    var snapGuides by remember(page.id) { mutableStateOf(SnapGuides()) }

    var textDialogOpen by remember { mutableStateOf(false) }
    var textDialogValue by remember { mutableStateOf("") }
    var textDialogSize by remember { mutableStateOf(20f) }
    var textDialogColor by remember { mutableStateOf(Color(0xFF2E2B24)) }
    var textDialogTarget by remember { mutableStateOf<StrokePoint?>(null) }
    var editingTextId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(page.id) {
        strokes.clear()
        strokes.addAll(page.strokes)
        textItems.clear()
        textItems.addAll(page.textItems)
        imageItems.clear()
        imageItems.addAll(page.imageItems)
        undoStack.clear()
        redoStack.clear()
        paperStyle = page.paperStyle
        backgroundPath = page.backgroundPath
        selectionState = SelectionState()
        moveSnapshot = null
        transformState = null
        snapGuides = SnapGuides()
        zoomScale = 1f
        zoomOffset = Offset.Zero
    }

    LaunchedEffect(currentTool) {
        if (currentTool != ToolType.LASSO && !selectionState.isEmpty) {
            selectionState = SelectionState()
            transformState = null
            snapGuides = SnapGuides()
        }
    }

    LaunchedEffect(insertImagePath) {
        val path = insertImagePath ?: return@LaunchedEffect
        recordChange(
            strokes = strokes,
            textItems = textItems,
            imageItems = imageItems,
            paperStyle = paperStyle,
            backgroundPath = backgroundPath,
            undoStack = undoStack,
            redoStack = redoStack,
        ) {
            imageItems.add(createImageItem(path))
        }
        selectionState = SelectionState()
        snapGuides = SnapGuides()
        persistPage(
            page = page,
            strokes = strokes,
            textItems = textItems,
            imageItems = imageItems,
            paperStyle = paperStyle,
            backgroundPath = backgroundPath,
            onSavePage = onSavePage,
        )
        onConsumeImagePath()
    }

    val zoomGestureModifier = Modifier.pointerInput(canvasSize) {
        awaitEachGesture {
            var workingScale = zoomScale
            var workingOffset = zoomOffset
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    workingScale = (workingScale * zoomChange).coerceIn(1f, 3f)
                    workingOffset = clampZoomOffset(workingOffset + panChange, workingScale, canvasSize)
                    zoomScale = workingScale
                    zoomOffset = workingOffset
                    event.changes.forEach { it.consume() }
                }
                if (pressed.isEmpty()) break
            }
        }
    }

    LaunchedEffect(canvasSize, page.id) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        var updated = false
        textItems.forEachIndexed { index, item ->
            val newHeight = computeTextHeightNormalized(
                text = item.text,
                fontSizeSp = item.fontSizeSp,
                widthNormalized = item.width,
                canvasSize = canvasSize,
                density = density.density,
            )
            if (abs(newHeight - item.height) > 0.001f) {
                val maxY = (1f - newHeight).coerceAtLeast(0f)
                textItems[index] = item.copy(
                    height = newHeight,
                    y = item.y.coerceIn(0f, maxY),
                )
                updated = true
            }
        }
        if (updated) {
            selectionState = SelectionState()
            persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
        }
        zoomOffset = clampZoomOffset(zoomOffset, zoomScale, canvasSize)
    }

    fun updateSelection(selection: SelectionIds) {
        val bounds = computeSelectionBounds(strokes, textItems, imageItems, selection)
        val transformTarget = computeTransformTarget(selection, textItems, imageItems)
        selectionState = SelectionState(selection, bounds, transformTarget)
        snapGuides = SnapGuides()
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        applySnapshot(action.before, strokes, textItems, imageItems) {
            paperStyle = it
            backgroundPath = action.before.backgroundPath
        }
        redoStack.add(action)
        selectionState = SelectionState()
        snapGuides = SnapGuides()
        persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
    }

    fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        applySnapshot(action.after, strokes, textItems, imageItems) {
            paperStyle = it
            backgroundPath = action.after.backgroundPath
        }
        undoStack.add(action)
        selectionState = SelectionState()
        snapGuides = SnapGuides()
        persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
    }

    val palette = listOf(
        Color(0xFF2E2B24),
        Color(0xFF1C7C7D),
        Color(0xFFE07A5F),
        Color(0xFF3D405B),
        Color(0xFF81B29A),
        Color(0xFFF2CC8F),
    )

    Column(modifier = modifier) {
        // 상단 툴바 패널
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 도구 선택 행
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        ToolToggle(
                            icon = Icons.Default.Brush,
                            selected = currentTool == ToolType.PEN,
                            onClick = { currentTool = ToolType.PEN },
                        )
                        ToolToggle(
                            icon = Icons.Default.AutoFixHigh,
                            selected = currentTool == ToolType.HIGHLIGHTER,
                            onClick = { currentTool = ToolType.HIGHLIGHTER },
                        )
                        ToolToggle(
                            icon = Icons.Default.ContentCut,
                            selected = currentTool == ToolType.ERASER,
                            onClick = { currentTool = ToolType.ERASER },
                        )
                        ToolToggle(
                            icon = Icons.Default.Gesture,
                            selected = currentTool == ToolType.LASSO,
                            onClick = { currentTool = ToolType.LASSO },
                        )
                        ToolToggle(
                            icon = Icons.Default.TextFields,
                            selected = currentTool == ToolType.TEXT,
                            onClick = { currentTool = ToolType.TEXT },
                        )
                        ToolToggle(
                            icon = Icons.Default.Image,
                            selected = currentTool == ToolType.IMAGE,
                            onClick = {
                                currentTool = ToolType.IMAGE
                                onRequestImagePick()
                            },
                        )
                    }
                    
                    // 실행 취소/재실행 그룹
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(onClick = { if (undoStack.isNotEmpty()) undo() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "실행 취소",
                                tint = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = { if (redoStack.isNotEmpty()) redo() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "재실행",
                                tint = if (redoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // 세부 설정 행 (색상, 두께, 스타일)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        palette.forEach { color ->
                            ColorChip(
                                color = color,
                                selected = selectedColor == color,
                                onClick = { selectedColor = color },
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        PaperStyleMenu(
                            paperStyle = paperStyle,
                            onPaperStyleChange = {
                                recordChange(
                                    strokes = strokes,
                                    textItems = textItems,
                                    imageItems = imageItems,
                                    paperStyle = paperStyle,
                                    backgroundPath = backgroundPath,
                                    undoStack = undoStack,
                                    redoStack = redoStack,
                                ) {
                                    paperStyle = it
                                }
                                persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
                            },
                        )
                    }

                    // 두께 슬라이더 (컴팩트하게)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.width(120.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (strokeWidth < 5f) 4.dp else 8.dp)
                                .background(selectedColor, CircleShape)
                        )
                        Slider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 1.5f..12f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 선택 도구 메뉴 (선택된 경우에만 표시)
                if (!selectionState.isEmpty) {
                    SelectionBar(
                        selectionState = selectionState,
                        onCopy = {
                            recordChange(
                                strokes = strokes,
                                textItems = textItems,
                                imageItems = imageItems,
                                paperStyle = paperStyle,
                                backgroundPath = backgroundPath,
                                undoStack = undoStack,
                                redoStack = redoStack,
                            ) {
                                duplicateSelection(selectionState.ids, strokes, textItems, imageItems)
                            }
                            selectionState = SelectionState()
                            snapGuides = SnapGuides()
                            persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
                        },
                        onDelete = {
                            recordChange(
                                strokes = strokes,
                                textItems = textItems,
                                imageItems = imageItems,
                                paperStyle = paperStyle,
                                backgroundPath = backgroundPath,
                                undoStack = undoStack,
                                redoStack = redoStack,
                            ) {
                                deleteSelection(selectionState.ids, strokes, textItems, imageItems)
                            }
                            selectionState = SelectionState()
                            snapGuides = SnapGuides()
                            persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
                        },
                        onClear = {
                            selectionState = SelectionState()
                            snapGuides = SnapGuides()
                        },
                        onEditText = {
                            val textId = selectionState.ids.textIds.firstOrNull() ?: return@SelectionBar
                            val item = textItems.firstOrNull { it.id == textId } ?: return@SelectionBar
                            editingTextId = textId
                            textDialogValue = item.text
                            textDialogSize = item.fontSizeSp
                            textDialogColor = Color(item.color)
                            textDialogTarget = StrokePoint(item.x, item.y)
                            textDialogOpen = true
                        },
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White, // 캔버스 배경은 흰색 고정 (종이 느낌)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            DrawingCanvas(
                strokes = strokes,
                textItems = textItems,
                imageItems = imageItems,
                tool = currentTool,
                color = selectedColor,
                widthDp = strokeWidth,
                paperStyle = paperStyle,
                backgroundPath = backgroundPath,
                selectionBounds = selectionState.bounds,
                transformTarget = selectionState.transformTarget,
                snapGuides = snapGuides,
                scale = zoomScale,
                viewportOffset = zoomOffset,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .onSizeChanged { canvasSize = it }
                    .then(zoomGestureModifier)
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = zoomOffset.x
                        translationY = zoomOffset.y
                    },
                onAction = { action ->
                    when (action) {
                        is CanvasAction.AddStroke -> {
                            recordChange(
                                strokes = strokes,
                                textItems = textItems,
                                imageItems = imageItems,
                                paperStyle = paperStyle,
                                backgroundPath = backgroundPath,
                                undoStack = undoStack,
                                redoStack = redoStack,
                            ) {
                                val strokeColor = if (action.stroke.tool == ToolType.HIGHLIGHTER) {
                                    Color(action.stroke.color).copy(alpha = 0.4f).toArgb()
                                } else {
                                    action.stroke.color
                                }
                                strokes.add(action.stroke.copy(color = strokeColor))
                            }
                            selectionState = SelectionState()
                            snapGuides = SnapGuides()
                            persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
                        }
                        is CanvasAction.EraseAt -> {
                            if (canvasSize.width == 0 || canvasSize.height == 0) return@DrawingCanvas
                            val radiusPx = with(density) { strokeWidth.dp.toPx() } * 1.4f
                            val before = snapshot(strokes, textItems, imageItems, paperStyle, backgroundPath)
                            val changed = eraseStrokesAt(
                                position = action.position,
                                strokes = strokes,
                                radiusPx = radiusPx,
                                canvasSize = canvasSize,
                            )
                            if (changed) {
                                val after = snapshot(strokes, textItems, imageItems, paperStyle, backgroundPath)
                                undoStack.add(EditAction(before, after))
                                redoStack.clear()
                                selectionState = SelectionState()
                                snapGuides = SnapGuides()
                                persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
                            }
                        }
                        is CanvasAction.SelectionChanged -> {
                            updateSelection(action.selection)
                        }
                        CanvasAction.ClearSelection -> {
                            selectionState = SelectionState()
                            snapGuides = SnapGuides()
                        }
                        CanvasAction.MoveSelectionStart -> {
                            if (!selectionState.isEmpty) {
                                moveSnapshot = snapshot(strokes, textItems, imageItems, paperStyle, backgroundPath)
                                snapGuides = SnapGuides()
                            }
                        }
                        is CanvasAction.MoveSelection -> {
                            if (!selectionState.isEmpty) {
                                val bounds = selectionState.bounds
                                val snapResult = if (bounds != null) {
                                    snapDeltaForBounds(bounds, action.delta)
                                } else {
                                    SnapDeltaResult(action.delta, SnapGuides())
                                }
                                applyDeltaToSelection(
                                    delta = snapResult.delta,
                                    selection = selectionState.ids,
                                    strokes = strokes,
                                    textItems = textItems,
                                    imageItems = imageItems,
                                )
                                val updatedBounds = computeSelectionBounds(
                                    strokes,
                                    textItems,
                                    imageItems,
                                    selectionState.ids,
                                )
                                val transformTarget = computeTransformTarget(
                                    selectionState.ids,
                                    textItems,
                                    imageItems,
                                )
                                selectionState = selectionState.copy(
                                    bounds = updatedBounds,
                                    transformTarget = transformTarget,
                                )
                                snapGuides = snapResult.guides
                            }
                        }
                        CanvasAction.MoveSelectionEnd -> {
                            val before = moveSnapshot
                            if (before != null) {
                                val after = snapshot(strokes, textItems, imageItems, paperStyle, backgroundPath)
                                if (before != after) {
                                    undoStack.add(EditAction(before, after))
                                    redoStack.clear()
                                    persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
                                }
                            }
                            moveSnapshot = null
                            snapGuides = SnapGuides()
                        }
                        is CanvasAction.TransformStart -> {
                            val target = selectionState.transformTarget ?: return@DrawingCanvas
                            transformState = TransformState(
                                handle = action.handle,
                                target = target,
                                startSnapshot = snapshot(strokes, textItems, imageItems, paperStyle, backgroundPath),
                            )
                            snapGuides = SnapGuides()
                        }
                        is CanvasAction.TransformUpdate -> {
                            val state = transformState ?: return@DrawingCanvas
                            val result = applyTransform(
                                handle = action.handle,
                                position = action.position,
                                target = state.target,
                                textItems = textItems,
                                imageItems = imageItems,
                                canvasSize = canvasSize,
                                density = density.density,
                            ) ?: return@DrawingCanvas
                            val bounds = computeSelectionBounds(
                                strokes,
                                textItems,
                                imageItems,
                                selectionState.ids,
                            )
                            selectionState = selectionState.copy(
                                bounds = bounds,
                                transformTarget = result.target,
                            )
                            snapGuides = result.guides
                        }
                        CanvasAction.TransformEnd -> {
                            val state = transformState ?: return@DrawingCanvas
                            val after = snapshot(strokes, textItems, imageItems, paperStyle, backgroundPath)
                            if (state.startSnapshot != after) {
                                undoStack.add(EditAction(state.startSnapshot, after))
                                redoStack.clear()
                                persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
                            }
                            transformState = null
                            snapGuides = SnapGuides()
                        }
                        is CanvasAction.TextTap -> {
                            editingTextId = null
                            textDialogTarget = action.position
                            textDialogValue = ""
                            textDialogSize = 20f
                            textDialogColor = selectedColor
                            textDialogOpen = true
                        }
                        is CanvasAction.TextEdit -> {
                            val item = textItems.firstOrNull { it.id == action.textId } ?: return@DrawingCanvas
                            editingTextId = item.id
                            textDialogValue = item.text
                            textDialogSize = item.fontSizeSp
                            textDialogColor = Color(item.color)
                            textDialogTarget = StrokePoint(item.x, item.y)
                            textDialogOpen = true
                        }
                    }
                },
            )
        }
    }

    if (textDialogOpen) {
        AlertDialog(
            onDismissRequest = { textDialogOpen = false },
            title = { Text(if (editingTextId == null) "텍스트 추가" else "텍스트 수정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = textDialogValue,
                        onValueChange = { textDialogValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    Text("크기 ${textDialogSize.toInt()}sp", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = textDialogSize,
                        onValueChange = { textDialogSize = it },
                        valueRange = 12f..36f,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        palette.forEach { color ->
                            ColorChip(
                                color = color,
                                selected = textDialogColor == color,
                                onClick = { textDialogColor = color },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = textDialogValue.isNotBlank(),
                    onClick = {
                        val target = textDialogTarget
                        if (target != null) {
                            recordChange(
                                strokes = strokes,
                                textItems = textItems,
                                imageItems = imageItems,
                                paperStyle = paperStyle,
                                backgroundPath = backgroundPath,
                                undoStack = undoStack,
                                redoStack = redoStack,
                            ) {
                                if (editingTextId == null) {
                                    val width = 0.45f
                                    val clampedHeight = computeTextHeightNormalized(
                                        text = textDialogValue,
                                        fontSizeSp = textDialogSize,
                                        widthNormalized = width,
                                        canvasSize = canvasSize,
                                        density = density.density,
                                    )
                                    val x = target.x.coerceIn(0f, 1f - width)
                                    val maxY = (1f - clampedHeight).coerceAtLeast(0f)
                                    val y = target.y.coerceIn(0f, maxY)
                                    val item = TextItem(
                                        id = UUID.randomUUID().toString(),
                                        text = textDialogValue,
                                        x = x,
                                        y = y,
                                        width = width,
                                        height = clampedHeight,
                                        fontSizeSp = textDialogSize,
                                        color = textDialogColor.toArgb(),
                                        rotation = 0f,
                                    )
                                    textItems.add(item)
                                } else {
                                    val index = textItems.indexOfFirst { it.id == editingTextId }
                                    if (index >= 0) {
                                        val existing = textItems[index]
                                        val clampedHeight = computeTextHeightNormalized(
                                            text = textDialogValue,
                                            fontSizeSp = textDialogSize,
                                            widthNormalized = existing.width,
                                            canvasSize = canvasSize,
                                            density = density.density,
                                        )
                                        val maxY = (1f - clampedHeight).coerceAtLeast(0f)
                                        val y = existing.y.coerceIn(0f, maxY)
                                        textItems[index] = existing.copy(
                                            text = textDialogValue,
                                            fontSizeSp = textDialogSize,
                                            color = textDialogColor.toArgb(),
                                            height = clampedHeight,
                                            y = y,
                                        )
                                    }
                                }
                            }
                            selectionState = SelectionState()
                            snapGuides = SnapGuides()
                            persistPage(page, strokes, textItems, imageItems, paperStyle, backgroundPath, onSavePage)
                        }
                        textDialogOpen = false
                    }
                ) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { textDialogOpen = false }) {
                    Text("취소")
                }
            },
        )
    }
}

private data class PageSnapshot(
    val strokes: List<Stroke>,
    val textItems: List<TextItem>,
    val imageItems: List<ImageItem>,
    val paperStyle: PaperStyle,
    val backgroundPath: String?,
)

private data class EditAction(
    val before: PageSnapshot,
    val after: PageSnapshot,
)

private data class TransformState(
    val handle: TransformHandle,
    val target: TransformTarget,
    val startSnapshot: PageSnapshot,
)

private data class SelectionState(
    val ids: SelectionIds = SelectionIds(),
    val bounds: NormalizedRect? = null,
    val transformTarget: TransformTarget? = null,
) {
    val isEmpty: Boolean
        get() = ids.isEmpty
}

private data class TransformOutcome(
    val target: TransformTarget,
    val guides: SnapGuides,
)

private data class SnapDeltaResult(
    val delta: StrokePoint,
    val guides: SnapGuides,
)

private data class SnapCandidate(
    val offset: Float,
    val guide: Float,
)

private const val SNAP_THRESHOLD = 0.015f
private val SNAP_GUIDES = listOf(0f, 0.5f, 1f)

private fun snapshot(
    strokes: List<Stroke>,
    textItems: List<TextItem>,
    imageItems: List<ImageItem>,
    paperStyle: PaperStyle,
    backgroundPath: String?,
): PageSnapshot = PageSnapshot(
    strokes = strokes.toList(),
    textItems = textItems.toList(),
    imageItems = imageItems.toList(),
    paperStyle = paperStyle,
    backgroundPath = backgroundPath,
)

private fun applySnapshot(
    snapshot: PageSnapshot,
    strokes: MutableList<Stroke>,
    textItems: MutableList<TextItem>,
    imageItems: MutableList<ImageItem>,
    updatePaperStyle: (PaperStyle) -> Unit,
) {
    strokes.clear()
    strokes.addAll(snapshot.strokes)
    textItems.clear()
    textItems.addAll(snapshot.textItems)
    imageItems.clear()
    imageItems.addAll(snapshot.imageItems)
    updatePaperStyle(snapshot.paperStyle)
}

private fun recordChange(
    strokes: MutableList<Stroke>,
    textItems: MutableList<TextItem>,
    imageItems: MutableList<ImageItem>,
    paperStyle: PaperStyle,
    backgroundPath: String?,
    undoStack: MutableList<EditAction>,
    redoStack: MutableList<EditAction>,
    block: () -> Unit,
) {
    val before = snapshot(strokes, textItems, imageItems, paperStyle, backgroundPath)
    block()
    val after = snapshot(strokes, textItems, imageItems, paperStyle, backgroundPath)
    if (before != after) {
        undoStack.add(EditAction(before, after))
        redoStack.clear()
    }
}

private fun persistPage(
    page: Page,
    strokes: List<Stroke>,
    textItems: List<TextItem>,
    imageItems: List<ImageItem>,
    paperStyle: PaperStyle,
    backgroundPath: String?,
    onSavePage: (Page) -> Unit,
) {
    val updated = page.copy(
        strokes = strokes.toList(),
        textItems = textItems.toList(),
        imageItems = imageItems.toList(),
        paperStyle = paperStyle,
        backgroundPath = backgroundPath,
        updatedAt = System.currentTimeMillis(),
    )
    onSavePage(updated)
}

private fun computeSelectionBounds(
    strokes: List<Stroke>,
    textItems: List<TextItem>,
    imageItems: List<ImageItem>,
    selection: SelectionIds,
): NormalizedRect? {
    var left = 1f
    var top = 1f
    var right = 0f
    var bottom = 0f
    var hasAny = false

    strokes.filter { selection.strokeIds.contains(it.id) }.forEach { stroke ->
        stroke.points.forEach { point ->
            left = minOf(left, point.x)
            top = minOf(top, point.y)
            right = maxOf(right, point.x)
            bottom = maxOf(bottom, point.y)
            hasAny = true
        }
    }

    textItems.filter { selection.textIds.contains(it.id) }.forEach { item ->
        left = minOf(left, item.x)
        top = minOf(top, item.y)
        right = maxOf(right, item.x + item.width)
        bottom = maxOf(bottom, item.y + item.height)
        hasAny = true
    }

    imageItems.filter { selection.imageIds.contains(it.id) }.forEach { item ->
        left = minOf(left, item.x)
        top = minOf(top, item.y)
        right = maxOf(right, item.x + item.width)
        bottom = maxOf(bottom, item.y + item.height)
        hasAny = true
    }

    if (!hasAny) return null

    return NormalizedRect(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f),
    )
}

private fun computeTransformTarget(
    selection: SelectionIds,
    textItems: List<TextItem>,
    imageItems: List<ImageItem>,
): TransformTarget? {
    if (selection.strokeIds.isNotEmpty()) return null
    if (selection.textIds.size == 1 && selection.imageIds.isEmpty()) {
        val textId = selection.textIds.first()
        val item = textItems.firstOrNull { it.id == textId } ?: return null
        return TransformTarget(
            type = TransformType.TEXT,
            id = item.id,
            bounds = NormalizedRect(
                left = item.x,
                top = item.y,
                right = item.x + item.width,
                bottom = item.y + item.height,
            ),
            rotation = item.rotation,
        )
    }
    if (selection.imageIds.size == 1 && selection.textIds.isEmpty()) {
        val imageId = selection.imageIds.first()
        val item = imageItems.firstOrNull { it.id == imageId } ?: return null
        return TransformTarget(
            type = TransformType.IMAGE,
            id = item.id,
            bounds = NormalizedRect(
                left = item.x,
                top = item.y,
                right = item.x + item.width,
                bottom = item.y + item.height,
            ),
            rotation = item.rotation,
        )
    }
    return null
}

private fun applyTransform(
    handle: TransformHandle,
    position: StrokePoint,
    target: TransformTarget,
    textItems: MutableList<TextItem>,
    imageItems: MutableList<ImageItem>,
    canvasSize: IntSize,
    density: Float,
): TransformOutcome? {
    val minSize = 0.06f
    val bounds = target.bounds
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f

    if (handle == TransformHandle.ROTATE) {
        val angle = Math.toDegrees(kotlin.math.atan2(position.y - centerY, position.x - centerX).toDouble()).toFloat()
        val rotation = angle + 90f
        when (target.type) {
            TransformType.TEXT -> {
                val index = textItems.indexOfFirst { it.id == target.id }
                if (index >= 0) {
                    textItems[index] = textItems[index].copy(rotation = rotation)
                }
            }
            TransformType.IMAGE -> {
                val index = imageItems.indexOfFirst { it.id == target.id }
                if (index >= 0) {
                    imageItems[index] = imageItems[index].copy(rotation = rotation)
                }
            }
        }
        return TransformOutcome(target.copy(rotation = rotation), SnapGuides())
    }

    val movesLeft = handle == TransformHandle.TOP_LEFT || handle == TransformHandle.BOTTOM_LEFT
    val movesRight = handle == TransformHandle.TOP_RIGHT || handle == TransformHandle.BOTTOM_RIGHT
    val movesTop = handle == TransformHandle.TOP_LEFT || handle == TransformHandle.TOP_RIGHT
    val movesBottom = handle == TransformHandle.BOTTOM_LEFT || handle == TransformHandle.BOTTOM_RIGHT

    var left = bounds.left
    var right = bounds.right
    var top = bounds.top
    var bottom = bounds.bottom
    var guides = SnapGuides()

    when (target.type) {
        TransformType.TEXT -> {
            if (movesLeft) left = position.x
            if (movesRight) right = position.x
            if (movesTop) top = position.y
            if (movesBottom) bottom = position.y

            var width = (right - left).coerceAtLeast(minSize).coerceAtMost(1f)
            if (movesLeft) left = right - width
            if (movesRight) right = left + width
            if (left < 0f) {
                left = 0f
                right = left + width
            }
            if (right > 1f) {
                right = 1f
                left = right - width
            }

            val textIndex = textItems.indexOfFirst { it.id == target.id }
            val textItem = textItems.getOrNull(textIndex) ?: return null
            var height = computeTextHeightNormalized(
                text = textItem.text,
                fontSizeSp = textItem.fontSizeSp,
                widthNormalized = width,
                canvasSize = canvasSize,
                density = density,
            ).coerceAtLeast(minSize)

            if (movesTop) {
                top = position.y
                bottom = top + height
            } else if (movesBottom) {
                bottom = position.y
                top = bottom - height
            }

            if (height > 1f) {
                height = 1f
                top = 0f
                bottom = 1f
            } else {
                if (top < 0f) {
                    top = 0f
                    bottom = top + height
                }
                if (bottom > 1f) {
                    bottom = 1f
                    top = bottom - height
                }
            }

            val snapX = when {
                movesLeft -> findSnapCandidate(left, SNAP_GUIDES, SNAP_THRESHOLD)
                movesRight -> findSnapCandidate(right, SNAP_GUIDES, SNAP_THRESHOLD)
                else -> null
            }
            if (snapX != null) {
                if (movesLeft) {
                    left += snapX.offset
                } else if (movesRight) {
                    right += snapX.offset
                }
                width = (right - left).coerceAtLeast(minSize).coerceAtMost(1f)
                if (movesLeft) left = right - width else right = left + width
                height = computeTextHeightNormalized(
                    text = textItem.text,
                    fontSizeSp = textItem.fontSizeSp,
                    widthNormalized = width,
                    canvasSize = canvasSize,
                    density = density,
                ).coerceAtLeast(minSize)
                if (movesTop) {
                    bottom = top + height
                } else if (movesBottom) {
                    top = bottom - height
                }
                guides = guides.copy(verticals = listOf(snapX.guide))
            }

            val snapY = when {
                movesTop -> findSnapCandidate(top, SNAP_GUIDES, SNAP_THRESHOLD)
                movesBottom -> findSnapCandidate(bottom, SNAP_GUIDES, SNAP_THRESHOLD)
                else -> null
            }
            if (snapY != null) {
                if (movesTop) {
                    top += snapY.offset
                    bottom = top + height
                } else if (movesBottom) {
                    bottom += snapY.offset
                    top = bottom - height
                }
                guides = guides.copy(horizontals = listOf(snapY.guide))
            }

            if (top < 0f) {
                top = 0f
                bottom = top + height
            }
            if (bottom > 1f) {
                bottom = 1f
                top = bottom - height
            }

            val maxLeft = (1f - width).coerceAtLeast(0f)
            left = left.coerceIn(0f, maxLeft)
            right = left + width

            textItems[textIndex] = textItem.copy(
                x = left,
                y = top,
                width = width,
                height = height,
                rotation = target.rotation,
            )
        }
        TransformType.IMAGE -> {
            val startWidth = (bounds.right - bounds.left).coerceAtLeast(minSize)
            val startHeight = (bounds.bottom - bounds.top).coerceAtLeast(minSize)
            val ratio = if (startHeight > 0f) startWidth / startHeight else 1f
            val anchorX = if (movesLeft) bounds.right else bounds.left
            val anchorY = if (movesTop) bounds.bottom else bounds.top
            val rawWidth = abs(position.x - anchorX)
            val rawHeight = abs(position.y - anchorY)
            val scaleX = if (startWidth > 0f) rawWidth / startWidth else 1f
            val scaleY = if (startHeight > 0f) rawHeight / startHeight else 1f
            val minScale = max(minSize / startWidth, minSize / startHeight)
            val maxScale = min(1f / startWidth, 1f / startHeight)
            var scale = max(scaleX, scaleY).coerceIn(minScale, maxScale)
            var width = (startWidth * scale).coerceAtLeast(minSize)
            var height = (startHeight * scale).coerceAtLeast(minSize)

            if (movesLeft) {
                right = anchorX
                left = right - width
            } else if (movesRight) {
                left = anchorX
                right = left + width
            }
            if (movesTop) {
                bottom = anchorY
                top = bottom - height
            } else if (movesBottom) {
                top = anchorY
                bottom = top + height
            }

            val snapX = when {
                movesLeft -> findSnapCandidate(left, SNAP_GUIDES, SNAP_THRESHOLD)
                movesRight -> findSnapCandidate(right, SNAP_GUIDES, SNAP_THRESHOLD)
                else -> null
            }
            val snapY = when {
                movesTop -> findSnapCandidate(top, SNAP_GUIDES, SNAP_THRESHOLD)
                movesBottom -> findSnapCandidate(bottom, SNAP_GUIDES, SNAP_THRESHOLD)
                else -> null
            }
            val useX = snapX != null && (snapY == null || abs(snapX.offset) <= abs(snapY.offset))

            if (useX) {
                val snap = snapX ?: return null
                val snappedEdge = if (movesLeft) left + snap.offset else right + snap.offset
                width = abs(snappedEdge - anchorX).coerceAtLeast(minSize)
                height = width / ratio
                val fitScale = min(1f / width, 1f / height)
                width *= fitScale
                height *= fitScale
                if (movesLeft) {
                    right = anchorX
                    left = right - width
                } else if (movesRight) {
                    left = anchorX
                    right = left + width
                }
                if (movesTop) {
                    bottom = anchorY
                    top = bottom - height
                } else if (movesBottom) {
                    top = anchorY
                    bottom = top + height
                }
                guides = guides.copy(verticals = listOf(snap.guide))
            } else if (snapY != null) {
                val snappedEdge = if (movesTop) top + snapY.offset else bottom + snapY.offset
                height = abs(snappedEdge - anchorY).coerceAtLeast(minSize)
                width = height * ratio
                val fitScale = min(1f / width, 1f / height)
                width *= fitScale
                height *= fitScale
                if (movesTop) {
                    bottom = anchorY
                    top = bottom - height
                } else if (movesBottom) {
                    top = anchorY
                    bottom = top + height
                }
                if (movesLeft) {
                    right = anchorX
                    left = right - width
                } else if (movesRight) {
                    left = anchorX
                    right = left + width
                }
                guides = guides.copy(horizontals = listOf(snapY.guide))
            }

            if (left < 0f) {
                left = 0f
                right = left + width
            }
            if (right > 1f) {
                right = 1f
                left = right - width
            }
            if (top < 0f) {
                top = 0f
                bottom = top + height
            }
            if (bottom > 1f) {
                bottom = 1f
                top = bottom - height
            }

            val index = imageItems.indexOfFirst { it.id == target.id }
            if (index >= 0) {
                imageItems[index] = imageItems[index].copy(
                    x = left,
                    y = top,
                    width = right - left,
                    height = bottom - top,
                    rotation = target.rotation,
                )
            }
        }
    }

    return TransformOutcome(
        target = target.copy(bounds = NormalizedRect(left, top, right, bottom)),
        guides = guides,
    )
}

private fun snapDeltaForBounds(bounds: NormalizedRect, delta: StrokePoint): SnapDeltaResult {
    val centerX = (bounds.left + bounds.right) / 2f
    val centerY = (bounds.top + bounds.bottom) / 2f
    val candidatesX = listOf(bounds.left + delta.x, centerX + delta.x, bounds.right + delta.x)
    val candidatesY = listOf(bounds.top + delta.y, centerY + delta.y, bounds.bottom + delta.y)

    var bestX: SnapCandidate? = null
    candidatesX.forEach { value ->
        val candidate = findSnapCandidate(value, SNAP_GUIDES, SNAP_THRESHOLD)
        if (candidate != null && (bestX == null || abs(candidate.offset) < abs(bestX!!.offset))) {
            bestX = candidate
        }
    }
    var bestY: SnapCandidate? = null
    candidatesY.forEach { value ->
        val candidate = findSnapCandidate(value, SNAP_GUIDES, SNAP_THRESHOLD)
        if (candidate != null && (bestY == null || abs(candidate.offset) < abs(bestY!!.offset))) {
            bestY = candidate
        }
    }

    val snappedDelta = StrokePoint(
        x = delta.x + (bestX?.offset ?: 0f),
        y = delta.y + (bestY?.offset ?: 0f),
    )
    val guides = SnapGuides(
        verticals = listOfNotNull(bestX?.guide),
        horizontals = listOfNotNull(bestY?.guide),
    )
    return SnapDeltaResult(snappedDelta, guides)
}

private fun findSnapCandidate(value: Float, guides: List<Float>, threshold: Float): SnapCandidate? {
    var best: SnapCandidate? = null
    guides.forEach { guide ->
        val offset = guide - value
        if (abs(offset) <= threshold && (best == null || abs(offset) < abs(best!!.offset))) {
            best = SnapCandidate(offset, guide)
        }
    }
    return best
}

private fun computeTextHeightNormalized(
    text: String,
    fontSizeSp: Float,
    widthNormalized: Float,
    canvasSize: IntSize,
    density: Float,
): Float {
    if (canvasSize.width == 0 || canvasSize.height == 0) {
        val lines = max(1, text.split("\n").size)
        return (fontSizeSp / 120f) * lines
    }
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSizeSp * density
        typeface = Typeface.SERIF
    }
    val content = if (text.isEmpty()) " " else text
    val widthPx = (widthNormalized * canvasSize.width).coerceAtLeast(1f).toInt()
    val layout = StaticLayout.Builder
        .obtain(content, 0, content.length, paint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .build()
    val lineHeight = paint.fontMetrics.run { bottom - top }
    val heightPx = max(layout.height.toFloat(), lineHeight)
    return (heightPx / canvasSize.height.toFloat()).coerceAtMost(1f)
}

private fun clampZoomOffset(offset: Offset, scale: Float, canvasSize: IntSize): Offset {
    if (canvasSize.width == 0 || canvasSize.height == 0) return Offset.Zero
    val maxX = (canvasSize.width * (scale - 1f)) / 2f
    val maxY = (canvasSize.height * (scale - 1f)) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

private fun eraseStrokesAt(
    position: StrokePoint,
    strokes: MutableList<Stroke>,
    radiusPx: Float,
    canvasSize: IntSize,
): Boolean {
    if (strokes.isEmpty()) return false
    if (canvasSize.width == 0 || canvasSize.height == 0) return false
    val before = strokes.toList()
    val eraser = Offset(
        x = position.x * canvasSize.width,
        y = position.y * canvasSize.height,
    )
    val updated = buildList {
        before.forEach { stroke ->
            addAll(eraseStroke(stroke, eraser, radiusPx, canvasSize))
        }
    }
    if (updated == before) return false
    strokes.clear()
    strokes.addAll(updated)
    return true
}

private fun eraseStroke(
    stroke: Stroke,
    eraser: Offset,
    radiusPx: Float,
    canvasSize: IntSize,
): List<Stroke> {
    val points = stroke.points
    if (points.isEmpty()) return emptyList()
    val keep = BooleanArray(points.size) { true }
    var changed = false
    val offsets = points.map { point -> point.toOffsetPx(canvasSize) }
    offsets.forEachIndexed { index, point ->
        if (distance(point, eraser) <= radiusPx) {
            keep[index] = false
            changed = true
        }
    }
    for (i in 0 until offsets.lastIndex) {
        if (distanceToSegment(eraser, offsets[i], offsets[i + 1]) <= radiusPx) {
            keep[i] = false
            keep[i + 1] = false
            changed = true
        }
    }
    if (!changed) return listOf(stroke)

    val segments = mutableListOf<List<StrokePoint>>()
    var current = mutableListOf<StrokePoint>()
    points.forEachIndexed { index, point ->
        if (keep[index]) {
            current.add(point)
        } else if (current.size >= 2) {
            segments.add(current)
            current = mutableListOf()
        } else {
            current.clear()
        }
    }
    if (current.size >= 2) {
        segments.add(current)
    }
    return segments.map { segment ->
        stroke.copy(
            id = UUID.randomUUID().toString(),
            points = segment,
        )
    }
}

private fun StrokePoint.toOffsetPx(canvasSize: IntSize): Offset {
    return Offset(x * canvasSize.width, y * canvasSize.height)
}

private fun distance(a: Offset, b: Offset): Float {
    return hypot(a.x - b.x, a.y - b.y)
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return distance(p, a)
    val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
    val clamped = t.coerceIn(0f, 1f)
    val proj = Offset(a.x + clamped * dx, a.y + clamped * dy)
    return distance(p, proj)
}

private fun applyDeltaToSelection(
    delta: StrokePoint,
    selection: SelectionIds,
    strokes: MutableList<Stroke>,
    textItems: MutableList<TextItem>,
    imageItems: MutableList<ImageItem>,
) {
    strokes.forEachIndexed { index, stroke ->
        if (selection.strokeIds.contains(stroke.id)) {
            val newPoints = stroke.points.map { point ->
                StrokePoint(
                    x = (point.x + delta.x).coerceIn(0f, 1f),
                    y = (point.y + delta.y).coerceIn(0f, 1f),
                )
            }
            strokes[index] = stroke.copy(points = newPoints)
        }
    }
    textItems.forEachIndexed { index, item ->
        if (selection.textIds.contains(item.id)) {
            val maxX = 1f - item.width
            val maxY = 1f - item.height
            textItems[index] = item.copy(
                x = (item.x + delta.x).coerceIn(0f, maxX),
                y = (item.y + delta.y).coerceIn(0f, maxY),
            )
        }
    }
    imageItems.forEachIndexed { index, item ->
        if (selection.imageIds.contains(item.id)) {
            val maxX = 1f - item.width
            val maxY = 1f - item.height
            imageItems[index] = item.copy(
                x = (item.x + delta.x).coerceIn(0f, maxX),
                y = (item.y + delta.y).coerceIn(0f, maxY),
            )
        }
    }
}

private fun duplicateSelection(
    selection: SelectionIds,
    strokes: MutableList<Stroke>,
    textItems: MutableList<TextItem>,
    imageItems: MutableList<ImageItem>,
) {
    val offset = 0.03f
    strokes.filter { selection.strokeIds.contains(it.id) }.forEach { stroke ->
        strokes.add(
            stroke.copy(
                id = UUID.randomUUID().toString(),
                points = stroke.points.map { point ->
                    StrokePoint(
                        x = (point.x + offset).coerceIn(0f, 1f),
                        y = (point.y + offset).coerceIn(0f, 1f),
                    )
                }
            )
        )
    }
    textItems.filter { selection.textIds.contains(it.id) }.forEach { item ->
        textItems.add(
            item.copy(
                id = UUID.randomUUID().toString(),
                x = (item.x + offset).coerceIn(0f, 1f - item.width),
                y = (item.y + offset).coerceIn(0f, 1f - item.height),
            )
        )
    }
    imageItems.filter { selection.imageIds.contains(it.id) }.forEach { item ->
        imageItems.add(
            item.copy(
                id = UUID.randomUUID().toString(),
                x = (item.x + offset).coerceIn(0f, 1f - item.width),
                y = (item.y + offset).coerceIn(0f, 1f - item.height),
            )
        )
    }
}

private fun deleteSelection(
    selection: SelectionIds,
    strokes: MutableList<Stroke>,
    textItems: MutableList<TextItem>,
    imageItems: MutableList<ImageItem>,
) {
    strokes.removeAll { selection.strokeIds.contains(it.id) }
    textItems.removeAll { selection.textIds.contains(it.id) }
    imageItems.removeAll { selection.imageIds.contains(it.id) }
}

private fun createImageItem(path: String): ImageItem {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    val ratio = if (options.outHeight > 0) options.outWidth.toFloat() / options.outHeight.toFloat() else 1f
    var width = 0.6f
    var height = width / ratio
    if (height > 0.7f) {
        height = 0.7f
        width = height * ratio
    }
    val x = (1f - width) / 2f
    val y = (1f - height) / 2f
    return ImageItem(
        id = UUID.randomUUID().toString(),
        path = path,
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        width = width.coerceAtMost(1f),
        height = height.coerceAtMost(1f),
        rotation = 0f,
    )
}

@Composable
private fun ToolToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        shape = CircleShape, // 완전 원형
        color = containerColor,
        modifier = Modifier.size(40.dp) // 고정 크기
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ColorChip(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = color,
            border = androidx.compose.foundation.BorderStroke(2.dp, border),
            modifier = Modifier.fillMaxSize(),
        ) {}
    }
}

@Composable
private fun PaperStyleMenu(
    paperStyle: PaperStyle,
    onPaperStyleChange: (PaperStyle) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.BorderColor,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = paperStyleLabel(paperStyle),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PaperStyle.values().forEach { style ->
                DropdownMenuItem(
                    text = { Text(paperStyleLabel(style)) },
                    onClick = {
                        expanded = false
                        onPaperStyleChange(style)
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    selectionState: SelectionState,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onEditText: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onCopy,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                Text("복사", style = MaterialTheme.typography.labelLarge)
            }
        }
        Surface(
            onClick = onDelete,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                Text("삭제", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (selectionState.transformTarget?.type == TransformType.TEXT) {
            Surface(
                onClick = onEditText,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(imageVector = Icons.Default.TextFields, contentDescription = null)
                    Text("텍스트 수정", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onClear) {
            Icon(imageVector = Icons.Default.Close, contentDescription = null)
        }
    }
}
