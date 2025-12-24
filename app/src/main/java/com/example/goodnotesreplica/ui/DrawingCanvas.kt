package com.example.goodnotesreplica.ui

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.goodnotesreplica.data.ImageItem
import com.example.goodnotesreplica.data.Page
import com.example.goodnotesreplica.data.PaperStyle
import com.example.goodnotesreplica.data.Stroke as NoteStroke
import com.example.goodnotesreplica.data.StrokePoint
import com.example.goodnotesreplica.data.TextItem
import com.example.goodnotesreplica.data.ToolType
import java.io.File
import java.util.UUID
import kotlin.math.hypot

sealed interface CanvasAction {
    data class AddStroke(val stroke: NoteStroke) : CanvasAction
    data class EraseStrokes(val strokes: List<NoteStroke>) : CanvasAction
    data class SelectionChanged(val selection: SelectionIds) : CanvasAction
    data object ClearSelection : CanvasAction
    data object MoveSelectionStart : CanvasAction
    data class MoveSelection(val delta: StrokePoint) : CanvasAction
    data object MoveSelectionEnd : CanvasAction
    data class TransformStart(val handle: TransformHandle) : CanvasAction
    data class TransformUpdate(val handle: TransformHandle, val position: StrokePoint) : CanvasAction
    data object TransformEnd : CanvasAction
    data class TextTap(val position: StrokePoint) : CanvasAction
    data class TextEdit(val textId: String) : CanvasAction
}

@Composable
fun DrawingCanvas(
    strokes: List<NoteStroke>,
    textItems: List<TextItem>,
    imageItems: List<ImageItem>,
    tool: ToolType,
    color: Color,
    widthDp: Float,
    paperStyle: PaperStyle,
    backgroundPath: String?,
    selectionBounds: NormalizedRect?,
    transformTarget: TransformTarget?,
    snapGuides: SnapGuides,
    modifier: Modifier = Modifier,
    onAction: (CanvasAction) -> Unit,
) {
    val density = LocalDensity.current
    val currentPoints = remember { mutableStateListOf<StrokePoint>() }
    val lassoPoints = remember { mutableStateListOf<StrokePoint>() }
    val handleRadiusPx = with(density) { 18.dp.toPx() }
    val rotateHandleOffsetPx = with(density) { 28.dp.toPx() }

    val backgroundBitmap = remember(backgroundPath) {
        backgroundPath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

    val imageBitmaps = remember(imageItems) {
        imageItems.associate { item ->
            item.id to runCatching {
                val file = File(item.path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }.getOrNull()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(tool, strokes, textItems, imageItems, selectionBounds, transformTarget) {
                val canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                when (tool) {
                    ToolType.TEXT -> {
                        detectTapGestures { offset ->
                            val normalized = offset.toNormalized(canvasSize)
                            val hit = findTextHit(textItems, normalized)
                            if (hit != null) {
                                onAction(CanvasAction.TextEdit(hit))
                            } else {
                                onAction(CanvasAction.TextTap(normalized))
                            }
                        }
                    }
                    ToolType.LASSO -> {
                        var moving = false
                        var transforming = false
                        var activeHandle: TransformHandle? = null
                        var lastPosition: Offset? = null
                        detectDragGestures(
                            onDragStart = { offset ->
                                val normalized = offset.toNormalized(canvasSize)
                                val handle = transformTarget?.let {
                                    findHandleHit(it, offset, canvasSize, handleRadiusPx, rotateHandleOffsetPx)
                                }
                                if (handle != null) {
                                    transforming = true
                                    activeHandle = handle
                                    onAction(CanvasAction.TransformStart(handle))
                                } else if (selectionBounds?.contains(normalized) == true) {
                                    moving = true
                                    lastPosition = offset
                                    onAction(CanvasAction.MoveSelectionStart)
                                } else {
                                    moving = false
                                    transforming = false
                                    lassoPoints.clear()
                                    lassoPoints.add(normalized)
                                }
                            },
                            onDrag = { change, _ ->
                                val normalized = change.position.toNormalized(canvasSize)
                                if (transforming && activeHandle != null) {
                                    onAction(CanvasAction.TransformUpdate(activeHandle!!, normalized))
                                } else if (moving) {
                                    val last = lastPosition ?: change.position
                                    val delta = Offset(
                                        x = change.position.x - last.x,
                                        y = change.position.y - last.y,
                                    )
                                    lastPosition = change.position
                                    onAction(CanvasAction.MoveSelection(delta.toNormalizedDelta(canvasSize)))
                                } else {
                                    lassoPoints.add(normalized)
                                }
                            },
                            onDragEnd = {
                                if (transforming) {
                                    onAction(CanvasAction.TransformEnd)
                                } else if (moving) {
                                    onAction(CanvasAction.MoveSelectionEnd)
                                } else {
                                    val selection = selectWithinLasso(lassoPoints, strokes, textItems, imageItems)
                                    if (selection.isEmpty) {
                                        onAction(CanvasAction.ClearSelection)
                                    } else {
                                        onAction(CanvasAction.SelectionChanged(selection))
                                    }
                                }
                                moving = false
                                transforming = false
                                activeHandle = null
                                lastPosition = null
                                lassoPoints.clear()
                            },
                            onDragCancel = {
                                val wasTransforming = transforming
                                val wasMoving = moving
                                moving = false
                                transforming = false
                                activeHandle = null
                                lastPosition = null
                                lassoPoints.clear()
                                if (wasTransforming) {
                                    onAction(CanvasAction.TransformEnd)
                                } else if (wasMoving) {
                                    onAction(CanvasAction.MoveSelectionEnd)
                                }
                            },
                        )
                    }
                    ToolType.IMAGE -> {
                        detectTapGestures {
                            onAction(CanvasAction.ClearSelection)
                        }
                    }
                    else -> {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPoints.clear()
                                val normalized = offset.toNormalized(canvasSize)
                                currentPoints.add(normalized)
                                if (tool == ToolType.ERASER) {
                                    val removed = findStrokesAt(strokes, offset, canvasSize, widthDp.dp.toPx())
                                    if (removed.isNotEmpty()) {
                                        onAction(CanvasAction.EraseStrokes(removed))
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                val normalized = change.position.toNormalized(canvasSize)
                                currentPoints.add(normalized)
                                if (tool == ToolType.ERASER) {
                                    val removed = findStrokesAt(strokes, change.position, canvasSize, widthDp.dp.toPx())
                                    if (removed.isNotEmpty()) {
                                        onAction(CanvasAction.EraseStrokes(removed))
                                    }
                                }
                            },
                            onDragEnd = {
                                if (tool != ToolType.ERASER && currentPoints.size > 1) {
                                    val stroke = NoteStroke(
                                        id = UUID.randomUUID().toString(),
                                        tool = tool,
                                        color = color.toArgb(),
                                        widthDp = widthDp,
                                        points = currentPoints.toList(),
                                    )
                                    onAction(CanvasAction.AddStroke(stroke))
                                }
                                currentPoints.clear()
                            },
                            onDragCancel = { currentPoints.clear() },
                        )
                    }
                }
            }
    ) {
        drawBackground(backgroundBitmap, paperStyle)
        drawImages(imageItems, imageBitmaps)
        drawStrokes(strokes)
        drawTextItems(textItems, density.density)

        if (currentPoints.isNotEmpty() && tool != ToolType.ERASER && tool != ToolType.LASSO) {
            drawNormalizedStroke(
                NoteStroke(
                    id = UUID.randomUUID().toString(),
                    tool = tool,
                    color = color.toArgb(),
                    widthDp = widthDp,
                    points = currentPoints.toList(),
                )
            )
        }

        if (lassoPoints.size > 1) {
            drawLassoPath(lassoPoints)
        }

        if (!snapGuides.isEmpty) {
            drawSnapGuides(snapGuides)
        }
        if (selectionBounds != null) {
            drawSelectionBounds(selectionBounds)
        }
        if (transformTarget != null) {
            drawTransformHandles(transformTarget)
        }
    }
}

@Composable
fun PageThumbnail(
    page: Page,
    modifier: Modifier = Modifier,
) {
    val backgroundBitmap = remember(page.backgroundPath) {
        page.backgroundPath?.let { path ->
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }
    val imageBitmaps = remember(page.imageItems) {
        page.imageItems.associate { item ->
            item.id to runCatching {
                val file = File(item.path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }.getOrNull()
        }
    }
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        drawBackground(backgroundBitmap, page.paperStyle)
        drawImages(page.imageItems, imageBitmaps)
        drawStrokes(page.strokes)
        drawTextItems(page.textItems, density.density)
    }
}

private fun DrawScope.drawBackground(background: android.graphics.Bitmap?, paperStyle: PaperStyle) {
    if (background != null) {
        drawImage(
            image = background.asImageBitmap(),
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
        )
    } else {
        drawPaper(paperStyle)
    }
}

private fun DrawScope.drawPaper(style: PaperStyle) {
    val ink = Color(0xFFB4AD9F)
    val soft = Color(0xFFDDD6C8)
    val spacing = 32.dp.toPx()
    val margin = 44.dp.toPx()

    when (style) {
        PaperStyle.BLANK -> Unit
        PaperStyle.LINED -> {
            var y = spacing
            while (y < size.height) {
                drawLine(
                    color = soft,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                y += spacing
            }
            drawLine(
                color = ink,
                start = Offset(margin, 0f),
                end = Offset(margin, size.height),
                strokeWidth = 1.5.dp.toPx(),
                alpha = 0.6f,
            )
        }
        PaperStyle.GRID -> {
            var x = spacing
            while (x < size.width) {
                drawLine(
                    color = soft,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                x += spacing
            }
            var y = spacing
            while (y < size.height) {
                drawLine(
                    color = soft,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
                y += spacing
            }
        }
        PaperStyle.DOT -> {
            val radius = 1.4.dp.toPx()
            var x = spacing
            while (x < size.width) {
                var y = spacing
                while (y < size.height) {
                    drawCircle(
                        color = soft,
                        radius = radius,
                        center = Offset(x, y),
                    )
                    y += spacing
                }
                x += spacing
            }
        }
    }
}

private fun DrawScope.drawStrokes(strokes: List<NoteStroke>) {
    strokes.forEach { stroke ->
        drawNormalizedStroke(stroke)
    }
}

private fun DrawScope.drawNormalizedStroke(stroke: NoteStroke) {
    if (stroke.points.size < 2) return
    val path = Path()
    val first = stroke.points.first().toOffset(size)
    path.moveTo(first.x, first.y)
    stroke.points.drop(1).forEach { point ->
        val offset = point.toOffset(size)
        path.lineTo(offset.x, offset.y)
    }
    val alpha = if (stroke.tool == ToolType.HIGHLIGHTER) 0.4f else 1f
    val blendMode = if (stroke.tool == ToolType.HIGHLIGHTER) BlendMode.Multiply else BlendMode.SrcOver
    drawPath(
        path = path,
        color = Color(stroke.color),
        alpha = alpha,
        style = Stroke(
            width = stroke.widthDp.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        ),
        blendMode = blendMode,
    )
}

private fun DrawScope.drawImages(
    imageItems: List<ImageItem>,
    bitmaps: Map<String, android.graphics.Bitmap?>,
) {
    if (imageItems.isEmpty()) return
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        imageItems.forEach { item ->
            val bitmap = bitmaps[item.id] ?: return@forEach
            val left = item.x * size.width
            val top = item.y * size.height
            val right = left + item.width * size.width
            val bottom = top + item.height * size.height
            val src = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
            val dst = android.graphics.RectF(left, top, right, bottom)
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f
            nativeCanvas.save()
            if (item.rotation != 0f) {
                nativeCanvas.rotate(item.rotation, centerX, centerY)
            }
            nativeCanvas.drawBitmap(bitmap, src, dst, null)
            nativeCanvas.restore()
        }
    }
}

private fun DrawScope.drawTextItems(items: List<TextItem>, density: Float) {
    if (items.isEmpty()) return
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        items.forEach { item ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = item.color
                textSize = item.fontSizeSp * density
                typeface = Typeface.SERIF
            }
            val content = if (item.text.isEmpty()) " " else item.text
            val boxWidth = (item.width * size.width).coerceAtLeast(1f)
            val boxHeight = item.height * size.height
            val startX = item.x * size.width
            val startY = item.y * size.height
            val centerX = startX + boxWidth / 2f
            val centerY = startY + boxHeight / 2f
            val layout = StaticLayout.Builder
                .obtain(content, 0, content.length, paint, boxWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            nativeCanvas.save()
            if (item.rotation != 0f) {
                nativeCanvas.rotate(item.rotation, centerX, centerY)
            }
            nativeCanvas.translate(startX, startY)
            if (boxHeight > 0f) {
                nativeCanvas.clipRect(0f, 0f, boxWidth, boxHeight)
            }
            layout.draw(nativeCanvas)
            nativeCanvas.restore()
        }
    }
}

private fun DrawScope.drawLassoPath(points: List<StrokePoint>) {
    val path = Path()
    val first = points.first().toOffset(size)
    path.moveTo(first.x, first.y)
    points.drop(1).forEach { point ->
        val offset = point.toOffset(size)
        path.lineTo(offset.x, offset.y)
    }
    drawPath(
        path = path,
        color = Color(0xFF1C7C7D),
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        ),
    )
}

private fun DrawScope.drawSelectionBounds(bounds: NormalizedRect) {
    val left = bounds.left * size.width
    val top = bounds.top * size.height
    val right = bounds.right * size.width
    val bottom = bounds.bottom * size.height
    drawRect(
        color = Color(0xFF1C7C7D),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        ),
    )
}

private fun DrawScope.drawSnapGuides(guides: SnapGuides) {
    val color = Color(0xFF1C7C7D).copy(alpha = 0.45f)
    val strokeWidth = 1.5.dp.toPx()
    guides.verticals.forEach { x ->
        val xPos = x * size.width
        drawLine(
            color = color,
            start = Offset(xPos, 0f),
            end = Offset(xPos, size.height),
            strokeWidth = strokeWidth,
        )
    }
    guides.horizontals.forEach { y ->
        val yPos = y * size.height
        drawLine(
            color = color,
            start = Offset(0f, yPos),
            end = Offset(size.width, yPos),
            strokeWidth = strokeWidth,
        )
    }
}

private fun DrawScope.drawTransformHandles(target: TransformTarget) {
    val bounds = target.bounds
    val left = bounds.left * size.width
    val top = bounds.top * size.height
    val right = bounds.right * size.width
    val bottom = bounds.bottom * size.height
    val centerX = (left + right) / 2f
    val rotateOffset = 28.dp.toPx()
    val handleRadius = 8.dp.toPx()
    val color = Color(0xFF1C7C7D)

    val handles = listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(left, bottom),
        Offset(right, bottom),
    )

    handles.forEach { position ->
        drawCircle(
            color = color,
            radius = handleRadius,
            center = position,
        )
        drawCircle(
            color = Color.White,
            radius = handleRadius - 3.dp.toPx(),
            center = position,
        )
    }

    val rotateY = (top - rotateOffset).coerceAtLeast(0f)
    drawLine(
        color = color,
        start = Offset(centerX, top),
        end = Offset(centerX, rotateY),
        strokeWidth = 2.dp.toPx(),
    )
    drawCircle(
        color = color,
        radius = handleRadius,
        center = Offset(centerX, rotateY),
    )
    drawCircle(
        color = Color.White,
        radius = handleRadius - 3.dp.toPx(),
        center = Offset(centerX, rotateY),
    )
}

private fun findHandleHit(
    target: TransformTarget,
    position: Offset,
    size: Size,
    radiusPx: Float,
    rotateOffsetPx: Float,
): TransformHandle? {
    val handles = handlePositionsPx(target.bounds, size, rotateOffsetPx)
    return handles.entries.firstOrNull { (_, handlePosition) ->
        hypot(handlePosition.x - position.x, handlePosition.y - position.y) <= radiusPx
    }?.key
}

private fun handlePositionsPx(
    bounds: NormalizedRect,
    size: Size,
    rotateOffsetPx: Float,
): Map<TransformHandle, Offset> {
    val left = bounds.left * size.width
    val top = bounds.top * size.height
    val right = bounds.right * size.width
    val bottom = bounds.bottom * size.height
    val centerX = (left + right) / 2f
    val rotateY = top - rotateOffsetPx
    return mapOf(
        TransformHandle.TOP_LEFT to Offset(left, top),
        TransformHandle.TOP_RIGHT to Offset(right, top),
        TransformHandle.BOTTOM_LEFT to Offset(left, bottom),
        TransformHandle.BOTTOM_RIGHT to Offset(right, bottom),
        TransformHandle.ROTATE to Offset(centerX, rotateY),
    )
}

private fun Offset.toNormalized(size: Size): StrokePoint {
    if (size.width == 0f || size.height == 0f) return StrokePoint(0f, 0f)
    val x = (x / size.width).coerceIn(0f, 1f)
    val y = (y / size.height).coerceIn(0f, 1f)
    return StrokePoint(x, y)
}

private fun Offset.toNormalizedDelta(size: Size): StrokePoint {
    if (size.width == 0f || size.height == 0f) return StrokePoint(0f, 0f)
    return StrokePoint(x / size.width, y / size.height)
}

private fun StrokePoint.toOffset(size: Size): Offset {
    return Offset(x * size.width, y * size.height)
}

private fun findStrokesAt(
    strokes: List<NoteStroke>,
    position: Offset,
    size: Size,
    radiusPx: Float,
): List<NoteStroke> {
    val radius = radiusPx * 1.4f
    return strokes.filter { stroke ->
        stroke.points.any { point ->
            val offset = point.toOffset(size)
            hypot(offset.x - position.x, offset.y - position.y) <= radius
        }
    }
}

private fun selectWithinLasso(
    lassoPoints: List<StrokePoint>,
    strokes: List<NoteStroke>,
    textItems: List<TextItem>,
    imageItems: List<ImageItem>,
): SelectionIds {
    if (lassoPoints.size < 3) return SelectionIds()
    val strokeIds = strokes.filter { stroke ->
        stroke.points.any { point -> isPointInPolygon(point, lassoPoints) }
    }.map { it.id }.toSet()

    val textIds = textItems.filter { item ->
        val rectPoints = rectPoints(item.x, item.y, item.width, item.height)
        rectPoints.any { point -> isPointInPolygon(point, lassoPoints) }
    }.map { it.id }.toSet()

    val imageIds = imageItems.filter { item ->
        val rectPoints = rectPoints(item.x, item.y, item.width, item.height)
        rectPoints.any { point -> isPointInPolygon(point, lassoPoints) }
    }.map { it.id }.toSet()

    return SelectionIds(
        strokeIds = strokeIds,
        textIds = textIds,
        imageIds = imageIds,
    )
}

private fun rectPoints(x: Float, y: Float, width: Float, height: Float): List<StrokePoint> {
    return listOf(
        StrokePoint(x, y),
        StrokePoint(x + width, y),
        StrokePoint(x, y + height),
        StrokePoint(x + width, y + height),
        StrokePoint(x + width / 2f, y + height / 2f),
    )
}

private fun isPointInPolygon(point: StrokePoint, polygon: List<StrokePoint>): Boolean {
    var intersects = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].x
        val yi = polygon[i].y
        val xj = polygon[j].x
        val yj = polygon[j].y
        val intersect = ((yi > point.y) != (yj > point.y)) &&
            (point.x < (xj - xi) * (point.y - yi) / (yj - yi + 0.0001f) + xi)
        if (intersect) {
            intersects = !intersects
        }
        j = i
    }
    return intersects
}

private fun findTextHit(textItems: List<TextItem>, point: StrokePoint): String? {
    return textItems.firstOrNull { item ->
        point.x in item.x..(item.x + item.width) && point.y in item.y..(item.y + item.height)
    }?.id
}
