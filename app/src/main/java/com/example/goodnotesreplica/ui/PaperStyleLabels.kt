package com.example.goodnotesreplica.ui

import com.example.goodnotesreplica.data.PaperStyle

/**
 * 용지 스타일 열거형에 대한 표시 이름을 반환합니다.
 */
fun paperStyleLabel(style: PaperStyle): String = when (style) {
    PaperStyle.BLANK -> "무지"
    PaperStyle.LINED -> "줄 노트"
    PaperStyle.GRID -> "격자"
    PaperStyle.DOT -> "도트"
}
