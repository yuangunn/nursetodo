package com.yuangunn.nursetodo.lock

import android.app.KeyguardManager
import android.content.Context
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.Alarm
import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Fmt
import com.yuangunn.nursetodo.data.Pocket
import com.yuangunn.nursetodo.data.PocketKind
import com.yuangunn.nursetodo.data.PocketNode
import com.yuangunn.nursetodo.data.doneCount
import com.yuangunn.nursetodo.data.itemCount
import com.yuangunn.nursetodo.data.Settings
import com.yuangunn.nursetodo.data.Shortcut
import com.yuangunn.nursetodo.data.Task
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.cardTextSp
import com.yuangunn.nursetodo.data.due
import com.yuangunn.nursetodo.data.dutyTarget
import com.yuangunn.nursetodo.data.itemMinute
import com.yuangunn.nursetodo.data.itemText
import com.yuangunn.nursetodo.data.reminders
import com.yuangunn.nursetodo.data.shortcutSlots
import com.yuangunn.nursetodo.ui.MainActivity
import com.yuangunn.nursetodo.ui.NurseTodoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 잠금화면 위에 바로 뜨는 할 일 화면 (showWhenLocked). 툭의 잠금화면처럼 배경화면 위에 시계와 유리 카드 하나만 둡니다.
 * 잠금화면 카드, 상단바 타일, 위젯 `+`, 그리고 '화면 켜면 할 일 화면 먼저 보기'(LockPageService)에서 열립니다.
 * 동그라미를 누르면 체크. 위로 밀면 잠금 해제를 요청합니다: 밀어서 잠금이면 바로 홈, PIN 이면 PIN 화면으로
 * (잠금화면을 두 번 넘기지 않게). 얼굴·지문으로 이미 풀렸으면 그냥 닫힙니다. 뒤로 가기는 원래 잠금화면으로 돌아갑니다.
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
        TaskStore.refreshPreset()
        // 화면을 켜거나 카드를 눌러 열면 보고 체크가 먼저라 입력칸을 접어 둡니다. 타일·위젯 `+` 로 열면 바로 입력.
        val adding = !intent.getBooleanExtra(EXTRA_LOOK_FIRST, false) && !intent.getBooleanExtra(EXTRA_DUTY, false)
        handleDuty(intent)
        setContent {
            val settings by TaskStore.settings.collectAsStateWithLifecycle()
            NurseTodoTheme(settings) {
                LockPage(
                    adding,
                    dutyRequest = dutyRequest.intValue,
                    onUnlock = { unlockThen {} },
                    onOpen = ::openApp,
                    onOpenNoti = ::openNoti,
                    onAllowAlarm = { unlockThen { Setup.openExactAlarmSettings(this) } },
                )
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
private val Late = Color(0xFFFF8A80)

// 근무 페이지는 정보 전달이 먼저라 개수에 맞춰 키우지 않고 작은 글씨·좁은 줄로 (할 일 페이지는 크게 그대로)
private const val DUTY_SP = 15f
private const val DUTY_GAP = 2

internal val Glass = Color.Black.copy(alpha = 0.32f)
internal val GlassEdge = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))

/** 할 일 줄의 동그라미 누름 칸 높이. 줄 간격을 좁히면 같이 줄어 줄이 촘촘해집니다(최대 손가락 크기 48dp). */
internal fun circleBoxHeight(rowGap: Int) = (26 + 2 * rowGap).coerceIn(32, 48).dp

@Composable
private fun LockPage(
    startAdding: Boolean,
    dutyRequest: Int,
    onUnlock: () -> Unit,
    onOpen: (Intent) -> Unit,
    onOpenNoti: (Noti) -> Unit,
    onAllowAlarm: () -> Unit,
) {
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
    // 교대 프리셋이면 할 일이 시계와 바로가기 사이를 다 채우고, 다른 앱 알림은 오른쪽 아이콘 줄로 갑니다.
    val preset = settings.presetMode
    var peek by remember { mutableStateOf<String?>(null) }
    val peekNotis = notis.filter { it.pkg == peek }
    BackHandler(enabled = peekNotis.isNotEmpty()) { peek = null }
    // 적는 동안 키보드에 밀려 글자가 줄었다 커지지 않게, 마지막으로 다 보일 때의 높이로 맞춥니다.
    var fitHeight by remember { mutableFloatStateOf(0f) }
    // 화면 잠금이 '없음'이거나 얼굴·지문으로 이미 풀렸으면 풀 잠금이 없습니다.
    val locked = remember { context.getSystemService(KeyguardManager::class.java).isKeyguardLocked }
    // '할 일'(직접 적은 일), 교대 프리셋이면 '근무'(프리셋에서 온 일), 포켓북이 있으면 '포켓북'을 좌우로 넘겨 봅니다.
    // 설정에서 하나만 고를 수 있습니다.
    val pockets by TaskStore.pockets.collectAsStateWithLifecycle()
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
    val paged = pages != listOf(PAGE_TODO)
    val pager = rememberPagerState(initialPage = pages.indexOf(PAGE_DUTY).coerceAtLeast(0)) { pages.size }
    LaunchedEffect(dutyRequest) {
        if (dutyRequest > 0) pages.indexOf(PAGE_DUTY).takeIf { it >= 0 }?.let { pager.scrollToPage(it) }
    }
    val todo = if (preset) tasks.filter { it.shift == null } else tasks
    // 자정을 넘는 근무(Night 23:00~)도 시각 순서로. 시각 없는 항목은 적은 순서대로 맨 아래.
    val duty = tasks.filter { it.shift != null }.sortedBy { it.due ?: Long.MAX_VALUE }
    val dutyListState = rememberLazyListState()
    // 지난 근무 항목을 빨갛게, 근무 줄의 '다음' 시각을 맞추려고 분이 바뀔 때마다
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000 + 50)
        }
    }

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

                // 할 일 카드 하나. 근무 페이지([dutyPage])는 시각 지난 항목을 빨갛게, 메모 버튼은 할 일 페이지에만.
                val card: @Composable (List<Task>, LazyListState, Boolean, Modifier) -> Unit = { list, state, dutyPage, cardModifier ->
                        Surface(
                            color = Glass,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(28.dp),
                            border = GlassEdge,
                            modifier = cardModifier.then(if (preset) Modifier.fillMaxHeight() else Modifier),
                        ) {
                            BoxWithConstraints {
                                if (preset && !typing) SideEffect { fitHeight = maxHeight.value }
                                val (sp, gap) = when {
                                    dutyPage -> DUTY_SP to DUTY_GAP
                                    preset -> {
                                        val (textDp, fitGap) = autoFit(if (typing && fitHeight > 0f) fitHeight else maxHeight.value, list.size)
                                        with(LocalDensity.current) { textDp.dp.toSp().value } to fitGap
                                    }
                                    else -> settings.cardTextSp() to settings.rowGap
                                }
                                // 근무 페이지에서 다음에 할 시각 (진하게)
                                val nextId = if (dutyPage) list.firstOrNull { (it.due ?: Long.MIN_VALUE) >= now }?.id else null
                                if (list.isEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            when {
                                                anyDone -> "다 했어요!"
                                                dutyPage -> "이번 근무 할 일이 없어요"
                                                else -> "오늘 할 일이 없어요"
                                            },
                                            fontSize = sp.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 24.dp, vertical = 22.dp),
                                        )
                                        if (addInCard && !dutyPage) Box(Modifier.padding(end = 9.dp)) { memo() }
                                    }
                                } else {
                                    LazyColumn(state = state, contentPadding = PaddingValues(vertical = 8.dp)) {
                                        items(list, key = { it.id }) {
                                            Box(Modifier.animateItem()) {
                                                LockRow(
                                                    it, today, sp, gap, settings.checkOnLeft,
                                                    editing = it.id == editingId,
                                                    onEditing = { on -> editingId = if (on) it.id else null },
                                                    onOpenApp = onOpenApp,
                                                    late = dutyPage && (it.due ?: Long.MAX_VALUE) < now,
                                                    timeColumn = dutyPage,
                                                    next = it.id == nextId,
                                                    // 메모 버튼은 맨 아래 할 일의 오른쪽 끝에
                                                    trailing = memo.takeIf { _ -> addInCard && !dutyPage && it.id == list.last().id },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = preset || paged),
                ) {
                    Row(if (preset || paged) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                        if (paged) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            ) {
                                if (pages.size > 1) {
                                    PageTabs(pages, pager.currentPage) { i -> scope.launch { pager.animateScrollToPage(i) } }
                                }
                                HorizontalPager(
                                    pager,
                                    Modifier.weight(1f),
                                    pageSpacing = 16.dp,
                                    verticalAlignment = Alignment.Top,
                                ) { i ->
                                    when (pages[i]) {
                                        PAGE_DUTY -> Column(Modifier.fillMaxSize()) {
                                            DutyHeader(settings, now)
                                            card(duty, dutyListState, true, Modifier.weight(1f))
                                        }
                                        PAGE_POCKET -> PocketCard(pockets, Modifier.fillMaxSize())
                                        else -> card(todo, listState, false, if (preset) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        } else {
                            card(todo, listState, false, Modifier.weight(1f))
                        }
                        if (preset && notis.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            NotiRail(notis) { peek = it }
                        }
                    }
                    // 아이콘을 누르면 그 앱 알림을 위에 펼쳐 보여 줍니다. 바깥을 누르면 닫힘.
                    if (peekNotis.isNotEmpty()) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { peek = null },
                        )
                        // 뒤의 할 일 글씨가 비치지 않게 진한 바탕 위에
                        Surface(
                            color = Color.Black.copy(alpha = 0.92f),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(28.dp),
                            border = GlassEdge,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Box(Modifier.padding(8.dp)) { NotiList(peekNotis, onOpenNoti) } }
                    }
                }

                if (askingAlarm || addBar || adding) Spacer(Modifier.height(12.dp))
                if (askingAlarm) {
                    AlarmAsk(
                        onStart = { at, label ->
                            Alarms.add(context, at, label)
                            askingAlarm = false
                        },
                        onCancel = { askingAlarm = false },
                        onAllow = onAllowAlarm,
                    )
                } else if (addBar || adding) {
                    AddField(
                        adding = adding,
                        onAdding = { adding = it },
                        onAdded = { added ->
                            val index = TaskStore.tasksFor(today, today).filter { !preset || it.shift == null }.indexOfFirst { it.id == added.id }
                            scope.launch {
                                pages.indexOf(PAGE_TODO).takeIf { it >= 0 && it != pager.currentPage }?.let { pager.animateScrollToPage(it) }
                                if (index >= 0) listState.animateScrollToItem(index)
                            }
                        },
                    )
                }
                if (!preset && !typing && notis.isNotEmpty()) {
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
                    if (locked) "위로 밀어서 잠금 해제" else "위로 밀어서 닫기",
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

/**
 * 교대 프리셋 화면: 할 일 [count] 개가 [height](dp)를 고르게 채우도록 글자 크기(dp)와 줄 위아래 여백(dp).
 * 적으면 크고 넉넉하게, 많으면 작고 촘촘하게. 아주 많으면 가장 작은 크기로 두고 넘치는 줄은 밀어서 봅니다.
 */
internal fun autoFit(height: Float, count: Int): Pair<Float, Int> {
    // 목록 위아래 8dp, 줄마다 위아래 1dp
    val row = (height - 16f) / count.coerceAtLeast(1) - 2f
    val text = (row * 0.36f).coerceIn(15f, 26f)
    // 줄 높이 = 위아래 여백 + (글자 한 줄과 동그라미 26dp 중 큰 것)
    val gap = ((row - maxOf(26f, text * 1.4f)) / 2f).toInt().coerceIn(2, 32)
    return text to gap
}

/** 교대 프리셋 화면 오른쪽 줄: 알림이 온 앱 아이콘(최근 것부터, 2개 이상이면 숫자). 누르면 그 앱 알림을 펼쳐 봅니다. */
@Composable
private fun NotiRail(items: List<Noti>, onTap: (String) -> Unit) {
    val context = LocalContext.current
    Surface(color = Glass, contentColor = Color.White, shape = RoundedCornerShape(28.dp), border = GlassEdge) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 2.dp),
        ) {
            items.groupBy { it.pkg }.forEach { (pkg, list) ->
                val icon = remember(pkg) { NotiListener.icon(context, pkg) }
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .clickable { onTap(pkg) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) Image(icon, contentDescription = "${list.first().title} 알림", modifier = Modifier.size(34.dp))
                    if (list.size > 1) {
                        Text(
                            "${list.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .widthIn(min = 18.dp)
                                .padding(horizontal = 4.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
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

/** 페이지 이름 줄: 지금 페이지만 밝게. 눌러도 넘어갑니다. */
@Composable
private fun PageTabs(pages: List<Int>, current: Int, onTap: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        pages.forEachIndexed { i, page ->
            Text(
                when (page) {
                    PAGE_DUTY -> "근무"
                    PAGE_POCKET -> "포켓북"
                    else -> "할 일"
                },
                fontSize = 15.sp,
                fontWeight = if (i == current) FontWeight.Bold else FontWeight.Medium,
                color = Color.White.copy(alpha = if (i == current) 1f else 0.5f),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onTap(i) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 근무 페이지 맨 위: '근무 교대 받았습니다'를 누르면 이 폰에서 그 교대의 근무 루틴 알림이 켜집니다. 인계는 교대 전에 받으므로
 * 다음 교대 1시간 전부터는 다음 교대를 받습니다. 시각 붙은 항목이 없거나 설정에서 알림을 끄면 줄이 없습니다.
 */
@Composable
private fun DutyHeader(s: Settings, now: Long) {
    if (!s.routineAlarm) return
    val (p, shift) = remember(s.presets, now / 60_000) { s.presets.dutyTarget(LocalDateTime.now()) } ?: return
    if (p.items.none { itemMinute(it) != null && it !in p.quiet }) return
    val armed = s.dutyShift == shift
    Surface(
        color = Glass,
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        border = GlassEdge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    !armed -> " 근무"
                    shift > now -> " 근무 받음 · ${hhmm(shift)}부터"
                    else -> s.reminders(now).firstOrNull()?.let { " 근무 중 · 다음 ${hhmm(it.at)}" } ?: " 근무 중"
                },
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (armed) {
                Text(
                    "알림 끄기",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { TaskStore.setDuty(0) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            } else {
                Surface(
                    onClick = { TaskStore.setDuty(shift) },
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "근무 교대 받았습니다",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** 포켓북 한 줄(펼친 모양 그대로 줄 세운 것). */
private class PocketLine(
    val key: String,
    val depth: Int,
    val kind: PocketKind?, // null = 포켓북 이름 줄
    val text: String,
    val pocket: Long,
    val path: String = "",
    val done: Boolean = false,
    val progress: String? = null,
    val open: Boolean = false,
    val clear: Boolean = false, // 펼친 포켓북 맨 아래 '체크 지우기'
)

private fun pocketLines(pockets: List<Pocket>, open: Set<String>): List<PocketLine> = buildList {
    pockets.forEach { p ->
        val doc = p.doc
        val total = doc.nodes.sumOf { it.itemCount() }
        val pk = "p${p.id}"
        add(
            PocketLine(
                pk, 0, null, doc.title, p.id,
                progress = if (total > 0) "${doc.nodes.sumOf { it.doneCount(p.checks) }}/$total" else null,
                open = pk in open,
            ),
        )
        if (pk !in open) return@forEach
        fun addNode(n: PocketNode, depth: Int) {
            val key = "$pk/${n.path}"
            when (n.kind) {
                PocketKind.SECTION -> {
                    val total = n.itemCount()
                    add(
                        PocketLine(
                            key, depth, n.kind, n.text, p.id, n.path,
                            progress = if (total > 0) "${n.doneCount(p.checks)}/$total" else null,
                            open = key in open,
                        ),
                    )
                    if (key in open) n.children.forEach { addNode(it, depth + 1) }
                }
                else -> {
                    add(PocketLine(key, depth, n.kind, n.text, p.id, n.path, done = n.path in p.checks))
                    n.children.forEach { addNode(it, depth + 1) }
                }
            }
        }
        doc.nodes.forEach { addNode(it, 1) }
        if (p.checks.isNotEmpty()) add(PocketLine("$pk/clear", 1, null, "체크 지우기", p.id, clear = true))
    }
}

/**
 * 포켓북 페이지: 이름을 누르면 펼치고, 묶음(##)도 눌러 펼칩니다. 체크 항목은 동그라미, 설명은 흐린 글씨.
 * 읽을 거리가 많아 작은 글씨·좁은 줄로 한 화면에 많이 보이게 합니다.
 */
@Composable
private fun PocketCard(pockets: List<Pocket>, modifier: Modifier) {
    var open by remember { mutableStateOf(setOf<String>()) }
    val lines = remember(pockets, open) { pocketLines(pockets, open) }
    val toggle = { key: String -> open = if (key in open) open - key else open + key }
    Surface(
        color = Glass,
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        border = GlassEdge,
        modifier = modifier,
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 10.dp)) {
            items(lines, key = { it.key }) { line ->
                val indent = (14 + line.depth * 16).dp
                if (line.clear) {
                    Box(Modifier.fillMaxWidth().padding(end = 14.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            line.text,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { TaskStore.clearPocketChecks(line.pocket) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    return@items
                }
                when (line.kind) {
                    null, PocketKind.SECTION -> Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { toggle(line.key) }
                            .padding(start = indent - 4.dp, end = 18.dp, top = if (line.kind == null) 10.dp else 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = if (line.open) "접기" else "펼치기",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (line.open) 0f else -90f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            line.text,
                            fontSize = if (line.kind == null) 16.sp else 15.sp,
                            fontWeight = if (line.kind == null) FontWeight.Bold else FontWeight.SemiBold,
                            color = Color.White.copy(alpha = if (line.kind == null) 1f else 0.9f),
                            lineHeight = 1.25.em,
                            modifier = Modifier.weight(1f),
                        )
                        line.progress?.let {
                            Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), style = TextStyle(fontFeatureSettings = "tnum"))
                        }
                    }
                    PocketKind.ITEM -> Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { TaskStore.togglePocketCheck(line.pocket, line.path) }
                            .padding(start = indent, end = 18.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier
                                .padding(top = 1.dp)
                                .size(18.dp)
                                .then(
                                    if (line.done) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (line.done) Icon(Icons.Default.Check, contentDescription = "완료", tint = Color.White, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            line.text,
                            fontSize = 15.sp,
                            lineHeight = 1.3.em,
                            color = Color.White.copy(alpha = if (line.done) 0.45f else 1f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PocketKind.NOTE -> Text(
                        line.text,
                        fontSize = 14.sp,
                        lineHeight = 1.3.em,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = indent, end = 18.dp, top = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

private fun hhmm(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

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

/**
 * 직접 맞추는 알람: '몇 분 뒤'(타이머) 또는 '몇 시에'(1430 → 14:30, 지난 시각이면 내일). 숫자만 받습니다.
 * 이름은 '+ 이름 붙이기'를 눌렀을 때만 적고, 안 적으면 '30분 알람'·'14:30 알람'.
 */
@Composable
private fun AlarmAsk(onStart: (Long, String) -> Unit, onCancel: () -> Unit, onAllow: () -> Unit) {
    val context = LocalContext.current
    val exactAlarm = remember { Setup.exactAlarmAllowed(context) }
    var atClock by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var withLabel by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val labelFocus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused) focus.requestFocus() }
    LaunchedEffect(withLabel) { if (withLabel) labelFocus.requestFocus() }
    val target = if (atClock) clockTarget(minutes) else minutes.toIntOrNull()?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 60_000L }
    val ready = target != null
    val start: () -> Unit = { if (target != null) onStart(target, label.ifBlank { if (atClock) "${hhmm(target)} 알람" else "" }) }
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        color = Glass,
        contentColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        border = GlassEdge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("몇 분 뒤", "몇 시에").forEachIndexed { i, name ->
                    val on = atClock == (i == 1)
                    Text(
                        name,
                        fontSize = 16.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White.copy(alpha = if (on) 1f else 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable {
                                if (atClock != (i == 1)) minutes = ""
                                atClock = i == 1
                                focus.requestFocus()
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            // 항암 끝 같은 시각을 맞추는 곳이라 늦을 수 있으면 여기서 바로 알립니다. 허용하면 맞춰 둔 알람도 제시간으로 다시 맞춰집니다.
            if (!exactAlarm) {
                Text(
                    "'알람 및 리마인더'가 꺼져 있어 늦게 울릴 수 있어요. 눌러서 허용",
                    fontSize = 15.sp,
                    color = Color(0xFFFF9F0A),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAllow)
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(if (atClock) 4 else 3) },
                    visualTransformation = if (atClock) ClockDigits else VisualTransformation.None,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(accent),
                    // 숫자 자판만 뜨게 (NumberPassword 자판에는 점·쉼표·부호가 없음. 글자는 가리지 않음)
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { start() }),
                    decorationBox = { inner ->
                        Box {
                            if (minutes.isEmpty()) Text(if (atClock) "00:00" else "0", fontSize = 44.sp, color = Color.White.copy(alpha = 0.3f))
                            inner()
                        }
                    },
                    // 숫자 길이만큼만 차지해서 '분'이 바로 옆에 붙습니다.
                    modifier = Modifier
                        .width(IntrinsicSize.Min)
                        .widthIn(min = 28.dp)
                        .focusRequester(focus),
                )
                Text(
                    when {
                        !atClock -> "분"
                        target == null -> ""
                        Day.of(target) == Day.today() -> "오늘"
                        else -> "내일"
                    },
                    fontSize = 22.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
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
 * [timeColumn] 이면(근무 페이지) 맨 앞 시각을 왼쪽 한 칸에 따로: 지났는데 안 했으면 빨강([late]), 다음 것은 진하게([next]).
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
    late: Boolean = false,
    timeColumn: Boolean = false,
    next: Boolean = false,
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
        // 손가락 폭만큼(48dp, 근무 페이지는 40dp) 눌리고, 보이는 동그라미는 26dp(근무 페이지 22dp)
        Box(
            Modifier
                .size(if (timeColumn) 40.dp else 48.dp, circleBoxHeight(gap))
                .clip(CircleShape)
                .clickable(enabled = !done && !editing) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    done = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(if (timeColumn) 22.dp else 26.dp)
                    .then(
                        if (done) Modifier.background(accent, CircleShape)
                        else Modifier.border(2.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Default.Check, contentDescription = "완료", tint = Color.White, modifier = Modifier.size(if (timeColumn) 15.dp else 18.dp))
            }
        }
    }
    // 여러 줄이어도 시각·동그라미가 첫 줄에 맞게 근무 페이지는 위로 맞춥니다.
    val textPad = if (timeColumn) (gap + 6).dp else gap.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 9.dp, vertical = 1.dp),
        verticalAlignment = if (timeColumn) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (checkOnLeft) circle()
        if (timeColumn && !editing) {
            Text(
                itemMinute(task.title)?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "",
                color = when {
                    late -> Late
                    next -> Color.White
                    else -> Color.White.copy(alpha = 0.55f)
                }.copy(alpha = if (done) 0.45f else if (late || next) 1f else 0.55f),
                fontSize = sp.sp,
                lineHeight = 1.25.em,
                fontWeight = if (late || next) FontWeight.Bold else FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier
                    .padding(start = 2.dp, top = textPad)
                    .width((sp * 2.9f).dp),
            )
        }
        val textModifier = Modifier
            .weight(1f)
            .padding(horizontal = if (timeColumn) 0.dp else 6.dp)
        if (editing) {
            RenameField(task, sp, gap, onDone = { onEditing(false) }, modifier = textModifier)
        } else {
            Text(
                if (timeColumn) itemText(task.title) else task.title,
                color = (if (late) Late else Color.White).copy(alpha = if (done) 0.45f else 1f),
                fontSize = sp.sp,
                // 큰 글씨가 두 줄로 넘어가도 줄끼리 겹치지 않게 (기본 줄 높이는 글자 크기와 상관없이 24sp)
                lineHeight = 1.25.em,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = if (timeColumn) 3 else 2,
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
                    .padding(horizontal = 6.dp, vertical = textPad),
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

