package com.yuangunn.nursetodo.ui

import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Fmt
import com.yuangunn.nursetodo.data.Task
import com.yuangunn.nursetodo.data.TaskStore
import java.time.LocalDate

@Composable
fun CalendarScreen(onEdit: (Task) -> Unit, onDelete: (Task) -> Unit, onClearDone: (Long) -> Unit) {
    val all by TaskStore.tasks.collectAsStateWithLifecycle()
    val today = Day.today()
    var selected by rememberSaveable { mutableLongStateOf(today) }
    var monthStart by rememberSaveable {
        mutableLongStateOf(LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay())
    }
    val tasks = remember(all, selected) { TaskStore.tasksFor(selected, today) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TaskList(
            day = selected,
            tasks = tasks,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = Modifier.weight(1f),
            onClearDone = { onClearDone(selected) },
            header = {
                item(key = "calendar") {
                    MonthGrid(
                        monthStart = monthStart,
                        selected = selected,
                        today = today,
                        all = all,
                        onMonth = { monthStart = it },
                        onSelect = { selected = it },
                    )
                }
                item(key = "dayTitle") {
                    // 고른 날 제목과 개수 (위에 선)
                    val done = tasks.count { it.isDone(selected) }
                    Column(Modifier.padding(top = 8.dp)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                            Text(
                                Fmt.date(selected) + if (selected == today) " · 오늘" else "",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                when {
                                    tasks.isEmpty() -> ""
                                    selected == today -> "${tasks.size}개 중 ${done}개 완료"
                                    else -> "할 일 ${tasks.size}개"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        )
        AddBar(selected)
    }
}

@Composable
private fun MonthGrid(
    monthStart: Long,
    selected: Long,
    today: Long,
    all: List<Task>,
    onMonth: (Long) -> Unit,
    onSelect: (Long) -> Unit,
) {
    val first = LocalDate.ofEpochDay(monthStart)
    val lead = first.dayOfWeek.value % 7 // 일요일 시작
    val days = first.lengthOfMonth()
    val cells = ((lead + days + 6) / 7) * 7

    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${first.year}년 ${first.monthValue}월", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay() != monthStart || selected != today) {
                TextButton(onClick = {
                    onMonth(LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay())
                    onSelect(today)
                }) { Text("오늘") }
            }
            IconButton(onClick = { onMonth(first.minusMonths(1).toEpochDay()) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
            }
            IconButton(onClick = { onMonth(first.plusMonths(1).toEpochDay()) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
            }
        }
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        for (week in 0 until cells / 7) {
            Row(Modifier.padding(horizontal = 12.dp)) {
                for (col in 0..6) {
                    val index = week * 7 + col - lead
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (index in 0 until days) {
                            val day = monthStart + index
                            DayCell(day, day == selected, day == today, all, today, col == 0) { onSelect(day) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Long,
    selected: Boolean,
    isToday: Boolean,
    all: List<Task>,
    today: Long,
    sunday: Boolean,
    onClick: () -> Unit,
) {
    val visible = all.filter { it.visibleOn(day, today) }
    val date = LocalDate.ofEpochDay(day)
    val wd = listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1]
    // 날짜 칸 44: 고른 날은 강조 채움 원 32, 오늘은 container, 할 일이 있는 날은 숫자 아래 점 4
    Column(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${date.monthValue}월 ${date.dayOfMonth}일 ${wd}요일" +
                    (if (visible.isNotEmpty()) ", 할 일 ${visible.size}개" else "") + if (isToday) ", 오늘" else ""
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = if (isToday || selected) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    selected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (visible.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}
