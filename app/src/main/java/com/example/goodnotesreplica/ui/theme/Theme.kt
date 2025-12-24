package com.example.goodnotesreplica.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Teal,
    secondary = Coral,
    tertiary = Sand,
    background = Color(0xFF1E1C18),
    surface = Color(0xFF26221D),
    surfaceVariant = Color(0xFF3A342D),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF2E2B24),
    onBackground = Color(0xFFF2EBDD),
    onSurface = Color(0xFFF2EBDD),
    onSurfaceVariant = Color(0xFFCFC6B6),
    outlineVariant = Color(0xFF5B534A),
)

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    secondary = Coral,
    tertiary = Indigo,
    background = Cream,
    surface = Paper,
    surfaceVariant = PaperMuted,
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkSoft,
    outlineVariant = Color(0xFFD8D0C2),
)

/**
 * 앱의 메인 테마입니다.
 * 라이트/다크 모드에 따라 색상 스키마를 설정합니다.
 *
 * @param darkTheme 다크 테마 사용 여부
 * @param dynamicColor 동적 색상 사용 여부 (현재 미사용)
 * @param content 테마가 적용될 컨텐츠
 */
@Composable
fun GoodnotesreplicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
