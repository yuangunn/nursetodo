package com.yuangunn.nursetodo.lock

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 잠금화면 위 할 일 화면의 디자인 값. 배경화면 위 어두운 그라데이션에 흰 글자, 글자는 48/56 · 20/28 · 15/20 · 13/18 네 단계,
 * 굵기 400 · 600, 간격 4 · 8 · 12 · 16 · 24, 컨트롤 44, 목록 줄 40.
 * 배경화면을 흐리게 할 수 없어(앱이 잠금화면 배경화면을 읽지 못함) 덮개는 흐림 없이 검정 72%입니다. 흰 배경화면에서도 흐린 글자 4.5:1 이상.
 */
internal object Lk {
    val Text = Color.White
    val Body = Color(0xE0FFFFFF)
    val Muted = Color(0xB8FFFFFF)
    val Missed = Color(0xFFFFA69E)
    val OnAccent = Color(0xFF0A0A0A)
    val Badge = Color(0xFFE5242F)
    val Toast = Color(0xE02C2C2E)
    val Control = Color(0x24FFFFFF)
    val Selected = Color(0x33FFFFFF)
    val Divider = Color(0x1FFFFFFF)
    val DividerSoft = Color(0x14FFFFFF)
    val Cover = Color.Black.copy(alpha = 0.72f)
    val Edge = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))

    /** 키워드 카드: 유리와 다른 붉은 바탕 */
    val Alert = Color(0xE62C0806)

    // 위에서 아래로 검정 60 · 35 · 20 · 55%
    val Scrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.6f),
        0.3f to Color.Black.copy(alpha = 0.35f),
        0.55f to Color.Black.copy(alpha = 0.2f),
        1f to Color.Black.copy(alpha = 0.55f),
    )

    private val phrase = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
    val Display = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontFeatureSettings = "tnum")
    val Title = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, lineBreak = phrase)
    val Body15 = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, lineBreak = phrase)
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, lineBreak = phrase)
    val Strong = FontWeight.SemiBold

    /** 감속 곡선 (모션 300ms 이하) */
    val Decel = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** 고른 강조색의 잠금화면(어두운 바탕) 값. */
internal val LocalLockAccent = staticCompositionLocalOf { Color(0xFF6EA8FF) }

/** 카드·알약 바탕: 흐림 없이 검정 72% + 흰 14% 1dp 테두리. */
internal fun Modifier.cover(shape: Shape): Modifier = background(Lk.Cover, shape).border(Lk.Edge, shape)

/** 목록 위아래 끝에서 서서히 사라지게 (스크롤 신호). */
internal fun Modifier.fadeEdges(top: Dp = 0.dp, bottom: Dp = 24.dp): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        if (h <= 0f) return@drawWithContent
        val t = (top.toPx() / h).coerceIn(0f, 0.5f)
        val b = 1f - (bottom.toPx() / h).coerceIn(0f, 0.5f)
        drawRect(
            Brush.verticalGradient(0f to (if (t > 0f) Color.Transparent else Color.Black), t to Color.Black, b to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * 누르면 0.96배로 줄었다 돌아오는 버튼 바탕. [color] 를 [shape] 로 칠하고, [label] 은 화면 낭독기 이름.
 */
@Composable
internal fun Press(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = Color.Transparent,
    enabled: Boolean = true,
    border: BorderStroke? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(100, easing = Lk.Decel), label = "press")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(color, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(source, ripple(color = Color.White), enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** 작은 버튼: 보이는 높이 32(알약), 누르는 칸 40. */
@Composable
internal fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    val accent = LocalLockAccent.current
    Box(modifier.height(40.dp), contentAlignment = Alignment.Center) {
        Press(
            onClick,
            Modifier.heightIn(min = 32.dp).padding(horizontal = 0.dp),
            shape = RoundedCornerShape(50),
            color = if (filled) accent else Lk.Control,
        ) {
            Text(
                text,
                style = Lk.Caption,
                fontWeight = Lk.Strong,
                color = if (filled) Lk.OnAccent else Lk.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** 큰 알약 버튼(높이 44): [filled] 면 강조색 채움, 아니면 흰 14%. */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    height: Dp = 44.dp,
    style: TextStyle = Lk.Body15,
    weight: FontWeight = Lk.Strong,
) {
    val accent = LocalLockAccent.current
    Press(onClick, modifier.height(height), shape = RoundedCornerShape(50), color = if (filled) accent else Lk.Control) {
        Text(
            text,
            style = style,
            fontWeight = weight,
            color = if (filled) Lk.OnAccent else Lk.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

// SettingsScreen 의 잠금화면 미리보기가 씁니다.
internal val Glass = Lk.Cover
internal val GlassEdge = Lk.Edge

/** 설정 미리보기의 할 일 줄 동그라미 칸 높이. */
internal fun circleBoxHeight(rowGap: Int) = (26 + 2 * rowGap).coerceIn(32, 48).dp
