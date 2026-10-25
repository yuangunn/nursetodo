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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.data.Infusion
import com.ttaak.app.data.TaskStore

/** 기본 priming 용량(cc). 병동에서 별도 수액으로 line 을 채우는 양. */
private const val DEFAULT_PRIMING = "20"

/**
 * 항암약물 주입속도(cc/hr) 계산기. 숫자 넷을 넣으면 바로 계산하고, 결과까지 한 화면에 보입니다.
 * 용량은 기기에 저장하지 않습니다 (공용 폰에서 다음 사람이 이전 환자 값을 그대로 쓰지 않도록).
 */
@Composable
fun CalcScreen() {
    val s by TaskStore.settings.collectAsStateWithLifecycle()
    var base by rememberSaveable { mutableStateOf("") }
    var chemo by rememberSaveable { mutableStateOf("") }
    var priming by rememberSaveable { mutableStateOf(DEFAULT_PRIMING) }
    var withPriming by rememberSaveable { mutableStateOf(true) }
    var time by rememberSaveable { mutableStateOf("") }
    val result = Infusion.calc(base, chemo, priming, time, s.calcHours, withPriming)
    val incomplete = listOf(base, chemo, time).any { it.isBlank() } || (withPriming && priming.isBlank())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "항암 주입속도",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                base = ""
                chemo = ""
                priming = DEFAULT_PRIMING
                withPriming = true
                time = ""
            }) { Text("모두 지우기") }
        }

        InputRow("Base fluid", base, "cc") { base = it }
        InputRow("항암 fluid", chemo, "cc") { chemo = it }
        InputRow(
            "Priming", priming, "cc",
            enabled = withPriming,
            choice = listOf("포함", "빼기"),
            chosen = if (withPriming) 0 else 1,
            onChoose = { withPriming = it == 0 },
        ) { priming = it }
        InputRow(
            "투여시간", time, if (s.calcHours) "시간" else "분",
            last = true,
            choice = listOf("분", "시간"),
            chosen = if (s.calcHours) 1 else 0,
            onChoose = { i ->
                // 단위만 바꾸면 120분이 120시간이 되어 버리므로 시간 칸을 비웁니다.
                if (s.calcHours != (i == 1)) time = ""
                TaskStore.updateSettings { it.copy(calcHours = i == 1) }
            },
        ) { time = it }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val rate = result as? Infusion.Rate
                Text(
                    "${rate?.ccPerHr?.toPlainString() ?: "–"} cc/hr",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (rate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    rate != null -> {
                        val hours = if (s.calcHours) "${time.trim()}시간" else "${time.trim()}분"
                        Text(
                            "${Infusion.num(rate.totalCc)} cc ÷ $hours",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Base ${base.trim()} + 항암 ${chemo.trim()}" +
                                if (withPriming) " + priming ${priming.trim()}" else " (priming 빼고)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 아직 덜 적은 칸은 조용히 기다리고, 잘못 적은 값만 알려 줍니다.
                    !incomplete -> Text(
                        (result as Infusion.Invalid).message,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF3B30),
                    )
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "계산 결과는 참고용입니다. 처방과 기관 지침을 반드시 확인하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 이름 · 숫자 칸 · (선택) 두 갈래 버튼을 한 줄에. 줄마다 같은 자리에 와서 위아래로 훑어보기 쉽습니다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputRow(
    label: String,
    value: String,
    unit: String,
    enabled: Boolean = true,
    last: Boolean = false,
    choice: List<String>? = null,
    chosen: Int = 0,
    onChoose: (Int) -> Unit = {},
    onChange: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            suffix = { Text(unit) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 20.sp, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
            ),
            // '다음'은 옆의 포함/빼기 버튼이 아니라 바로 아래 숫자 칸으로
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.width(128.dp),
        )
        if (choice != null) {
            SingleChoiceSegmentedButtonRow(Modifier.width(112.dp)) {
                choice.forEachIndexed { i, text ->
                    SegmentedButton(
                        selected = i == chosen,
                        onClick = { onChoose(i) },
                        shape = SegmentedButtonDefaults.itemShape(i, choice.size),
                        icon = {},
                        label = { Text(text, maxLines = 1) },
                    )
                }
            }
        } else {
            Spacer(Modifier.width(112.dp))
        }
    }
}
