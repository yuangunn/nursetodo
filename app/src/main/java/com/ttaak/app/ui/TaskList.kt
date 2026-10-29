package com.ttaak.app.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.R
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore

/**
 * 하루치 할 일 목록. 길게 눌러 끌면 순서 변경, 왼쪽으로 밀면 삭제, 제목을 누르면 편집.
 * 끝낸 일은 목록에서 사라지고 맨 아래 접히는 '완료된 할 일'에서만 보입니다 (언제 끝냈는지·만들었는지와 함께).
 */
@Composable
fun TaskList(
    day: Long,
    tasks: List<Task>,
    onEdit: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    modifier: Modifier = Modifier,
    header: LazyListScope.() -> Unit = {},
    footer: LazyListScope.() -> Unit = {},
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var dragList by remember { mutableStateOf<List<Task>?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val done = tasks.filter { it.isDone(day) }
    val shown = dragList ?: tasks.filterNot { it.isDone(day) }

    fun drag(dy: Float) {
        val list = dragList ?: return
        val id = draggingId ?: return
        dragOffset += dy
        val idx = list.indexOfFirst { it.id == id }
        val visible = listState.layoutInfo.visibleItemsInfo
        val neighbor = when {
            dragOffset > 0 && idx + 1 < list.size -> idx + 1
            dragOffset < 0 && idx > 0 -> idx - 1
            else -> return
        }
        val info = visible.firstOrNull { it.key == list[neighbor].id } ?: return
        if (kotlin.math.abs(dragOffset) > info.size / 2f) {
            dragList = list.toMutableList().apply { add(neighbor, removeAt(idx)) }
            dragOffset += if (neighbor > idx) -info.size else info.size
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun endDrag() {
        dragList?.let { list -> TaskStore.reorder(list.map { it.id }) }
        dragList = null
        draggingId = null
        dragOffset = 0f
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        header()
        if (shown.isEmpty() && done.isEmpty()) {
            item(key = "empty") {
                Text(
                    "아직 할 일이 없어요.\n아래에 적어보세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                )
            }
        }
        items(shown, key = { it.id }) { task ->
            val dragging = task.id == draggingId
            Box(
                Modifier
                    .animateItem(placementSpec = if (dragging) null else spring())
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .then(if (dragging) Modifier.shadow(8.dp) else Modifier)
                    .pointerInput(task.id, day) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val current = TaskStore.get(task.id)
                                if (current != null && !current.isDone(day)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragList = TaskStore.tasksFor(day).filterNot { it.isDone(day) }
                                    draggingId = task.id
                                    dragOffset = 0f
                                }
                            },
                            onDrag = { change, amount ->
                                if (draggingId != null) {
                                    change.consume()
                                    drag(amount.y)
                                }
                            },
                            onDragEnd = { endDrag() },
                            onDragCancel = { endDrag() },
                        )
                    },
            ) {
                SwipeRow(task, day, onEdit, onDelete)
            }
        }
        if (done.isNotEmpty()) {
            item(key = "doneHeader") {
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .clickable { showDone = !showDone }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "완료된 할 일 ${done.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (showDone) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showDone) "접기" else "펼치기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showDone) {
                items(done, key = { it.id }) { task ->
                    Box(Modifier.animateItem()) { SwipeRow(task, day, onEdit, onDelete) }
                }
            }
        }
        footer()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(task: Task, day: Long, onEdit: (Task) -> Unit, onDelete: (Task) -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete(task)
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFF3B30))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Default.Delete, "삭제", tint = Color.White) }
        },
    ) {
        TaskRow(task, day, onEdit)
    }
}

@Composable
fun TaskRow(task: Task, day: Long, onEdit: (Task) -> Unit) {
    val settings by TaskStore.settings.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val done = task.isDone(day)
    val today = Day.today()

    val check = @Composable {
        IconButton(onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            TaskStore.toggle(task.id, day)
        }) {
            Icon(
                painterResource(if (done) R.drawable.ic_check_on else R.drawable.ic_check_off),
                contentDescription = if (done) "완료 취소" else "완료",
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onEdit(task) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.checkOnLeft) check() else Spacer(Modifier.width(16.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    task.title,
                    style = LocalTaskText.current,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    // 끝낸 일은 제목만 흐리게, 완료·만든 시각은 읽히게 둡니다.
                    modifier = Modifier.alpha(if (done) 0.4f else 1f),
                )
                val sub = when {
                    done -> listOfNotNull(
                        ("↻ " + Fmt.repeat(task.repeatMask)).takeIf { task.isRoutine },
                        task.doneAtOn(day)?.let { "완료 " + Fmt.stamp(it, today) },
                        "만듦 " + Fmt.stamp(task.createdAt, today),
                    ).joinToString(" · ")
                    task.isRoutine -> "↻ " + Fmt.repeat(task.repeatMask)
                    task.date != null && task.date < today && !done -> "${Fmt.relative(task.date, today)}부터 밀림"
                    task.date != null && task.date != day -> Fmt.relative(task.date, today)
                    else -> null
                }
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (task.date != null && task.date < today && !done) Color(0xFFFF3B30)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!settings.checkOnLeft) check() else Spacer(Modifier.width(16.dp))
        }
    }
}

/** 화면 아래 입력창 — 적고 엔터 치면 바로 추가, 키보드는 그대로. */
@Composable
fun AddBar(day: Long, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (TaskStore.add(text, day) != null) text = ""
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(if (day == Day.today()) "할 일을 적고 엔터" else "${Fmt.relative(day)} 할 일 추가")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = submit, enabled = text.isNotBlank()) {
                Icon(Icons.Default.Add, "추가", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
