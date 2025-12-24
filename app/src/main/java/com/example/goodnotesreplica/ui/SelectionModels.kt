package com.example.goodnotesreplica.ui

import com.example.goodnotesreplica.data.StrokePoint

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(point: StrokePoint): Boolean {
        return point.x in left..right && point.y in top..bottom
    }
}

data class SelectionIds(
    val strokeIds: Set<String> = emptySet(),
    val textIds: Set<String> = emptySet(),
    val imageIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = strokeIds.isEmpty() && textIds.isEmpty() && imageIds.isEmpty()
}

enum class TransformType {
    TEXT,
    IMAGE,
}

enum class TransformHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    ROTATE,
}

data class TransformTarget(
    val type: TransformType,
    val id: String,
    val bounds: NormalizedRect,
    val rotation: Float,
)

data class SnapGuides(
    val verticals: List<Float> = emptyList(),
    val horizontals: List<Float> = emptyList(),
) {
    val isEmpty: Boolean
        get() = verticals.isEmpty() && horizontals.isEmpty()
}
