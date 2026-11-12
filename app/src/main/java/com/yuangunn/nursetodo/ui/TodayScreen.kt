package com.yuangunn.nursetodo.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Fmt
import com.yuangunn.nursetodo.data.Task
import com.yuangunn.nursetodo.data.TaskStore

@Composable
fun TodayScreen(onEdit: (Task) -> Unit, onDelete: (Task) -> Unit) {
    val all by TaskStore.tasks.collectAsStateWithLifecycle()
    val settings by TaskStore.settings.collectAsStateWithLifecycle()
    val today = Day.today()
    val tasks = remember(all, today) { TaskStore.tasksFor(today, today) }
    val done = tasks.count { it.isDone(today) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TaskList(
            day = today,
            tasks = tasks,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = Modifier.weight(1f),
            header = {
                item(key = "header") {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                        Text(Fmt.date(today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("오늘", style = MaterialTheme.typography.headlineSmall)
                        val headline = Fmt.headline(settings, today)
                        if (headline.isNotEmpty()) {
                            Text(headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (tasks.isNotEmpty()) {
                            // 진행 막대 4 (container 바탕, 강조 채움)와 '6개 중 1개 끝냄'
                            Row(
                                Modifier
                                    .padding(top = 12.dp)
                                    .semantics(mergeDescendants = true) {},
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(done / tasks.size.toFloat())
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                                Text(
                                    "${tasks.size}개 중 ${done}개 완료",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            // 설정 도우미는 할 일 목록을 가리지 않게 목록 아래에 둡니다.
            footer = {
                if (!settings.setupDismissed) {
                    item(key = "setup") {
                        SetupGuide(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            hideDone = true,
                            onClose = { TaskStore.updateSettings { it.copy(setupDismissed = true) } },
                        )
                    }
                }
            },
        )
        AddBar(today)
    }
}
