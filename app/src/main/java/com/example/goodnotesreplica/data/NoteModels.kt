package com.example.goodnotesreplica.data

/**
 * 지원되는 그리기 및 편집 도구입니다.
 */
enum class ToolType {
    /** 펜 스트로크 도구 */
    PEN,
    /** 형광펜 스트로크 도구 */
    HIGHLIGHTER,
    /** 지우개 도구 */
    ERASER,
    /** 라쏘(올가미) 선택 도구 */
    LASSO,
    /** 텍스트 삽입/편집 도구 */
    TEXT,
    /** 이미지 삽입 도구 */
    IMAGE,
}

/**
 * 페이지의 시각적 배경 스타일입니다.
 */
enum class PaperStyle {
    /** 빈 배경 */
    BLANK,
    /** 가로 줄무늬 */
    LINED,
    /** 격자 무늬 */
    GRID,
    /** 도트 격자 */
    DOT,
}

/**
 * 페이지 상의 정규화된 좌표 점입니다.
 *
 * @property x 가로 위치 [0, 1] 범위
 * @property y 세로 위치 [0, 1] 범위
 */
data class StrokePoint(
    val x: Float,
    val y: Float,
)

/**
 * 정규화된 점들과 스타일 메타데이터를 포함하는 자유 곡선 스트로크입니다.
 *
 * @property id 스트로크의 고유 식별자
 * @property tool 스트로크를 생성한 도구
 * @property color ARGB 포맷의 색상 값
 * @property widthDp dp 단위의 스트로크 두께
 * @property points 스트로크를 구성하는 정규화된 점들의 순서 있는 목록
 */
data class Stroke(
    val id: String,
    val tool: ToolType,
    val color: Int,
    val widthDp: Float,
    val points: List<StrokePoint>,
)

/**
 * 레이아웃 경계와 스타일을 가진 페이지 상의 텍스트 상자입니다.
 *
 * @property id 텍스트 아이템의 고유 식별자
 * @property text 원본 텍스트 내용
 * @property x 정규화된 공간에서의 왼쪽 경계
 * @property y 정규화된 공간에서의 위쪽 경계
 * @property width 정규화된 공간에서의 너비
 * @property height 정규화된 공간에서의 높이
 * @property fontSizeSp sp 단위의 글꼴 크기
 * @property color ARGB 포맷의 색상 값
 * @property rotation 회전 각도 (도 단위)
 */
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

/**
 * 정규화된 경계를 가진 페이지 상의 이미지 상자입니다.
 *
 * @property id 이미지 아이템의 고유 식별자
 * @property path 이미지 파일의 절대 경로
 * @property x 정규화된 공간에서의 왼쪽 경계
 * @property y 정규화된 공간에서의 위쪽 경계
 * @property width 정규화된 공간에서의 너비
 * @property height 정규화된 공간에서의 높이
 * @property rotation 회전 각도 (도 단위)
 */
data class ImageItem(
    val id: String,
    val path: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
)

/**
 * 스트로크, 텍스트, 이미지를 포함하는 불변의 페이지 스냅샷입니다.
 *
 * @property id 페이지의 고유 식별자
 * @property index 노트북 내에서의 1부터 시작하는 인덱스
 * @property paperStyle 페이지의 배경 스타일
 * @property strokes 페이지 상의 모든 스트로크 아이템
 * @property textItems 페이지 상의 모든 텍스트 아이템
 * @property imageItems 페이지 상의 모든 이미지 아이템
 * @property backgroundPath 배경 비트맵의 선택적 경로
 * @property aspectRatio 레이아웃 및 내보내기를 위한 가로 세로 비율
 * @property sourcePdfPath 가져온 원본 PDF의 선택적 경로
 * @property sourcePdfPageIndex 가져온 원본 PDF의 페이지 인덱스 (선택적)
 * @property updatedAt 마지막 업데이트 시간 (epoch millis)
 */
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

/**
 * 노트북을 그룹화하기 위한 폴더 메타데이터입니다.
 *
 * @property id 폴더의 고유 식별자
 * @property name 표시 이름
 * @property createdAt 생성 시간 (epoch millis)
 * @property updatedAt 마지막 업데이트 시간 (epoch millis)
 */
data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 라이브러리 탐색을 위한 노트북 메타데이터입니다.
 *
 * @property id 노트북의 고유 식별자
 * @property title 표시 제목
 * @property createdAt 생성 시간 (epoch millis)
 * @property updatedAt 마지막 업데이트 시간 (epoch millis)
 * @property pageCount 노트북의 페이지 수
 * @property coverColor ARGB 포맷의 표지 색상
 * @property folderId 그룹화를 위한 선택적 폴더 ID
 * @property tags 검색 가능한 태그 목록
 */
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
