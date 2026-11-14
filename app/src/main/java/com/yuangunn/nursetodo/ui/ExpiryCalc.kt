package com.yuangunn.nursetodo.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.text.KeyboardActions
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.data.Expiry
import com.yuangunn.nursetodo.data.ExpiryPreset
import com.yuangunn.nursetodo.data.Fmt
import com.yuangunn.nursetodo.data.TaskStore
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val Pill = RoundedCornerShape(50)

/**
 * 사용기한 계산기: 물품마다 정해진 기간(시간·일·주·개월)을 넣으면 시작한 날(개봉·부착·조제 등, 기본 오늘)부터 '○까지'를 바로 계산합니다.
 * 기간 칸이 맨 위이고 비어 있으면 열자마자 커서. 자주 쓰는 기한은 칩(누르면 채움, 길게 누르면 빼기, '+ 저장'으로 이름 붙여 넣기).
 * 결과 아래 접힌 옵션: 제조사 유효기간(더 빠른 날로), 시작한 날을 1일째로 세기(기관마다 달라 저장, 설명은 지금 값으로 두 경우를 계산해 보여 줌).
 * 결과는 남은 날, 적어 붙일 라벨 줄, 기한 날의 할 일로 넣기. 적은 값은 기기에 저장하지 않습니다(공용 폰).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExpiryCalc(clear: Int) {
    val s by TaskStore.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val opened = remember { LocalDateTime.now() }
    var startDay by rememberSaveable { mutableLongStateOf(opened.toLocalDate().toEpochDay()) }
    var startMin by rememberSaveable { mutableIntStateOf(opened.hour * 60 + opened.minute) }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableIntStateOf(Expiry.DAYS) }
    var name by rememberSaveable { mutableStateOf("") }
    var maker by rememberSaveable { mutableStateOf<Long?>(null) }
    var added by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<ExpiryPreset?>(null) }
    var seen by rememberSaveable { mutableIntStateOf(clear) }
    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // 사용기한을 열면 기간이 비었을 때 바로 숫자를 칠 수 있게
    LaunchedEffect(Unit) { if (amount.isEmpty()) amountFocus.requestFocus() }
    LaunchedEffect(clear) {
        if (clear != seen) {
            seen = clear
            val now = LocalDateTime.now()
            startDay = now.toLocalDate().toEpochDay()
            startMin = now.hour * 60 + now.minute
            amount = ""
            unit = Expiry.DAYS
            name = ""
            maker = null
            amountFocus.requestFocus()
        }
    }
    // 값을 고치면 '할 일에 넣음' 표시는 지웁니다.
    LaunchedEffect(startDay, startMin, amount, unit, maker, s.expiryFirstDay) { added = false }

    val n = amount.trim().toIntOrNull()?.takeIf { it in 1..999 }
    val start = LocalDate.ofEpochDay(startDay).atTime(startMin / 60, startMin % 60)
    val result = n?.let { Expiry.capped(Expiry.until(start, it, unit, s.expiryFirstDay), maker?.let(LocalDate::ofEpochDay)) }
    val hours = unit == Expiry.HOURS

    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.expiryPresets.forEach { p ->
                PresetChip(p.label, on = n == p.amount && unit == p.unit && name == p.name, onClick = {
                    amount = "${p.amount}"
                    unit = p.unit
                    name = p.name
                }, onLongClick = { removing = p })
            }
            PresetChip("+ 저장", on = false, enabled = n != null, onClick = { saving = true })
        }
        // 기간이 먼저: 비어 있으면 열자마자 이 칸에 커서(숫자 키보드)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it.filter(Char::isDigit).take(3)
                    name = ""
                },
                label = { Text("기간") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.End, fontFeatureSettings = "tnum"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .width(96.dp)
                    .heightIn(min = 48.dp)
                    .focusRequester(amountFocus),
            )
            // 옆 기간 칸(OutlinedTextField 테두리 56)과 높이를 같게
            Segmented(Expiry.UNITS, unit, Modifier.weight(1f), height = 56.dp) {
                unit = it
                name = ""
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickField("시작한 날", Fmt.date(startDay), Modifier.weight(1f)) {
                val d = LocalDate.ofEpochDay(startDay)
                DatePickerDialog(context, { _, y, m, dd -> startDay = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }
            if (hours) {
                PickField("시각", clock(startMin), Modifier.width(112.dp)) {
                    TimePickerDialog(context, { _, h, m -> startMin = h * 60 + m }, startMin / 60, startMin % 60, true).show()
                }
            }
        }
        Text("개봉·부착·조제처럼 시작한 날이에요. 기본은 오늘.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))

        // 결과
        Column(
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(16.dp),
        ) {
            Text("사용기한", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val until = result?.first
            val today = LocalDate.now()
            val days = until?.let { ChronoUnit.DAYS.between(today, it.toLocalDate()) }
            val passed = until != null && (if (hours) until.isBefore(LocalDateTime.now()) else days!! < 0)
            Text(
                until?.let { Fmt.date(it.toLocalDate().toEpochDay()) + (if (hours) " " + clock(it.hour * 60 + it.minute) else "") + "까지" } ?: "–",
                style = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                color = when {
                    until == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    passed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (until != null) {
                Text(
                    when {
                        hours -> Duration.between(LocalDateTime.now(), until).let { d ->
                            if (d.isNegative) "${-d.toHours()}시간 지났어요" else "${d.toHours()}시간 ${d.toMinutes() % 60}분 남음"
                        }
                        days!! > 0 -> "오늘부터 ${days}일 남음"
                        days == 0L -> "오늘이 마지막 날이에요"
                        else -> "${-days}일 지났어요"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (passed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (result.second) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, null, tint = warningColor(s.isDark()), modifier = Modifier.size(16.dp))
                        Text("제조사 유효기간이 더 빨라 그 날로 잡았어요.", style = MaterialTheme.typography.bodySmall, color = warningColor(s.isDark()))
                    }
                }
                CardLine()
                // 물품에 적어 붙일 한 줄
                // 시간 단위는 길어서 시작·기한을 두 줄로
                val label = "${md(start.toLocalDate())}" + (if (hours) " ${clock(startMin)}" else "") + (if (hours) " 시작\n" else " 시작 · ") +
                    md(until.toLocalDate()) + (if (hours) " ${clock(until.hour * 60 + until.minute)}" else "") + "까지"
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("라벨에 적을 때", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                    }
                    FillButton(
                        if (added) "할 일에 넣음" else "할 일로 추가",
                        {
                            val title = (name.trim().ifEmpty { "사용기한" }.let { if (name.isBlank()) it else "$it 사용기한" }) +
                                " · ${md(start.toLocalDate())}부터 ${Expiry.period(n!!, unit)}"
                            TaskStore.add(title, until.toLocalDate().toEpochDay())
                            added = true
                            Toast.makeText(context, "${md(until.toLocalDate())} 할 일에 넣었어요", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !added,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
            CardLine()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text("기한은 참고용입니다. 제조사 설명서와 기관 지침을 반드시 확인하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 옵션: 자주 바꾸지 않아 접어 두고, 켠 것은 접힌 줄에 보입니다(결과가 달라지므로).
        val firstDay = s.expiryFirstDay
        val optionSummary = listOfNotNull(
            "시작한 날 1일째".takeIf { firstDay && !hours },
            maker?.let { "제조사 ${md(LocalDate.ofEpochDay(it))}" },
        ).joinToString(" · ")
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button) { optionsOpen = !optionsOpen }
                .semantics { stateDescription = if (optionsOpen) "펼침" else "접힘" }
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("옵션", style = MaterialTheme.typography.bodyLarge)
            Text(
                optionSummary.ifEmpty { "제조사 유효기간 · 날짜 세는 법" },
                style = MaterialTheme.typography.bodySmall,
                color = if (optionSummary.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (optionsOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        if (optionsOpen) {
            PickField(
                "제조사 유효기간 (선택)",
                maker?.let { Fmt.date(it) } ?: "",
                Modifier.fillMaxWidth(),
                onClear = maker?.let { { maker = null } },
            ) {
                val d = maker?.let(LocalDate::ofEpochDay) ?: LocalDate.ofEpochDay(startDay).plusMonths(1)
                DatePickerDialog(context, { _, y, m, dd -> maker = LocalDate.of(y, m + 1, dd).toEpochDay() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }
            Text("넣으면 계산한 날과 둘 중 빠른 날로 잡아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            if (!hours) {
                // 기관 지침에 따라 고르고 저장. 설명은 지금 넣은 값으로 두 가지를 다 보여 줍니다.
                val off = n?.let { Expiry.until(start, it, unit, false).toLocalDate() }
                val on = n?.let { Expiry.until(start, it, unit, true).toLocalDate() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Switch) { TaskStore.updateSettings { it.copy(expiryFirstDay = !firstDay) } }
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("시작한 날을 1일째로 세기", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (on == null || off == null) {
                                "끄면 다음 날이 1일째, 켜면 시작한 날이 1일째예요."
                            } else {
                                "켜면 ${md(on)}까지 · 끄면 ${md(off)}까지 (${md(start.toLocalDate())} 시작, ${Expiry.period(n, unit)})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppSwitch(firstDay) { v -> TaskStore.updateSettings { it.copy(expiryFirstDay = v) } }
                }
            }
        }
    }

    if (saving && n != null) {
        var text by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("자주 쓰는 기한으로 저장") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(16) },
                    singleLine = true,
                    label = { Text("이름 (선택)") },
                    placeholder = { Text("예: 인슐린 펜") },
                    supportingText = { Text(Expiry.period(n, unit) + " · 비우면 기간만 보여요") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = ExpiryPreset(text.trim(), n, unit)
                    TaskStore.updateSettings { it.copy(expiryPresets = it.expiryPresets.filterNot { q -> q == p } + p) }
                    name = p.name
                    saving = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text("취소") } },
        )
    }
    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("'${p.label}' 빼기") },
            text = { Text("자주 쓰는 기한 목록에서 빼요. 계산한 값은 그대로예요.") },
            confirmButton = {
                TextButton(onClick = {
                    TaskStore.updateSettings { it.copy(expiryPresets = it.expiryPresets - p) }
                    removing = null
                }) { Text("빼기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("취소") } },
        )
    }
}

private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

private fun md(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"

/** 자주 쓰는 기한 칩(높이 40 알약): 누르면 채움, 길게 누르면 [onLongClick]. 고른 칩은 primaryContainer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(text: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .height(40.dp)
            .clip(Pill)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, Pill)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = onLongClick?.let { "목록에서 빼기" },
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> MaterialTheme.colorScheme.outline
                on -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** 눌러서 고르는 칸(날짜·시각): 입력 칸 모양(모서리 12), 이름은 테두리 위. [onClear] 가 있으면 오른쪽 ✕. */
@Composable
private fun PickField(label: String, value: String, modifier: Modifier = Modifier, placeholder: String = "", onClear: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
            shape = MaterialTheme.shapes.small,
            textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, lineHeight = 24.sp, fontFeatureSettings = "tnum"),
            trailingIcon = onClear?.let {
                { IconButton(onClick = it, modifier = Modifier.semantics { contentDescription = "$label 지우기" }) { Icon(Icons.Default.Close, contentDescription = null) } }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
        // 읽기 전용 칸은 눌러도 키보드가 안 뜨게 위에 누르는 면을 둡니다(✕ 자리는 비움).
        Box(
            Modifier
                .matchParentSize()
                .padding(top = 8.dp, end = if (onClear != null) 48.dp else 0.dp)
                .clip(MaterialTheme.shapes.small)
                .combinedClickableCompat(label, onClick),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableCompat(label: String, onClick: () -> Unit) =
    combinedClickable(role = Role.Button, onClickLabel = "$label 고르기", onClick = onClick)
