package com.yuangunn.nursetodo.lock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.data.Alarm
import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Pocket
import com.yuangunn.nursetodo.data.PocketKind
import com.yuangunn.nursetodo.data.PocketNode
import com.yuangunn.nursetodo.data.Settings
import com.yuangunn.nursetodo.data.Shortcut
import com.yuangunn.nursetodo.data.Task
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.doneCount
import com.yuangunn.nursetodo.data.due
import com.yuangunn.nursetodo.data.DutyHead
import com.yuangunn.nursetodo.data.dutyHead
import com.yuangunn.nursetodo.data.itemCount
import com.yuangunn.nursetodo.data.itemMinute
import com.yuangunn.nursetodo.data.itemText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.yuangunn.nursetodo.data.shortcutSlots
import com.yuangunn.nursetodo.ui.MainActivity
import com.yuangunn.nursetodo.ui.NurseTodoTheme
import com.yuangunn.nursetodo.ui.lockAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 잠금화면 위에 바로 뜨는 할 일 화면 (showWhenLocked). 배경화면 위 어두운 그라데이션에 위에서 아래로
 * 시계 · 키워드 알림 카드 · 알람 줄 · 페이지 줄(할 일 · 근무 · 포켓북, 알림, 설정) · 카드 · 할 일 추가 · 바로가기 · 잠금 해제 안내를 쌓고,
 * 카드만 남은 높이를 차지합니다. 잠금화면 카드, 상단바 타일, 위젯 `+`, '화면 켜면 할 일 먼저'(LockPageService)에서 열립니다.
 * 동그라미를 누르면 체크. 위로 밀면 잠금 해제를 요청합니다: 밀어서 잠금이면 바로 홈, PIN 이면 PIN 화면으로
 * (잠금화면을 두 번 넘기지 않게). 얼굴·지문으로 이미 풀렸으면 그냥 닫힙니다. 뒤로 가기는 원래 잠금화면으로 돌아갑니다.
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
        TaskStore.refreshPreset()
        // 화면을 켜거나 카드를 눌러 열면 보고 체크가 먼저라 입력칸을 접어 둡니다. 타일·위젯 `+` 로 열면 바로 입력.
        val adding = !intent.getBooleanExtra(EXTRA_LOOK_FIRST, false) && !intent.getBooleanExtra(EXTRA_DUTY, false)
        handleDuty(intent)
        val opened = SystemClock.uptimeMillis()
        setContent {
            val settings by TaskStore.settings.collectAsStateWithLifecycle()
            NurseTodoTheme(settings) {
                val accent = lockAccent(settings)
                CompositionLocalProvider(
                    LocalLockAccent provides accent,
                    LocalScreenStart provides opened,
                    LocalTextSelectionColors provides TextSelectionColors(handleColor = accent, backgroundColor = accent.copy(alpha = 0.4f)),
                ) {
                    LockPage(
                        adding,
                        dutyRequest = dutyRequest.intValue,
                        onUnlock = { unlockThen {} },
                        onOpen = ::openApp,
                        onOpenNoti = ::openNoti,
                        onOpenHit = ::openHit,
                        onAllowAlarm = { unlockThen { Setup.openExactAlarmSettings(this) } },
                    )
                }
            }
        }
    }

    /** 근무 루틴 알림으로 열면 근무 페이지로 넘기고, 꺼진 화면이면 잠깐 켭니다(꺼짐 시간이 지나면 다시 꺼짐). */
    private val dutyRequest = mutableIntStateOf(0)

    private fun handleDuty(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_DUTY, false)) return
        dutyRequest.intValue++
        if (intent.getBooleanExtra(EXTRA_WAKE, false) && Build.VERSION.SDK_INT >= 27) setTurnScreenOn(true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDuty(intent)
    }

    override fun onStop() {
        super.onStop()
        // 다음에 늦게 열릴 때 꺼진 화면을 다시 켜지 않게 되돌립니다.
        if (Build.VERSION.SDK_INT >= 27) setTurnScreenOn(false)
    }

    /** 잠금을 풀고 앱(의 [target] 화면)으로. 밀어서 잠금이면 바로, PIN 이 걸려 있으면 PIN 을 넣은 뒤 열립니다. */
    private fun openApp(target: Intent) = unlockThen { startActivity(target) }

    /** 다른 앱 알림을 누르면 잠금을 풀고 그 알림의 화면으로. */
    private fun openNoti(n: Noti) = unlockThen { NotiListener.open(this, n) }

    /** 키워드 카드의 '열기': 잠금을 풀고 원래 알림(문자·팀즈)의 화면으로. 연 것은 확인한 것으로 봅니다. */
    private fun openHit(h: KeywordAlert.Hit) = unlockThen {
        KeywordAlert.dismiss(this, h.key)
        NotiListener.open(this, Noti(h.key, h.pkg, h.title, h.text, 0, h.open, autoCancel = false))
    }

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
        private const val EXTRA_DUTY = "duty"
        private const val EXTRA_WAKE = "wake"

        /** 근무 페이지로 여는 인텐트. [wake] 면 꺼진 화면을 잠깐 켭니다(근무 루틴 알림). */
        fun dutyIntent(context: Context, wake: Boolean = true): Intent =
            Intent(context, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_DUTY, true)
                .putExtra(EXTRA_WAKE, wake)
    }
}

private const val PAGE_TODO = 0
private const val PAGE_DUTY = 1
private const val PAGE_POCKET = 2

/** 근무 항목은 시각이 지나고 이만큼 지나도록 안 하면 미체크(빨강). 그 전에는 '다음'처럼 진하게. ponytail: 임시값, 기관 투약 지침으로 확인할 것 */
internal const val GRACE_MS = 30 * 60_000L

private enum class Panel { NONE, NOTIS, ALARMS, HANDOFF }

@Composable
private fun LockPage(
    startAdding: Boolean,
    dutyRequest: Int,
    onUnlock: () -> Unit,
    onOpen: (Intent) -> Unit,
    onOpenNoti: (Noti) -> Unit,
    onOpenHit: (KeywordAlert.Hit) -> Unit,
    onAllowAlarm: () -> Unit,
) {
    val context = LocalContext.current
    val onOpenApp = { onOpen(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val all by TaskStore.tasks.collectAsStateWithLifecycle()
    val alarms by TaskStore.alarms.collectAsStateWithLifecycle()
    val settings by TaskStore.settings.collectAsStateWithLifecycle()
    val allNotis by NotiListener.notis.collectAsStateWithLifecycle()
    val notis = allNotis.filter { settings.notiApps.isEmpty() || it.pkg in settings.notiApps }
    val hitsVersion by KeywordAlert.version.collectAsStateWithLifecycle()
    val hits = remember(hitsVersion) { KeywordAlert.active(context) }
    val pockets by TaskStore.pockets.collectAsStateWithLifecycle()
    val today = Day.today()
    val preset = settings.presetMode
    // 방금 체크한 할 일은 되돌리기 토스트가 떠 있는 동안 취소선으로 남깁니다.
    var recent by remember { mutableStateOf(setOf<Long>()) }
    val todo = remember(all, today, recent, preset) {
        TaskStore.tasksFor(today, today)
            .filter { (!preset || it.shift == null) && (!it.isDone(today) || it.id in recent) }
            .sortedBy { it.order }
    }
    // 근무: 지금 불러온 교대의 항목 전부(끝낸 것도 체크 시각과 함께).
    val duty = remember(all, settings.presetApplied, preset) {
        if (!preset) emptyList() else dutyOrder(all.filter { it.shift != null && it.shift == settings.presetApplied })
    }
    val listState = rememberLazyListState()
    val dutyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(startAdding) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var askingAlarm by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var kwOpen by remember { mutableStateOf<String?>(null) }
    // 목록을 닫는 애니메이션과 그 뒤 300ms 는 아래 항목을 못 누르게 (이어서 누른 것이 체크되지 않게)
    var blocking by remember { mutableStateOf(false) }
    LaunchedEffect(blocking) {
        if (blocking) {
            delay(500)
            blocking = false
        }
    }
    val closePanel = {
        panel = Panel.NONE
        blocking = true
    }
    BackHandler(enabled = editingId != null) { editingId = null }
    BackHandler(enabled = askingAlarm) { askingAlarm = false }
    BackHandler(enabled = panel != Panel.NONE) { closePanel() }
    // 키보드를 쓰는 동안엔 키워드 카드·알람 줄·바로가기·안내를 접어 자리를 만듭니다.
    val typing = adding || editingId != null
    // 지워진 앱은 빼고 고른 모양의 칸 수까지
    val shortcuts = remember(settings.shortcuts, settings.shortcutStyle) {
        settings.shortcuts.filter { Shortcuts.defaultName(context, it) != null }.take(settings.shortcutSlots())
    }
    // 추가 버튼은 한 군데만: 바로가기에 '할 일 추가'가 있으면 아래 버튼은 숨기고, 적는 동안엔 카드 안 버튼도 숨깁니다.
    val addInShortcuts = shortcuts.any { it.id == Shortcut.ADD }
    val addBar = !addInShortcuts && settings.addButton == 0
    val addInCard = !addInShortcuts && settings.addButton == 1 && !typing
    val onShortcut: (Shortcut) -> Unit = { sc ->
        when (sc.id) {
            Shortcut.CALC -> onOpen(MainActivity.calcIntent(context))
            Shortcut.ALARM -> {
                panel = Panel.NONE
                askingAlarm = true
            }
            Shortcut.ADD -> adding = true
            Shortcut.CALENDAR -> onOpen(MainActivity.calendarIntent(context))
            else -> Shortcuts.launchIntent(sc)?.let(onOpen)
        }
    }
    var drag by remember { mutableFloatStateOf(0f) }
    // 화면 잠금이 '없음'이거나 얼굴·지문으로 이미 풀렸으면 풀 잠금이 없습니다.
    val locked = remember { context.getSystemService(KeyguardManager::class.java).isKeyguardLocked }
    // '할 일'(직접 적은 일), 교대 프리셋이면 '근무'(프리셋에서 온 일), 포켓북이 있으면 '포켓북'을 좌우로 넘겨 봅니다.
    // 설정에서 하나만 고를 수 있습니다.
    val available = buildList {
        add(PAGE_TODO)
        if (preset) add(PAGE_DUTY)
        if (pockets.isNotEmpty()) add(PAGE_POCKET)
    }
    val pages = when (settings.lockPages) {
        1 -> listOf(PAGE_TODO)
        2 -> if (preset) listOf(PAGE_DUTY) else available
        3 -> if (pockets.isNotEmpty()) listOf(PAGE_POCKET) else available
        else -> available
    }
    val pager = rememberPagerState(initialPage = pages.indexOf(PAGE_DUTY).coerceAtLeast(0)) { pages.size }
    LaunchedEffect(dutyRequest) {
        if (dutyRequest > 0) pages.indexOf(PAGE_DUTY).takeIf { it >= 0 }?.let { pager.scrollToPage(it) }
    }
    val page = pages.getOrElse(pager.currentPage) { PAGE_TODO }
    // 근무 줄의 미체크·다음을 맞추려고 분이 바뀔 때마다
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000 + 50)
        }
    }

    // 되돌리기 토스트. 새 토스트가 오면 앞의 동작은 확정. 화면 낭독기를 쓰면 읽을 시간만큼 더 둡니다.
    var toast by remember { mutableStateOf<Undo?>(null) }
    val a11y = LocalAccessibilityManager.current
    LaunchedEffect(toast) {
        val u = toast ?: return@LaunchedEffect
        delay(a11y?.calculateRecommendedTimeoutMillis(u.ms, containsIcons = false, containsText = true, containsControls = true) ?: u.ms)
        if (toast === u) {
            toast = null
            u.onEnd()
        }
    }
    val show: (Undo) -> Unit = { u ->
        toast?.onEnd?.invoke()
        toast = u
    }
    val toggleTodo: (Task) -> Unit = { t ->
        val wasDone = t.isDone(today)
        TaskStore.toggle(t.id, today)
        if (wasDone) {
            recent = recent - t.id
            if (toast?.tag == t.id) toast = null
        } else {
            recent = recent + t.id
            show(
                Undo(
                    "‘${t.title}’ 끝냄", PAGE_TODO, tag = t.id,
                    onUndo = {
                        TaskStore.toggle(t.id, today)
                        recent = recent - t.id
                    },
                    onEnd = { recent = recent - t.id },
                ),
            )
        }
    }
    // 교대 받기: 앞 근무에 못 끝낸 항목이 있으면 카드 자리에서 가져올지 고르고, 받으면 10초 되돌리기
    val head = remember(settings, now) { settings.dutyHead(LocalDateTime.now()) }
    var handoff by remember { mutableStateOf<DutyHead?>(null) }
    val pendingDuty = duty.filter { it.doneOn == null }
    val take: (DutyHead, Boolean) -> Unit = { h, carry ->
        val switching = h.shift != settings.presetApplied
        val n = if (switching) pendingDuty.size else 0
        val snap = TaskStore.snapshot()
        val (cleared, unmuted) = TaskStore.receive(h.target, h.shift, carry)
        panel = Panel.NONE
        handoff = null
        val verb = if (h.stale != null) "시작했어요" else "받았어요"
        show(
            Undo(
                when {
                    !switching -> "${h.target.name} 근무를 $verb"
                    n == 0 -> "${h.target.name} 할 일로 바뀌었어요"
                    carry -> "미체크 ${n}개를 가져왔어요"
                    else -> "미체크 ${n}개를 지웠어요"
                },
                PAGE_DUTY,
                sub = listOfNotNull(if (cleared > 0) "포켓북 체크 ${cleared}개 지움" else null, if (unmuted) "루틴 알림 켬" else null).joinToString(" · "),
                ms = 10_000,
                onUndo = { TaskStore.restore(snap) },
            ),
        )
        scope.launch { dutyListState.scrollToItem(0) }
    }
    val receive: (DutyHead) -> Unit = { h ->
        if (h.shift != settings.presetApplied && pendingDuty.isNotEmpty()) {
            handoff = h
            panel = Panel.HANDOFF
        } else {
            take(h, false)
        }
    }
    val offAlarm: (Alarm) -> Unit = { a ->
        Alarms.cancel(context, a.id)
        if (alarms.size <= 1 && panel == Panel.ALARMS) panel = Panel.NONE
        show(Undo(if (a.label.isNotEmpty()) "‘${a.label}’ 알람 끔" else "${hhmm(a.at)} 알람 끔", page, onUndo = { Alarms.restore(context, a) }))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Lk.Scrim)
            .pointerInput(Unit) {
                // 위로 밀면 잠금 해제
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag < -120.dp.toPx()) onUnlock()
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
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!askingAlarm) ClockRow()
            if (!typing && !askingAlarm && hits.isNotEmpty()) {
                KeywordCards(
                    hits, kwOpen,
                    onExpand = { kwOpen = it.key },
                    onOk = { h ->
                        KeywordAlert.dismiss(context, h.key)
                        show(Undo("키워드 알림을 확인했어요", page, onUndo = { KeywordAlert.restore(context, h) }))
                    },
                    onOpen = onOpenHit,
                    onTodo = { h ->
                        val t = TaskStore.add(KeywordAlert.todoText(h), top = true)
                        KeywordAlert.dismiss(context, h.key)
                        show(
                            Undo("할 일에 추가했어요", page, onUndo = {
                                t?.let { TaskStore.delete(it.id) }
                                KeywordAlert.restore(context, h)
                            }),
                        )
                    },
                )
            }
            if (!typing && !askingAlarm && alarms.isNotEmpty()) {
                AlarmRow(alarms, panel == Panel.ALARMS, onToggleList = { if (panel == Panel.ALARMS) closePanel() else panel = Panel.ALARMS }, onOff = offAlarm)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageTabs(
                    pages.map {
                        when (it) {
                            PAGE_DUTY -> "근무"
                            PAGE_POCKET -> "포켓북"
                            else -> "할 일"
                        }
                    },
                    pager.currentPage + pager.currentPageOffsetFraction,
                    pager.currentPage,
                ) { i -> scope.launch { pager.animateScrollToPage(i) } }
                Spacer(Modifier.weight(1f))
                if (page != PAGE_TODO && NotiListener.available(context)) {
                    BellButton(notis, panel == Panel.NOTIS) { if (panel == Panel.NOTIS) closePanel() else panel = Panel.NOTIS }
                }
                SettingsButton { onOpen(MainActivity.settingsIntent(context)) }
            }

            // 카드: 세 페이지의 목록, 그 자리를 채우는 알림·알람 목록, 아래 되돌리기 토스트
            val cardShape = RoundedCornerShape(24.dp)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .cover(cardShape)
                    .clip(cardShape),
            ) {
                val listAlpha by animateFloatAsState(if (panel != Panel.NONE) 0f else 1f, tween(200), label = "list")
                HorizontalPager(
                    pager,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = listAlpha },
                    userScrollEnabled = panel == Panel.NONE && !typing,
                    verticalAlignment = Alignment.Top,
                ) { i ->
                    when (pages[i]) {
                        PAGE_DUTY -> DutyPage(duty, head, settings, now, dutyListState, editingId, { editingId = it }, onOpenApp, receive)
                        PAGE_POCKET -> PocketPage(pockets) { p ->
                            val old = p.checks
                            TaskStore.clearPocketChecks(p.id)
                            show(Undo("체크 ${old.size}개 지움", PAGE_POCKET, onUndo = { TaskStore.setPocketChecks(p.id, old) }))
                        }
                        else -> TodoPage(
                            todo, today, recent, listState, editingId, { editingId = it }, onOpenApp, toggleTodo,
                            notis = notis,
                            rail = NotiListener.available(context),
                            onRail = { panel = Panel.NOTIS },
                            addInCard = addInCard,
                            onAdd = { adding = true },
                            dutyPage = PAGE_DUTY in pages,
                        )
                    }
                }
                val enter = expandVertically(tween(280, easing = Lk.Decel), expandFrom = Alignment.Top) + fadeIn(tween(280), initialAlpha = 0.4f)
                val exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(200), targetAlpha = 0.4f)
                Reveal(panel == Panel.NOTIS, enter = enter, exit = exit) {
                    NotiPanel(notis, onTap = { n -> closePanel(); onOpenNoti(n) }, onClose = closePanel)
                }
                Reveal(panel == Panel.ALARMS && alarms.isNotEmpty(), enter = enter, exit = exit) {
                    AlarmPanel(alarms, onOff = offAlarm, onClose = closePanel)
                }
                Reveal(panel == Panel.HANDOFF && handoff != null && page == PAGE_DUTY, enter = enter, exit = exit) {
                    handoff?.let { h ->
                        HandoffPanel(h, pendingDuty, onCarry = { take(h, true) }, onDrop = { take(h, false) }, onCancel = {
                            closePanel()
                            handoff = null
                        })
                    }
                }
                // 토스트는 그 페이지에서만 보입니다.
                val visible = toast?.takeIf { it.page == null || it.page == page }
                var last by remember { mutableStateOf<Undo?>(null) }
                if (visible != null) last = visible
                Reveal(
                    visible != null,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                    enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it / 3 } + scaleIn(initialScale = 0.96f) + fadeIn(),
                    exit = slideOutVertically(tween(160)) { it / 3 } + fadeOut(tween(160)),
                ) {
                    last?.let { u ->
                        ToastBar(u) {
                            toast = null
                            u.onUndo()
                        }
                    }
                }
                if (blocking) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            },
                    )
                }
            }

            if (askingAlarm) {
                AlarmSheet(
                    onStart = { at, label ->
                        Alarms.add(context, at, label)
                        askingAlarm = false
                    },
                    onCancel = { askingAlarm = false },
                    onAllow = onAllowAlarm,
                )
            } else {
                if ((addBar && editingId == null) || adding) {
                    AddBar(
                        adding = adding,
                        onAdding = { adding = it },
                        onAdd = { text ->
                            val t = TaskStore.add(text, top = true)
                            adding = false
                            if (t != null) {
                                scope.launch {
                                    pages.indexOf(PAGE_TODO).takeIf { it >= 0 && it != pager.currentPage }?.let { pager.animateScrollToPage(it) }
                                    listState.animateScrollToItem(0)
                                }
                                show(Undo("‘${t.title}’ 추가", PAGE_TODO, onUndo = { TaskStore.delete(t.id) }))
                            }
                        },
                    )
                }
                if (!typing) {
                    if (shortcuts.isNotEmpty()) ShortcutGrid(shortcuts, settings.shortcutStyle, onShortcut)
                    UnlockHint(locked, onUnlock)
                }
            }
        }
    }
}

/** 할 일 페이지: 동그라미 · 글자, 오른쪽에 알림 세로 줄(알림판). 아이콘은 카드 아래쪽에 모아 위쪽 할 일 줄에 붙어 읽히지 않게 합니다. */
@Composable
private fun TodoPage(
    list: List<Task>,
    today: Long,
    recent: Set<Long>,
    state: LazyListState,
    editingId: Long?,
    onEditing: (Long?) -> Unit,
    onOpenApp: () -> Unit,
    onToggle: (Task) -> Unit,
    notis: List<Noti>,
    rail: Boolean,
    onRail: () -> Unit,
    addInCard: Boolean,
    onAdd: () -> Unit,
    dutyPage: Boolean,
) {
    Row(Modifier.fillMaxSize()) {
        if (list.isEmpty()) {
            EmptyState(
                Icons.Default.CheckCircle,
                "할 일이 없어요",
                if (dutyPage) "아래 할 일 추가로 적거나, 근무로 넘겨 교대 할 일을 보세요." else "아래 할 일 추가로 적어요.",
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .fadeEdges(),
                state = state,
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            ) {
                items(list, key = { it.id }) { t ->
                    Box(Modifier.animateItem()) {
                        TodoRow(
                            t, on = t.isDone(today),
                            editing = t.id == editingId,
                            onEditing = { on -> onEditing(if (on) t.id else null) },
                            onOpenApp = onOpenApp,
                            onToggle = { onToggle(t) },
                        )
                    }
                }
            }
        }
        if (addInCard) {
            Box(Modifier.fillMaxHeight().padding(bottom = 8.dp), contentAlignment = Alignment.BottomEnd) { MemoButton(onAdd) }
        }
        // 알림이 없어도 44 자리를 비워 두어 글자 줄바꿈이 바뀌지 않게 (알림판만)
        if (rail) {
            Box(
                Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .padding(bottom = 52.dp),
                contentAlignment = Alignment.BottomCenter,
            ) { if (notis.isNotEmpty()) NotiRail(notis, onRail) }
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** 카드 안 '할 일 추가' 버튼(설정에서 '카드 안'): 강조색 동그라미. */
@Composable
private fun MemoButton(onClick: () -> Unit) {
    val accent = LocalLockAccent.current
    Press(onClick, Modifier.size(44.dp).semantics { contentDescription = "할 일 추가" }) {
        Box(Modifier.size(36.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Lk.OnAccent, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * 할 일 한 줄: 동그라미 · 글자(15/20, 어절 단위 줄바꿈, 말줄임 없음). 동그라미를 눌러야만 체크되고,
 * 글씨를 누르면 앱으로, 0.5초 길게 누르면 그 자리에서 이름을 고칩니다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoRow(task: Task, on: Boolean, editing: Boolean, onEditing: (Boolean) -> Unit, onOpenApp: () -> Unit, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CheckCircle(on, Lk.Body, (if (on) "체크 풀기: " else "끝냄: ") + task.title, onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle()
        }, enabled = !editing)
        if (editing) {
            RenameField(task, onDone = { onEditing(false) }, modifier = Modifier.weight(1f))
        } else {
            Text(
                task.title,
                style = Lk.Body15,
                color = if (on) Lk.Muted else Lk.Text,
                textDecoration = if (on) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        enabled = !on,
                        onClick = onOpenApp,
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEditing(true)
                        },
                        onLongClickLabel = "이름 고치기",
                    )
                    .padding(top = 10.dp, bottom = 10.dp),
            )
        }
    }
}

/** 길게 눌러 여는 이름 고치기 칸(강조 테두리 2, 모서리 12)과 오른쪽 저장. 키보드 완료도 저장, 비우거나 그대로면 저장하지 않고 닫힘. */
@Composable
private fun RenameField(task: Task, onDone: () -> Unit, modifier: Modifier) {
    val accent = LocalLockAccent.current
    var text by remember { mutableStateOf(TextFieldValue(task.title, TextRange(task.title.length))) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused) focus.requestFocus() }
    // 키보드를 내리면 고치기를 취소합니다.
    CloseWhenImeHides(onClose = onDone)
    val save = {
        val title = text.text.trim()
        if (title.isNotEmpty() && title != task.title) TaskStore.update(task.copy(title = title))
        // 포커스를 비워 둬야 남은 키 입력이 옆 줄 동그라미로 넘어가 체크되지 않습니다.
        focusManager.clearFocus()
        onDone()
    }
    Row(modifier.padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = Lk.Body15.copy(color = Lk.Text),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
                .background(Lk.Control, RoundedCornerShape(12.dp))
                .border(2.dp, accent, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .focusRequester(focus)
                .semantics { contentDescription = "${task.title} 이름 고치기" },
        )
        Spacer(Modifier.width(4.dp))
        SmallButton("저장", save, filled = true)
    }
}

/** 근무 줄 상태. 앱이 아는 것은 체크되지 않았다는 사실뿐이라 '놓침'이 아니라 '미체크'라고 씁니다. */
internal enum class Duty { MISSED, NEXT, REST, DONE }

/**
 * 시각순 근무 목록의 줄마다 상태: 끝냄, 앞 근무에서 가져왔거나 시각이 지나고 [GRACE_MS] 넘게 안 함 = 미체크,
 * 그 밖의 첫 안 한 시각 항목 = 다음(유예 안의 항목이 여럿이면 가장 빠른 것만), 나머지. 시각 없는 줄은 늘 나머지.
 */
internal fun dutyStates(list: List<Task>, now: Long): Map<Long, Duty> {
    var next: Long? = null
    return list.associate { t ->
        val due = t.due
        t.id to when {
            t.doneOn != null -> Duty.DONE
            t.carried != null -> Duty.MISSED
            due == null -> Duty.REST
            due + GRACE_MS < now -> Duty.MISSED
            next == null -> {
                next = t.id
                Duty.NEXT
            }
            else -> Duty.REST
        }
    }
}

/**
 * 근무 목록 순서: 앞 근무에서 가져온 것이 맨 위(가져올 때 정한 순서, 새 근무 시각표로 다시 재지 않음),
 * 그다음 시각순(자정을 넘는 Night 도), 시각 없는 항목은 맨 아래.
 */
internal fun dutyOrder(list: List<Task>): List<Task> =
    list.sortedWith(compareBy({ it.carried == null }, { if (it.carried != null) it.order else it.due ?: Long.MAX_VALUE }, { it.order }))

/** '{이름}로/으로'. 받침(한글)이나 n·m·ng 로 끝나는 영어 이름이면 '으로'. ㄹ 받침은 '로'. */
internal fun withRo(name: String): String {
    val c = name.trimEnd().lastOrNull() ?: return name
    val eu = when (c) {
        in '가'..'힣' -> ((c - '가') % 28).let { it != 0 && it != 8 }
        else -> c.lowercaseChar() in "mn" || name.endsWith("ng", ignoreCase = true)
    }
    return name + if (eu) "으로" else "로"
}

/**
 * 근무 페이지: 맨 위 상태(교대 받기 · 근무 중), 아래 시각순 목록. 줄은 동그라미 · 시각 칸 44 · 내용.
 * 시각이 지나고 30분 넘게 안 한 것은 미체크(빨강), 그 밖의 첫 안 한 것은 다음(진하게), 끝낸 것은 체크 시각과 함께 남깁니다.
 * 앞 근무에서 가져온 항목은 'Evening에서 넘어옴' 아래 맨 위에 둡니다. 하위 항목이 있는 줄은 끝낸 수/전체 칩으로 펼칩니다.
 */
@Composable
private fun DutyPage(
    list: List<Task>,
    head: DutyHead?,
    s: Settings,
    now: Long,
    state: LazyListState,
    editingId: Long?,
    onEditing: (Long?) -> Unit,
    onOpenApp: () -> Unit,
    onReceive: (DutyHead) -> Unit,
) {
    val today = Day.today()
    val states = dutyStates(list, now)
    val missed = states.count { it.value == Duty.MISSED }
    val nextAt = list.firstOrNull { states[it.id] == Duty.NEXT }?.due
    val pending = list.count { it.doneOn == null }
    var open by remember { mutableStateOf(setOf<Long>()) }
    val header = @Composable { DutyHeader(head, s, now, missed, nextAt, pending, onReceive) }
    if (list.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            header()
            EmptyState(Icons.Outlined.DateRange, "이번 근무 할 일이 없어요", "앱 › 설정 › 교대 프리셋에서 적어요.", Modifier.weight(1f).fillMaxWidth())
        }
        return
    }
    val lastCarried = list.lastOrNull { it.carried != null }?.id
    LazyColumn(Modifier.fillMaxSize().fadeEdges(), state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "head") { header() }
        items(list, key = { it.id }) { t ->
            Column(Modifier.animateItem()) {
                if (t.carried != null && t.id == list.first().id) {
                    Text("${t.carried}에서 넘어옴", style = Lk.Caption, color = Lk.Muted, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp))
                }
                DutyRow(
                    t, states[t.id] ?: Duty.REST, today,
                    editing = t.id == editingId,
                    onEditing = { on -> onEditing(if (on) t.id else null) },
                    onOpenApp = onOpenApp,
                    subsOpen = t.id in open,
                    onSubs = { open = if (t.id in open) open - t.id else open + t.id },
                )
                if (t.id == lastCarried) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp).fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Lk.Divider))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DutyRow(
    task: Task,
    st: Duty,
    today: Long,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    onOpenApp: () -> Unit,
    subsOpen: Boolean,
    onSubs: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val time = itemMinute(task.title)?.let { "%02d:%02d".format(it / 60, it % 60) } ?: ""
    val strong = st == Duty.MISSED || st == Duty.NEXT
    val on = st == Duty.DONE
    val doneAt = task.doneAtOn(task.doneOn ?: today)?.let(::hhmm)
    val text = itemText(task.title)
    val subs = task.subs
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            CheckCircle(
                on,
                if (st == Duty.MISSED) Lk.Missed else Lk.Body,
                (if (on) "체크 풀기: " else "끝냄: ") + listOf(time, text).filter { it.isNotEmpty() }.joinToString(" ") +
                    (if (subs.isNotEmpty()) ", 하위 항목 ${subs.size}개 모두" else "") +
                    when (st) {
                        Duty.MISSED -> ", 미체크"
                        Duty.NEXT -> ", 다음"
                        else -> ""
                    } + (doneAt?.let { ", $it 체크" } ?: ""),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    TaskStore.toggle(task.id, task.doneOn ?: today)
                },
                enabled = !editing,
            )
            Column(
                Modifier
                    .padding(start = 2.dp, top = 10.dp)
                    .width(44.dp),
            ) {
                Text(
                    time,
                    style = Lk.Body15.merge(TextStyle(fontFeatureSettings = "tnum")),
                    fontWeight = if (strong) Lk.Strong else FontWeight.Normal,
                    color = when (st) {
                        Duty.MISSED -> Lk.Missed
                        Duty.NEXT -> Lk.Text
                        else -> Lk.Muted
                    },
                )
                if (strong) {
                    Text(
                        if (st == Duty.MISSED) "미체크" else "다음",
                        style = Lk.Caption,
                        fontWeight = Lk.Strong,
                        color = if (st == Duty.MISSED) Lk.Missed else Lk.Text,
                    )
                }
            }
            if (editing) {
                RenameField(task, onDone = { onEditing(false) }, modifier = Modifier.weight(1f).padding(start = 10.dp))
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = onOpenApp,
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEditing(true)
                            },
                            onLongClickLabel = "이름 고치기",
                        )
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text,
                        style = Lk.Body15,
                        fontWeight = if (strong) Lk.Strong else FontWeight.Normal,
                        color = when (st) {
                            Duty.MISSED -> Lk.Missed
                            Duty.NEXT -> Lk.Text
                            Duty.DONE -> Lk.Muted
                            Duty.REST -> Lk.Body
                        },
                        textDecoration = if (on) TextDecoration.LineThrough else null,
                    )
                    if (doneAt != null) Text("$doneAt 체크", style = Lk.Caption.merge(TextStyle(fontFeatureSettings = "tnum")), color = Lk.Muted)
                }
            }
            if (subs.isNotEmpty() && !editing) {
                Box(Modifier.padding(start = 8.dp)) {
                    ChevronChip(
                        "${task.subsDone.size}/${subs.size}", subsOpen,
                        "$text 하위 항목 ${subs.size}개 중 ${task.subsDone.size}개 끝냄, ${if (subsOpen) "접기" else "펼치기"}",
                        onSubs,
                    )
                }
            }
        }
        if (subsOpen) {
            subs.forEachIndexed { i, sub ->
                val sOn = i in task.subsDone
                // 하위 줄은 근무 내용 칸에 맞춰(동그라미 104), 시각·체크 시각은 따로 두지 않습니다.
                Row(Modifier.fillMaxWidth().padding(start = 94.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
                    CheckCircle(sOn, Lk.Body, (if (sOn) "체크 풀기: " else "끝냄: ") + "$sub ($time $text)".replace("( ", "("), onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        TaskStore.toggleSub(task.id, i, task.doneOn ?: today)
                    })
                    Text(
                        sub,
                        style = Lk.Body15,
                        color = if (sOn) Lk.Muted else Lk.Body,
                        textDecoration = if (sOn) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f).padding(start = 2.dp, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * 근무 페이지 맨 위. 교대는 '근무 교대 받았습니다'를 눌러야 바뀝니다(그때 이 폰에서 루틴·키워드 알림이 켜짐).
 * 교대 1시간 전부터 다음 근무를 받을 수 있고, 시작 시각이 지나도 안 받으면 지난 시간을 진하게, 다음 근무도 끝나도록 안 받으면
 * 지금 시각에 맞는 근무를 시작할지 묻습니다. 받은 뒤엔 '근무 중 · 다음 시각'과 루틴 알림 끄기·켜기.
 */
@Composable
private fun DutyHeader(head: DutyHead?, s: Settings, now: Long, missed: Int, nextAt: Long?, pending: Int, onReceive: (DutyHead) -> Unit) {
    val bold = androidx.compose.ui.text.SpanStyle(color = Lk.Text, fontWeight = FontWeight.SemiBold)
    val divider = @Composable { Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Lk.Divider).heightIn(min = 1.dp, max = 1.dp)) }
    head ?: return
    val loaded = head.loaded?.name ?: ""
    val name = head.target.name
    if (!head.received) {
        val switching = head.loaded != null && head.shift != s.presetApplied
        Column {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        when {
                            head.stale != null -> {
                                append("아직 ")
                                withStyle(bold) { append(loaded) }
                                append(" 할 일 · ")
                                withStyle(bold) { append("${head.stale.first}도 ${hhmm(head.stale.second)}에 끝남") }
                            }
                            head.late -> withStyle(bold) { append("$name ${hhmm(head.shift)} 시작, ${fmtLeft(now - head.shift)} 지남") }
                            switching -> {
                                append("지금 ")
                                withStyle(bold) { append(loaded) }
                                append(" 근무 · $name ${hhmm(head.shift)} 시작")
                            }
                            else -> {
                                append("지금 ")
                                withStyle(bold) { append(name) }
                                append(" 근무")
                            }
                        }
                    },
                    style = Lk.Caption,
                    color = Lk.Muted,
                )
                if (head.stale != null) Text("지금 시각에 맞는 $name 근무를 시작할까요?", style = Lk.Body15, fontWeight = Lk.Strong, color = Lk.Text)
                PillButton(if (head.stale != null) "$name 근무 시작" else "$name 근무 교대 받았습니다", { onReceive(head) }, Modifier.fillMaxWidth(), filled = true)
                Column {
                    Text(
                        when {
                            !switching -> "받으면 이 폰에서 루틴 알림과 키워드 알림이 울려요."
                            head.stale != null -> "시작하면 $name 할 일로 바뀌어요."
                            else -> "받으면 $name 할 일로 바뀌어요."
                        },
                        style = Lk.Caption,
                        color = Lk.Muted,
                    )
                    if (switching && pending > 0) Text("미체크 ${pending}개는 가져올지 고를 수 있어요.", style = Lk.Caption, color = Lk.Muted)
                }
            }
            divider()
        }
        return
    }
    // 받은 근무 중: 상태 줄
    val routineOff = s.routineMuted
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(bold) { append(name) }
                        append(" 근무 중 · ")
                        if (routineOff && s.routineAlarm) withStyle(bold) { append("루틴 알림 꺼짐") } else append(nextAt?.let { "다음 ${hhmm(it)}" } ?: "남은 알림 없음")
                    },
                    style = Lk.Caption,
                    color = Lk.Muted,
                )
                if (missed > 0) Text("미체크 $missed", style = Lk.Caption, fontWeight = Lk.Strong, color = Lk.Missed)
            }
            if (s.routineAlarm) SmallButton(if (routineOff) "켜기" else "루틴 알림 끄기", { TaskStore.muteRoutine(!routineOff) })
        }
        divider()
    }
}

/** 교대 받을 때 앞 근무의 미체크 항목: 카드 자리에 항목, 아래에 가져오기 · 취소 · 지우고 받기(목록만 스크롤). */
@Composable
private fun HandoffPanel(head: DutyHead, items: List<Task>, onCarry: () -> Unit, onDrop: () -> Unit, onCancel: () -> Unit) {
    val name = head.target.name
    Column(Modifier.fillMaxSize().semantics { contentDescription = "미체크 항목 처리" }) {
        Column(
            Modifier
                .weight(1f)
                .fadeEdges()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text("미체크 ${items.size}개가 있어요", style = Lk.Body15, fontWeight = Lk.Strong, color = Lk.Text)
                Text("가져오면 $name 목록 맨 위에 남아요.", style = Lk.Caption, color = Lk.Muted, modifier = Modifier.padding(top = 4.dp))
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Lk.Divider))
            items.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        itemMinute(t.title)?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "",
                        style = Lk.Body15.merge(TextStyle(fontFeatureSettings = "tnum")),
                        fontWeight = Lk.Strong,
                        color = Lk.Missed,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        itemText(t.title) + if (t.subs.isNotEmpty()) " (${t.subsDone.size}/${t.subs.size})" else "",
                        style = Lk.Body15,
                        fontWeight = Lk.Strong,
                        color = Lk.Missed,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Lk.DividerSoft))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("${withRo(name)} 가져오기", onCarry, Modifier.fillMaxWidth(), filled = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("취소", onCancel, Modifier.weight(1f))
                PillButton(if (head.stale != null) "지우고 시작" else "지우고 받기", onDrop, Modifier.weight(1f))
            }
        }
    }
}

/** 포켓북 한 줄(펼친 모양 그대로 줄 세운 것). [left] 는 카드 안쪽에서 동그라미(항목)·글자(설명)가 시작하는 자리(dp). */
private class PocketLine(
    val key: String,
    val kind: PocketKind?, // null = 포켓북 이름 줄
    val text: String,
    val pocket: Pocket,
    val left: Int = 16,
    val path: String = "",
    val done: Boolean = false,
    val progress: String? = null,
    val open: Boolean = false,
    val first: Boolean = false,
    val clear: Boolean = false, // 펼친 포켓북 맨 아래 '체크 지우기'
)

/** 들여쓰기 단계 16 · 48 · 80 · 112: 이름 16, 묶음 48, 항목 동그라미 48(하위 80), 설명 80(바로 아래면 48). */
private fun pocketLines(pockets: List<Pocket>, open: Set<String>): List<PocketLine> = buildList {
    pockets.forEachIndexed { pi, p ->
        val doc = p.doc
        val total = doc.nodes.sumOf { it.itemCount() }
        val pk = "p${p.id}"
        add(PocketLine(pk, null, doc.title, p, progress = if (total > 0) "${doc.nodes.sumOf { it.doneCount(p.checks) }}/$total" else null, open = pk in open, first = pi == 0))
        if (pk !in open) return@forEachIndexed
        fun addNode(n: PocketNode, inSection: Boolean, nest: Int) {
            val key = "$pk/${n.path}"
            when (n.kind) {
                PocketKind.SECTION -> {
                    val count = n.itemCount()
                    add(PocketLine(key, n.kind, n.text, p, 48, n.path, progress = if (count > 0) "${n.doneCount(p.checks)}/$count" else null, open = key in open))
                    if (key in open) n.children.forEach { addNode(it, true, 0) }
                }
                PocketKind.ITEM -> {
                    add(PocketLine(key, n.kind, n.text, p, 48 + 32 * nest, n.path, done = n.path in p.checks))
                    n.children.forEach { addNode(it, inSection, nest + 1) }
                }
                PocketKind.NOTE -> add(PocketLine(key, n.kind, n.text, p, if (!inSection && nest == 0) 48 else 80 + 32 * (nest - 1).coerceAtLeast(0), n.path))
            }
        }
        doc.nodes.forEach { addNode(it, false, 0) }
        if (p.checks.isNotEmpty()) add(PocketLine("$pk/clear", null, "체크 지우기", p, clear = true))
    }
}

/**
 * 포켓북 페이지: 이름을 누르면 펼치고, 묶음(##)도 눌러 펼칩니다. 체크 항목은 동그라미, 설명은 흐린 글씨.
 * 읽을 거리가 많아 작은 글씨·좁은 줄로 한 화면에 많이 보이게 합니다.
 */
@Composable
private fun PocketPage(pockets: List<Pocket>, onClear: (Pocket) -> Unit) {
    var open by remember { mutableStateOf(setOf<String>()) }
    val lines = remember(pockets, open) { pocketLines(pockets, open) }
    val toggle = { key: String -> open = if (key in open) open - key else open + key }
    LazyColumn(Modifier.fillMaxSize().fadeEdges(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        items(lines, key = { it.key }) { line ->
            when {
                line.clear -> Box(Modifier.padding(start = 48.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                    SmallButton(line.text, { onClear(line.pocket) })
                }
                line.kind == null || line.kind == PocketKind.SECTION -> {
                    val book = line.kind == null
                    val turn by animateFloatAsState(if (line.open) 90f else 0f, tween(200, easing = Lk.Decel), label = "chev")
                    Column {
                        if (book && !line.first) {
                            Box(Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Lk.Divider))
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .clickable(role = Role.Button) { toggle(line.key) }
                                .semantics {
                                    contentDescription = line.text + (line.progress?.let { " $it" } ?: "")
                                    stateDescription = if (line.open) "펼침" else "접힘"
                                }
                                .padding(start = if (book) 16.dp else 48.dp, end = 16.dp, top = if (book) 4.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Lk.Text, modifier = Modifier.size(20.dp).rotate(turn))
                            Text(
                                line.text,
                                style = if (book) Lk.Body15 else Lk.Caption,
                                fontWeight = Lk.Strong,
                                color = Lk.Text,
                                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            )
                            line.progress?.let { Text(it, style = Lk.Caption.merge(TextStyle(fontFeatureSettings = "tnum")), color = Lk.Muted) }
                        }
                    }
                }
                line.kind == PocketKind.ITEM -> Row(Modifier.fillMaxWidth().padding(start = (line.left - 10).dp, end = 16.dp), verticalAlignment = Alignment.Top) {
                    CheckCircle(line.done, Lk.Body, (if (line.done) "체크 풀기: " else "끝냄: ") + line.text, onClick = {
                        TaskStore.togglePocketCheck(line.pocket.id, line.path)
                    })
                    Text(
                        line.text,
                        style = Lk.Body15,
                        color = if (line.done) Lk.Muted else Lk.Text,
                        modifier = Modifier.weight(1f).padding(start = 2.dp, top = 10.dp, bottom = 10.dp),
                    )
                }
                else -> Text(
                    line.text,
                    style = Lk.Caption,
                    color = Lk.Muted,
                    modifier = Modifier.padding(start = line.left.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                )
            }
        }
        if (open.none { it.matches(Regex("p\\d+")) }) {
            item(key = "hint") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Lk.Divider))
                    Text("이름을 누르면 펼쳐져요. 포켓북은 앱 › 설정 › 포켓북에서 붙여 넣어 만들어요.", style = Lk.Caption, color = Lk.Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
