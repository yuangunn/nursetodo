package com.ttaak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.data.Infusion
import com.ttaak.app.data.TaskStore

/**
 * 항암약물 주입속도(cc/hr) 계산기. 입력이 바뀌면 바로 다시 계산합니다.
 * 용량은 기기에 저장하지 않습니다 (공용 폰에서 다음 사람이 이전 환자 값을 그대로 쓰지 않도록).
 */
@Composable
fun CalcScreen() {
    val s by TaskStore.settings.collectAsStateWithLifecycle()
    var base by rememberSaveable { mutableStateOf("") }
    var chemo by rememberSaveable { mutableStateOf("") }
    var priming by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("") }
    val fluid = Fluids[s.calcFluid.coerceIn(Fluids.indices)]
    val result = Infusion.calc(base, chemo, priming, time, s.calcHours)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp)) {
            Text("계산기", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                "항암약물 주입속도 (cc/hr)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Base fluid", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Fluids.forEachIndexed { i, name ->
                FilterChip(
                    selected = s.calcFluid == i,
                    onClick = { TaskStore.updateSettings { it.copy(calcFluid = i) } },
                    label = { Text(name) },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        NumberField("$fluid 용량", base, "cc") { base = it }
        NumberField("mix된 항암 fluid 용량", chemo, "cc") { chemo = it }
        NumberField("Priming 용량", priming, "cc", hint = "별도 수액으로 line을 채운 용량 · 환자에게 들어가므로 총량에 더해요") { priming = it }
        Row(Modifier.padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                "투여 예정시간", time, if (s.calcHours) "시간" else "분",
                last = true, modifier = Modifier.weight(1f),
            ) { time = it }
            listOf("분" to false, "시간" to true).forEach { (label, hours) ->
                FilterChip(
                    selected = s.calcHours == hours,
                    onClick = {
                        // 단위만 바꾸면 120분이 120시간으로 바뀌어 버리므로 시간 칸을 비웁니다.
                        if (s.calcHours != hours) time = ""
                        TaskStore.updateSettings { it.copy(calcHours = hours) }
                    },
                    label = { Text(label) },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (result) {
                    is Infusion.Rate -> {
                        Text(
                            "${result.ccPerHr.toPlainString()} cc/hr",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        val total = Infusion.num(result.totalCc)
                        val hours = if (s.calcHours) "${time.trim()}시간" else "(${time.trim()}분 ÷ 60)"
                        Process("총 주입량", "$fluid ${base.trim()} + 항암 ${chemo.trim()} + priming ${priming.trim()} = $total cc")
                        Process("주입속도", "$total cc ÷ $hours = ${result.ccPerHr.toPlainString()} cc/hr")
                    }
                    is Infusion.Invalid -> Text(
                        if (listOf(base, chemo, priming, time).all { it.isBlank() }) "네 칸을 채우면 바로 계산돼요" else result.message,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "계산 결과는 참고용입니다. 처방과 기관 지침을 반드시 확인하세요.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            onClick = { base = ""; chemo = ""; priming = ""; time = "" },
            modifier = Modifier.padding(start = 12.dp),
        ) { Text("모두 지우기") }
    }
}

private val Fluids = listOf("NS", "5DW")

@Composable
private fun NumberField(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    last: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text(unit) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

@Composable
private fun Process(label: String, text: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
