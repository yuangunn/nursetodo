package com.ttaak.app.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.TtaakApp
import com.ttaak.app.data.TaskStore
import com.ttaak.app.lock.Setup

private enum class State { Done, Todo, Manual }

/**
 * 잠금화면 카드 · 위젯 · 타일을 단계별로 점검하고 버튼 한 번으로 고치는 카드.
 * 설정 화면에서 돌아올 때마다(onResume) 상태를 다시 확인합니다.
 *
 * 오늘 탭([hideDone])에서는 처음 설정용으로만: 한 줄로 접혀 있고 꼭 필요한 단계 중 남은 것만 보이며,
 * 다 끝나면 저절로 사라집니다. 상단바 타일·홈 위젯 같은 선택 단계는 설정 화면에만 둡니다.
 */
@Composable
fun SetupGuide(
    modifier: Modifier = Modifier,
    hideDone: Boolean = false,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val settings by TaskStore.settings.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        (context.applicationContext as TtaakApp).refreshSurfaces()
        if (!granted) Setup.openAppNotificationSettings(context)
        tick++
    }

    // tick 이 바뀌면 다시 계산
    val allowed = remember(tick, settings) { Setup.notificationsAllowed(context) }
    val blocked = remember(tick, settings) { Setup.channelBlocked(context) }
    val showing = remember(tick, settings) { allowed && Setup.cardShowing(context) }
    val widget = remember(tick, settings) { Setup.widgetAdded(context) }
    val style = remember(tick, settings) { Setup.samsungLockStyle(context) }
    val overlay = remember(tick, settings) { android.provider.Settings.canDrawOverlays(context) }
    // 딱 화면(A안)을 쓰면 잠금화면 카드·알림 스타일·라이브 알림은 필요 없습니다 (카드를 띄우지 않음).
    val page = settings.lockPage
    val live = !page && Build.VERSION.SDK_INT >= 36 && settings.lockLive
    val promoted = remember(tick, settings) { live && Setup.liveShowing(context) }
    val hiddenOnSamsung = style != null && style != 0

    val steps = buildList {
        add(
            StepSpec(
                state = if (allowed) State.Done else State.Todo,
                title = "알림 허용",
                body = if (page) "알람이 울리고 딱 화면이 늘 켜져 있으려면 필요해요." else "잠금화면 카드는 알림으로 표시돼요.",
                action = "허용하기".takeIf { !allowed },
            ) {
                if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else Setup.openAppNotificationSettings(context)
            },
        )
        if (page) {
            add(
                StepSpec(
                    state = if (overlay && settings.lockEnabled) State.Done else State.Todo,
                    title = "화면 켜면 딱 화면",
                    body = when {
                        !settings.lockEnabled -> "'잠금화면에 오늘 할 일 표시'가 꺼져 있어요."
                        overlay -> "화면을 켜면 잠금 위에 오늘 할 일이 먼저 떠요."
                        else -> "'다른 앱 위에 표시'를 허용해야 화면을 켤 때 딱 화면이 떠요. 열리는 목록에서 '딱'을 찾아 켜 주세요."
                    },
                    action = when {
                        !settings.lockEnabled -> "켜기"
                        !overlay -> "허용하기"
                        else -> null
                    },
                ) {
                    if (!settings.lockEnabled) TaskStore.updateSettings { it.copy(lockEnabled = true) } else Setup.requestOverlay(context)
                    tick++
                },
            )
        } else {
            add(
                StepSpec(
                    state = if (showing) State.Done else State.Todo,
                    title = "잠금화면 카드 켜기",
                    body = when {
                        !allowed -> "알림 허용을 먼저 해 주세요."
                        blocked -> "'잠금화면 카드' 알림이 꺼져 있어요. 켜 주세요."
                        !settings.lockEnabled -> "설정에서 꺼 두었어요."
                        showing -> "상단바를 내리면 '딱' 카드가 보여요."
                        else -> "카드가 아직 없어요. 다시 띄워 볼게요."
                    },
                    action = when {
                        !allowed || showing -> null
                        blocked -> "알림 켜기"
                        !settings.lockEnabled -> "켜기"
                        else -> "다시 띄우기"
                    },
                ) {
                    when {
                        blocked -> Setup.openChannelSettings(context)
                        !settings.lockEnabled -> TaskStore.updateSettings { it.copy(lockEnabled = true) }
                        else -> (context.applicationContext as TtaakApp).refreshSurfaces()
                    }
                    tick++
                },
            )
            add(
                StepSpec(
                    // 앱이 확인할 수 없는 폰(삼성 외)은 설정 화면에서만 안내합니다.
                    state = when (style) {
                        0 -> State.Done
                        null -> State.Manual
                        else -> State.Todo
                    },
                    title = "잠금화면에 알림 내용 보이게",
                    body = when (style) {
                        0 -> "잠금화면 알림이 '카드'로 보여요."
                        null -> "폰 설정이라 직접 바꿔야 해요. 화면을 끄고 켜서 카드가 보이는지 확인하세요.\n" +
                            "· 삼성: 설정 → 알림 → 잠금화면 → 알림 스타일 '카드'\n" +
                            "· 픽셀/기타: 설정 → 알림 → 잠금 화면의 알림 → '모든 알림 표시'"
                        else -> "잠금화면 알림이 '${if (style == 1) "아이콘" else "동그란 점"}'이라 딱 카드가 안 보여요. " +
                            "열리는 화면에서 알림 스타일을 '카드'로 골라 주세요."
                    },
                    action = "설정 열기".takeIf { style != 0 },
                ) { Setup.openLockScreenSettings(context) },
            )
            if (live) {
                // 삼성 Now Bar(잠금화면 아래 알약)는 삼성이 고른 앱만 들어가서 설정으로도 못 넣습니다 (One UI 8.5 실기기 확인).
                add(
                    StepSpec(
                        state = if (promoted && !hiddenOnSamsung) State.Done else State.Todo,
                        title = "라이브 알림",
                        body = when {
                            !showing -> "잠금화면 카드를 먼저 켜 주세요."
                            // 삼성은 라이브 알림도 알림 스타일이 '카드'여야 잠금화면에 보입니다 (One UI 8.5 실기기 확인).
                            promoted && hiddenOnSamsung -> "라이브 알림은 켜졌지만 잠금화면에 보이려면 알림 스타일을 '카드'로 해 주세요."
                            promoted -> "잠금화면 맨 위에 펼쳐진 채로 떠 있어요."
                            // 사용자가 끈 경우와 폰이 지원하지 않는 경우(안드로이드 16 초기판 등)를 앱이 구분할 수 없습니다.
                            else -> "라이브 알림으로 안 올라가서 일반 알림으로 보여요. 열리는 화면에 '라이브'나 '실시간' 항목이 " +
                                "있으면 켜 주세요. 없으면 이 폰은 지원하지 않아요."
                        },
                        action = "켜러 가기".takeIf { showing && !promoted },
                    ) { Setup.openLiveSettings(context) },
                )
            }
        }
        add(
            StepSpec(
                state = if (settings.tileAdded) State.Done else if (Setup.canRequestTile) State.Todo else State.Manual,
                optional = true,
                title = "잠금화면에서 바로 추가 (상단바 버튼)",
                body = if (Setup.canRequestTile) "상단바를 내려 '딱 추가'(✓)를 누르면 잠금을 풀지 않고 적을 수 있어요. " +
                    "삼성은 오른쪽 위에서 내린 빠른 설정창 맨 아래에 있어요."
                else "상단바를 내려 연필(편집) 버튼 → '딱 추가' 타일을 끌어다 놓으세요.",
                action = "추가하기".takeIf { Setup.canRequestTile && !settings.tileAdded },
            ) { (context as? Activity)?.let { Setup.requestTile(it) } },
        )
        add(
            StepSpec(
                state = if (widget) State.Done else State.Todo,
                optional = true,
                title = "홈 화면 위젯 (선택)",
                body = if (widget) "위젯이 홈 화면에 있어요." else "홈 화면을 길게 눌러 위젯 → '딱'을 골라도 돼요.",
                action = "추가하기".takeIf { !widget },
            ) {
                if (!Setup.requestWidget(context)) {
                    Toast.makeText(context, "홈 화면을 길게 눌러 위젯 → '딱'을 추가해 주세요", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    // 다 끝났으면 빈 카드를 남기지 않습니다.
    val left = steps.count { !it.optional && it.state == State.Todo }
    if (hideDone && left == 0) return
    var open by rememberSaveable { mutableStateOf(false) }
    val expanded = !hideDone || open

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.then(if (hideDone) Modifier.clickable { open = !open } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (hideDone) "잠금화면 설정 ${left}개 남음" else "잠금화면에서 쓰려면",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (hideDone) {
                    Icon(
                        if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (open) "접기" else "펼치기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onClose != null) TextButton(onClick = onClose) { Text("닫기") }
            }
            if (!expanded) return@Column
            // 오늘 탭에서는 꼭 필요한데 아직 안 한 단계만
            steps.filter { !hideDone || (!it.optional && it.state == State.Todo) }
                .forEachIndexed { i, step -> Step(i + 1, step) }
        }
    }
}

private class StepSpec(
    val state: State,
    val title: String,
    val body: String,
    val action: String?,
    val optional: Boolean = false,
    val onAction: () -> Unit,
)

@Composable
private fun Step(n: Int, step: StepSpec) {
    val state = step.state
    val (icon: ImageVector, tint: Color) = when (state) {
        State.Done -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        State.Todo -> Icons.Default.Warning to Color(0xFFFF9F0A)
        State.Manual -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("$n. ${step.title}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(step.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step.action != null) {
                FilledTonalButton(onClick = step.onAction, modifier = Modifier.padding(top = 6.dp)) { Text(step.action) }
            }
        }
    }
}
