package com.yuangunn.nursetodo.ui

import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.data.Infusion
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.lock.Alarms
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 기본 priming 용량(cc). 병동에서 별도 수액으로 line 을 채우는 양. */
private const val DEFAULT_PRIMING = "20"

private fun hhmm(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * 항암약물 주입속도(cc/hr) 계산기. 입력 칸은 모두 같은 폭이고 숫자 오른쪽 끝을 한 세로줄에 맞춥니다.
 * 투여시간은 시간·분 두 칸(1시간 30분을 그대로). 결과가 설정의 상한을 넘으면 주황으로 바꾸고 확인 문구를 보이며,
 * 아니면 지금 시작할 때 끝나는 시각과 알람 맞추기. 용량은 기기에 저장하지 않습니다(공용 폰에서 이전 환자 값이 남지 않게).
 */
/** 계산기 탭: 위 '항암 주입속도 | 사용기한' 고르기. 둘을 오가도 각자 적은 값은 남고, 모두 지우기는 보이는 쪽만. */
@Composable
fun CalcScreen() {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var clearInfusion by rememberSaveable { mutableIntStateOf(0) }
    var clearExpiry by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("계산기", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (mode == 0) clearInfusion++ else clearExpiry++ }) { Text("모두 지우기", style = MaterialTheme.typography.labelLarge) }
        }
        Segmented(listOf("항암 주입속도", "사용기한"), mode, Modifier.padding(top = 8.dp)) { mode = it }
        holder.SaveableStateProvider(mode) {
            if (mode == 0) InfusionCalc(clearInfusion) else ExpiryCalc(clearExpiry)
        }
    }
}

@Composable
private fun InfusionCalc(clear: Int) {
    val s by TaskStore.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var base by rememberSaveable { mutableStateOf("") }
    var chemo by rememberSaveable { mutableStateOf("") }
    var priming by rememberSaveable { mutableStateOf(DEFAULT_PRIMING) }
    var withPriming by rememberSaveable { mutableStateOf(true) }
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var alarmAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val result = Infusion.calc(base, chemo, priming, hours, minutes, withPriming)
    val incomplete = listOf(base, chemo).any { it.isBlank() } || (hours.isBlank() && minutes.isBlank()) || (withPriming && priming.isBlank())
    // 값을 고치면 맞춘 알람 표시는 지웁니다(알람 자체는 그대로).
    LaunchedEffect(base, chemo, priming, withPriming, hours, minutes) { alarmAt = null }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000 + 50)
        }
    }
    val dark = s.isDark()
    val rate = result as? Infusion.Rate
    val over = rate != null && rate.ccPerHr.toDouble() > s.rateLimit
    val color = if (over) warningColor(dark) else MaterialTheme.colorScheme.primary

    // 위 '모두 지우기'(누른 횟수가 바뀌면). 다른 계산기에 다녀와 다시 그려질 때는 지우지 않습니다.
    var seen by rememberSaveable { mutableIntStateOf(clear) }
    LaunchedEffect(clear) {
        if (clear != seen) {
            seen = clear
            base = ""
            chemo = ""
            priming = DEFAULT_PRIMING
            withPriming = true
            hours = ""
            minutes = ""
        }
    }
    Column {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NumField("Base fluid", base, "cc") { base = it }
            NumField("항암 fluid", chemo, "cc") { chemo = it }
            NumField("Priming", priming, "cc", enabled = withPriming, trailing = {
                InFieldChoice(listOf("포함", "제외"), if (withPriming) 0 else 1) { withPriming = it == 0 }
            }) { priming = it }
            // 이름이 있는 칸은 테두리 위에 이름 자리가 있어 아래로 맞춥니다.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("투여시간", hours, "시간", Modifier.weight(1f)) { hours = it }
                NumField("", minutes, "분", Modifier.weight(1f), last = true) { minutes = it }
            }
        }
        Column(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(16.dp),
        ) {
            Text("주입속도", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    rate?.ccPerHr?.toPlainString() ?: "–",
                    style = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = if (rate != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("cc/hr", style = MaterialTheme.typography.titleMedium, color = if (rate != null) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            when {
                rate != null -> {
                    val h = hours.trim().ifEmpty { "0" }
                    val m = minutes.trim().ifEmpty { "0" }
                    Text(
                        "${Infusion.num(rate.totalCc)} cc ÷ " + listOfNotNull("${h}시간".takeIf { h != "0" }, "${m}분".takeIf { m != "0" }).joinToString(" "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Base ${base.trim()} + 항암 ${chemo.trim()}" + if (withPriming) " + priming ${priming.trim()}" else " (priming 제외)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 아직 덜 적은 칸은 조용히 기다리고, 잘못 적은 값만 알려 줍니다.
                !incomplete -> Text((result as Infusion.Invalid).message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
            if (over) {
                CardLine()
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, null, tint = warningColor(dark), modifier = Modifier.size(16.dp))
                    Text("상한 ${s.rateLimit} cc/hr를 넘었어요.\n시간과 분 칸을 확인하세요.", style = MaterialTheme.typography.titleSmall, color = warningColor(dark))
                }
            } else if (rate != null) {
                CardLine()
                val end = now + (rate.hours * 3_600_000).toLong()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text("지금 시작하면", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${hhmm(end)}에 끝나요", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                    }
                    FillButton(
                        alarmAt?.let { "${hhmm(it)} 알람 맞춤" } ?: "알람 맞추기",
                        {
                            Alarms.add(context, end, "항암 끝")
                            alarmAt = end
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        enabled = alarmAt == null,
                    )
                }
            }
            CardLine()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text("계산 결과는 참고용입니다. 처방과 기관 지침을 반드시 확인하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CardLine() = Box(
    Modifier
        .padding(vertical = 12.dp)
        .fillMaxWidth()
        .height(1.dp)
        .background(MaterialTheme.colorScheme.outlineVariant),
)

/** 숫자 칸(높이 48 이상): 이름은 테두리 위, 숫자는 오른쪽 끝 정렬, 단위, (선택) 칸 안 오른쪽 고르기. */
@Composable
private fun NumField(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    last: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = if (label.isNotEmpty()) ({ Text(label) }) else null,
        suffix = { Text(unit, modifier = Modifier.width(if (trailing != null) 24.dp else 32.dp)) },
        trailingIcon = trailing,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, lineHeight = 24.sp, textAlign = TextAlign.End, fontFeatureSettings = "tnum"),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
        modifier = modifier.heightIn(min = 48.dp),
    )
}

/** 칸 안 오른쪽 두 갈래(폭 104, 보이는 32·누르는 40, 고른 것은 container). */
@Composable
private fun InFieldChoice(options: List<String>, chosen: Int, onChoose: (Int) -> Unit) {
    Row(Modifier.width(104.dp).padding(end = 4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == chosen
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = { onChoose(i) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.fillMaxSize()) {
                    Text(
                        o,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
