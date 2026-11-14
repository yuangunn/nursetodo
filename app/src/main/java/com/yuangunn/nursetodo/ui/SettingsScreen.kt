package com.yuangunn.nursetodo.ui

import com.yuangunn.nursetodo.data.repeatLines
import com.yuangunn.nursetodo.data.splitPresetItems
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.yuangunn.nursetodo.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Preset
import com.yuangunn.nursetodo.data.Settings
import com.yuangunn.nursetodo.data.PresetShare
import com.yuangunn.nursetodo.data.Shortcut
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.currentShift
import com.yuangunn.nursetodo.data.isSubLine
import com.yuangunn.nursetodo.data.itemMinute
import com.yuangunn.nursetodo.data.itemText
import com.yuangunn.nursetodo.data.keywordList
import com.yuangunn.nursetodo.data.presetRows
import com.yuangunn.nursetodo.data.shortcutSlots
import com.yuangunn.nursetodo.lock.AlarmRingService
import com.yuangunn.nursetodo.lock.AlarmSound
import com.yuangunn.nursetodo.lock.KeywordAlert
import com.yuangunn.nursetodo.lock.NotiListener
import com.yuangunn.nursetodo.lock.Setup
import com.yuangunn.nursetodo.lock.Shortcuts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.roundToInt

private val PageNames = listOf("할 일", "근무", "포켓북")

/** 잠금화면에 실제로 보일 페이지 이름: 근무는 교대 프리셋을 켰을 때만(잠금화면도 그때만 근무 페이지를 둠). 하나도 없으면 할 일. */
private fun shownPages(s: Settings): List<String> =
    PageNames.filterIndexed { i, _ -> s.pageMask and (1 shl i) != 0 && (i != 1 || s.presetMode) }.ifEmpty { listOf(PageNames[0]) }

/** 설정 하위 화면. 첫 화면 줄 이름과 하위 화면 제목이 같습니다. */
private enum class SettingsPage(val title: String) {
    LOCK("잠금화면"),
    SHORTCUTS("바로가기"),
    NOTI("다른 앱 알림"),
    SETUP("권한 점검"),
    PRESET("교대 프리셋"),
    KEYWORD("키워드 알림"),
    ALARM("알람"),
    CALC("항암계산기"),
    LOOK("테마·강조색"),
}

private val AlarmModes = listOf("소리+진동", "소리만", "진동만")

/**
 * 설정: 첫 화면은 묶음(잠금화면 · 근무 · 도구 · 화면 · 기타)마다 아이콘 · 이름 · 지금 값 줄([NavRow])이고, 누르면 하위 화면.
 * 한 화면에 줄이 10~15개를 넘지 않게 나누고, 긴 설명은 하위 화면 아래 짧은 설명(1~2줄)으로 둡니다(Android·AOSP 설정 가이드).
 * 하위 화면은 위 막대(뒤로 · 제목) 아래 묶음 카드(모서리 16, 줄 48). 값을 누르면 아래 고르기 시트.
 * 말투는 안내가 ~요, 안전 문구가 ~하세요.
 */
@Composable
fun SettingsScreen(home: Int = 0) {
    val s by TaskStore.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf("") }
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // [home] 이 늘면 첫 화면으로. 다른 탭에 다녀와 다시 그릴 때는 본 값과 같아 그대로 둠.
    // 본 값은 상태가 아닌 배열이라 읽은 뒤 바꿔도 다시 그리기를 부르지 않음(page 는 아래에서 읽으므로 앞쪽 쓰기)
    val seenHome = rememberSaveable { intArrayOf(home) }
    if (home != seenHome[0]) {
        seenHome[0] = home
        page = null
    }
    BackHandler(enabled = page != null) { page = null }
    // 라이트는 칸 색 바탕 위에 흰 묶음, 다크는 검정 바탕 위에 #1C1C1E 묶음
    val bg = if (s.isDark()) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainer
    AnimatedContent(
        page,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally(tween(250)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(250))) togetherWith
                (slideOutHorizontally(tween(200)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(150)))
        },
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        label = "settingsPage",
    ) { p ->
        if (p == null) {
            SettingsHome(s, open = { page = it }, onSheet = { sheet = it })
        } else {
            SettingsSub(p.title, onBack = { page = null }) {
                when (p) {
                    SettingsPage.LOCK -> LockSettings(s) { sheet = it }
                    SettingsPage.SHORTCUTS -> Group("보이는 바로가기", count = "${s.shortcuts.size}/${s.shortcutSlots()}", foot = "길게 눌러 끌면 순서가 바뀌고, 누르면 버튼 이름을 바꿔요(비우면 앱 이름).") {
                        ShortcutSettings(s) { sheet = "shape" }
                    }
                    SettingsPage.NOTI -> NotiSettings(s)
                    SettingsPage.SETUP -> SetupGuide(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp), showTitle = false)
                    SettingsPage.PRESET -> Group("", foot = "교대를 받으면 그 근무 할 일로 바뀌어요. '07:00 정규라운딩'처럼 시각을 쓴 줄은 받은 폰에서만 그 시각에 알려요.") {
                        PresetSettings(s)
                    }
                    SettingsPage.KEYWORD -> Group("", foot = "고른 앱 알림에 키워드가 있으면 화면을 켜고 진동해요. " + if (s.presetMode) "교대 프리셋을 쓰니 교대를 받은 폰에서만 그 근무 동안 알려요." else "교대 프리셋을 안 쓰니 켜 둔 동안 늘 알려요.") {
                        KeywordSettings(s) { sheet = it }
                    }
                    SettingsPage.ALARM -> Group("", foot = "할 일 화면 '알람'으로 맞춘 알람이에요. 화면이 꺼져 있어도 정시에 울리고, 끌 때까지(최대 10분) 울려요.") {
                        AlarmSettings(s)
                    }
                    SettingsPage.CALC -> Group("", foot = "기관·펌프 기준으로 정하세요. 넘으면 계산 결과가 주황으로 바뀌어요.") {
                        ValueRow("주입속도 상한", "${s.rateLimit} cc/hr") { sheet = "rate" }
                    }
                    SettingsPage.LOOK -> {
                        Group("", foot = "잠금화면은 늘 어두워요. 다크로 두면 잠금을 풀 때 눈부시지 않아요.") {
                            ValueRow("테마", listOf("시스템 따라감", "라이트", "다크")[s.theme]) { sheet = "theme" }
                        }
                        AccentPicker(s)
                    }
                }
            }
        }
    }

    val close = { sheet = "" }
    when (sheet) {
        "pages" -> PickSheet(
            "보일 페이지", "잠금화면에서 좌우로 넘기는 페이지예요. 하나 이상 골라야 해요.", PageNames,
            PageNames.indices.filter { s.pageMask and (1 shl it) != 0 && (it != 1 || s.presetMode) }.toSet(), multi = true, onDismiss = close,
            disabled = if (s.presetMode) emptyMap() else mapOf(1 to "교대 프리셋을 켜야 보여요"),
        ) { i ->
            val next = s.pageMask xor (1 shl i)
            if (next != 0) TaskStore.updateSettings { it.copy(pageMask = next) }
        }
        "addPos" -> PickSheet("할 일 추가 버튼", "", listOf("카드 아래", "카드 안"), setOf(s.addButton), onDismiss = close) { i ->
            TaskStore.updateSettings { it.copy(addButton = i) }
            close()
        }
        "shape" -> PickSheet("바로가기 모양", "", listOf("한 줄 3개", "한 줄 2개", "아이콘만"), setOf(s.shortcutStyle), onDismiss = close) { i ->
            TaskStore.updateSettings { it.copy(shortcutStyle = i) }
            close()
        }
        "theme" -> PickSheet("테마", "잠금화면은 늘 어두워요. 다크로 두면 잠금을 풀 때 눈부시지 않아요.", listOf("시스템 따라감", "라이트", "다크"), setOf(s.theme), onDismiss = close) { i ->
            TaskStore.updateSettings { it.copy(theme = i) }
            close()
        }
        "newOnTop" -> PickSheet("새 할 일 위치", "앱에서 적은 할 일이 들어갈 자리예요. 잠금화면에서 적은 것은 늘 맨 위예요.", listOf("맨 아래", "맨 위"), setOf(if (s.newOnTop) 1 else 0), onDismiss = close) { i ->
            TaskStore.updateSettings { it.copy(newOnTop = i == 1) }
            close()
        }
        "kwSound" -> PickSheet("소리·진동", "소리와 진동을 고르면 휴대폰 알림 설정이 열려요. 거기서 소리를 고르세요.", listOf("진동만", "소리와 진동"), setOf(if (s.keywordSound) 1 else 0), onDismiss = close) { i ->
            TaskStore.updateSettings { it.copy(keywordSound = i == 1) }
            close()
            if (i == 1) Setup.openChannelSettings(context, KeywordAlert.SOUND_CHANNEL_ID)
        }
        "rate" -> {
            var text by remember { mutableStateOf(s.rateLimit.toString()) }
            val save = {
                text.toIntOrNull()?.takeIf { it > 0 }?.let { v -> TaskStore.updateSettings { it.copy(rateLimit = v) } }
                close()
            }
            PickSheet(
                "주입속도 상한", "기관·펌프 기준으로 정하세요. 넘으면 계산 결과가 주황으로 바뀌어요.", emptyList(), emptySet(),
                onDismiss = save, onPick = {}, done = true,
                extra = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter(Char::isDigit).take(5) },
                        singleLine = true,
                        suffix = { Text("cc/hr") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                },
            )
        }
    }
}

/** 설정 첫 화면: 묶음마다 아이콘 · 이름 · 지금 값. 권한 점검은 꼭 필요한데 안 한 단계가 있으면 주황으로 'N개 남음'. */
@Composable
private fun SettingsHome(s: Settings, open: (SettingsPage) -> Unit, onSheet: (String) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val left = remember(tick, s) { setupLeft(context, s) }
    val notiAvailable = remember { NotiListener.available(context) }
    val notiOn = remember(tick) { NotiListener.allowed(context) && NotiListener.connected }
    val icon = @Composable { v: ImageVector -> Icon(v, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    val res = @Composable { id: Int -> Icon(painterResource(id), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Text("설정", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        Group("잠금화면") {
            NavRow("잠금화면", lockSummary(s), icon = { icon(Icons.Outlined.Lock) }) { open(SettingsPage.LOCK) }
            NavRow("바로가기", remember(s.shortcuts) { s.shortcuts.joinToString(" · ") { Shortcuts.name(context, it) }.ifEmpty { "없음" } }, icon = { icon(Icons.AutoMirrored.Outlined.List) }) {
                open(SettingsPage.SHORTCUTS)
            }
            if (notiAvailable) {
                NavRow(
                    "다른 앱 알림",
                    when {
                        !notiOn -> "꺼짐"
                        s.notiApps.isEmpty() -> "모든 앱"
                        else -> "앱 ${s.notiApps.size}개"
                    },
                    icon = { icon(Icons.Outlined.Notifications) },
                ) { open(SettingsPage.NOTI) }
            }
            NavRow(
                "권한 점검",
                if (left > 0) "${left}개 남음" else "모두 켜짐",
                warn = left > 0,
                icon = {
                    if (left > 0) Icon(Icons.Outlined.Warning, contentDescription = null, tint = Warn) else icon(Icons.Outlined.CheckCircle)
                },
            ) { open(SettingsPage.SETUP) }
        }
        Group("근무") {
            NavRow(
                "교대 프리셋",
                if (!s.presetMode) "꺼짐" else s.presets.joinToString(" · ") { it.name }.ifEmpty { "프리셋 없음" } + if (s.routineAlarm) " · 루틴 알림" else "",
                icon = { icon(Icons.Outlined.Refresh) },
            ) { open(SettingsPage.PRESET) }
            if (notiAvailable) {
                NavRow(
                    "키워드 알림",
                    when {
                        !s.keywordOn -> "꺼짐"
                        s.keywordList.isEmpty() -> "키워드 없음"
                        else -> "${s.keywordList.size}개 · " + (if (s.keywordSound) "소리와 진동" else "진동만") + if (s.presetMode) " · 근무 받은 동안" else " · 늘"
                    },
                    icon = { icon(Icons.Outlined.Search) },
                ) { open(SettingsPage.KEYWORD) }
            }
        }
        Group("도구") {
            NavRow(
                "알람",
                if (s.alarmMode == 2) AlarmModes[2] else "크기 ${s.alarmVolume} · ${AlarmModes[s.alarmMode.coerceIn(0, 2)]}",
                icon = { res(R.drawable.ic_alarm) },
            ) { open(SettingsPage.ALARM) }
            NavRow("항암계산기", "주입속도 상한 ${s.rateLimit} cc/hr", icon = { res(R.drawable.ic_calc) }) { open(SettingsPage.CALC) }
        }
        Group("화면") {
            val accent = (if (s.isDark()) AccentDark else AccentLight)[s.accent.coerceIn(AccentNames.indices)]
            NavRow(
                "테마·강조색",
                listOf("시스템 따라감", "라이트", "다크")[s.theme] + " · " + AccentNames[s.accent.coerceIn(AccentNames.indices)],
                icon = { Box(Modifier.size(20.dp).background(accent, CircleShape)) },
            ) { open(SettingsPage.LOOK) }
            NavRow("새 할 일 위치", if (s.newOnTop) "맨 위" else "맨 아래", icon = { icon(Icons.Outlined.Add) }) { onSheet("newOnTop") }
        }
        Group("기타", foot = "데이터는 이 폰에만 저장돼요. 인터넷 권한이 없어 밖으로 나가지 않아요.") {
            NavRow("처음 안내 다시 보기", "쓸 기능과 권한을 차례로 다시 골라요", chevron = false, icon = { icon(Icons.Outlined.PlayArrow) }) {
                TaskStore.updateSettings { it.copy(onboarded = false) }
            }
            NavRow("지난 완료 항목 정리", "어제까지 끝낸 할 일을 지워요. 루틴은 남겨요", chevron = false, icon = { icon(Icons.Outlined.Delete) }) {
                val n = TaskStore.clearOldDone()
                Toast.makeText(context, if (n > 0) "${n}개 정리했어요" else "정리할 항목이 없어요", Toast.LENGTH_SHORT).show()
            }
            NavRow("버전", remember { appVersion(context) }, chevron = false, icon = { icon(Icons.Outlined.Info) }, onClick = null)
        }
    }
}

private val Warn = Color(0xFFFF9F0A)

/** '2.6.1 · 알림판': versionName 의 판 꼬리(-noti)는 떼고 판 이름으로. */
private fun appVersion(context: Context): String {
    val name = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty().removeSuffix("-noti")
    return name + if (NotiListener.available(context)) " · 알림판" else " · 기본판"
}

/** 잠금화면 줄의 지금 값: 꺼짐 / 할 일 화면 · 보일 페이지 / 잠금화면 카드(라이브 알림). */
private fun lockSummary(s: Settings): String = when {
    !s.lockEnabled -> "꺼짐"
    s.lockPage -> "할 일 화면 · " + shownPages(s).joinToString(", ")
    Build.VERSION.SDK_INT >= 36 && s.lockLive -> "잠금화면 카드 · 라이브 알림"
    else -> "잠금화면 카드"
}

/**
 * 첫 화면 줄(최소 64): 아이콘 칸 24 · 이름(17/24) · 지금 값(13/18, [warn] 이면 주황) · 하위 화면이면 오른쪽 ›.
 * 줄 사이 선은 이름 글자 앞(16 + 24 + 16)부터.
 */
@Composable
private fun NavRow(title: String, summary: String, warn: Boolean = false, chevron: Boolean = true, icon: @Composable () -> Unit, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .rowLine(start = 56.dp)
            .heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warn) Warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (chevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** 하위 화면 틀: 위 막대 56(뒤로 48 · 제목 17/24 600) 아래 스크롤. 뒤로 가기·시스템 뒤로 모두 첫 화면으로. */
@Composable
private fun SettingsSub(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            content = content,
        )
    }
}

/**
 * 잠금화면: 대표 스위치(잠금화면에 할 일 보이기)를 끄면 딸린 설정은 숨깁니다.
 * 켜져 있으면 보이는 방식(할 일 화면 / 잠금화면 카드)과 그 방식의 설정.
 */
@Composable
private fun LockSettings(s: Settings, onSheet: (String) -> Unit) {
    val context = LocalContext.current
    Group("") {
        SwitchSetRow("잠금화면에 할 일 보이기", s.lockEnabled) { v -> TaskStore.updateSettings { it.copy(lockEnabled = v) } }
    }
    if (!s.lockEnabled) return
    Group(
        "보이는 방식",
        foot = when {
            s.lockPage -> "화면을 켜면 잠금 위에 할 일 화면이 먼저 떠요."
            // 라이브 알림은 글자만 보이는 알림이라 줄마다 누를 수 없고 알림 아래 ✓ 버튼(남은 할 일 위 3개)으로 체크
            s.lockLive && Build.VERSION.SDK_INT >= 36 -> "잠금화면 알림으로 늘 펼쳐 보여요. 알림 아래 ✓ 버튼(위에서 3개)으로 잠금을 풀지 않고 체크해요."
            else -> "잠금화면 알림 카드로 보여요. 줄을 누르면 잠금을 풀지 않고 체크해요."
        },
    ) {
        SwitchSetRow("화면 켜면 할 일 먼저", s.lockPage) { v ->
            TaskStore.updateSettings { it.copy(lockPage = v) }
            // 백그라운드에서 화면을 띄우려면 '다른 앱 위에 표시'를 허용해야 합니다.
            if (v) Setup.requestOverlay(context)
        }
        if (s.lockPage) {
            ValueRow("보일 페이지", shownPages(s).joinToString(" · ")) { onSheet("pages") }
            if (s.shortcuts.take(s.shortcutSlots()).any { it.id == Shortcut.ADD }) {
                SetRow("할 일 추가 버튼", "아래 바로가기의 '할 일 추가'를 써요")
            } else {
                ValueRow("할 일 추가 버튼", listOf("카드 아래", "카드 안")[s.addButton.coerceIn(0, 1)]) { onSheet("addPos") }
            }
        } else if (Build.VERSION.SDK_INT >= 36) {
            SwitchSetRow("라이브 알림으로 늘 펼쳐 보기", s.lockLive) { v -> TaskStore.updateSettings { it.copy(lockLive = v) } }
        }
    }
    if (s.lockPage) {
        // 안드로이드가 상시 서비스에 알림 하나를 꼭 요구해서 앱이 아이콘을 직접 지울 수는 없습니다.
        var tick by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
        val hidden = remember(tick) { Setup.pageIconHidden(context) }
        Group("") {
            LinkRow("상태바 아이콘 숨기기", "열리는 화면에서 '알림 표시'를 끄면 사라져요", if (hidden) "숨김" else "") { Setup.openChannelSettings(context) }
        }
    }
    if (!NotiListener.available(context)) {
        Group("", foot = "다른 앱 알림을 읽는 앱은 구글 Play 프로텍트가 파일 설치를 막아서, 알림판은 Play 스토어로 받아야 해요.") {
            LinkRow("다른 앱 알림 보기", "이 설치본(기본판)에는 없어요")
        }
    }
}

/** 다른 앱 알림(알림판): 알림 접근 상태 · 볼 앱. 허용이 막혔으면 '제한된 설정' 안내. */
@Composable
private fun NotiSettings(s: Settings) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notiOn = remember(tick) { NotiListener.allowed(context) }
    val notiLive = remember(tick) { NotiListener.connected }
    Group(
        "",
        foot = "할 일 페이지 오른쪽, 근무·포켓북 페이지 위 알림 버튼에 앱 아이콘이 보여요. 알림은 이 폰 밖으로 나가지 않아요.",
    ) {
        LinkRow(
            "알림 접근",
            when {
                notiOn && notiLive -> ""
                notiOn -> "연결이 끊겼어요. 열리는 화면에서 다시 켜 주세요"
                else -> "열리는 화면에서 '널스투두'를 허용해 주세요"
            },
            when {
                notiOn && notiLive -> "켜짐"
                notiOn -> "다시 켜기"
                else -> "꺼짐"
            },
        ) { Setup.openNotiAccess(context) }
        if (notiOn) {
            AppPicker("알림 볼 앱", s.notiApps, "아무것도 안 고르면 모든 앱 알림을 보여요.", emptyValue = "모든 앱") { c -> TaskStore.updateSettings { it.copy(notiApps = c) } }
        } else {
            LinkRow("허용이 막혀 있으면", "앱 정보 › 오른쪽 위 ⋮ › '제한된 설정 허용' 뒤 다시 켜 주세요", "앱 정보") { Setup.openAppInfo(context) }
        }
    }
}

/** 강조색 8가지: 동그라미 24(누르는 칸 44), 고른 색은 2 띄우고 2 테두리. 고른 이름은 묶음 이름 줄 오른쪽. */
@Composable
private fun AccentPicker(s: Settings) {
    val colors = if (s.isDark()) AccentDark else AccentLight
    Group("강조색", count = AccentNames[s.accent.coerceIn(AccentNames.indices)], foot = "코랄·오렌지·핑크는 끝낸 체크가 미체크 빨강과 비슷해 보여요.") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            colors.forEachIndexed { i, c ->
                val on = s.accent == i
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clickable(role = Role.RadioButton) { TaskStore.updateSettings { it.copy(accent = i) } }
                        .semantics { contentDescription = AccentNames[i] + if (on) ", 고름" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (on) 32.dp else 24.dp)
                            .then(if (on) Modifier.border(2.dp, c, CircleShape).padding(4.dp) else Modifier)
                            .background(c, CircleShape),
                    )
                }
            }
        }
    }
}

/** 교대 프리셋: 켜기, 루틴 알림, 프리셋마다 근무 시작 시각·할 일 수(지금 목록이면 '지금'). 누르면 전체 화면에서 고칩니다. */
@Composable
private fun PresetSettings(s: Settings) {
    var editing by remember { mutableStateOf<Preset?>(null) }
    SwitchSetRow("교대 프리셋 사용", s.presetMode) { TaskStore.setPresetMode(it) }
    if (s.presetMode) {
        SwitchSetRow("근무 루틴 알림", s.routineAlarm) { v -> TaskStore.updateSettings { it.copy(routineAlarm = v) } }
    }
    s.presets.forEach { p ->
        ValueRow(
            p.name,
            "${p.start?.let(::clock) ?: "시각 없음"} · ${presetRows(p.items).size}개",
            badge = if (s.presetMode && p.id == s.presetLoaded) "지금" else "",
        ) { editing = p }
    }
    AddRow("프리셋 추가") { editing = Preset(System.currentTimeMillis(), "", null, emptyList()) }
    // 다른 사람과 주고받기: 글로 보내고(카톡·게시판에 붙여도 읽힘), 복사한 글이나 공유하기로 받은 글을 읽어 옴
    val context = LocalContext.current
    var importing by remember { mutableStateOf<List<Preset>?>(null) }
    var sharing by remember { mutableStateOf(false) }
    SetRow("다른 사람과 공유하기", "프리셋을 글로 보내요. 팀즈 채널·단톡방에 올려도 읽혀요.", onClick = { sharing = true })
    SetRow("붙여넣어 가져오기", "복사한 프리셋 글을 읽어 와요.", onClick = {
        val got = PresetShare.parse(clipboardText(context))
        if (got == null) Toast.makeText(context, "복사한 글에서 교대 프리셋을 찾지 못했어요", Toast.LENGTH_SHORT).show() else importing = got
    })
    if (sharing) PresetShareDialog(s.presets) { sharing = false }
    importing?.let { list ->
        PresetImportDialog(list) { msg ->
            importing = null
            msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }
    editing?.let { p -> PresetEditor(p, s, onDone = { editing = it }) }
}

/**
 * 프리셋 고치기(전체 화면): 위 막대(닫기 · 제목 · 저장), 이름, 근무 시작 시각, 할 일 두 칸 —
 * 시간별 할 일(앞에 시각, 그 시각에 알림 · 반복해서 넣기 · 예시)과 이번 근무 할 일(시각 없음). 한 줄에 하나, 두 칸 들여 쓰면 하위 항목.
 * 저장 형식은 한 목록 그대로(시각 유무로 갈림)라 다른 칸에 쓴 줄은 다시 열 때 제 칸으로 갑니다(칸 아래에 미리 알림).
 * 시각 쓴 줄마다 근무 루틴 알림 켜고 끄기. 아래에 복제 · 삭제 · 지금 불러오기.
 */
@Composable
private fun PresetEditor(p: Preset, s: Settings, onDone: (Preset?) -> Unit) {
    val context = LocalContext.current
    var name by remember(p.id) { mutableStateOf(p.name) }
    var start by remember(p.id) { mutableStateOf(p.start) }
    val split = remember(p.id) { splitPresetItems(p.items) }
    var timedText by remember(p.id) { mutableStateOf(split.first.joinToString("\n")) }
    var otherText by remember(p.id) { mutableStateOf(split.second.joinToString("\n")) }
    var quiet by remember(p.id) { mutableStateOf(p.quiet) }
    var repeating by remember(p.id) { mutableStateOf(false) }
    // 두 칸 들여 쓴 줄(하위 항목)은 들여쓰기를 남깁니다. 저장은 시간별 다음 근무 할 일 순서.
    val timedLines = timedText.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    val otherLines = otherText.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    val lines = timedLines + otherLines
    var deleting by remember(p.id) { mutableStateOf(false) }
    val isNew = s.presets.none { it.id == p.id }
    val edited = { Preset(p.id, name.trim().ifEmpty { "프리셋" }, start, lines, quiet.filter { it in lines }.toSet()) }
    val withEdits = { if (isNew) s.presets + edited() else s.presets.map { if (it.id == p.id) edited() else it } }
    FullScreen(onClose = { onDone(null) }) {
        TopBar(if (isNew) "프리셋 추가" else "${name.ifBlank { "프리셋" }} 프리셋 고치기", onClose = { onDone(null) }) {
            FillButton("저장", {
                TaskStore.savePresets(withEdits(), changed = p.id)
                onDone(null)
            })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("근무 시작 시각", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Box(
                        Modifier
                            .heightIn(min = 48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                            .clickable {
                                val t = start ?: (7 * 60)
                                TimePickerDialog(context, { _, h, m -> start = h * 60 + m }, t / 60, t % 60, true).apply {
                                    setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "없음") { _, _ -> start = null }
                                }.show()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(start?.let(::clock) ?: "없음", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                // 시간별 할 일: 그 시각에 알림, 30분 넘게 안 하면 미체크
                Column(Modifier.padding(top = 8.dp)) {
                    Text("시간별 할 일", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("앞에 08:00처럼 시각을 써요. 그 시각에 알리고, 지나면 미체크로 보여요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                    OutlinedTextField(
                        value = timedText,
                        onValueChange = { timedText = it },
                        placeholder = { Text("07:00 정규라운딩\n07:00 혈당측정, V/S CHECK\n09:00 정규라운딩") },
                        supportingText = { Text("한 줄에 하나, 두 칸 들여 쓰면 위 줄의 하위 항목이에요.") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val noTime = timedLines.count { !isSubLine(it) && itemMinute(it) == null }
                    if (noTime > 0) {
                        Text("시각이 없는 줄 ${noTime}개는 저장하면 '이번 근무 할 일'로 가요.", style = MaterialTheme.typography.bodySmall, color = warningColor(s.isDark()), modifier = Modifier.padding(start = 4.dp))
                    }
                    // 글 버튼 안쪽 여백(12)만큼 당겨 글자를 위 글과 같은 왼쪽 끝에
                    Row(Modifier.offset(x = (-12).dp)) {
                        TextButton(onClick = { repeating = true }) { Text("반복해서 넣기") }
                        // 예시는 지운 것 없이 맨 아래에 붙입니다.
                        // 이 근무에 맞는 예시를 두 칸 모두에. 이미 있는 줄은 빼고 뒤에 붙입니다(지운 것 없음).
                        val ex = Preset.exampleFor(Preset(p.id, name, start, emptyList()))
                        TextButton(onClick = {
                            val (t, o) = Preset.Examples.getValue(ex)
                            timedText = (timedLines + t.filter { it !in timedLines }).joinToString("\n")
                            otherText = (otherLines + o.filter { it !in otherLines }).joinToString("\n")
                        }) { Text("예시 불러오기 ($ex)") }
                    }
                }
                // 이번 근무 할 일: 시간과 상관없이 근무마다 하는 일
                Column {
                    Text("이번 근무 할 일", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("시간과 상관없이 이번 근무에 할 일이에요. 알림 없이 체크만 해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                    OutlinedTextField(
                        value = otherText,
                        onValueChange = { otherText = it },
                        placeholder = { Text("루틴 차팅\n시계열\n처치물품fee") },
                        supportingText = { Text("한 줄에 하나, 두 칸 들여 쓰면 위 줄의 하위 항목이에요.") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val withTime = otherLines.count { !isSubLine(it) && itemMinute(it) != null }
                    if (withTime > 0) {
                        Text("시각을 쓴 줄 ${withTime}개는 저장하면 '시간별 할 일'로 가요.", style = MaterialTheme.typography.bodySmall, color = warningColor(s.isDark()), modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (repeating) {
                    RepeatDialog(defaultStart = start ?: (8 * 60), onDismiss = { repeating = false }) { added ->
                        timedText = (timedLines + added).joinToString("\n")
                        repeating = false
                    }
                }
            }
            // 시각 붙은 줄마다 근무 루틴 알림을 따로 끄고 켭니다.
            val timed = lines.filter { !isSubLine(it) && itemMinute(it) != null }
            if (timed.isNotEmpty()) {
                Text("근무 루틴 알림", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp))
                Text("시각을 쓴 줄만 알려요. 끄면 목록에만 남아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                timed.forEach { line ->
                    Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(itemMinute(line)?.let(::clock) ?: "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(40.dp))
                        Text(itemText(line), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        AppSwitch(line !in quiet) { on -> quiet = if (on) quiet - line else quiet + line }
                    }
                }
            }
            Row(Modifier.padding(top = 16.dp, bottom = 16.dp).offset(x = (-12).dp)) {
                if (!isNew) {
                    // 고친 내용을 저장하고, 시각 없는 복사본을 만들어 바로 엽니다.
                    TextButton(onClick = {
                        val copy = edited().copy(id = System.currentTimeMillis(), name = "${edited().name} 복사", start = null)
                        TaskStore.savePresets(withEdits() + copy, changed = p.id)
                        onDone(copy)
                    }) { Text("복제") }
                    TextButton(onClick = {
                        if (deleting) {
                            TaskStore.savePresets(s.presets.filterNot { it.id == p.id })
                            onDone(null)
                        } else {
                            deleting = true
                        }
                    }) { Text(if (deleting) "정말 삭제" else "삭제", color = MaterialTheme.colorScheme.error) }
                }
                if (s.presetMode) {
                    // 시각과 상관없이 지금 목록을 이 프리셋으로 바꿉니다(교대 받기와 달리 알림은 그대로).
                    TextButton(onClick = {
                        val list = withEdits()
                        TaskStore.savePresets(list)
                        TaskStore.loadPreset(edited(), list.currentShift(LocalDateTime.now())?.second ?: System.currentTimeMillis())
                        onDone(null)
                    }) { Text("지금 불러오기") }
                }
            }
        }
    }
}

/**
 * 반복해서 넣기: 이름 · 시작 · 끝 시각 · 간격(30분~6시간)으로 '08:00 라운딩' 줄을 여러 개([repeatLines]). 끝이 시작보다 이르면 자정을 넘김.
 * 미리 보기에 넣을 시각들과 줄 수.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatDialog(defaultStart: Int, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var from by remember { mutableIntStateOf(defaultStart) }
    var to by remember { mutableIntStateOf((defaultStart + 6 * 60) % 1440) }
    var every by remember { mutableIntStateOf(120) }
    val made = repeatLines(text, from, every, to)
    val pick = { now: Int, set: (Int) -> Unit -> TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, now / 60, now % 60, true).show() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("반복해서 넣기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    label = { Text("할 일") },
                    placeholder = { Text("예: 라운딩") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LineButton("${clock(from)}부터", { pick(from) { from = it } }, Modifier.weight(1f))
                    LineButton("${clock(to)}까지", { pick(to) { to = it } }, Modifier.weight(1f))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30분", 60 to "1시간", 120 to "2시간", 180 to "3시간", 240 to "4시간", 360 to "6시간").forEach { (m, label) ->
                        FilterChip(selected = every == m, onClick = { every = m }, label = { Text("${label}마다") })
                    }
                }
                Text(
                    if (made.isEmpty()) {
                        "할 일 이름을 적으면 넣을 시각이 보여요."
                    } else {
                        made.take(6).joinToString(" · ") { it.take(5) } + (if (made.size > 6) " …" else "") + " (${made.size}줄)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(made) }, enabled = made.isNotEmpty()) { Text("넣기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

/**
 * 바로가기: 모양(시트), 항목(빼기 44, 길게 눌러 끌면 순서 변경, 누르면 이름 바꾸기), 바로가기 추가.
 */
@Composable
private fun ShortcutSettings(s: Settings, onShape: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var renaming by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf(false) }
    val save = { list: List<Shortcut> -> TaskStore.updateSettings { it.copy(shortcuts = list) } }
    val slots = s.shortcutSlots()
    ValueRow("모양", listOf("한 줄 3개", "한 줄 2개", "아이콘만")[s.shortcutStyle.coerceIn(0, 2)], onClick = onShape)
    // 길게 눌러 끌기: 줄 높이(48)만큼 움직이면 이웃과 자리를 바꿉니다.
    var order by remember { mutableStateOf<List<Shortcut>?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 48.dp.toPx() }
    val shown = order ?: s.shortcuts
    shown.forEachIndexed { i, sc ->
        // 줄을 바로가기 id 로 묶어, 끄는 중 자리가 바뀌어도 같은 줄(손가락 처리 포함)이 따라 움직이게 합니다.
        // 위치로만 그리면 자리가 바뀌는 순간 그 칸의 pointerInput 키가 달라져 끌기가 취소됩니다.
        key(sc.id) {
            val name = remember(sc) { Shortcuts.name(context, sc) }
            val dragging = i == dragIndex
            Box(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragY else 0f }
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .pointerInput(sc.id, s.shortcuts) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                order = s.shortcuts
                                dragIndex = s.shortcuts.indexOf(sc)
                                dragY = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val list = order ?: return@detectDragGesturesAfterLongPress
                                dragY += amount.y
                                val to = when {
                                    dragY > rowPx / 2 && dragIndex + 1 < list.size -> dragIndex + 1
                                    dragY < -rowPx / 2 && dragIndex > 0 -> dragIndex - 1
                                    else -> return@detectDragGesturesAfterLongPress
                                }
                                order = list.toMutableList().apply { add(to, removeAt(dragIndex)) }
                                dragY += if (to > dragIndex) -rowPx else rowPx
                                dragIndex = to
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragEnd = {
                                order?.let(save)
                                order = null
                                dragIndex = -1
                            },
                            onDragCancel = {
                                order = null
                                dragIndex = -1
                            },
                        )
                    }
                    .clickable { renaming = i },
            ) {
                RemovableRow(
                    // 칸이 적은 모양으로 바꾸면 뒤쪽은 저장만 되고 안 보입니다.
                    if (i >= slots) "$name (칸이 모자라 안 보여요)" else name,
                    onRemove = { save(s.shortcuts.filterIndexed { j, _ -> j != i }) },
                    leading = { ShortcutIcon(sc) },
                )
            }
        }
    }
    if (s.shortcuts.size < slots) AddRow("바로가기 추가") { picking = true } else SetRow("${slots}칸이 다 찼어요", "빼거나 모양을 바꾸면 더 넣을 수 있어요.")

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
        val builtIn = listOf(Shortcut.ADD, Shortcut.CALC, Shortcut.ALARM, Shortcut.CALENDAR).map { Shortcut(it) }
        val choices = (builtIn.map { it to Shortcuts.name(context, it) } + apps)
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

/** 키워드 알림(알림판): 켜기, 키워드 한 줄에 하나(빼기), 키워드 추가, 볼 앱, 소리·진동. */
@Composable
private fun KeywordSettings(s: Settings, onSheet: (String) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val notiOn = remember(tick) { NotiListener.allowed(context) }
    var adding by remember { mutableStateOf(false) }
    val list = s.keywordList
    val saveList = { l: List<String> -> TaskStore.updateSettings { it.copy(keywords = l.joinToString("\n")) } }
    SwitchSetRow("키워드 알림 받기", s.keywordOn) { v -> TaskStore.updateSettings { it.copy(keywordOn = v) } }
    list.forEach { k -> RemovableRow(k, onRemove = { saveList(list - k) }) }
    AddRow("키워드 추가") { adding = true }
    AppPicker("볼 앱", s.keywordApps, "고른 앱의 새 알림에서만 키워드를 찾아요. 아무것도 안 고르면 울리지 않아요.") { c ->
        TaskStore.updateSettings { it.copy(keywordApps = c) }
    }
    ValueRow("소리·진동", if (s.keywordSound) "소리와 진동" else "진동만") { onSheet("kwSound") }
    if (!notiOn) {
        LinkRow("알림 접근이 꺼져 있어요", "다른 앱 알림을 읽어야 키워드를 찾을 수 있어요. 알림은 이 폰 밖으로 나가지 않아요.", "켜기") {
            Setup.openNotiAccess(context)
        }
    }
    if (adding) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("키워드 추가") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace(",", "").replace("\n", "").take(30) },
                    singleLine = true,
                    placeholder = { Text("예: 배정되었습니다") },
                    supportingText = { Text("띄어쓰기는 상관없이 찾아요") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank() && text.trim() !in list) saveList(list + text.trim())
                    adding = false
                }) { Text("추가") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("취소") } },
        )
    }
}

/** 앱 고르기(런처에 보이는 앱). 줄에는 고른 앱 이름, 누르면 고르는 창. [chosenApps] 가 비었을 때 뜻은 [emptyText]. */
@Composable
internal fun AppPicker(title: String, chosenApps: Set<String>, emptyText: String, emptyValue: String = "없음", onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val names = remember(chosenApps) {
        val pm = context.packageManager
        // 안 깔린 앱(처음 골라 둔 삼성 메시지 등)은 이름을 못 읽으니 뺍니다.
        chosenApps.mapNotNull { pkg -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() }.sorted()
    }
    ValueRow(title, if (names.isEmpty()) emptyValue else names.joinToString(" · ")) { picking = true }

    if (picking) {
        // 고른 앱이 맨 위, 나머지는 이름순 (런처에 보이는 앱만)
        val apps = remember {
            Shortcuts.installedApps(context)
                .mapNotNull { (sc, label) -> Shortcuts.component(sc)?.packageName?.let { it to label } }
                .distinctBy { it.first }
                .sortedByDescending { it.first in chosenApps }
        }
        var chosen by remember { mutableStateOf(chosenApps) }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(title) },
            text = {
                Column {
                    Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape)) }
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
                    onSave(chosen intersect installed)
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
        Image(icon, contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape))
    } else {
        Icon(painterResource(Shortcuts.builtInIcon(sc)), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

/** 할 일 화면의 '알람'이 울리는 방식: 알람음, 크기, 소리/진동, 무음 모드에서도 소리, 들어 보기. */
@Composable
private fun AlarmSettings(s: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ringing by AlarmRingService.ringing.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
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
            "화면이 꺼진 채 알람이 울리면 소리와 알림만 나와요. 열리는 화면에서 '널스투두'를 허용해 주세요.",
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
    ValueRow("알람음", soundName) {
        pick.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "알람음")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, s.alarmSound?.let(Uri::parse) ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI),
        )
    }
    var volume by remember(s.alarmVolume) { mutableFloatStateOf(s.alarmVolume.toFloat()) }
    Row(Modifier.fillMaxWidth().rowLine().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("크기", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { TaskStore.updateSettings { it.copy(alarmVolume = volume.roundToInt()) } },
            valueRange = 1f..10f,
            steps = 8,
            // 칸 점은 지워 막대만: 숫자는 오른쪽에 보여 줍니다.
            colors = SliderDefaults.colors(activeTickColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent),
            enabled = s.alarmMode != 2,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text("${volume.roundToInt()}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(24.dp))
    }
    val modes = listOf("소리+진동", "소리만", "진동만")
    ValueRow("방식", modes[s.alarmMode.coerceIn(0, 2)]) { sheet = true }
    SwitchSetRow("무음·진동 모드여도 소리로 울리기", s.alarmAlwaysSound) { v -> TaskStore.updateSettings { it.copy(alarmAlwaysSound = v) } }
    SetRow(
        "들어 보기 (3초)",
        "울리는 동안만 폰의 알람 음량을 위 크기로 맞췄다가 되돌려요.",
        onClick = if (ringing == null) {
            {
                preview?.cancel()
                preview = scope.launch {
                    AlarmSound.start(context, TaskStore.settings.value)
                    try {
                        delay(3_000)
                    } finally {
                        stopPreview()
                    }
                }
            }
        } else null,
        titleColor = MaterialTheme.colorScheme.primary,
    )
    if (sheet) {
        PickSheet("알람 방식", "", modes, setOf(s.alarmMode), onDismiss = { sheet = false }) { i ->
            TaskStore.updateSettings { it.copy(alarmMode = i) }
            sheet = false
        }
    }
}

/** 누르면 시스템 화면을 여는 줄: 제목, 짧은 설명, 오른쪽 지금 상태(흐리게) · ›. [onClick] 이 없으면 안내만. */
@Composable
private fun LinkRow(title: String, body: String, action: String = "", onClick: (() -> Unit)? = null) {
    SetRow(title, body, onClick) {
        if (action.isNotEmpty()) Text(action, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
