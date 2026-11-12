package com.yuangunn.nursetodo.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.NurseTodoApp
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.lock.NotiListener
import com.yuangunn.nursetodo.lock.Setup

/**
 * 처음 설치한 사람을 위한 안내(설정 › 처음 안내 다시 보기로 다시 볼 수 있음):
 * 소개 → 쓸 기능 고르기(근무 루틴·키워드 알림) → 권한을 하나씩(알림 · 다른 앱 위에 표시 · 알람 및 리마인더 · 알림 접근) →
 * 알림 볼 앱 고르기(알림판) → 마무리. 어느 단계든 건너뛸 수 있고, 끝내거나 건너뛰면 [com.yuangunn.nursetodo.data.Settings.onboarded].
 */
@Composable
fun Onboarding() {
    val context = LocalContext.current
    val settings by TaskStore.settings.collectAsStateWithLifecycle()
    val noti = remember { NotiListener.available(context) }
    // 알림판이 아니면 '알림 볼 앱' 단계는 없습니다.
    val steps = if (noti) 5 else 4
    var step by rememberSaveable { mutableIntStateOf(0) }
    val finish = { TaskStore.updateSettings { it.copy(onboarded = true) } }
    BackHandler(enabled = step > 0) { step-- }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // 건너뛰기가 없는 마지막 단계에서도 높이가 그대로이게 48
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 단계 점
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(steps) { i ->
                    Box(
                        Modifier
                            .size(if (i == step) 20.dp else 6.dp, 6.dp)
                            .clip(CircleShape)
                            .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            if (step < steps - 1) TextButton(onClick = finish) { Text("건너뛰기") }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (if (!noti && step >= 3) step + 1 else step) {
                0 -> Welcome()
                1 -> Features(settings.presetMode, settings.keywordOn, noti)
                2 -> Permissions(noti)
                3 -> NotiApps()
                else -> Ready()
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step > 0) LineButton("이전", { step-- }, Modifier.weight(1f))
            FillButton(
                when (step) {
                    0 -> "시작하기"
                    steps - 1 -> "다 됐어요"
                    else -> "다음"
                },
                { if (step < steps - 1) step++ else finish() },
                Modifier.weight(if (step > 0) 2f else 1f),
            )
        }
    }
}

@Composable
private fun Title(text: String, body: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ColumnScope.Welcome() {
    Spacer(Modifier.height(24.dp))
    // 앱 아이콘: 남색 바탕 위 고리·체크(108 중 가운데 72가 보이는 부분)
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colorResource(R.color.brand)),
        contentAlignment = Alignment.Center,
    ) { Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(108.dp)) }
    Title("널스투두", "화면을 켜면 잠금화면 위에 오늘 할 일이 먼저 보여요. 잠금을 풀지 않고 체크하고 적어요.")
    Bullet("근무 루틴", "Day·Evening·Night 할 일을 교대를 받을 때 불러오고, 시각이 되면 알려요.")
    Bullet("키워드 알림", "문자·메신저에 '배정되었습니다' 같은 낱말이 오면 잠금화면 맨 위에 크게 남겨요.")
    Bullet("포켓북 · 항암계산기 · 알람", "라운딩 체크리스트, 주입속도 계산, '몇 분 뒤' 알람을 잠금화면에서 바로.")
    Text(
        "인터넷을 쓰지 않아요. 적은 내용과 알림은 이 폰 밖으로 나가지 않아요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Bullet(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Features(preset: Boolean, keyword: Boolean, noti: Boolean) {
    Title("무엇을 쓸까요?", "나중에 설정에서 언제든 바꿀 수 있어요.")
    Column(Modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)) {
        SwitchSetRow("근무 루틴(교대 프리셋)", preset, "잠금화면 '근무' 페이지에서 교대를 받으면 그 근무 할 일로 바뀌어요. 근무별 할 일은 설정 › 교대 프리셋에서 고쳐요.") { v ->
            TaskStore.updateSettings { it.copy(presetMode = v) }
        }
        if (noti) {
            SwitchSetRow("키워드 알림", keyword, "근무 중에 고른 앱 알림에 키워드가 있으면 진동하고 잠금화면 맨 위에 남겨요.") { v ->
                TaskStore.updateSettings { it.copy(keywordOn = v) }
            }
        }
    }
}

/** 권한을 위에서부터 하나씩. 아직 안 한 첫 줄만 진한 버튼이고, 설정 화면에서 돌아오면 다시 확인합니다. */
@Composable
private fun Permissions(noti: Boolean) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        (context.applicationContext as NurseTodoApp).refreshSurfaces()
        if (!granted) Setup.openAppNotificationSettings(context)
        tick++
    }
    val items = remember(tick) {
        buildList {
            add(Perm("알림", "알람과 근무 루틴 알림이 울리고, 할 일 화면이 늘 켜져 있으려면 필요해요.", Setup.notificationsAllowed(context)) {
                if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else Setup.openAppNotificationSettings(context)
            })
            add(Perm("다른 앱 위에 표시", "화면을 켜면 잠금화면 위에 할 일 화면이 뜨게 해요. 열리는 목록에서 '널스투두'를 켜 주세요.", android.provider.Settings.canDrawOverlays(context)) {
                Setup.requestOverlay(context)
            })
            add(Perm("알람 및 리마인더", "'몇 분 뒤' 알람과 근무 루틴 알림이 맞춘 시각에 울려요.", Setup.exactAlarmAllowed(context)) {
                Setup.openExactAlarmSettings(context)
            })
            if (noti) {
                add(Perm("알림 접근", "할 일 화면에 문자·메신저 알림을 보여 주고 키워드를 찾아요. 알림은 이 폰 밖으로 나가지 않아요.", NotiListener.allowed(context)) {
                    Setup.openNotiAccess(context)
                })
            }
        }
    }
    Title("권한을 차례로 허용해 주세요", "버튼을 누르면 설정 화면이 열려요. 켜고 돌아오면 다음 줄로 넘어가요.")
    val next = items.indexOfFirst { !it.done }
    items.forEachIndexed { i, p ->
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (p.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (p.done) {
                    Icon(Icons.Default.Check, contentDescription = "허용됨", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                } else {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(p.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    p.done -> Text("허용했어요", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    i == next -> FillButton("허용하기", p.onAsk, Modifier.padding(top = 8.dp))
                    else -> FilledTonalButton(onClick = p.onAsk, modifier = Modifier.padding(top = 4.dp)) { Text("허용하기") }
                }
            }
        }
    }
}

private class Perm(val title: String, val body: String, val done: Boolean, val onAsk: () -> Unit)

/** 알림판: 할 일 화면에 보일 알림 앱과 키워드를 찾을 앱·키워드. 알림 접근을 아직 안 했으면 그것부터. */
@Composable
private fun NotiApps() {
    val context = LocalContext.current
    val s by TaskStore.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val allowed = remember(tick) { NotiListener.allowed(context) }
    Title("어떤 앱 알림을 볼까요?", "할 일 화면 오른쪽(근무·포켓북 페이지는 위 종 버튼)에 고른 앱 알림이 보여요.")
    if (!allowed) {
        Text("알림 접근을 허용해야 고를 수 있어요.", style = MaterialTheme.typography.bodyMedium)
        FillButton("알림 접근 허용하기", { Setup.openNotiAccess(context) })
        return
    }
    Column(Modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)) {
        AppPicker("알림 볼 앱", s.notiApps, "아무것도 안 고르면 모든 앱 알림을 보여요.", emptyValue = "모든 앱") { c -> TaskStore.updateSettings { it.copy(notiApps = c) } }
        if (s.keywordOn) {
            AppPicker("키워드 찾을 앱", s.keywordApps, "고른 앱의 새 알림에서만 키워드를 찾아요.") { c -> TaskStore.updateSettings { it.copy(keywordApps = c) } }
        }
    }
    if (s.keywordOn) {
        OutlinedTextField(
            value = s.keywords,
            onValueChange = { v -> TaskStore.updateSettings { it.copy(keywords = v.take(300)) } },
            label = { Text("키워드 (한 줄에 하나)") },
            placeholder = { Text("배정되었습니다\n응급") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Ready() {
    Spacer(Modifier.height(24.dp))
    Title("준비됐어요", "화면을 끄고 다시 켜 보세요. 잠금화면 위에 할 일 화면이 떠요.")
    Bullet("체크는 동그라미만", "글씨를 누르면 잠금을 풀고 앱이 열리고, 길게 누르면 이름을 고쳐요.")
    Bullet("위로 밀어서 잠금 해제", "맨 아래 손잡이나 화면 아무 데서나 위로 밀어요.")
    Bullet("바로가기 편집", "아래 바로가기를 길게 누르면 넣고 빼고 순서를 바꿔요.")
    Text(
        "남은 설정은 '오늘' 탭 위 안내와 설정에서 할 수 있어요.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
