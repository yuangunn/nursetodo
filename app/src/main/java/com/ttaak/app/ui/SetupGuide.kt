package com.ttaak.app.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
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
    val live = Build.VERSION.SDK_INT >= 36 && settings.lockLive
    val promoted = remember(tick, settings) { live && Setup.liveShowing(context) }
    val hiddenOnSamsung = style != null && style != 0

    val states = listOfNotNull(
        if (allowed) State.Done else State.Todo,
        if (showing) State.Done else State.Todo,
        when (style) {
            0 -> State.Done
            null -> State.Manual
            else -> State.Todo
        },
        if (settings.tileAdded) State.Done else if (Setup.canRequestTile) State.Todo else State.Manual,
        if (widget) State.Done else State.Todo,
        if (!live) null else if (promoted && !hiddenOnSamsung) State.Done else State.Todo,
    )
    // 다 끝났으면 빈 카드를 남기지 않습니다.
    if (hideDone && states.all { it == State.Done }) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "잠금화면에서 쓰려면",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (onClose != null) TextButton(onClick = onClose) { Text("닫기") }
            }

            Step(
                hideDone = hideDone,
                n = 1,
                state = states[0],
                title = "알림 허용",
                body = "잠금화면 카드는 알림으로 표시돼요.",
                action = "허용하기".takeIf { !allowed },
            ) {
                if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else Setup.openAppNotificationSettings(context)
            }

            Step(
                hideDone = hideDone,
                n = 2,
                state = states[1],
                title = "잠금화면 카드 켜기",
                body = when {
                    !allowed -> "1번을 먼저 해 주세요."
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
            }

            Step(
                hideDone = hideDone,
                n = 3,
                state = states[2],
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
            ) { Setup.openLockScreenSettings(context) }

            Step(
                hideDone = hideDone,
                n = 4,
                state = states[3],
                title = "잠금화면에서 바로 추가 (상단바 버튼)",
                body = if (Setup.canRequestTile) "상단바를 내려 '딱 추가'(✓)를 누르면 잠금을 풀지 않고 적을 수 있어요. " +
                    "삼성은 오른쪽 위에서 내린 빠른 설정창 맨 아래에 있어요."
                else "상단바를 내려 연필(편집) 버튼 → '딱 추가' 타일을 끌어다 놓으세요.",
                action = "추가하기".takeIf { Setup.canRequestTile && !settings.tileAdded },
            ) { (context as? Activity)?.let { Setup.requestTile(it) } }

            Step(
                hideDone = hideDone,
                n = 5,
                state = states[4],
                title = "홈 화면 위젯 (선택)",
                body = if (widget) "위젯이 홈 화면에 있어요." else "홈 화면을 길게 눌러 위젯 → '딱'을 골라도 돼요.",
                action = "추가하기".takeIf { !widget },
            ) {
                if (!Setup.requestWidget(context)) {
                    Toast.makeText(context, "홈 화면을 길게 눌러 위젯 → '딱'을 추가해 주세요", Toast.LENGTH_LONG).show()
                }
            }

            if (live) {
                // 삼성 Now Bar(잠금화면 아래 알약)는 삼성이 고른 앱만 들어가서 설정으로도 못 넣습니다 (One UI 8.5 실기기 확인).
                Step(
                    hideDone = hideDone,
                    n = 6,
                    state = states[5],
                    title = "라이브 알림 (켠 경우)",
                    body = when {
                        !showing -> "2번을 먼저 해 주세요."
                        // 삼성은 라이브 알림도 알림 스타일이 '카드'여야 잠금화면에 보입니다 (One UI 8.5 실기기 확인).
                        promoted && hiddenOnSamsung -> "라이브 알림은 켜졌지만 잠금화면에 보이려면 3번을 해 주세요."
                        promoted -> "잠금화면 맨 위에 펼쳐진 채로 떠 있어요."
                        // 사용자가 끈 경우와 폰이 지원하지 않는 경우(안드로이드 16 초기판 등)를 앱이 구분할 수 없습니다.
                        else -> "라이브 알림으로 안 올라가서 일반 알림으로 보여요. 열리는 화면에 '라이브'나 '실시간' 항목이 " +
                            "있으면 켜 주세요. 없으면 이 폰은 지원하지 않아요."
                    },
                    action = "켜러 가기".takeIf { showing && !promoted },
                ) { Setup.openLiveSettings(context) }
            }
        }
    }
}

@Composable
private fun Step(
    hideDone: Boolean,
    n: Int,
    state: State,
    title: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
) {
    if (hideDone && state == State.Done) return
    val (icon: ImageVector, tint: Color) = when (state) {
        State.Done -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        State.Todo -> Icons.Default.Warning to Color(0xFFFF9F0A)
        State.Manual -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("$n. $title", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 6.dp)) { Text(action) }
            }
        }
    }
}
