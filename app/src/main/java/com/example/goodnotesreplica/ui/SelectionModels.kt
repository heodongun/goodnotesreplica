package com.example.goodnotesreplica.ui

import com.example.goodnotesreplica.data.StrokePoint

/**
 * 정규화된 좌표계에서의 사각형 영역을 나타냅니다.
 *
 * @property left 왼쪽 좌표 [0, 1]
 * @property top 위쪽 좌표 [0, 1]
 * @property right 오른쪽 좌표 [0, 1]
 * @property bottom 아래쪽 좌표 [0, 1]
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /**
     * 주어진 점이 이 사각형 내부에 포함되는지 확인합니다.
     */
    fun contains(point: StrokePoint): Boolean {
        return point.x in left..right && point.y in top..bottom
    }
}

/**
 * 캔버스 상에서 선택된 아이템들의 ID 집합입니다.
 *
 * @property strokeIds 선택된 스트로크 ID 목록
 * @property textIds 선택된 텍스트 아이템 ID 목록
 * @property imageIds 선택된 이미지 아이템 ID 목록
 */
data class SelectionIds(
    val strokeIds: Set<String> = emptySet(),
    val textIds: Set<String> = emptySet(),
    val imageIds: Set<String> = emptySet(),
) {
    /** 선택된 아이템이 하나도 없는지 여부 */
    val isEmpty: Boolean
        get() = strokeIds.isEmpty() && textIds.isEmpty() && imageIds.isEmpty()
}

/**
 * 변형 가능한 아이템의 유형입니다.
 */
enum class TransformType {
    TEXT,
    IMAGE,
}

/**
 * 변형 핸들의 종류입니다.
 */
enum class TransformHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    ROTATE,
}

/**
 * 변형 작업의 대상을 나타냅니다.
 *
 * @property type 변형 대상 유형 (텍스트/이미지)
 * @property id 대상 아이템의 ID
 * @property bounds 현재 경계 영역
 * @property rotation 현재 회전 각도
 */
data class TransformTarget(
    val type: TransformType,
    val id: String,
    val bounds: NormalizedRect,
    val rotation: Float,
)

/**
 * 스냅 가이드 정보입니다.
 *
 * @property verticals 수직 가이드 위치 목록
 * @property horizontals 수평 가이드 위치 목록
 */
data class SnapGuides(
    val verticals: List<Float> = emptyList(),
    val horizontals: List<Float> = emptyList(),
) {
    /** 활성화된 가이드가 없는지 여부 */
    val isEmpty: Boolean
        get() = verticals.isEmpty() && horizontals.isEmpty()
}
