package com.ttaak.app.lock

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ttaak.app.R
import com.ttaak.app.data.TaskStore
import com.ttaak.app.ui.TtaakTheme

/**
 * 알람이 울릴 때 잠금 위에 뜨는 화면. '끄기'(또는 뒤로 가기)를 누르면 멈춥니다.
 * 알림의 '끄기'나 10분 경과로 먼저 멈추면 이 화면도 저절로 닫힙니다.
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
            val title = ringing ?: fallbackTitle
            BackHandler(onBack = stop)
            TtaakTheme(settings) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .systemBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_alarm),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = stop,
                        color = Glass,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(50),
                        border = GlassEdge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("끄기", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
