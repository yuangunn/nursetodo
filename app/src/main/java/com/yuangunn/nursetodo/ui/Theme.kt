package com.yuangunn.nursetodo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.yuangunn.nursetodo.data.Settings

/** 강조색 이름 (순서는 [Settings.accent]). 테마마다 명도만 맞춘 값이 [AccentLight]·[AccentDark] 입니다. */
val AccentNames = listOf("시그니처 블루", "코랄", "그린", "퍼플", "오렌지", "인디고", "핑크", "그레이")
val AccentLight = listOf(0xFF0054D1, 0xFFE02900, 0xFF23863C, 0xFFA843DB, 0xFFA66500, 0xFF5E5CE6, 0xFFE9002F, 0xFF74747A).map(::Color)

/** 어두운 바탕(다크 테마·잠금화면) 값. */
val AccentDark = listOf(0xFF6EA8FF, 0xFFFF6B4A, 0xFF34C759, 0xFFC27BE5, 0xFFFF9F0A, 0xFF908FED, 0xFFFF6080, 0xFF97979C).map(::Color)

/** 위젯·잠금화면 카드처럼 테마를 모르는 곳에 쓰는 라이트 값. */
val Accents = AccentLight

fun lockAccent(s: Settings): Color = AccentDark[s.accent.coerceIn(AccentDark.indices)]

/** 계산기 상한 경고처럼 M3 역할 밖의 주의 색. */
fun warningColor(dark: Boolean) = if (dark) Color(0xFFFF9F0A) else Color(0xFFA35B00)

/** 설정의 테마: 0 시스템 따라감, 1 라이트, 2 다크. */
@Composable
fun Settings.isDark(): Boolean = when (theme) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

private val phrase = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

/** 앱 글자 단계: 24/32 · 17/24 · 15/20 · 13/18, 굵기 400 · 600. 계산 결과 40/48 만 예외. */
private val AppType = Typography().let { t ->
    fun s(size: Int, line: Int, w: FontWeight = FontWeight.Normal) = TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = w, lineBreak = phrase)
    t.copy(
        displaySmall = s(24, 32, FontWeight.SemiBold),
        headlineSmall = s(24, 32, FontWeight.SemiBold),
        titleLarge = s(17, 24, FontWeight.SemiBold),
        titleMedium = s(17, 24, FontWeight.SemiBold),
        titleSmall = s(13, 18, FontWeight.SemiBold),
        bodyLarge = s(15, 20),
        bodyMedium = s(15, 20),
        bodySmall = s(13, 18),
        labelLarge = s(15, 20, FontWeight.SemiBold),
        labelMedium = s(13, 18),
        labelSmall = s(13, 18),
    )
}

/**
 * 앱 화면 테마: 유리 없이 M3 역할 색만. 라이트 바탕 #FFFFFF·칸 #F2F2F7, 다크 #000000·칸 #1C1C1E.
 * 강조색은 테마마다 명도를 맞춘 값이고, 시그니처 블루면 container 도 시안 값 그대로.
 */
@Composable
fun NurseTodoTheme(settings: Settings, content: @Composable () -> Unit) {
    val i = settings.accent.coerceIn(AccentLight.indices)
    val dark = settings.isDark()
    val scheme = if (dark) {
        val accent = AccentDark[i]
        darkColorScheme(
            primary = accent,
            onPrimary = if (i == 0) Color(0xFF001B3D) else Color(0xFF0A0A0A),
            primaryContainer = if (i == 0) Color(0xFF1D3357) else lerp(Color(0xFF1C1C1E), accent, 0.3f),
            onPrimaryContainer = if (i == 0) Color(0xFFD6E3FF) else lerp(Color.White, accent, 0.2f),
            inversePrimary = accent,
            secondaryContainer = if (i == 0) Color(0xFF1D3357) else lerp(Color(0xFF1C1C1E), accent, 0.3f),
            onSecondaryContainer = if (i == 0) Color(0xFFD6E3FF) else Color.White,
            background = Color.Black,
            surface = Color.Black,
            onSurface = Color.White,
            onBackground = Color.White,
            surfaceVariant = Color(0xFF1C1C1E),
            surfaceContainerLowest = Color(0xFF1C1C1E),
            surfaceContainerLow = Color(0xFF1C1C1E),
            surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF2C2C2E),
            onSurfaceVariant = Color(0xFF98989D),
            outline = Color(0xFF8E8E93),
            outlineVariant = Color(0xFF2C2C2E),
            error = Color(0xFFFFA69E),
        )
    } else {
        val accent = AccentLight[i]
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = if (i == 0) Color(0xFFDCE6F9) else lerp(Color.White, accent, 0.16f),
            onPrimaryContainer = if (i == 0) Color(0xFF002E73) else lerp(Color.Black, accent, 0.6f),
            inversePrimary = accent, // 스낵바 '되돌리기' 색
            secondaryContainer = if (i == 0) Color(0xFFDCE6F9) else lerp(Color.White, accent, 0.16f),
            onSecondaryContainer = if (i == 0) Color(0xFF002E73) else Color(0xFF1C1C1E),
            background = Color.White,
            surface = Color.White,
            onSurface = Color(0xFF1C1C1E),
            onBackground = Color(0xFF1C1C1E),
            surfaceVariant = Color(0xFFF2F2F7),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F7FA),
            surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFFEDEDF2),
            surfaceContainerHighest = Color(0xFFE5E5EA),
            onSurfaceVariant = Color(0xFF6C6C70),
            outline = Color(0xFF8E8E93),
            outlineVariant = Color(0xFFE5E5EA),
            error = Color(0xFFC5221F),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppType, content = content)
}
