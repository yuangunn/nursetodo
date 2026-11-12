package com.yuangunn.nursetodo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yuangunn.nursetodo.data.Settings

val Accents = listOf(
    Color(0xFF0054D1), // 시그니처 블루 (res/values/colors.xml 의 accent)
    Color(0xFFFF6B4A), // 코랄
    Color(0xFF34C759), // 그린
    Color(0xFFAF52DE), // 퍼플
    Color(0xFFFF9F0A), // 오렌지
    Color(0xFF5E5CE6), // 인디고
    Color(0xFFFF375F), // 핑크
    Color(0xFF8E8E93), // 그레이
)

/** 강조색 이름 (순서는 [Settings.accent]). 테마마다 명도만 맞춘 값이 [AccentLight]·[AccentDark] 입니다. */
val AccentNames = listOf("시그니처 블루", "코랄", "그린", "퍼플", "오렌지", "인디고", "핑크", "그레이")
val AccentLight = listOf(0xFF0054D1, 0xFFE02900, 0xFF23863C, 0xFFA843DB, 0xFFA66500, 0xFF5E5CE6, 0xFFE9002F, 0xFF74747A).map(::Color)

/** 어두운 바탕(다크 테마·잠금화면) 값. */
val AccentDark = listOf(0xFF6EA8FF, 0xFFFF6B4A, 0xFF34C759, 0xFFC27BE5, 0xFFFF9F0A, 0xFF908FED, 0xFFFF6080, 0xFF97979C).map(::Color)

fun lockAccent(s: Settings): Color = AccentDark[s.accent.coerceIn(AccentDark.indices)]

val TextSizes = listOf("작게" to 15, "보통" to 17, "크게" to 20)

/** 할 일 제목에 쓰이는 글자 스타일 (설정 → 디자인). */
val LocalTaskText = staticCompositionLocalOf { TextStyle(fontSize = 17.sp) }

@Composable
fun NurseTodoTheme(settings: Settings, content: @Composable () -> Unit) {
    val accent = Accents[settings.accent.coerceIn(Accents.indices)]
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            inversePrimary = accent,
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1C1C1E),
            surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerLow = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF2C2C2E),
            onSurfaceVariant = Color(0xFF8E8E93),
            surfaceContainerHighest = Color(0xFF2C2C2E),
            secondaryContainer = lerp(Color(0xFF1C1C1E), accent, 0.3f),
            onSecondaryContainer = Color.White,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            inversePrimary = accent, // 스낵바 '되돌리기' 색
            background = Color.White,
            surface = Color.White,
            surfaceVariant = Color(0xFFF2F2F7),
            surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerLow = Color(0xFFF7F7FA),
            surfaceContainerHigh = Color(0xFFEDEDF2),
            onSurfaceVariant = Color(0xFF8E8E93),
            surfaceContainerHighest = Color(0xFFE5E5EA),
            secondaryContainer = lerp(Color.White, accent, 0.16f),
            onSecondaryContainer = Color(0xFF1C1C1E),
        )
    }
    val taskText = TextStyle(
        fontSize = TextSizes[settings.textSize.coerceIn(TextSizes.indices)].second.sp,
        fontWeight = if (settings.bold) FontWeight.SemiBold else FontWeight.Normal,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalTaskText provides taskText, content = content)
    }
}
