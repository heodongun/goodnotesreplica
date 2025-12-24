package com.example.goodnotesreplica.data

/**
 * 페이지 내보내기 시 사용된 대상 유형입니다.
 */
enum class ExportTargetKind {
    /** 내보내기가 앱 소유의 디스크 파일로 작성됨 */
    FILE,
    /** 내보내기가 사용자가 선택한 문서 URI로 작성됨 */
    URI,
}

/**
 * 내보내기 기록 UI 및 분석을 위한 단일 내보내기 작업의 스냅샷입니다.
 *
 * @property id 내보내기 기록의 고유 식별자
 * @property notebookId 원본 노트북 ID
 * @property notebookTitle 내보내기 시점의 원본 노트북 제목
 * @property pageId 원본 페이지 ID
 * @property pageIndex 노트북 내 페이지의 1부터 시작하는 인덱스
 * @property exportType 내보내기에 사용된 형식
 * @property targetKind 대상 유형
 * @property target 절대 파일 경로 또는 URI 문자열
 * @property createdAt 내보내기가 완료된 시간 (epoch millis)
 */
data class ExportRecord(
    val id: String,
    val notebookId: String,
    val notebookTitle: String,
    val pageId: String,
    val pageIndex: Int,
    val exportType: ExportType,
    val targetKind: ExportTargetKind,
    val target: String,
    val createdAt: Long,
)
