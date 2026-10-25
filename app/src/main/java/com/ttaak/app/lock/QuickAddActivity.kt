package com.ttaak.app.lock

import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
                LockPage(adding, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_LOOK_FIRST = "lookFirst"
    }
}

private val Glass = Color.Black.copy(alpha = 0.32f)
private val GlassEdge = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))

@Composable
private fun LockPage(startAdding: Boolean, onClose: () -> Unit) {
    val all by TaskStore.tasks.collectAsStateWithLifecycle()
    val settings by TaskStore.settings.collectAsStateWithLifecycle()
    val today = Day.today()
    val tasks = remember(all, today) { TaskStore.tasksFor(today, today) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(startAdding) }
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
            // 입력 중엔 키보드 자리를 만들려고 시계를 접습니다.
            if (adding) Spacer(Modifier.height(24.dp)) else Clock(Modifier.padding(top = 40.dp, bottom = 28.dp))

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
                        "오늘 할 일이 없어요",
                        fontSize = settings.cardTextSp().sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                    )
                } else {
                    LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(tasks, key = { it.id }) { LockRow(it, today, settings.cardTextSp(), settings.checkOnLeft) }
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

/** 동그라미 + 큰 글씨 한 줄. 줄 어디를 눌러도 체크됩니다. */
@Composable
private fun LockRow(task: Task, day: Long, sp: Float, checkOnLeft: Boolean) {
    val done = task.isDone(day)
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    val circle = @Composable {
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
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                TaskStore.toggle(task.id, day)
            }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (checkOnLeft) circle()
        Text(
            task.title,
            color = Color.White.copy(alpha = if (done) 0.45f else 1f),
            fontSize = sp.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!checkOnLeft) circle()
    }
}

/** 접혀 있으면 '＋ 할 일 추가' 알약, 누르면 그 자리에서 입력칸이 됩니다. 적고 엔터면 추가, 빈 채로 엔터면 접힘. */
@Composable
private fun AddField(adding: Boolean, onAdding: (Boolean) -> Unit, onAdded: (Task) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // 잠금화면 위에서는 창이 포커스를 받은 뒤에야 키보드가 뜹니다.
    LaunchedEffect(adding, windowFocused) { if (adding && windowFocused) focus.requestFocus() }

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
