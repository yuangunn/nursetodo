package com.ttaak.app.lock

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import com.ttaak.app.data.cardTextSp
import com.ttaak.app.ui.MainActivity
import com.ttaak.app.ui.TtaakTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 잠금화면 위에 바로 뜨는 딱 화면 (showWhenLocked). 툭의 잠금화면처럼 배경화면 위에 시계와 유리 카드 하나만 둡니다.
 * 잠금화면 카드, 상단바 타일, 위젯 `+`, 그리고 '화면 켜면 딱 화면 먼저 보기'(LockPageService)에서 열립니다.
 * 줄을 누르면 체크, 위로 밀거나 뒤로 가기를 누르면 원래 잠금화면으로 돌아갑니다.
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
            // 8.0 은 매니페스트의 showWhenLocked/turnScreenOn 을 무시하므로 창 플래그로 대신합니다.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        TaskStore.init(this)
        // 화면을 켜거나 카드를 눌러 열면 보고 체크가 먼저라 입력칸을 접어 둡니다. 타일·위젯 `+` 로 열면 바로 입력.
        val adding = !intent.getBooleanExtra(EXTRA_LOOK_FIRST, false)
        setContent {
            val settings by TaskStore.settings.collectAsStateWithLifecycle()
            TtaakTheme(settings) {
                LockPage(adding, onClose = { finish() }, onOpenApp = ::openApp)
            }
        }
    }

    /** 잠금을 풀고 앱으로. 밀어서 잠금이면 바로, PIN 이 걸려 있으면 PIN 을 넣은 뒤 열립니다. */
    private fun openApp() {
        val go = {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

private val Glass = Color.Black.copy(alpha = 0.32f)
private val GlassEdge = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))

@Composable
private fun LockPage(startAdding: Boolean, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val all by TaskStore.tasks.collectAsStateWithLifecycle()
    val settings by TaskStore.settings.collectAsStateWithLifecycle()
    val today = Day.today()
    // 끝낸 일은 사라지고 앱의 '완료된 할 일'에서만 보입니다.
    val tasks = remember(all, today) { TaskStore.tasksFor(today, today).filterNot { it.isDone(today) } }
    val anyDone = remember(all, today) { TaskStore.tasksFor(today, today).any { it.isDone(today) } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(startAdding) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    BackHandler(enabled = editingId != null) { editingId = null }
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
                .padding(start = 20.dp, end = 20.dp, bottom = if (adding) 12.dp else 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 입력·수정 중엔 키보드 자리를 만들려고 시계를 접습니다.
            if (adding || editingId != null) Spacer(Modifier.height(24.dp)) else Clock(Modifier.padding(top = 40.dp, bottom = 28.dp))

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
                    Text(
                        if (anyDone) "다 했어요!" else "오늘 할 일이 없어요",
                        fontSize = settings.cardTextSp().sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    )
                } else {
                    LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(tasks, key = { it.id }) {
                            Box(Modifier.animateItem()) {
                                LockRow(
                                    it, today, settings.cardTextSp(), settings.checkOnLeft,
                                    editing = it.id == editingId,
                                    onEditing = { on -> editingId = if (on) it.id else null },
                                    onOpenApp = onOpenApp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            AddField(
                adding = adding,
                onAdding = { adding = it },
                onAdded = { added ->
                    val index = TaskStore.tasksFor(today, today).indexOfFirst { it.id == added.id }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                },
            )
        }
        if (!adding) {
            Text(
                "위로 밀어서 닫기",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }
    }
}

/** 잠금화면을 가리는 대신 시계는 그대로 보여 줍니다. 분이 바뀔 때마다 다시 그립니다. */
@Composable
private fun Clock(modifier: Modifier) {
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - value.second * 1_000L - value.nano / 1_000_000L)
        }
    }
    val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Fmt.date(now.toLocalDate().toEpochDay()), color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            now.format(DateTimeFormatter.ofPattern(pattern)),
            color = Color.White,
            fontSize = 84.sp,
            lineHeight = 90.sp,
            fontWeight = FontWeight.Light,
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
    checkOnLeft: Boolean,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    onOpenApp: () -> Unit,
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
        // 손가락 크기만큼(48dp) 눌리고, 보이는 동그라미는 26dp
        Box(
            Modifier
                .size(48.dp)
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
            RenameField(task, sp, onDone = { onEditing(false) }, modifier = textModifier)
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
                    .padding(horizontal = 6.dp, vertical = 12.dp),
            )
        }
        if (!checkOnLeft) circle()
    }
}

/** 길게 눌러 여는 이름 고치기 칸. 엔터면 저장, 비우거나 그대로면 저장하지 않고 닫힘. */
@Composable
private fun RenameField(task: Task, sp: Float, onDone: () -> Unit, modifier: Modifier) {
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
            .padding(horizontal = 6.dp, vertical = 12.dp)
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

