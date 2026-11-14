package com.yuangunn.nursetodo.lock

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.Alarm
import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Fmt
import com.yuangunn.nursetodo.data.Settings
import com.yuangunn.nursetodo.data.Shortcut
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.shortcutSlots
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun hhmm(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/** 남은 시간: 45초 · 28분 · 1시간 12분. 시각(05:30)과 헷갈리지 않게 쌍점을 쓰지 않습니다. */
internal fun fmtLeft(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    if (s < 60) return "${s}초"
    val m = (s + 59) / 60
    return if (m < 60) "${m}분" else "${m / 60}시간" + if (m % 60 != 0L) " ${m % 60}분" else ""
}

internal val Tnum = TextStyle(fontFeatureSettings = "tnum")
private val Pill = RoundedCornerShape(50)

/** 화면을 켤 때 처음 그리는 것은 움직이지 않고, 그 뒤에 나타나는 것만 위에서 내려오게 하려고 화면을 연 시각을 둡니다. */
internal val LocalScreenStart = staticCompositionLocalOf { 0L }

/** 나타날 때 [dy] 만큼 떨어진 곳에서 제자리로 (240ms). 화면을 연 직후에는 바로 보입니다. */
@Composable
internal fun Modifier.enterFrom(dy: Dp): Modifier {
    val start = LocalScreenStart.current
    val anim = remember { Animatable(if (SystemClock.uptimeMillis() - start < 600) 1f else 0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(240, easing = Lk.Decel)) }
    val px = with(LocalDensity.current) { dy.toPx() }
    return graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * px
    }
}

/** 시계(24시간제)와 날짜를 한 줄에, 글자 밑선을 맞춰서. */
@Composable
internal fun ClockRow() {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            kotlinx.coroutines.delay(60_000L - value.second * 1_000L - value.nano / 1_000_000L)
        }
    }
    Row(Modifier.height(56.dp), verticalAlignment = Alignment.Bottom) {
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = Lk.Display, color = Lk.Text, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(12.dp))
        Text(Fmt.date(now.toLocalDate().toEpochDay()), style = Lk.Title, color = Lk.Text, maxLines = 1, modifier = Modifier.alignByBaseline())
    }
}

/** 알림을 보낸 앱 아이콘(동그랗게). 못 읽으면 흐린 동그라미. */
@Composable
internal fun AppIcon(pkg: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(pkg) { NotiListener.icon(context, pkg) }
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = modifier.size(size).clip(CircleShape))
    } else {
        Box(modifier.size(size).background(Lk.Control, CircleShape))
    }
}

/** 빨간 개수 배지(1~9, 9+). 기본은 아이콘 오른쪽 위 모서리에서 4 나가고, [inline] 이면 제자리(줄 안 글자처럼). */
@Composable
internal fun Badge(count: Int, modifier: Modifier = Modifier, inline: Boolean = false) {
    Text(
        if (count > 9) "9+" else "$count",
        style = Lk.Caption.merge(Tnum),
        fontWeight = Lk.Strong,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = modifier
            .offset(if (inline) 0.dp else 4.dp, if (inline) 0.dp else (-4).dp)
            .heightIn(min = 18.dp)
            .widthIn(min = 18.dp)
            .background(Lk.Badge, Pill)
            .padding(horizontal = 5.dp),
    )
}

/**
 * 체크 동그라미: 보이는 20, 누르는 칸 40×40. 체크하면 강조색으로 채우고 0.8 → 1.15 → 1 배로 튕깁니다.
 * 동그라미를 눌러야만 체크됩니다(주머니 안 오작동 방지).
 */
@Composable
internal fun CheckCircle(on: Boolean, ring: Color, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val accent = LocalLockAccent.current
    val scale = remember { Animatable(1f) }
    var last by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on && !last) {
            scale.snapTo(0.8f)
            scale.animateTo(1.15f, tween(100, easing = Lk.Decel))
            scale.animateTo(1f, tween(140, easing = Lk.Decel))
        }
        last = on
    }
    val fill by androidx.compose.animation.animateColorAsState(if (on) accent else Color.Transparent, tween(180), label = "fill")
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(fill, CircleShape)
                .border(2.dp, if (on) accent else ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Icons.Default.Check, contentDescription = null, tint = Lk.OnAccent, modifier = Modifier.size(14.dp))
        }
    }
}

/** 되돌리기 토스트 하나. [page] 가 아닌 페이지에서는 안 보이고(시간은 계속 흐름), 새 토스트가 오면 앞의 것은 확정됩니다([onEnd]). */
internal class Undo(
    val text: String,
    val page: Int?,
    val sub: String = "",
    val ms: Long = 4_000,
    val tag: Any? = null,
    val onUndo: () -> Unit,
    val onEnd: () -> Unit = {},
)

@Composable
internal fun ToastBar(u: Undo, onUndo: () -> Unit) {
    val accent = LocalLockAccent.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(Lk.Toast, Pill)
            .padding(start = 16.dp, end = 4.dp, top = if (u.sub.isNotEmpty()) 6.dp else 0.dp, bottom = if (u.sub.isNotEmpty()) 6.dp else 0.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(u.text, style = Lk.Body15, color = Lk.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (u.sub.isNotEmpty()) Text(u.sub, style = Lk.Caption, color = Lk.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .height(40.dp)
                .clip(Pill)
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text("되돌리기", style = Lk.Body15, fontWeight = Lk.Strong, color = accent) }
    }
}

/** 내용 [text] 안의 키워드를 빨강 600으로. 띄어쓰기가 달라 그대로 못 찾으면 강조 없이. */
private fun highlight(text: String, hit: String): AnnotatedString = buildAnnotatedString {
    val at = if (hit.isEmpty()) -1 else text.indexOf(hit, ignoreCase = true)
    if (at < 0) return@buildAnnotatedString append(text)
    append(text.substring(0, at))
    withStyle(SpanStyle(color = Lk.Missed, fontWeight = FontWeight.SemiBold)) { append(text.substring(at, at + hit.length)) }
    append(text.substring(at + hit.length))
}

/**
 * 키워드 알림 카드(시계 바로 아래). 시스템 팝업은 몇 초면 접혀서 '확인'할 때까지 남는 건 이 카드뿐이라 붉은 바탕·빨강 테두리로
 * 한눈에 급한 것으로 보이게 합니다. 2건까지: 새 것은 펼치고 이전 것은 한 줄로 접으며, 접힌 줄을 누르면 서로 바꿔 펼칩니다.
 */
@Composable
internal fun KeywordCards(
    hits: List<KeywordAlert.Hit>,
    openKey: String?,
    onExpand: (KeywordAlert.Hit) -> Unit,
    onOk: (KeywordAlert.Hit) -> Unit,
    onOpen: (KeywordAlert.Hit) -> Unit,
    onTodo: (KeywordAlert.Hit) -> Unit,
) {
    // ponytail: 3건째부터는 시스템 알림창에만 남김
    val shown = hits.take(2)
    val open = shown.firstOrNull { it.key == openKey } ?: shown.firstOrNull() ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { h ->
            androidx.compose.runtime.key(h.key) {
                if (h.key == open.key) KeywordCard(h, onOk, onOpen, onTodo) else KeywordCompact(h) { onExpand(h) }
            }
        }
    }
}

@Composable
private fun KeywordCard(h: KeywordAlert.Hit, onOk: (KeywordAlert.Hit) -> Unit, onOpen: (KeywordAlert.Hit) -> Unit, onTodo: (KeywordAlert.Hit) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .enterFrom((-8).dp)
            .background(Lk.Alert, shape)
            .border(2.dp, Lk.Missed, shape)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            .semantics(mergeDescendants = false) {
                contentDescription = "키워드 알림: ${h.app} ${h.title}, ${h.hit}"
                liveRegion = LiveRegionMode.Assertive
            },
    ) {
        Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(h.pkg, 20.dp)
            Text(
                buildAnnotatedString {
                    append("${h.app} · ")
                    withStyle(SpanStyle(color = Lk.Missed, fontWeight = FontWeight.SemiBold)) { append(h.hit) }
                },
                style = Lk.Caption,
                color = Lk.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(hhmm(h.time), style = Lk.Caption.merge(Tnum), color = Lk.Muted)
        }
        if (h.title.isNotBlank()) {
            Text(
                h.title,
                style = Lk.Body15,
                fontWeight = Lk.Strong,
                color = Lk.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(highlight(h.text, h.hit), style = Lk.Body15, color = Lk.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(
            Modifier
                .padding(top = 12.dp)
                .offset(x = (-4).dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton("확인", { onOk(h) }, Modifier.weight(1f), filled = true)
            if (h.open != null) PillButton("열기", { onOpen(h) }, Modifier.weight(1f))
            PillButton("할 일로 추가", { onTodo(h) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeywordCompact(h: KeywordAlert.Hit, onExpand: () -> Unit) {
    Press(
        onExpand,
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics { contentDescription = "키워드 알림 펼치기: ${hhmm(h.time)} ${h.app} ${h.title}" },
        shape = Pill,
        color = Lk.Alert,
        border = androidx.compose.foundation.BorderStroke(2.dp, Lk.Missed.copy(alpha = 0.6f)),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(hhmm(h.time), style = Lk.Caption.merge(Tnum), color = Lk.Muted)
            if (h.title.isNotBlank()) Text(h.title, style = Lk.Caption, fontWeight = Lk.Strong, color = Lk.Text, maxLines = 1)
            Text(h.text, style = Lk.Caption, color = Lk.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(16.dp))
        }
    }
}

/** 칩: 보이는 32, 누르는 칸 40. 글 13/18 600 + 아래 화살표 16(펼치면 180도). [+N] 알람 칩과 하위 항목 칩. */
@Composable
internal fun ChevronChip(text: String, open: Boolean, label: String, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(200, easing = Lk.Decel), label = "chev")
    Box(
        Modifier
            .height(40.dp)
            .semantics {
                contentDescription = label
                stateDescription = if (open) "펼침" else "접힘"
            },
        contentAlignment = Alignment.Center,
    ) {
        Press(onClick, Modifier.height(32.dp), shape = Pill, color = if (open) Lk.Selected else Lk.Control) {
            Row(Modifier.padding(start = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text, style = Lk.Caption.merge(Tnum), fontWeight = Lk.Strong, color = Lk.Text, maxLines = 1)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(16.dp).rotate(turn))
            }
        }
    }
}

/**
 * 알람 줄(높이 44): 가장 빨리 울릴 알람의 남은 시간과 이름, 둘 이상이면 +N 칩(누르면 알람 목록), 오른쪽 끄기.
 * 1분 안쪽은 초까지 보여 줍니다.
 */
@Composable
internal fun AlarmRow(alarms: List<Alarm>, listOpen: Boolean, onToggleList: () -> Unit, onOff: (Alarm) -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000 - value % 1_000)
        }
    }
    val first = alarms.minByOrNull { it.at } ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .enterFrom((-8).dp)
            .cover(Pill)
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_alarm), contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp))
        Text("${fmtLeft(first.at - now)} 남음", style = Lk.Body15.merge(Tnum), fontWeight = Lk.Strong, color = Lk.Text, maxLines = 1)
        Text(
            first.label.ifEmpty { "${hhmm(first.at)}에 울림" },
            style = Lk.Caption,
            color = Lk.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (alarms.size > 1) {
            val more = alarms.size - 1
            ChevronChip(if (more > 9) "+9+" else "+$more", listOpen, "알람 ${alarms.size}개 모두 보기", onToggleList)
        }
        Press({ onOff(first) }, Modifier.size(40.dp).semantics { contentDescription = "알람 끄기" }) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 카드 자리를 채우는 목록(알림·알람·완료): 머리 줄, 스크롤 목록과 아래 안내, 맨 아래에 고정한 닫기 44.
 * [action] 이 있으면 닫기 왼쪽에 같은 폭의 보조 버튼(알림 모두 지우기).
 */
@Composable
internal fun PanelFrame(
    title: String,
    hint: String,
    onClose: () -> Unit,
    closeLabel: String = "닫기",
    action: Pair<String, () -> Unit>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fadeEdges(scrolled = { scroll.value.toFloat() })
                .verticalScroll(scroll)
                .padding(vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text(title, style = Lk.Body15, fontWeight = Lk.Strong, color = Lk.Text) }
            Divider(Lk.Divider)
            content()
            Text(hint, style = Lk.Caption, color = Lk.Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp))
        }
        // 카드 모서리까지 옆·아래 간격을 같게(16)
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            action?.let { (label, run) -> PillButton(label, run, Modifier.weight(1f)) }
            PillButton(closeLabel, onClose, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Divider(color: Color) = Box(
    Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(color),
)

/**
 * 알림 목록: 최근 순, 줄을 누르면 잠금을 풀고 그 앱을 엽니다. 줄 오른쪽 ✕ 는 그 알림을 지우고(알림 창에서도 사라짐),
 * 아래 '모두 지우기'는 목록 전체를 지웁니다. 확인해서 끝난 알림을 잠금을 풀지 않고 정리하는 길.
 */
@Composable
internal fun NotiPanel(notis: List<Noti>, onTap: (Noti) -> Unit, onDismiss: (Noti) -> Unit, onClearAll: () -> Unit, onClose: () -> Unit) {
    PanelFrame(
        "알림 ${notis.size}개",
        "누르면 잠금을 풀고 그 앱을 열어요. ✕로 지운 알림은 알림 창에서도 사라져요.",
        onClose,
        action = ("모두 지우기" to onClearAll).takeIf { notis.size > 1 },
    ) {
        if (notis.isEmpty()) {
            Text(
                "새 알림이 없어요",
                style = Lk.Caption,
                color = Lk.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
            )
        }
        notis.forEach { n ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onTap(n) }
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(n.pkg, 24.dp)
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(n.app, style = Lk.Caption, fontWeight = Lk.Strong, color = Lk.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(Fmt.stamp(n.time).removePrefix("오늘 "), style = Lk.Caption.merge(Tnum), color = Lk.Muted)
                    }
                    if (n.hidden) {
                        Text("잠금을 풀면 내용이 보여요", style = Lk.Body15, color = Lk.Muted, modifier = Modifier.padding(top = 4.dp))
                    } else {
                        if (n.title.isNotBlank()) {
                            Text(n.title, style = Lk.Body15, fontWeight = Lk.Strong, color = Lk.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (n.text.isNotBlank()) Text(n.text, style = Lk.Body15, color = Lk.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                // 첫 줄(앱 이름)에 맞춘 지우기: 보이는 ✕ 16, 누르는 칸 40
                Press(
                    { onDismiss(n) },
                    Modifier
                        .offset(y = (-11).dp)
                        .size(40.dp)
                        .semantics { contentDescription = "${n.app} 알림 지우기" },
                ) { Icon(Icons.Default.Close, contentDescription = null, tint = Lk.Muted, modifier = Modifier.size(16.dp)) }
            }
            Divider(Lk.DividerSoft)
        }
    }
}

/**
 * 바로가기 편집(바로가기를 길게 누르면 카드 자리에 열림): 모양 3가지 · 보이는 바로가기(길게 눌러 끌면 순서, ✕ 빼기) ·
 * 넣을 수 있는 것(계산기·알람·할 일 추가·캘린더, 앱). 고칠 때마다 저장돼 아래 바로가기에 바로 보입니다. 버튼 이름 바꾸기는 앱 설정에서.
 */
@Composable
internal fun ShortcutPanel(settings: Settings, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val save = { list: List<Shortcut> -> TaskStore.updateSettings { it.copy(shortcuts = list) } }
    val slots = settings.shortcutSlots()
    val list = settings.shortcuts
    val full = list.size >= slots
    PanelFrame("바로가기 편집", "길게 눌러 끌면 순서가 바뀌어요. 버튼 이름은 앱 › 설정 › 바로가기에서 바꿔요.", onClose, closeLabel = "완료") {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("3개씩", "2개씩", "아이콘만").forEachIndexed { i, name ->
                val on = settings.shortcutStyle == i
                Press(
                    { TaskStore.updateSettings { it.copy(shortcutStyle = i) } },
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .semantics { selected = on },
                    shape = Pill,
                    color = if (on) Lk.Selected else Lk.Control,
                ) { Text(name, style = Lk.Caption, fontWeight = if (on) Lk.Strong else null, color = Lk.Text) }
            }
        }
        SectionLabel("보이는 바로가기 ${list.size.coerceAtMost(slots)}/$slots")
        // 길게 눌러 끌기: 줄 높이(48)만큼 움직이면 이웃과 자리를 바꿉니다.
        var order by remember { mutableStateOf<List<Shortcut>?>(null) }
        var dragIndex by remember { mutableIntStateOf(-1) }
        var dragY by remember { mutableFloatStateOf(0f) }
        val rowPx = with(LocalDensity.current) { 48.dp.toPx() }
        (order ?: list).forEachIndexed { i, sc ->
            // 줄을 바로가기 id 로 묶어, 끄는 중 자리가 바뀌어도 같은 줄(손가락 처리 포함)이 따라 움직이게 합니다.
            // 위치로만 그리면 자리가 바뀌는 순간 그 칸의 pointerInput 키가 달라져 끌기가 취소됩니다(실기기·에뮬레이터 재현).
            key(sc.id) {
                val name = remember(sc) { Shortcuts.name(context, sc) }
                val dragging = i == dragIndex
                Row(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragY else 0f }
                        .background(if (dragging) Lk.Toast else Color.Transparent)
                        .pointerInput(sc.id, list) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    order = list
                                    dragIndex = list.indexOf(sc)
                                    dragY = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val now = order ?: return@detectDragGesturesAfterLongPress
                                    dragY += amount.y
                                    val to = when {
                                        dragY > rowPx / 2 && dragIndex + 1 < now.size -> dragIndex + 1
                                        dragY < -rowPx / 2 && dragIndex > 0 -> dragIndex - 1
                                        else -> return@detectDragGesturesAfterLongPress
                                    }
                                    order = now.toMutableList().apply { add(to, removeAt(dragIndex)) }
                                    dragY += if (to > dragIndex) -rowPx else rowPx
                                    dragIndex = to
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onDragEnd = {
                                    order?.let(save)
                                    order = null
                                    dragIndex = -1
                                },
                                onDragCancel = {
                                    order = null
                                    dragIndex = -1
                                },
                            )
                        }
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Menu, contentDescription = null, tint = Lk.Muted, modifier = Modifier.size(18.dp))
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { ShortcutIcon(sc, 20.dp) }
                    Text(
                        // 칸이 적은 모양으로 바꾸면 뒤쪽은 저장만 되고 안 보입니다.
                        if (i >= slots) "$name · 칸이 모자라 안 보여요" else name,
                        style = Lk.Body15,
                        color = if (i >= slots) Lk.Muted else Lk.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Press({ save(list - sc) }, Modifier.size(40.dp).semantics { contentDescription = "$name 빼기" }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (list.isEmpty()) {
            Text("아래에서 넣어 주세요.", style = Lk.Caption, color = Lk.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        SectionLabel(if (full) "넣기 · ${slots}칸이 다 찼어요" else "넣기")
        val present = list.map { it.id }.toSet()
        val builtIn = listOf(Shortcut.ADD, Shortcut.CALC, Shortcut.ALARM, Shortcut.CALENDAR).map { Shortcut(it) }.filter { it.id !in present }
        var appsOpen by remember { mutableStateOf(false) }
        val apps = remember(appsOpen) { if (appsOpen) Shortcuts.installedApps(context) else emptyList() }
        val addRow: @Composable (Shortcut, String) -> Unit = { sc, name ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { ShortcutIcon(sc, 20.dp) }
                Text(name, style = Lk.Body15, color = if (full) Lk.Muted else Lk.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Press({ save(list + sc) }, Modifier.size(40.dp).semantics { contentDescription = "$name 넣기" }, enabled = !full) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = if (full) Lk.Muted else Lk.Text, modifier = Modifier.size(20.dp))
                }
            }
        }
        builtIn.forEach { sc -> addRow(sc, Shortcuts.name(context, sc)) }
        Press(
            { appsOpen = !appsOpen },
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .semantics { stateDescription = if (appsOpen) "펼침" else "접힘" },
            shape = RoundedCornerShape(0.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(start = 16.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("앱", style = Lk.Body15, fontWeight = Lk.Strong, color = Lk.Text, modifier = Modifier.weight(1f))
                Icon(if (appsOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Lk.Muted, modifier = Modifier.size(20.dp))
            }
        }
        if (appsOpen) apps.filter { it.first.id !in present }.forEach { (sc, name) -> addRow(sc, name) }
    }
}

/** 패널 안 묶음 이름(13/18 600, 흐리게). */
@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Lk.Caption, fontWeight = Lk.Strong, color = Lk.Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

/** 알람 목록: 울릴 시각순, 줄마다 끄기. */
@Composable
internal fun AlarmPanel(alarms: List<Alarm>, onOff: (Alarm) -> Unit, onClose: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000 - value % 1_000)
        }
    }
    PanelFrame("알람 ${alarms.size}개", "가장 빨리 울릴 알람이 위 알람 줄에 보여요.", onClose) {
        alarms.sortedBy { it.at }.forEach { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(hhmm(a.at), style = Lk.Body15.merge(Tnum), fontWeight = Lk.Strong, color = Lk.Text, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label.ifEmpty { "이름 없음" }, style = Lk.Body15, color = Lk.Text)
                    Text("${fmtLeft(a.at - now)} 남음", style = Lk.Caption.merge(Tnum), color = Lk.Muted)
                }
                SmallButton("끄기", { onOff(a) }, Modifier.semantics { contentDescription = "${hhmm(a.at)} ${a.label.ifEmpty { "이름 없는" }} 알람 끄기" })
            }
            Divider(Lk.DividerSoft)
        }
    }
}

/** 페이지 이름 줄: 고른 칸은 페이지를 넘기는 만큼 따라 미끄러집니다([position] = 페이지 + 넘긴 비율). */
@Composable
internal fun PageTabs(names: List<String>, position: Float, current: Int, onTap: (Int) -> Unit) {
    // 칸 폭: 가장 긴 이름(굵게) + 20, 최소 62 — 큰 글씨에서 '포켓북'이 칸 끝에 붙거나 잘리지 않게
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val want = remember(names, density) {
        val w = names.maxOf { measurer.measure(it, Lk.Body15.copy(fontWeight = Lk.Strong), maxLines = 1).size.width }
        maxOf(62.dp, with(density) { w.toDp() } + 20.dp)
    }
    BoxWithConstraints {
    // 줄 폭이 모자라면(아주 큰 글씨) 칸을 줄이고 글자는 말줄임
    val tabW = minOf(want, (maxWidth - 12.dp) / names.size)
    Box(
        Modifier
            .height(44.dp)
            .cover(Pill)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = tabW * position.coerceIn(0f, (names.size - 1).toFloat()))
                .size(tabW, 32.dp)
                .background(Lk.Selected, Pill),
        )
        Row {
            names.forEachIndexed { i, name ->
                val on = i == current
                Box(
                    Modifier
                        .size(tabW, 44.dp)
                        .clip(Pill)
                        .clickable(role = Role.Tab) { onTap(i) }
                        .semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, style = Lk.Body15, fontWeight = if (on) Lk.Strong else FontWeight.Normal, color = if (on) Lk.Text else Lk.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    }
}

/**
 * 할 일 화면의 페이지 줄: 왼쪽 탭 · 오른쪽 알림 버튼 · 설정(사이 8). 큰 글씨·큰 화면 배율에서도 설정이 밀려 잘리지 않게
 * 설정 → 탭 → 알림 버튼 순으로 폭을 나눠 주고, 알림 버튼은 남은 폭에 맞춰 앱 아이콘 수를 줄입니다([BellButton]).
 */
@Composable
internal fun PageRow(tabs: (@Composable () -> Unit)?, bell: (@Composable () -> Unit)?, settings: @Composable () -> Unit) {
    Layout(contents = listOf(tabs ?: {}, bell ?: {}, settings)) { (tm, bm, sm), c ->
        val gap = 8.dp.roundToPx()
        val s = sm.first().measure(Constraints(maxWidth = c.maxWidth))
        // 알림 버튼은 가장 작은 모양(44 원)만큼 먼저 비워 두고 탭에 나머지를 줌
        val bellMin = if (bm.isEmpty()) 0 else 44.dp.roundToPx() + gap
        val t = tm.firstOrNull()?.measure(Constraints(maxWidth = (c.maxWidth - s.width - gap - bellMin).coerceAtLeast(0)))
        val used = s.width + (t?.let { it.width + gap } ?: 0)
        val b = bm.firstOrNull()?.measure(Constraints(maxWidth = (c.maxWidth - used - gap).coerceAtLeast(0)))
        val h = listOfNotNull(s, t, b).maxOf { it.height }
        layout(c.maxWidth, h) {
            t?.place(0, (h - t.height) / 2)
            s.place(c.maxWidth - s.width, (h - s.height) / 2)
            b?.place(c.maxWidth - s.width - gap - b.width, (h - b.height) / 2)
        }
    }
}

/** 둥근 유리 버튼 44(설정·종). */
@Composable
internal fun RoundButton(label: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Press(
        onClick,
        Modifier
            .size(44.dp)
            .semantics { contentDescription = label },
        color = Lk.Cover,
        border = Lk.Edge,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClick?.let { "바로가기 편집" },
    ) { content() }
}

@Composable
internal fun SettingsButton(onClick: () -> Unit) = RoundButton("설정", onClick) {
    Icon(Icons.Outlined.Settings, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp))
}

/**
 * 근무·포켓북 페이지의 알림 버튼. 알림이 없으면 유리 원과 종, 있으면 할 일 페이지 알림 줄과 같은 옅은 알약 안에
 * 종 · 앱 아이콘(최근 2개, 나란히) · 빨간 전체 개수. 아이콘·배지를 겹치지 않고 한 줄에 둡니다. 목록이 열린 동안은 진한 바탕.
 */
@Composable
internal fun BellButton(notis: List<Noti>, open: Boolean, onClick: () -> Unit) {
    if (notis.isEmpty()) {
        RoundButton("알림 없음", onClick) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp))
        }
        return
    }
    // 남은 폭에 들어가는 만큼만 앱 아이콘(최근 2개 → 1개 → 없음): 알약 = 앞 12 · 종 16 · (6 + 아이콘 24) × n · 6 · 배지 · 뒤 10
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val badgeW = remember(notis.size, density) {
        val w = measurer.measure(if (notis.size > 9) "9+" else "${notis.size}", Lk.Caption.merge(Tnum).copy(fontWeight = Lk.Strong), maxLines = 1).size.width
        maxOf(18.dp, with(density) { w.toDp() } + 10.dp)
    }
    val recent = notis.map { it.pkg }.distinct().take(2)
    BoxWithConstraints {
    val fixed = 12.dp + 16.dp + 6.dp + badgeW + 10.dp
    if (fixed > maxWidth) {
        // 알약도 안 들어가면(아주 큰 글씨) 설정과 같은 44 원 안에 종, 개수는 원 밖 오른쪽 위 배지
        Box {
            Press(
                onClick,
                Modifier
                    .size(44.dp)
                    .semantics {
                        contentDescription = "알림 ${notis.size}개 보기"
                        stateDescription = if (open) "펼침" else "접힘"
                    },
                color = if (open) Lk.Selected else Lk.Rail,
                border = Lk.Edge,
            ) { Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp)) }
            Badge(notis.size, Modifier.align(Alignment.TopEnd).clearAndSetSemantics {})
        }
        return@BoxWithConstraints
    }
    val apps = recent.take((0..recent.size).lastOrNull { fixed + 30.dp * it <= maxWidth } ?: 0)
    Press(
        onClick,
        Modifier
            .height(44.dp)
            .semantics {
                contentDescription = "알림 ${notis.size}개 보기"
                stateDescription = if (open) "펼침" else "접힘"
            },
        shape = Pill,
        color = if (open) Lk.Selected else Lk.Rail,
        border = Lk.Edge,
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Lk.Body, modifier = Modifier.size(16.dp))
            apps.forEach { AppIcon(it, 24.dp) }
            Badge(notis.size, Modifier.clearAndSetSemantics {}, inline = true)
        }
    }
    }
}

/**
 * 할 일 페이지 카드 안 오른쪽 세로 줄: 할 일 줄과 섞이지 않게 옅은 바탕의 세로 알약, 맨 위 종.
 * 앱마다 아이콘 하나(최근 3개), 같은 앱 2개 이상이면 개수 배지. 누르면 알림 목록.
 * 배지가 둥근 모서리에 잘리지 않게 바탕은 자르지 않고 칠하기만 합니다.
 */
@Composable
internal fun NotiRail(notis: List<Noti>, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .width(44.dp)
            .background(Lk.Rail, shape)
            .border(Lk.Edge, shape)
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Notifications, contentDescription = null, tint = Lk.Body, modifier = Modifier.size(16.dp))
        notis.groupBy { it.pkg }.entries.take(3).forEach { (pkg, list) ->
            Box(Modifier.size(44.dp, 40.dp)) {
                Press(
                    onTap,
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "${list.first().app} 알림 ${list.size}개 보기" },
                    shape = Pill,
                ) { AppIcon(pkg, 32.dp) }
                // 아이콘(가운데 32)의 오른쪽 위 모서리에서 4 나감
                if (list.size > 1) Badge(list.size, Modifier.align(Alignment.TopEnd).offset((-6).dp, 4.dp).clearAndSetSemantics {})
            }
        }
    }
}

/**
 * 할 일 추가(높이 44). 누르면 그 자리가 입력 칸: 강조 테두리 2, 왼쪽 닫기, 오른쪽 추가.
 * 추가 버튼은 바깥 알약과 같은 중심의 둥근 끝(테두리 안쪽에서 위·아래·오른쪽 4씩, 높이 32 · 반지름 16 = 22 − 6)이고
 * 누르는 칸은 높이 44 전체. 글자가 없으면 흐리게 두고 눌리지 않습니다(키보드 완료는 빈 칸이면 닫기).
 * 추가나 키보드 완료면 맨 위에 넣고 닫습니다.
 */
@Composable
internal fun AddBar(adding: Boolean, onAdding: (Boolean) -> Unit, onAdd: (String) -> Unit) {
    val accent = LocalLockAccent.current
    if (!adding) {
        Press(
            { onAdding(true) },
            Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = Pill,
            color = Lk.Cover,
            border = Lk.Edge,
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp))
                Text("할 일 추가", style = Lk.Body15, fontWeight = Lk.Strong, color = Lk.Text)
            }
        }
        return
    }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // 잠금화면 위에서는 창이 포커스를 받은 뒤에야 키보드가 뜹니다.
    LaunchedEffect(windowFocused) { if (windowFocused) focus.requestFocus() }
    CloseWhenImeHides(onClose = { if (text.isBlank()) onAdding(false) })
    val submit = { if (text.isBlank()) onAdding(false) else onAdd(text) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Lk.Cover, Pill)
            .border(2.dp, accent, Pill)
            .padding(start = 2.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Press({ onAdding(false) }, Modifier.size(40.dp).semantics { contentDescription = "입력 닫기" }) {
            Icon(Icons.Default.Close, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp))
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = Lk.Body15.copy(color = Lk.Text),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("할 일 적기", style = Lk.Body15, color = Lk.Muted)
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .semantics { contentDescription = "새 할 일" },
        )
        val ready = text.isNotBlank()
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(100, easing = Lk.Decel), label = "add")
        val fill by animateColorAsState(if (ready) accent else Lk.Control, tween(150), label = "addFill")
        Box(
            Modifier
                .fillMaxHeight()
                .clickable(source, indication = null, enabled = ready, role = Role.Button, onClick = submit)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(fill, Pill)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("추가", style = Lk.Body15, fontWeight = Lk.Strong, color = if (ready) Lk.OnAccent else Lk.Muted)
            }
        }
    }
}

/** 할 일 줄 팝업의 한 줄. [danger] 면 붉은 글자(지우기). */
internal class RowMenuItem(val label: String, val danger: Boolean = false, val icon: @Composable () -> Unit, val onClick: () -> Unit)

/**
 * 할 일 줄 글자를 누르거나 길게 누르면 그 줄 아래(자리가 없으면 위)에 뜨는 팝업: 불투명 #2C2C2E, 모서리 16, 테두리 흰 14%,
 * 줄 48(아이콘 20 · 15/20). 잠금화면 모서리는 카드 24 · 팝업 16 · 입력 칸 12 · 알약.
 */
@Composable
internal fun RowMenu(expanded: Boolean, items: () -> List<RowMenuItem>, onDismiss: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Lk.Toast,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = Lk.Edge,
    ) {
        if (expanded) {
            items().forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label, style = Lk.Body15, color = if (m.danger) Lk.Missed else Lk.Text) },
                    leadingIcon = m.icon,
                    onClick = m.onClick,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** 열고 닫을 때 움직이는 덩어리. (Column 안의 Box 에서 부르면 ColumnScope 판이 잡혀서 따로 둡니다.) */
@Composable
internal fun Reveal(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    content: @Composable () -> Unit,
) = androidx.compose.animation.AnimatedVisibility(visible, modifier, enter, exit) { content() }

/** 키보드가 한 번 올라왔다가 내려가면 [onClose]. 뒤로 가기로 키보드만 내린 경우를 알아차리려고 씁니다. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun CloseWhenImeHides(onClose: () -> Unit) {
    val imeVisible = WindowInsets.isImeVisible
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) seen = true else if (seen) onClose()
    }
}

/**
 * 아래 바로가기(높이 44): [style] 0 한 줄 3개, 1 한 줄 2개(아이콘 16 + 이름 13/18 600), 2 아이콘만(44 원, 사이 16, 가운데).
 * 길게 누르면 [onEdit](카드 자리에 편집 패널).
 */
@Composable
internal fun ShortcutGrid(items: List<Shortcut>, style: Int, onTap: (Shortcut) -> Unit, onEdit: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val edit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onEdit()
    }
    val context = LocalContext.current
    if (style == 2) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(5).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { sc ->
                        val name = remember(sc) { Shortcuts.name(context, sc) }
                        RoundButton(name, { onTap(sc) }, onLongClick = edit) { ShortcutIcon(sc, 20.dp) }
                    }
                }
            }
        }
        return
    }
    val perRow = if (style == 1) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sc ->
                    val name = remember(sc) { Shortcuts.name(context, sc) }
                    Press(
                        { onTap(sc) },
                        Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = Pill,
                        color = Lk.Cover,
                        border = Lk.Edge,
                        onLongClick = edit,
                        onLongClickLabel = "바로가기 편집",
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ShortcutIcon(sc, 16.dp)
                            Text(name, style = Lk.Caption, fontWeight = Lk.Strong, color = Lk.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun ShortcutIcon(sc: Shortcut, size: Dp) {
    val context = LocalContext.current
    val icon = remember(sc.id) { Shortcuts.icon(context, sc) }
    if (icon != null) {
        // 앱 아이콘은 동그랗게, 글자 아이콘보다 한 단계 크게
        Image(icon, contentDescription = null, modifier = Modifier.size(size + 4.dp).clip(CircleShape))
    } else {
        Icon(painterResource(Shortcuts.builtInIcon(sc)), contentDescription = null, tint = Lk.Text, modifier = Modifier.size(size))
    }
}

/**
 * 맨 아래 잠금 해제 손잡이(높이 52): 화살표 · '위로 밀어서 잠금 해제' · 막대 40×5. 여기서 시작해 밀어도 되고 화면 어디서 밀어도 됩니다
 * (밀기는 할 일 화면 전체가 받음). 손가락이 내비게이션 바까지 내려가지 않게 넉넉히 둡니다. 화면 낭독기에서는 이 줄의 동작으로 잠금을 풉니다.
 */
@Composable
internal fun UnlockHint(locked: Boolean, onUnlock: () -> Unit) {
    val text = if (locked) "위로 밀어서 잠금 해제" else "위로 밀어서 닫기"
    Column(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(if (locked) "잠금 해제" else "닫기") { onUnlock(); true })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = Lk.Caption, fontWeight = Lk.Strong, color = Lk.Text)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(40.dp, 5.dp).background(Lk.Body, Pill))
    }
}

/** 빈 목록: 아이콘 32, 제목 20/28 600, 안내 13/18. */
@Composable
internal fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = Lk.Muted, modifier = Modifier.size(32.dp))
        Text(title, style = Lk.Title, fontWeight = Lk.Strong, color = Lk.Text, textAlign = TextAlign.Center)
        Text(hint, style = Lk.Caption, color = Lk.Muted, textAlign = TextAlign.Center)
    }
}

/**
 * 알람 입력(카드 아래 시트): '몇 분 뒤'(칩 5·10·15·30·60분) 또는 '몇 시에'(네 자리, 지난 시각이면 내일).
 * 오른쪽에 울릴 시각이나 남은 시간을 미리 보여 주고, 키보드의 완료는 시작과 같습니다.
 */
@Composable
internal fun AlarmSheet(onStart: (Long, String) -> Unit, onCancel: () -> Unit, onAllow: () -> Unit, initialLabel: String = "") {
    val context = LocalContext.current
    val accent = LocalLockAccent.current
    val exactAlarm = remember { Setup.exactAlarmAllowed(context) }
    var atClock by remember { mutableStateOf(false) }
    // 30분을 고른 채로 열고, 숫자를 누르면 바로 바뀌게 전체 선택
    var digits by remember { mutableStateOf(TextFieldValue("30", TextRange(0, 2))) }
    // 할 일 줄 팝업의 '타이머 맞추기'면 할 일 이름을 미리 채워 두고, 커서는 분 칸에 둡니다.
    var label by remember { mutableStateOf(initialLabel) }
    var withLabel by remember { mutableStateOf(initialLabel.isNotEmpty()) }
    var labelAsked by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val labelFocus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused && !labelAsked) focus.requestFocus() }
    LaunchedEffect(labelAsked) { if (labelAsked) labelFocus.requestFocus() }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000 - value % 1_000)
        }
    }
    val minutes = digits.text
    val target = if (atClock) clockTarget(minutes) else minutes.toIntOrNull()?.takeIf { it > 0 }?.let { now + it * 60_000L }
    val start: () -> Unit = { if (target != null) onStart(target, label.trim()) }
    val setMode = { clock: Boolean ->
        if (atClock != clock) digits = TextFieldValue(if (clock) "" else "30", TextRange(0, if (clock) 0 else 2))
        atClock = clock
        focus.requestFocus()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .enterFrom(32.dp)
            .cover(RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 방식 고르기: 고른 칸이 미끄러져 옮겨 감
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.White.copy(alpha = 0.08f), Pill)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val half = (maxWidth - 4.dp) / 2
            val x by animateDpAsState(if (atClock) half + 4.dp else 0.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "seg")
            Box(
                Modifier
                    .offset(x = x)
                    .size(half, 32.dp)
                    .background(Lk.Selected, Pill),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("몇 분 뒤", "몇 시에").forEachIndexed { i, name ->
                    val on = atClock == (i == 1)
                    Box(
                        Modifier
                            .size(half, 44.dp)
                            .clip(Pill)
                            .clickable(role = Role.Tab) { setMode(i == 1) }
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) { Text(name, style = Lk.Body15, fontWeight = if (on) Lk.Strong else FontWeight.Normal, color = if (on) Lk.Text else Lk.Muted) }
                }
            }
        }
        // 항암 끝 같은 시각을 맞추는 곳이라 늦을 수 있으면 여기서 바로 알립니다. 허용하면 맞춰 둔 알람도 제시간으로 다시 맞춰집니다.
        if (!exactAlarm) {
            Text(
                "'알람 및 리마인더'가 꺼져 있어 늦게 울릴 수 있어요. 눌러서 허용",
                style = Lk.Caption,
                color = Color(0xFFFF9F0A),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAllow)
                    .padding(vertical = 4.dp),
            )
        }
        Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = digits,
                onValueChange = { v ->
                    val d = v.text.filter(Char::isDigit).take(if (atClock) 4 else 3)
                    digits = TextFieldValue(d, TextRange(d.length))
                },
                visualTransformation = if (atClock) ClockDigits else VisualTransformation.None,
                singleLine = true,
                textStyle = Lk.Display.copy(color = Lk.Text),
                cursorBrush = SolidColor(accent),
                // 숫자 자판만 뜨게 (NumberPassword 자판에는 점·쉼표·부호가 없음. 글자는 가리지 않음)
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { start() }),
                decorationBox = { inner ->
                    Box {
                        if (minutes.isEmpty()) Text(if (atClock) "00:00" else "0", style = Lk.Display, color = Lk.Muted.copy(alpha = 0.4f))
                        inner()
                    }
                },
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 28.dp)
                    .focusRequester(focus)
                    .semantics { contentDescription = if (atClock) "울릴 시각" else "몇 분 뒤" },
            )
            Text(
                when {
                    !atClock -> "분"
                    target == null -> ""
                    Day.of(target) == Day.today() -> "오늘"
                    else -> "내일"
                },
                style = Lk.Title,
                color = Lk.Muted,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    target == null -> ""
                    atClock -> "${fmtLeft(target - now)} 남음"
                    else -> "${hhmm(target)}에 울림"
                },
                style = Lk.Caption.merge(Tnum),
                color = Lk.Muted,
                maxLines = 1,
            )
        }
        if (!atClock) {
            Row(Modifier.offset(x = (-4).dp).fillMaxWidth()) {
                listOf(5, 10, 15, 30, 60).forEach { m ->
                    val on = minutes == "$m"
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(Pill)
                            .clickable(role = Role.Button) { digits = TextFieldValue("$m", TextRange(0, "$m".length)) }
                            .padding(4.dp)
                            .background(if (on) accent else Lk.Control, Pill),
                        contentAlignment = Alignment.Center,
                    ) { Text("${m}분", style = Lk.Caption, fontWeight = Lk.Strong, color = if (on) Lk.OnAccent else Lk.Text) }
                }
            }
        } else {
            Text("네 자리로 적어요 (예: 0730). 지난 시각이면 내일로 맞춰져요.", style = Lk.Caption, color = Lk.Muted)
        }
        if (withLabel) {
            BasicTextField(
                value = label,
                onValueChange = { label = it.take(30) },
                singleLine = true,
                textStyle = Lk.Body15.copy(color = Lk.Text),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { start() }),
                decorationBox = { inner ->
                    Box {
                        if (label.isEmpty()) Text("이름 (예: 항암 끝)", style = Lk.Body15, color = Lk.Muted)
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Lk.Control, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .focusRequester(labelFocus),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) {
                        withLabel = true
                        labelAsked = true
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp))
                Text("이름 붙이기", style = Lk.Body15, color = Lk.Text)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("취소", onCancel, Modifier.weight(1f))
            PillButton("시작", start, Modifier.weight(1f).graphicsLayer { alpha = if (target != null) 1f else 0.4f }, filled = true)
        }
    }
}

/** '몇 시에' 숫자(9 → 9:00, 930 → 9:30, 1430 → 14:30)의 다음 그 시각(ms). 지난 시각이면 내일, 틀린 시각이면 null. */
internal fun clockTarget(digits: String, now: LocalDateTime = LocalDateTime.now()): Long? {
    if (digits.isEmpty()) return null
    val (h, m) = when (digits.length) {
        1, 2 -> digits.toInt() to 0
        3 -> digits.take(1).toInt() to digits.drop(1).toInt()
        else -> digits.take(2).toInt() to digits.drop(2).take(2).toInt()
    }
    if (h > 23 || m > 59) return null
    val today = now.toLocalDate().atTime(h, m)
    val at = if (today.isAfter(now)) today else today.plusDays(1)
    return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** '몇 시에' 칸: 숫자 3~4개면 가운데 쌍점을 보여 줍니다(입력은 숫자만). */
private val ClockDigits = VisualTransformation { text ->
    val d = text.text
    val colon = when (d.length) {
        3 -> 1
        4 -> 2
        else -> return@VisualTransformation TransformedText(text, OffsetMapping.Identity)
    }
    TransformedText(
        AnnotatedString(d.take(colon) + ":" + d.drop(colon)),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = if (offset <= colon) offset else offset + 1
            override fun transformedToOriginal(offset: Int) = if (offset <= colon) offset else offset - 1
        },
    )
}
