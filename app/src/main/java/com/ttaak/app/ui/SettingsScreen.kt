package com.ttaak.app.ui

import android.app.DatePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.data.CardTextSizes
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.TaskStore
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun SettingsScreen() {
    val s by TaskStore.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        Text(
            "설정",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp),
        )

        Section("잠금화면 카드")
        SwitchRow("잠금화면에 오늘 할 일 표시", s.lockEnabled) { v -> TaskStore.updateSettings { it.copy(lockEnabled = v) } }
        SwitchRow("완료한 일은 카드에서 숨기기", s.lockHideDone) { v -> TaskStore.updateSettings { it.copy(lockHideDone = v) } }
        ChoiceRow("카드·위젯 글자", CardTextSizes.map { it.first }, s.cardTextSize) { i ->
            TaskStore.updateSettings { it.copy(cardTextSize = i) }
        }
        SwitchRow("화면 켜면 딱 화면 먼저 보기 (시험)", s.lockPage) { v ->
            TaskStore.updateSettings { it.copy(lockPage = v) }
            // 백그라운드에서 화면을 띄우려면 '다른 앱 위에 표시'를 허용해야 합니다.
            if (v && !android.provider.Settings.canDrawOverlays(context)) {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 36) {
            SwitchRow("라이브 알림으로 크게 표시 (시험)", s.lockLive) { v -> TaskStore.updateSettings { it.copy(lockLive = v) } }
        }
        SettingField(
            value = s.lockText,
            max = 40,
            label = "카드 문구 (예: 오늘도 딱 해내자)",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        ) { v -> TaskStore.updateSettings { it.copy(lockText = v) } }
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingField(
                value = s.ddayLabel,
                max = 20,
                label = "D-day 이름",
                modifier = Modifier.weight(1f),
            ) { v -> TaskStore.updateSettings { it.copy(ddayLabel = v) } }
            TextButton(onClick = {
                val d = LocalDate.ofEpochDay(s.ddayDate ?: Day.today())
                DatePickerDialog(context, { _, y, m, dd ->
                    TaskStore.updateSettings { it.copy(ddayDate = LocalDate.of(y, m + 1, dd).toEpochDay()) }
                }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }) {
                Text(s.ddayDate?.let { "${Fmt.date(it)} · ${Fmt.dday(it)}" } ?: "날짜 선택")
            }
        }
        if (s.ddayDate != null) {
            TextButton(
                onClick = { TaskStore.updateSettings { it.copy(ddayDate = null, ddayLabel = "") } },
                modifier = Modifier.padding(start = 12.dp),
            ) { Text("D-day 지우기") }
        }
        SetupGuide(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        Section("목록")
        ChoiceRow("새 할 일 위치", listOf("맨 아래", "맨 위"), if (s.newOnTop) 1 else 0) { i ->
            TaskStore.updateSettings { it.copy(newOnTop = i == 1) }
        }

        Section("디자인")
        Text("테마 색", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Accents.forEachIndexed { i, color ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (s.accent == i) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier,
                        )
                        .clickable { TaskStore.updateSettings { it.copy(accent = i) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (s.accent == i) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        ChoiceRow("글자 크기", TextSizes.map { it.first }, s.textSize) { i -> TaskStore.updateSettings { it.copy(textSize = i) } }
        ChoiceRow("글자 굵기", listOf("보통", "굵게"), if (s.bold) 1 else 0) { i -> TaskStore.updateSettings { it.copy(bold = i == 1) } }
        ChoiceRow("체크 위치", listOf("왼쪽", "오른쪽"), if (s.checkOnLeft) 0 else 1) { i ->
            TaskStore.updateSettings { it.copy(checkOnLeft = i == 0) }
        }

        Section("데이터")
        TextButton(
            onClick = {
                val n = TaskStore.clearOldDone()
                Toast.makeText(context, if (n > 0) "${n}개 정리했어요" else "정리할 항목이 없어요", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.padding(start = 12.dp),
        ) { Text("지난 완료 항목 정리") }
        Hint("모든 데이터는 이 기기 안에만 저장됩니다. 인터넷 권한이 없어 외부로 전송되지 않아요.")
    }
}

/**
 * 글자는 화면 상태로 바로 받고, 저장(잠금화면 카드 다시 그리기 포함)은 입력이 멈춘 뒤 한 번만 합니다.
 * 저장소 값을 그대로 입력칸에 물리면 한글 조합이 깨지고, 글자마다 알림을 갱신하다 시스템 제한에 걸립니다.
 */
@Composable
private fun SettingField(value: String, max: Int, label: String, modifier: Modifier, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var saved by remember { mutableStateOf(value) }
    // 바깥에서 바뀐 값(D-day 지우기 등)만 입력칸에 반영합니다. 방금 저장한 값이 돌아온 것으로
    // 입력칸을 덮으면 그 사이에 친 글자가 사라집니다.
    LaunchedEffect(value) { if (value != saved) { saved = value; text = value } }
    LaunchedEffect(text) {
        if (text == saved) return@LaunchedEffect
        delay(500)
        saved = text
        onSave(text)
    }
    val latest by rememberUpdatedState(text)
    DisposableEffect(Unit) { onDispose { if (latest != saved) onSave(latest) } }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(max) },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 20.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, o ->
                FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) })
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}
