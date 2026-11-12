package com.yuangunn.nursetodo.lock

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.ui.NurseTodoTheme
import com.yuangunn.nursetodo.ui.lockAccent

/**
 * 알람이 울릴 때 잠금 위에 뜨는 화면: 위에 아이콘(뒤로 번지는 원)·시각·이름(48/56, 두 줄까지), 아래에 '5분 뒤 다시 울림'과 '끄기'.
 * 뒤로 가기는 끄기와 같습니다. 알림의 '끄기'나 10분 경과로 먼저 멈추면 이 화면도 저절로 닫힙니다.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 27) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        TaskStore.init(this)
        val fallbackTitle = intent.getStringExtra(Alarms.EXTRA_LABEL).orEmpty()
        val at = AlarmRingService.startedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        val stop = {
            Alarms.stop(this)
            finish()
        }
        setContent {
            val settings by TaskStore.settings.collectAsStateWithLifecycle()
            val ringing by AlarmRingService.ringing.collectAsStateWithLifecycle()
            var started by remember { mutableStateOf(false) }
            // 다른 곳에서 꺼지면 닫힘 (울리기 시작한 뒤에만 — 서비스가 늦게 뜨는 순간에 닫히지 않게)
            LaunchedEffect(ringing) {
                if (ringing != null) started = true else if (started) finish()
            }
            val label = ringing ?: fallbackTitle
            BackHandler(onBack = stop)
            val accent = lockAccent(settings)
            // 첫 초점은 끄기
            val offFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { offFocus.requestFocus() } }
            val fade = remember { Animatable(0f) }
            LaunchedEffect(Unit) { fade.animateTo(1f, tween(240, easing = Lk.Decel)) }
            NurseTodoTheme(settings) {
                CompositionLocalProvider(LocalLockAccent provides accent) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = fade.value }
                            .background(Lk.Cover)
                            .systemBarsPadding()
                            .padding(start = 20.dp, end = 20.dp, top = 112.dp, bottom = 40.dp)
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Halo(accent)
                        Text(
                            hhmm(at),
                            style = Lk.Title.merge(TextStyle(fontFeatureSettings = "tnum")),
                            color = Lk.Text,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            label.ifEmpty { "알람" },
                            style = Lk.Display.copy(fontFeatureSettings = null),
                            fontWeight = Lk.Strong,
                            color = Lk.Text,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton(
                                "5분 뒤 다시 울림",
                                {
                                    Alarms.snooze(this@AlarmActivity, label)
                                    finish()
                                },
                                Modifier.fillMaxWidth(),
                                height = 64.dp,
                                style = Lk.Title,
                                weight = androidx.compose.ui.text.font.FontWeight.Normal,
                            )
                            PillButton(
                                "끄기",
                                stop,
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(offFocus),
                                filled = true,
                                height = 64.dp,
                                style = Lk.Title,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 울리는 동안 아이콘 뒤로 번지는 원(1 → 2.2배, 50% → 0, 1.6초마다). 시스템 애니메이션을 끄면 멈춘 채로 보입니다. */
@androidx.compose.runtime.Composable
private fun Halo(accent: Color) {
    val t = rememberInfiniteTransition(label = "halo")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing), RepeatMode.Restart), label = "p")
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = 1f + 1.2f * p
                    scaleY = 1f + 1.2f * p
                    alpha = 0.5f * (1f - p)
                }
                .background(accent, CircleShape),
        )
        Icon(painterResource(R.drawable.ic_alarm), contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
    }
}
