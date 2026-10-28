package com.ttaak.app.lock

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.R
import com.ttaak.app.data.Alarm
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.Shortcut
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import com.ttaak.app.data.cardTextSp
import com.ttaak.app.data.shortcutSlots
import com.ttaak.app.ui.MainActivity
import com.ttaak.app.ui.TtaakTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 잠금화면 위에 바로 뜨는 딱 화면 (showWhenLocked). 툭의 잠금화면처럼 배경화면 위에 시계와 유리 카드 하나만 둡니다.
 * 잠금화면 카드, 상단바 타일, 위젯 `+`, 그리고 '화면 켜면 딱 화면 먼저 보기'(LockPageService)에서 열립니다.
 * 동그라미를 누르면 체크, 위로 밀거나 뒤로 가기를 누르면 원래 잠금화면으로 돌아갑니다.
 * 아래 반투명 버튼으로 항암 주입속도 계산기와 '몇 분 뒤' 알람을 열고, 맞춰 둔 알람은 남은 시간이 보입니다.
 * 할 일 추가는 카드 아래 넓은 줄, 카드 안 메모 버튼, 아래 바로가기 중 하나로 합니다(설정).
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 배경화면 위라 상태바 글씨는 늘 흰색
        enableEdgeToEdge(
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 27) {
            // 8.0 은 매니페스트의 showWhenLocked 를 무시하므로 창 플래그로 대신합니다.
            // 화면을 켜는 플래그는 넣지 않습니다: 늘 화면이 켜진 뒤에 열리고, 늦게 열려도 꺼진 화면을 다시 켜지 않게.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        TaskStore.init(this)
        // 화면을 켜거나 카드를 눌러 열면 보고 체크가 먼저라 입력칸을 접어 둡니다. 타일·위젯 `+` 로 열면 바로 입력.
        val adding = !intent.getBooleanExtra(EXTRA_LOOK_FIRST, false)
        setContent {
            val settings by TaskStore.settings.collectAsStateWithLifecycle()
            TtaakTheme(settings) {
                LockPage(adding, onClose = { finish() }, onOpen = ::openApp, onOpenNoti = ::openNoti)
            }
        }
    }

    /** 잠금을 풀고 앱(의 [target] 화면)으로. 밀어서 잠금이면 바로, PIN 이 걸려 있으면 PIN 을 넣은 뒤 열립니다. */
    private fun openApp(target: Intent) = unlockThen { startActivity(target) }

    /** 다른 앱 알림을 누르면 잠금을 풀고 그 알림의 화면으로. */
    private fun openNoti(n: Noti) = unlockThen { NotiListener.open(this, n) }

    private fun unlockThen(action: () -> Unit) {
        val go = {
            action()
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) return go()
        km.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = go()
            },
        )
    }

    companion object {
        const val EXTRA_LOOK_FIRST = "lookFirst"
    }
}

internal val Glass = Color.Black.copy(alpha = 0.32f)
internal val GlassEdge = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))

/** 할 일 줄의 동그라미 누름 칸 높이. 줄 간격을 좁히면 같이 줄어 줄이 촘촘해집니다(최대 손가락 크기 48dp). */
internal fun circleBoxHeight(rowGap: Int) = (26 + 2 * rowGap).coerceIn(32, 48).dp

@Composable
private fun LockPage(startAdding: Boolean, onClose: () -> Unit, onOpen: (Intent) -> Unit, onOpenNoti: (Noti) -> Unit) {
    val context = LocalContext.current
    val onOpenApp = { onOpen(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val all by TaskStore.tasks.collectAsStateWithLifecycle()
    val alarms by TaskStore.alarms.collectAsStateWithLifecycle()
    val settings by TaskStore.settings.collectAsStateWithLifecycle()
    val allNotis by NotiListener.notis.collectAsStateWithLifecycle()
    val notis = allNotis.filter { settings.notiApps.isEmpty() || it.pkg in settings.notiApps }
    val today = Day.today()
    // 끝낸 일은 사라지고 앱의 '완료된 할 일'에서만 보입니다.
    val tasks = remember(all, today) { TaskStore.tasksFor(today, today).filterNot { it.isDone(today) } }
    val anyDone = remember(all, today) { TaskStore.tasksFor(today, today).any { it.isDone(today) } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(startAdding) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var askingAlarm by remember { mutableStateOf(false) }
    BackHandler(enabled = editingId != null) { editingId = null }
    BackHandler(enabled = askingAlarm) { askingAlarm = false }
    // 키보드를 쓰는 동안엔 시계와 아래 버튼을 접어 자리를 만듭니다.
    val typing = adding || editingId != null || askingAlarm
    // 지워진 앱은 빼고 고른 모양의 칸 수까지
    val shortcuts = remember(settings.shortcuts, settings.shortcutStyle) {
        settings.shortcuts.filter { Shortcuts.defaultName(context, it) != null }.take(settings.shortcutSlots())
    }
    // 추가 버튼은 한 군데만: 바로가기에 '할 일 추가'가 있으면 위쪽 버튼은 숨기고, 적는 동안엔 메모 버튼도 숨깁니다.
    val addInShortcuts = shortcuts.any { it.id == Shortcut.ADD }
    val addBar = !addInShortcuts && settings.addButton == 0
    val addInCard = !addInShortcuts && settings.addButton == 1 && !typing
    val memo = @Composable { MemoButton { adding = true } }
    val onShortcut: (Shortcut) -> Unit = { sc ->
        when (sc.id) {
            Shortcut.CALC -> onOpen(MainActivity.calcIntent(context))
            Shortcut.ALARM -> askingAlarm = true
            Shortcut.ADD -> adding = true
            Shortcut.CALENDAR -> onOpen(MainActivity.calendarIntent(context))
            else -> Shortcuts.launchIntent(sc)?.let(onOpen)
        }
    }
    var drag by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            // 밝은 배경화면에서도 흰 글씨가 보이게 살짝만 어둡게
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.5f to Color.Black.copy(alpha = 0.15f),
                    1f to Color.Black.copy(alpha = 0.45f),
                ),
            )
            .pointerInput(Unit) {
                // 잠금 해제하듯 위로 밀면 닫힘
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag < -120.dp.toPx()) onClose()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, dy -> drag += dy }
            }
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 위(시계·타이머·할 일·입력)는 아래 바로가기가 차지하고 남은 자리를 씁니다.
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (typing) Spacer(Modifier.height(24.dp)) else Clock(Modifier.padding(top = 24.dp, bottom = 20.dp))
                if (alarms.isNotEmpty()) {
                    Timers(alarms) { Alarms.cancel(context, it) }
                    Spacer(Modifier.height(12.dp))
                }

                Surface(
                    color = Glass,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(28.dp),
                    border = GlassEdge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    if (tasks.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (anyDone) "다 했어요!" else "오늘 할 일이 없어요",
                                fontSize = settings.cardTextSp().sp,
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 24.dp, vertical = 22.dp),
                            )
                            if (addInCard) Box(Modifier.padding(end = 9.dp)) { memo() }
                        }
                    } else {
                        LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(tasks, key = { it.id }) {
                                Box(Modifier.animateItem()) {
                                    LockRow(
                                        it, today, settings.cardTextSp(), settings.rowGap, settings.checkOnLeft,
                                        editing = it.id == editingId,
                                        onEditing = { on -> editingId = if (on) it.id else null },
                                        onOpenApp = onOpenApp,
                                        // 메모 버튼은 맨 아래 할 일의 오른쪽 끝에
                                        trailing = memo.takeIf { _ -> addInCard && it.id == tasks.last().id },
                                    )
                                }
                            }
                        }
                    }
                }

                if (askingAlarm || addBar || adding) Spacer(Modifier.height(12.dp))
                if (askingAlarm) {
                    AlarmAsk(
                        onStart = { minutes, label ->
                            Alarms.add(context, minutes, label)
                            askingAlarm = false
                        },
                        onCancel = { askingAlarm = false },
                    )
                } else if (addBar || adding) {
                    AddField(
                        adding = adding,
                        onAdding = { adding = it },
                        onAdded = { added ->
                            val index = TaskStore.tasksFor(today, today).indexOfFirst { it.id == added.id }
                            if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                        },
                    )
                }
                if (!typing && notis.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    NotiList(notis, onOpenNoti)
                }
            }
            if (!typing) {
                if (shortcuts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ShortcutGrid(shortcuts, iconsOnly = settings.shortcutStyle == 2, onShortcut)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "위로 밀어서 닫기",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

/**
 * 아래쪽 바로가기: 계산기·알람·할 일 추가·고른 앱. 낮은 반투명 타일에 작은 아이콘과 오른쪽 이름(한 줄 3개),
 * 또는 [iconsOnly] 면 아이콘만 한 줄 4개.
 */
@Composable
private fun ShortcutGrid(items: List<Shortcut>, iconsOnly: Boolean, onTap: (Shortcut) -> Unit) {
    val perRow = if (iconsOnly) 4 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { sc -> ShortcutTile(sc, iconsOnly, Modifier.weight(1f)) { onTap(sc) } }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShortcutTile(sc: Shortcut, iconsOnly: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val name = remember(sc) { Shortcuts.name(context, sc) }
    val icon = remember(sc.id) { Shortcuts.icon(context, sc) }
    Surface(
        onClick = onClick,
        color = Glass,
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = GlassEdge,
        modifier = modifier,
    ) {
        val iconSize = if (iconsOnly) 24.dp else 20.dp
        // 아이콘만 보일 땐 이름을 화면 낭독기에 알려 줍니다.
        val label = name.takeIf { iconsOnly }
        val iconView = @Composable {
            when {
                icon != null -> Image(icon, contentDescription = label, modifier = Modifier.size(iconSize))
                else -> Icon(
                    painterResource(Shortcuts.builtInIcon(sc)),
                    contentDescription = label,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
        if (iconsOnly) {
            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) { iconView() }
        } else {
            // 좁은 폰(360dp)에서도 한글 4~5글자가 들어가게 여백을 줄였습니다.
            Row(
                Modifier.padding(start = 8.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                iconView()
                Spacer(Modifier.width(5.dp))
                Text(name, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 다른 앱 알림 (최근 3개): 앱 아이콘, 제목·시각, 내용 한 줄. 누르면 잠금을 풀고 그 앱으로. */
@Composable
private fun NotiList(items: List<Noti>, onTap: (Noti) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.take(3).forEach { n ->
            val icon = remember(n.pkg) { NotiListener.icon(context, n.pkg) }
            Surface(
                onClick = { onTap(n) },
                color = Glass,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = GlassEdge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                n.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(Fmt.stamp(n.time).removePrefix("오늘 "), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        if (n.text.isNotBlank()) {
                            Text(n.text, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (items.size > 3) {
            Text(
                "알림 ${items.size - 3}개 더",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** 맞춰 둔 알람마다 남은 시간을 초 단위로. ✕ 로 취소. */
@Composable
private fun Timers(alarms: List<Alarm>, onCancel: (Long) -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000 - value % 1_000)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        alarms.forEach { alarm ->
            Surface(
                color = Glass,
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                border = GlassEdge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_alarm),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        Fmt.countdown(alarm.at - now),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(fontFeatureSettings = "tnum"), // 숫자 폭을 같게 해서 초가 바뀌어도 안 흔들림
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        alarm.label.ifEmpty { "${alarm.minutes}분 알람" },
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onCancel(alarm.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "알람 취소", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

/** '몇 분 뒤?' — 숫자만 받습니다. 이름은 '+ 이름 붙이기'를 눌렀을 때만 적습니다. */
@Composable
private fun AlarmAsk(onStart: (Int, String) -> Unit, onCancel: () -> Unit) {
    var minutes by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var withLabel by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val labelFocus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused) focus.requestFocus() }
    LaunchedEffect(withLabel) { if (withLabel) labelFocus.requestFocus() }
    val ready = (minutes.toIntOrNull() ?: 0) > 0
    val start = { if (ready) onStart(minutes.toInt(), label) }
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = Glass,
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        border = GlassEdge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("몇 분 뒤?", fontSize = 17.sp, color = Color.White.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(accent),
                    // 숫자 자판만 뜨게 (NumberPassword 자판에는 점·쉼표·부호가 없음. 글자는 가리지 않음)
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { start() }),
                    decorationBox = { inner ->
                        Box {
                            if (minutes.isEmpty()) Text("0", fontSize = 44.sp, color = Color.White.copy(alpha = 0.3f))
                            inner()
                        }
                    },
                    // 숫자 길이만큼만 차지해서 '분'이 바로 옆에 붙습니다.
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .widthIn(min = 28.dp)
                        .focusRequester(focus),
                )
                Text("분", fontSize = 22.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
            }
            if (withLabel) {
                BasicTextField(
                    value = label,
                    onValueChange = { label = it.take(30) },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { start() }),
                    decorationBox = { inner ->
                        Box {
                            if (label.isEmpty()) Text("이름 (예: 항암 끝)", fontSize = 18.sp, color = Color.White.copy(alpha = 0.4f))
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .focusRequester(labelFocus),
                )
            } else {
                Text(
                    "+ 이름 붙이기",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { withLabel = true }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onCancel,
                    color = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Box(contentAlignment = Alignment.Center) { Text("취소", fontSize = 17.sp) } }
                Surface(
                    onClick = start,
                    enabled = ready,
                    color = if (ready) accent else accent.copy(alpha = 0.35f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Box(contentAlignment = Alignment.Center) { Text("시작", fontSize = 17.sp, fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

/** 잠금화면을 가리는 대신 시계는 그대로 보여 줍니다. 분이 바뀔 때마다 다시 그립니다. */
@Composable
private fun Clock(modifier: Modifier) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - value.second * 1_000L - value.nano / 1_000_000L)
        }
    }
    // 시계와 날짜를 한 줄에 (글자 밑선을 맞춰서). 할 일 자리를 넓히려고 작게, 대신 얇은 글씨는 피합니다.
    Row(modifier) {
        Text(
            now.format(DateTimeFormatter.ofPattern("HH:mm")), // 폰 설정과 상관없이 24시간제
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            Fmt.date(now.toLocalDate().toEpochDay()),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * 동그라미 + 큰 글씨 한 줄. 동그라미를 눌러야만 체크되고(체크 표시를 잠깐 보여 준 뒤 사라짐),
 * 글씨를 누르면 앱으로, 글씨를 길게 누르면 그 자리에서 이름을 고칩니다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LockRow(
    task: Task,
    day: Long,
    sp: Float,
    gap: Int,
    checkOnLeft: Boolean,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    onOpenApp: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    var done by remember(task.id) { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(done) {
        if (done) {
            delay(350)
            TaskStore.toggle(task.id, day)
        }
    }
    val circle = @Composable {
        // 손가락 폭만큼(48dp) 눌리고, 보이는 동그라미는 26dp
        Box(
            Modifier
                .size(48.dp, circleBoxHeight(gap))
                .clip(CircleShape)
                .clickable(enabled = !done && !editing) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    done = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .then(
                        if (done) Modifier.background(accent, CircleShape)
                        else Modifier.border(2.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Default.Check, contentDescription = "완료", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checkOnLeft) circle()
        val textModifier = Modifier
            .weight(1f)
            .padding(horizontal = 6.dp)
        if (editing) {
            RenameField(task, sp, gap, onDone = { onEditing(false) }, modifier = textModifier)
        } else {
            Text(
                task.title,
                color = Color.White.copy(alpha = if (done) 0.45f else 1f),
                fontSize = sp.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = textModifier
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        enabled = !done,
                        onClick = onOpenApp,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEditing(true)
                        },
                    )
                    .padding(horizontal = 6.dp, vertical = gap.dp),
            )
        }
        // 오른쪽 체크일 때도 동그라미 줄이 다른 줄과 맞도록 메모 버튼은 동그라미 앞에
        trailing?.invoke()
        if (!checkOnLeft) circle()
    }
}

/** 카드 안 '할 일 추가' 버튼: 메모 아이콘만, 테마 색 동그라미라 유리 카드 위에서도 잘 보입니다. */
@Composable
private fun MemoButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_memo), contentDescription = "할 일 추가", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

/** 길게 눌러 여는 이름 고치기 칸. 엔터면 저장, 비우거나 그대로면 저장하지 않고 닫힘. */
@Composable
private fun RenameField(task: Task, sp: Float, gap: Int, onDone: () -> Unit, modifier: Modifier) {
    var text by remember { mutableStateOf(TextFieldValue(task.title, TextRange(task.title.length))) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused) focus.requestFocus() }
    // 키보드를 내리면 고치기를 취소합니다.
    CloseWhenImeHides(onClose = onDone)
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = sp.sp, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            val title = text.text.trim()
            if (title.isNotEmpty() && title != task.title) TaskStore.update(task.copy(title = title))
            // 포커스를 비워 둬야 남은 키 입력이 옆 줄 동그라미로 넘어가 체크되지 않습니다.
            focusManager.clearFocus()
            onDone()
        }),
        modifier = modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = gap.dp)
            .focusRequester(focus),
    )
}

/** 접혀 있으면 '＋ 할 일 추가' 알약, 누르면 그 자리에서 입력칸이 됩니다. 적고 엔터면 추가, 빈 채로 엔터면 접힘. */
@Composable
private fun AddField(adding: Boolean, onAdding: (Boolean) -> Unit, onAdded: (Task) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // 잠금화면 위에서는 창이 포커스를 받은 뒤에야 키보드가 뜹니다.
    LaunchedEffect(adding, windowFocused) { if (adding && windowFocused) focus.requestFocus() }
    // 빈 채로 키보드를 내리면 알약으로 접고 시계를 다시 보여 줍니다.
    if (adding) CloseWhenImeHides(onClose = { if (text.isBlank()) onAdding(false) })

    Surface(
        color = Glass,
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        border = GlassEdge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !adding) { onAdding(true) },
    ) {
        if (!adding) {
            Row(Modifier.padding(horizontal = 22.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(10.dp))
                Text("할 일 추가", fontSize = 17.sp, color = Color.White.copy(alpha = 0.8f))
            }
        } else {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val added = TaskStore.add(text)
                    if (added == null) {
                        onAdding(false)
                    } else {
                        text = ""
                        onAdded(added)
                    }
                }),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("적고, 엔터. 끝.", fontSize = 18.sp, color = Color.White.copy(alpha = 0.5f))
                        inner()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 16.dp)
                    .focusRequester(focus),
            )
        }
    }
}

/** 키보드가 한 번 올라왔다가 내려가면 [onClose]. 뒤로 가기로 키보드만 내린 경우를 알아차리려고 씁니다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloseWhenImeHides(onClose: () -> Unit) {
    val imeVisible = WindowInsets.isImeVisible
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) seen = true else if (seen) onClose()
    }
}

