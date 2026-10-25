package com.ttaak.app.ui

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
import com.ttaak.app.data.Settings

val Accents = listOf(
    Color(0xFFFF6B4A), // 코랄
    Color(0xFF3478F6), // 블루
    Color(0xFF34C759), // 그린
    Color(0xFFAF52DE), // 퍼플
    Color(0xFFFF9F0A), // 오렌지
    Color(0xFF5E5CE6), // 인디고
    Color(0xFFFF375F), // 핑크
    Color(0xFF8E8E93), // 그레이
)

val TextSizes = listOf("작게" to 15, "보통" to 17, "크게" to 20)

/** 할 일 제목에 쓰이는 글자 스타일 (설정 → 디자인). */
val LocalTaskText = staticCompositionLocalOf { TextStyle(fontSize = 17.sp) }

@Composable
fun TtaakTheme(settings: Settings, content: @Composable () -> Unit) {
    val accent = Accents[settings.accent.coerceIn(Accents.indices)]
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
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
