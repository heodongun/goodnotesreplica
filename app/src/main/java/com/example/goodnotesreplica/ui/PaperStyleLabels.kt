package com.example.goodnotesreplica.ui

import com.example.goodnotesreplica.data.PaperStyle

fun paperStyleLabel(style: PaperStyle): String = when (style) {
    PaperStyle.BLANK -> "무지"
    PaperStyle.LINED -> "줄 노트"
    PaperStyle.GRID -> "격자"
    PaperStyle.DOT -> "도트"
}
