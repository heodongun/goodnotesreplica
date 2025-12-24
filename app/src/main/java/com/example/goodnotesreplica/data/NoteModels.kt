package com.example.goodnotesreplica.data

enum class ToolType {
    PEN,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    TEXT,
    IMAGE,
}

enum class PaperStyle {
    BLANK,
    LINED,
    GRID,
    DOT,
}

data class StrokePoint(
    val x: Float,
    val y: Float,
)

data class Stroke(
    val id: String,
    val tool: ToolType,
    val color: Int,
    val widthDp: Float,
    val points: List<StrokePoint>,
)

data class TextItem(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSizeSp: Float,
    val color: Int,
    val rotation: Float,
)

data class ImageItem(
    val id: String,
    val path: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
)

data class Page(
    val id: String,
    val index: Int,
    val paperStyle: PaperStyle,
    val strokes: List<Stroke>,
    val textItems: List<TextItem>,
    val imageItems: List<ImageItem>,
    val backgroundPath: String?,
    val aspectRatio: Float,
    val sourcePdfPath: String?,
    val sourcePdfPageIndex: Int?,
    val updatedAt: Long,
)

data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Notebook(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val coverColor: Int,
    val folderId: String?,
    val tags: List<String>,
)
