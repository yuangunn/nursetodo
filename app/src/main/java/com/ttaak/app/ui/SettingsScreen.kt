package com.ttaak.app.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.R
import com.ttaak.app.data.CardSpRange
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.RowGapRange
import com.ttaak.app.data.Settings
import com.ttaak.app.data.Shortcut
import com.ttaak.app.data.TaskStore
import com.ttaak.app.data.shortcutSlots
import com.ttaak.app.lock.AlarmRingService
import com.ttaak.app.lock.AlarmSound
import com.ttaak.app.lock.Glass
import com.ttaak.app.lock.GlassEdge
import com.ttaak.app.lock.NotiListener
import com.ttaak.app.lock.circleBoxHeight
import com.ttaak.app.lock.Setup
import com.ttaak.app.lock.Shortcuts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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

        Section("잠금화면")
        SwitchRow("잠금화면에 오늘 할 일 표시", s.lockEnabled) { v -> TaskStore.updateSettings { it.copy(lockEnabled = v) } }
        SwitchRow("화면 켜면 딱 화면 먼저 보기", s.lockPage) { v ->
            TaskStore.updateSettings { it.copy(lockPage = v) }
            // 백그라운드에서 화면을 띄우려면 '다른 앱 위에 표시'를 허용해야 합니다.
            if (v) Setup.requestOverlay(context)
        }
        CardLook(s)
        if (s.shortcuts.take(s.shortcutSlots()).any { it.id == Shortcut.ADD }) {
            LinkRow("할 일 추가 버튼", "딱 화면 아래 바로가기의 '할 일 추가'로 해요. 위쪽 버튼은 숨겼어요.")
        } else {
            ChoiceRow("할 일 추가 버튼", listOf("카드 아래", "카드 안"), s.addButton) { i -> TaskStore.updateSettings { it.copy(addButton = i) } }
        }
        if (s.lockPage) {
            // 안드로이드가 상시 서비스에 알림 하나를 꼭 요구해서 앱이 아이콘을 직접 지울 수는 없습니다.
            var tick by remember { mutableIntStateOf(0) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
            val hidden = remember(tick) { Setup.pageIconHidden(context) }
            val notiOn = remember(tick) { NotiListener.allowed(context) }
            val notiLive = remember(tick) { NotiListener.connected }
            LinkRow(
                "상태바의 딱 아이콘 숨기기",
                if (hidden) "숨겼어요. 딱 화면은 그대로 동작해요."
                else "열리는 화면에서 '알림 표시'를 끄면 상태바·알림창에서 사라지고, 딱 화면은 그대로 동작해요.",
                if (hidden) "숨김" else "열기",
            ) { Setup.openPageChannelSettings(context) }
            if (!NotiListener.available(context)) {
                LinkRow(
                    "딱 화면에 다른 앱 알림 보기",
                    "이 설치본(기본판)에는 없어요. 다른 앱 알림을 읽는 앱은 구글 Play 프로텍트가 파일 설치를 막아서, 알림판은 컴퓨터로 설치해야 해요.",
                )
            } else {
                LinkRow(
                    "딱 화면에 다른 앱 알림 보기",
                    when {
                        notiOn && notiLive -> "팀즈 같은 앱 알림이 할 일 아래에 최근 3개까지 보여요. 누르면 잠금을 풀고 그 알림을 열어요."
                        notiOn -> "연결이 끊겼어요(다른 판을 깔았다가 다시 깔면 이래요). 열리는 화면에서 다시 켜 주세요."
                        else -> "열리는 화면에서 '딱'의 알림 접근을 허용해 주세요. 알림은 이 폰 밖으로 나가지 않아요. 켜 두면 내선 앱 같은 인터넷 전화가 올 때도 수신 화면을 가리지 않아요."
                    },
                    when {
                        notiOn && notiLive -> "켜짐"
                        notiOn -> "다시 켜기"
                        else -> "켜기"
                    },
                ) { Setup.openNotiAccess(context) }
                if (notiOn) NotiApps(s)
                if (!notiOn) {
                    LinkRow(
                        "허용이 막혀 있으면",
                        "스토어가 아닌 파일로 설치한 앱은 안드로이드가 막아 둬요. 앱 정보 → 오른쪽 위 ⋮ → '제한된 설정 허용' 뒤 다시 켜 주세요.",
                        "앱 정보",
                    ) { Setup.openAppInfo(context) }
                }
            }
        }
        // 딱 화면을 쓰면 잠금화면 카드는 겹쳐서 띄우지 않으므로, 카드 방식일 때만 의미가 있습니다.
        if (Build.VERSION.SDK_INT >= 36 && !s.lockPage) {
            SwitchRow("라이브 알림으로 늘 펼쳐 보기", s.lockLive) { v -> TaskStore.updateSettings { it.copy(lockLive = v) } }
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

        Section("딱 화면 바로가기")
        ShortcutSettings(s)

        Section("알람")
        AlarmSettings(s)

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

/** 딱 화면 아래 바로가기(모양에 따라 6~9칸): 앱을 골라 넣고, 눌러서 이름을 바꾸고, ↑ 로 앞으로, ✕ 로 뺍니다. */
@Composable
private fun ShortcutSettings(s: Settings) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf(false) }
    val save = { list: List<Shortcut> -> TaskStore.updateSettings { it.copy(shortcuts = list) } }
    val slots = s.shortcutSlots()

    ChoiceRow("모양", listOf("이름 3줄", "이름 2줄", "아이콘"), s.shortcutStyle) { i -> TaskStore.updateSettings { it.copy(shortcutStyle = i) } }
    s.shortcuts.forEachIndexed { i, sc ->
        val name = remember(sc) { Shortcuts.name(context, sc) }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { renaming = i }
                .padding(start = 24.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortcutIcon(sc)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sc.name.isNotBlank()) {
                    Text(
                        Shortcuts.defaultName(context, sc) ?: "지워진 앱",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 칸이 적은 모양으로 바꾸면 뒤쪽은 저장만 되고 안 보입니다.
                if (i >= slots) {
                    Text("칸이 모자라 딱 화면엔 안 보여요", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            if (i > 0) {
                IconButton(onClick = { save(s.shortcuts.toMutableList().apply { add(i - 1, removeAt(i)) }) }) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "앞으로")
                }
            }
            IconButton(onClick = { save(s.shortcuts.filterIndexed { j, _ -> j != i }) }) {
                Icon(Icons.Default.Close, contentDescription = "빼기")
            }
        }
    }
    TextButton(
        onClick = { picking = true },
        enabled = s.shortcuts.size < slots,
        modifier = Modifier.padding(start = 12.dp),
    ) { Text(if (s.shortcuts.size < slots) "+ 바로가기 추가 (${s.shortcuts.size}/$slots)" else "${slots}칸이 다 찼어요") }
    Hint(
        "이름 3줄은 9개, 이름 2줄은 6개, 아이콘은 한 줄에 4개씩 8개까지 놓여요. 눌러서 버튼 이름을 바꿀 수 있어요(비우면 앱 이름). " +
            "'할 일 추가'를 넣으면 위쪽 할 일 추가 버튼은 숨겨요.",
    )

    renaming?.let { i ->
        val sc = s.shortcuts.getOrNull(i) ?: return@let
        var text by remember(i) { mutableStateOf(sc.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("버튼 이름") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(12) },
                    singleLine = true,
                    placeholder = { Text(Shortcuts.defaultName(context, sc).orEmpty()) },
                    supportingText = { Text("비우면 앱 이름을 써요") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    save(s.shortcuts.toMutableList().also { it[i] = sc.copy(name = text.trim()) })
                    renaming = null
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("취소") } },
        )
    }

    if (picking) {
        val apps = remember { Shortcuts.installedApps(context) }
        var query by remember { mutableStateOf("") }
        val present = s.shortcuts.map { it.id }.toSet()
        val choices = (listOf(Shortcut(Shortcut.ADD) to "할 일 추가", Shortcut(Shortcut.CALC) to "계산기", Shortcut(Shortcut.ALARM) to "알람") + apps)
            .filter { (sc, label) -> sc.id !in present && label.contains(query.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("바로가기 추가") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("앱 이름으로 찾기") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(choices, key = { it.first.id }) { (sc, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        save(s.shortcuts + sc)
                                        picking = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ShortcutIcon(sc)
                                Spacer(Modifier.width(14.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("닫기") } },
        )
    }
}

/** 딱 화면에 알림을 보여 줄 앱 고르기. 아무것도 안 고르면 모든 앱. */
@Composable
private fun NotiApps(s: Settings) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val names = remember(s.notiApps) {
        val pm = context.packageManager
        s.notiApps.map { pkg -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }.sorted()
    }
    LinkRow(
        "알림 볼 앱",
        if (names.isEmpty()) "모든 앱 알림을 보여요. 메시지·팀즈처럼 몇 개만 고를 수도 있어요." else names.joinToString(", "),
        "고르기",
    ) { picking = true }

    if (picking) {
        // 고른 앱이 맨 위, 나머지는 이름순 (런처에 보이는 앱만)
        val apps = remember {
            Shortcuts.installedApps(context)
                .mapNotNull { (sc, label) -> Shortcuts.component(sc)?.packageName?.let { it to label } }
                .distinctBy { it.first }
                .sortedByDescending { it.first in s.notiApps }
        }
        var chosen by remember { mutableStateOf(s.notiApps) }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("알림 볼 앱") },
            text = {
                Column {
                    Text(
                        "아무것도 고르지 않으면 모든 앱 알림을 보여요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("앱 이름으로 찾기") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(apps.filter { it.second.contains(query.trim(), ignoreCase = true) }, key = { it.first }) { (pkg, label) ->
                            val icon = remember(pkg) { NotiListener.icon(context, pkg) }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { chosen = if (pkg in chosen) chosen - pkg else chosen + pkg }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = pkg in chosen, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(28.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // 지워진 앱은 목록에 없어서 풀 수도 없으니 저장할 때 뺍니다.
                    val installed = apps.map { it.first }.toSet()
                    TaskStore.updateSettings { it.copy(notiApps = chosen intersect installed) }
                    picking = false
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun ShortcutIcon(sc: Shortcut) {
    val context = LocalContext.current
    val icon = remember(sc.id) { Shortcuts.icon(context, sc) }
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(28.dp))
    } else {
        Icon(
            painterResource(Shortcuts.builtInIcon(sc)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** 딱 화면의 '알람'이 울리는 방식: 알람음, 크기, 소리/진동, 무음 모드에서도 소리. */
@Composable
private fun AlarmSettings(s: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ringing by AlarmRingService.ringing.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            ?: return@rememberLauncherForActivityResult
        val isDefault = uri == AlarmSound.defaultUri() || uri == android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        TaskStore.updateSettings { it.copy(alarmSound = if (isDefault) null else uri.toString()) }
    }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    if (!remember(tick) { Setup.fullScreenAllowed(context) }) {
        LinkRow(
            "알람 화면이 꺼져 있어요",
            "화면이 꺼진 채 알람이 울리면 소리와 알림만 나와요. 열리는 화면에서 '딱'을 허용해 주세요.",
            "허용",
        ) { Setup.openFullScreenSettings(context) }
    }
    val soundName = remember(s.alarmSound) {
        s.alarmSound?.let { runCatching { RingtoneManager.getRingtone(context, Uri.parse(it))?.getTitle(context) }.getOrNull() }
            ?: "기본 알람음"
    }
    var preview by remember { mutableStateOf<Job?>(null) }
    // 미리 듣기 중에 진짜 알람이 울리기 시작했으면 그 소리는 끄지 않습니다.
    val stopPreview = { if (AlarmRingService.ringing.value == null) AlarmSound.stop(context) }
    DisposableEffect(Unit) {
        onDispose { if (preview?.isActive == true) stopPreview() }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                pick.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "알람음")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
                        .putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            s.alarmSound?.let(Uri::parse) ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                        ),
                )
            }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("알람음", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(soundName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }

    var volume by remember(s.alarmVolume) { mutableFloatStateOf(s.alarmVolume.toFloat()) }
    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("크기", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { TaskStore.updateSettings { it.copy(alarmVolume = volume.roundToInt()) } },
            valueRange = 1f..10f,
            steps = 8,
            enabled = s.alarmMode != 2,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text("${volume.roundToInt()}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(24.dp))
    }
    ChoiceRow("방식", listOf("소리+진동", "소리만", "진동만"), s.alarmMode) { i ->
        TaskStore.updateSettings { it.copy(alarmMode = i) }
    }
    SwitchRow("무음·진동 모드여도 소리로 울리기", s.alarmAlwaysSound) { v ->
        TaskStore.updateSettings { it.copy(alarmAlwaysSound = v) }
    }
    TextButton(
        onClick = {
            preview?.cancel()
            preview = scope.launch {
                AlarmSound.start(context, TaskStore.settings.value)
                try {
                    delay(3_000)
                } finally {
                    stopPreview()
                }
            }
        },
        // 진짜 알람이 울리는 중엔 미리 듣기가 그 소리를 끄지 않게
        enabled = ringing == null,
        modifier = Modifier.padding(start = 12.dp),
    ) { Text("들어 보기 (3초)") }
    Hint(
        "딱 화면의 '알람' 버튼으로 몇 분 뒤에 울릴지 맞춰요. 화면이 꺼져 있어도 정시에 울리고, 끄기를 누를 때까지" +
            "(최대 10분) 울려요. 울리는 동안만 폰의 알람 음량을 위 크기로 맞췄다가 되돌려요.",
    )
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

/**
 * 잠금화면 글자 크기·줄 간격. 아래에 딱 화면 모양 예시가 있어 움직이는 대로 바로 보입니다.
 * 저장(카드·위젯 다시 그리기)은 손을 뗄 때 한 번만.
 */
@Composable
private fun CardLook(s: Settings) {
    var sp by remember(s.cardSp) { mutableFloatStateOf(s.cardSp.toFloat()) }
    var gap by remember(s.rowGap) { mutableFloatStateOf(s.rowGap.toFloat()) }
    // 예시가 커지면 그 아래가 밀리므로, 끄는 동안 손가락 밑이 움직이지 않게 슬라이더를 위에 둡니다.
    SliderRow("글자 크기", sp, CardSpRange, { sp = it }) { TaskStore.updateSettings { it.copy(cardSp = sp.roundToInt()) } }
    SliderRow("줄 간격", gap, RowGapRange, { gap = it }) { TaskStore.updateSettings { it.copy(rowGap = gap.roundToInt()) } }
    Surface(
        color = Color(0xFF3C4454),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Surface(
            color = Glass,
            contentColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            border = GlassEdge,
            modifier = Modifier.padding(12.dp),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                listOf("오전 투약 확인", "인계 준비", "수액 교체").forEach { title ->
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val circle = @Composable {
                            Box(Modifier.size(48.dp, circleBoxHeight(gap.roundToInt())), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(26.dp).border(2.dp, Color.White.copy(alpha = 0.75f), CircleShape))
                            }
                        }
                        if (s.checkOnLeft) circle()
                        Text(
                            title,
                            fontSize = sp.roundToInt().sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp, vertical = gap.roundToInt().dp),
                        )
                        if (!s.checkOnLeft) circle()
                    }
                }
            }
        }
    }
    Text(
        "글자 크기는 잠금화면 카드·홈 위젯에도 같이 적용돼요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp),
    )
}

/** 1씩 움직이는 슬라이더 한 줄. 오른쪽에 지금 값. */
@Composable
private fun SliderRow(label: String, value: Float, range: IntRange, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(72.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            // 칸이 많아 눈금 점이 빽빽하게 깔리므로 숨깁니다.
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text("${value.roundToInt()}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(28.dp))
    }
}

/** 누르면 시스템 화면을 여는 줄: 제목, 설명, 오른쪽에 상태·동작. [onClick] 이 없으면 안내만. */
@Composable
private fun LinkRow(title: String, body: String, action: String = "", onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            action,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
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
