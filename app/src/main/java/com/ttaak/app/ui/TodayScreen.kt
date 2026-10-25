package com.ttaak.app.ui

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
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore

@Composable
fun TodayScreen(onEdit: (Task) -> Unit) {
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
            modifier = Modifier.weight(1f),
            header = {
                item(key = "header") {
                    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
                        Text(
                            Fmt.date(today),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "오늘",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        val headline = Fmt.headline(settings, today)
                        if (headline.isNotEmpty()) {
                            Text(
                                headline,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (tasks.isNotEmpty()) {
                            LinearProgressIndicator(
                                progress = { done / tasks.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .clip(MaterialTheme.shapes.small),
                                drawStopIndicator = {},
                            )
                            Text(
                                if (done == tasks.size) "다 했어요! 🎉" else "${tasks.size}개 중 ${done}개 완료",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                if (!settings.setupDismissed) {
                    item(key = "setup") {
                        SetupGuide(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
