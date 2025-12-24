package com.example.goodnotesreplica.data

enum class ExportTargetKind {
    FILE,
    URI,
}

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
