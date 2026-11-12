package com.yuangunn.nursetodo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 앱 화면 공용 부품. 화면 좌우 여백 16, 컨트롤 48, 한 줄 48 · 두 줄 64, 칸·결과·설정 묶음 모서리 16, 시트 24,
 * 입력 칸·칩 8, 버튼은 완전히 둥글게. 글자는 24/32 · 17/24 · 15/20 · 13/18.
 */
private val Pill = RoundedCornerShape(50)

/** 앱 화면 위에 덮어 그릴 전체 화면 편집기 자리. [MainActivity] 가 아래 탭 위에 그립니다. */
val LocalOverlay = staticCompositionLocalOf<MutableState<(@Composable () -> Unit)?>?> { null }

/**
 * 아래 탭 없이 화면 전체를 쓰는 편집기(프리셋 고치기, 포켓북 붙여넣기). 뒤로 가기는 [onClose].
 * 대화상자 창은 안드로이드 15+ 의 강제 전체 화면에서 상태바·내비게이션 바 여백이 어긋나서, 앱 화면 위에 덮어 그립니다.
 */
@Composable
fun FullScreen(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val host = LocalOverlay.current ?: return
    val body by rememberUpdatedState(content)
    val close by rememberUpdatedState(onClose)
    DisposableEffect(host) {
        val ui: @Composable () -> Unit = {
            BackHandler { close() }
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .imePadding(),
                ) { body() }
            }
        }
        host.value = ui
        onDispose { if (host.value === ui) host.value = null }
    }
}

/** 위 막대(높이 56): 닫기 48 · 제목 17/24 600 · 오른쪽 동작. */
@Composable
fun TopBar(title: String, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 2.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "닫기") }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

/** 강조 채움 버튼(높이 48, 15/20 600). */
@Composable
fun FillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors()) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Pill,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** 외곽선 버튼(높이 48). */
@Composable
fun LineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = Pill,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
}

/** 설정 묶음: 이름(13/18 600, 오른쪽에 [count]), 모서리 16 카드, 아래 설명. 카드 안 줄은 [SetRow] 류. */
@Composable
fun Group(title: String, count: String = "", foot: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (count.isNotEmpty()) Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                // 줄마다 아래에 긋는 선 중 마지막 줄 것은 카드 밖으로 잘라 냅니다.
                .layout { m, c ->
                    val p = m.measure(c)
                    layout(p.width, (p.height - 1.dp.roundToPx()).coerceAtLeast(0)) { p.place(0, 0) }
                },
            content = content,
        )
        if (foot.isNotEmpty()) {
            Text(foot, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
        }
    }
}

/** 설정 줄 아래 선(안쪽 16부터, outlineVariant). [Group] 이 마지막 줄 선은 잘라 냅니다. */
@Composable
fun Modifier.rowLine(): Modifier {
    val c = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        val y = size.height - 0.5.dp.toPx()
        drawLine(c, Offset(16.dp.toPx(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

/** 묶음 안 줄 사이 선(안쪽 16부터). 첫 줄 위에는 긋지 않습니다. */
@Composable
fun RowLine() = Box(
    Modifier
        .padding(start = 16.dp)
        .fillMaxWidth()
        .height(1.dp)
        .background(MaterialTheme.colorScheme.outlineVariant),
)

/** 설정 한 줄(최소 48): 제목(과 설명), 오른쪽 [trailing]. [onClick] 이 있으면 줄 전체를 누름. */
@Composable
fun SetRow(
    title: String,
    sub: String = "",
    onClick: (() -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .rowLine()
            .heightIn(min = if (sub.isEmpty()) 48.dp else 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

/** 스위치(52×32): 켜짐 트랙 primary·노브 24 onPrimary, 꺼짐 트랙 outlineVariant·테두리 2·노브 16 outline. */
@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = Switch(
    checked = checked,
    onCheckedChange = onChange,
    colors = SwitchDefaults.colors(
        uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    ),
)

@Composable
fun SwitchSetRow(title: String, checked: Boolean, sub: String = "", onChange: (Boolean) -> Unit) =
    SetRow(title, sub, onClick = { onChange(!checked) }) { AppSwitch(checked, onChange) }

/** 지금 값을 보여 주고 누르면 고르기 시트. */
@Composable
fun ValueRow(title: String, value: String, badge: String = "", onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .rowLine()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
            if (badge.isNotEmpty()) Text(badge, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** 빼기(44)가 붙은 줄: 키워드, 바로가기 항목. */
@Composable
fun RemovableRow(title: String, onRemove: () -> Unit, leading: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .rowLine()
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading()
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove, modifier = Modifier.size(44.dp).semantics { contentDescription = "$title 빼기" }) {
            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** 묶음 맨 아래 '…추가' 줄(강조색 15/20 600). */
@Composable
fun AddRow(title: String, onClick: () -> Unit) = Text(
    title,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier
        .fillMaxWidth()
        .rowLine()
        .clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 48.dp)
        .padding(horizontal = 16.dp, vertical = 14.dp),
)

/**
 * 고르기 시트: 손잡이 32×4, 제목 17/24 600, 설명 13/18, 항목 줄 48(하나 고르기는 동그라미, 여러 개는 네모).
 * 하나 고르기는 누르면 닫히고, 여러 개·입력은 아래 완료(48)로 닫습니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickSheet(
    title: String,
    note: String,
    options: List<String>,
    selected: Set<Int>,
    multi: Boolean = false,
    onDismiss: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
    done: Boolean = multi,
    onPick: (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 8.dp)
                    .size(32.dp, 4.dp)
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            if (note.isNotEmpty()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))
            }
            extra()
            Column(Modifier.padding(top = 8.dp)) {
                options.forEachIndexed { i, label ->
                    if (i > 0) RowLine()
                    val on = i in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = if (multi) Role.Checkbox else Role.RadioButton) { onPick(i) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        val color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            Modifier
                                .size(20.dp)
                                .then(
                                    if (multi) Modifier.background(if (on) color else Color.Transparent, RoundedCornerShape(4.dp)).border(2.dp, color, RoundedCornerShape(4.dp))
                                    else Modifier.border(2.dp, color, CircleShape),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                multi && on -> Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                on -> Box(Modifier.size(10.dp).background(color, CircleShape))
                            }
                        }
                    }
                }
            }
            if (done) FillButton("완료", onDismiss, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
    }
}

/** 앱 목록의 체크 동그라미: 누르는 칸 44, 보이는 20(테두리 2 onSurfaceVariant / primary 채움 + 체크 14). */
@Composable
fun AppCheck(on: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val c = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier
                .size(20.dp)
                .background(if (on) c else Color.Transparent, CircleShape)
                .border(2.dp, c, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (on) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp)) }
    }
}

/** 큰 제목(24/32 600)과 위 작은 줄. */
@Composable
fun ScreenTitle(title: String, over: String = "", modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (over.isNotEmpty()) Text(over, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        trailing()
    }
}
