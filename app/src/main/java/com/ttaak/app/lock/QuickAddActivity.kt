package com.ttaak.app.lock

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.data.Day
import com.ttaak.app.data.TaskStore
import com.ttaak.app.data.cardTextSp
import com.ttaak.app.ui.LocalTaskText
import com.ttaak.app.ui.TaskRow
import com.ttaak.app.ui.TtaakTheme
import kotlinx.coroutines.launch

/**
 * 잠금화면 위에 바로 뜨는 딱 화면 (showWhenLocked). 잠금화면 카드·타일·위젯 `+`, 그리고
 * '화면 켜면 딱 화면 먼저 보기'(LockPageService)에서 열립니다. 오늘 할 일을 보고 체크하고, 적고 엔터 치면 추가.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 27) {
            // 8.0 은 매니페스트의 showWhenLocked/turnScreenOn 을 무시하므로 창 플래그로 대신합니다.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        TaskStore.init(this)
        // 화면 켤 때마다 뜨는 경우엔 키보드를 자동으로 올리지 않습니다 (보고 체크가 먼저).
        val autoFocus = !intent.getBooleanExtra(EXTRA_FROM_SCREEN_ON, false)
        setContent {
            val settings by TaskStore.settings.collectAsStateWithLifecycle()
            TtaakTheme(settings) {
                QuickAdd(autoFocus, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_FROM_SCREEN_ON = "fromScreenOn"
    }
}

@Composable
private fun QuickAdd(autoFocus: Boolean, onClose: () -> Unit) {
    val all by TaskStore.tasks.collectAsStateWithLifecycle()
    val today = Day.today()
    val tasks = remember(all, today) { TaskStore.tasksFor(today, today) }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    // 잠금화면 위에서는 창이 포커스를 받은 뒤에야 키보드가 뜹니다.
    LaunchedEffect(windowFocused) { if (windowFocused && autoFocus) focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 군더더기 없이: 진행률과 닫기만. 할 일 글씨는 '카드·위젯 글자' 설정을 따릅니다.
                Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val done = tasks.count { it.isDone(today) }
                    Text(
                        if (tasks.isEmpty()) "" else "$done/${tasks.size}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text("닫기", style = MaterialTheme.typography.titleMedium) }
                }
                val settings by TaskStore.settings.collectAsStateWithLifecycle()
                val maxList = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
                CompositionLocalProvider(LocalTaskText provides TextStyle(fontSize = settings.cardTextSp().sp)) {
                    LazyColumn(state = listState, modifier = Modifier.heightIn(max = maxList)) {
                        items(tasks, key = { it.id }) { TaskRow(it, today, onEdit = {}) }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("적고, 엔터. 끝.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val added = TaskStore.add(text) ?: return@KeyboardActions onClose()
                        text = ""
                        val index = TaskStore.tasksFor(today, today).indexOfFirst { it.id == added.id }
                        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .focusRequester(focus),
                )
            }
        }
    }
}
