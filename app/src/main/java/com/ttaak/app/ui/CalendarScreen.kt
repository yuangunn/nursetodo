package com.ttaak.app.ui

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
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import java.time.LocalDate

@Composable
fun CalendarScreen(onEdit: (Task) -> Unit, onDelete: (Task) -> Unit) {
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
                    Text(
                        Fmt.date(selected) + if (selected == today) " · 오늘" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
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

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${first.year}년 ${first.monthValue}월",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            TextButton(onClick = {
                onMonth(LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay())
                onSelect(today)
            }) { Text("오늘") }
            IconButton(onClick = { onMonth(first.minusMonths(1).toEpochDay()) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
            }
            IconButton(onClick = { onMonth(first.plusMonths(1).toEpochDay()) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (i) {
                        0 -> Color(0xFFFF3B30)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        for (week in 0 until cells / 7) {
            Row {
                for (col in 0..6) {
                    val index = week * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
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
    val pending = visible.any { !it.isDone(day) }
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) primary else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            LocalDate.ofEpochDay(day).dayOfMonth.toString(),
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = when {
                selected -> Color.White
                isToday -> primary
                sunday -> Color(0xFFFF3B30)
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        visible.isEmpty() -> Color.Transparent
                        selected -> Color.White
                        pending -> primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                ),
        )
    }
}
