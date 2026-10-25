package com.ttaak.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditSheet(task: Task, onDismiss: () -> Unit, onDelete: (Task) -> Unit) {
    val context = LocalContext.current
    val today = Day.today()
    var title by remember(task.id) { mutableStateOf(task.title) }
    var date by remember(task.id) { mutableStateOf(task.date) }
    var mask by remember(task.id) { mutableIntStateOf(task.repeatMask) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun save() {
        val current = TaskStore.get(task.id) ?: return
        val routine = mask != 0
        // 일반 ↔ 루틴으로 바꿀 때 '오늘 체크함'은 그대로 옮기고, 지난 기록은 버립니다.
        val doneToday = current.isDone(today)
        TaskStore.update(
            current.copy(
                title = title.trim().ifEmpty { current.title },
                date = if (routine) null else date?.takeIf { it != today },
                repeatMask = mask,
                doneOn = when {
                    routine -> null
                    current.isRoutine -> today.takeIf { doneToday }
                    else -> current.doneOn
                },
                doneDays = when {
                    !routine -> emptySet()
                    current.isRoutine -> current.doneDays
                    else -> if (doneToday && current.doneOn == today) setOf(today) else emptySet()
                },
            ),
        )
    }

    // 바깥을 누르거나 뒤로 가기로 닫아도 고친 내용은 저장합니다.
    ModalBottomSheet(onDismissRequest = { save(); onDismiss() }, sheetState = sheet) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("할 일") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("날짜", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val isToday = mask == 0 && (date == null || date == today)
                FilterChip(selected = isToday, onClick = { mask = 0; date = null }, label = { Text("오늘") })
                FilterChip(
                    selected = mask == 0 && date == today + 1,
                    onClick = { mask = 0; date = today + 1 },
                    label = { Text("내일") },
                )
                val custom = mask == 0 && date != null && date != today && date != today + 1
                FilterChip(
                    selected = custom,
                    onClick = {
                        val d = LocalDate.ofEpochDay(date ?: today)
                        DatePickerDialog(context, { _, y, m, dd ->
                            mask = 0
                            date = LocalDate.of(y, m + 1, dd).toEpochDay()
                        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    },
                    label = { Text(if (custom) Fmt.date(date!!) else "날짜 선택") },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("루틴 (반복)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { mask = if (mask == Task.EVERY_DAY) 0 else Task.EVERY_DAY }) {
                    Text(if (mask == Task.EVERY_DAY) "반복 끄기" else "매일")
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Fmt.WEEKDAYS.forEachIndexed { i, label ->
                    val on = mask and (1 shl i) != 0
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { mask = mask xor (1 shl i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (mask != 0) {
                Text(
                    "${Fmt.repeat(mask)} 오늘 목록에 다시 나타나요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                listOfNotNull(
                    "만듦 " + Fmt.stamp(task.createdAt),
                    task.doneAt?.takeIf { task.doneOn != null || task.isRoutine }?.let { "마지막 완료 " + Fmt.stamp(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    onDismiss()
                    onDelete(TaskStore.get(task.id) ?: task)
                }) { Text("삭제", color = Color(0xFFFF3B30)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    save()
                    onDismiss()
                }) { Text("저장") }
            }
        }
    }
}
