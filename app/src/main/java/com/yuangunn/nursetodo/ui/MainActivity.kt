package com.yuangunn.nursetodo.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.NurseTodoApp
import com.yuangunn.nursetodo.data.Task
import com.yuangunn.nursetodo.data.TaskStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** 위젯 등 바깥에서 열어 달라고 한 탭. 보여 준 뒤 -1 로 돌립니다. */
    private val requestedTab = mutableIntStateOf(-1)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            (application as NurseTodoApp).refreshSurfaces()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 처음 안내 중이면 알림 허용은 안내의 권한 단계에서 차례대로 받습니다.
        if (Build.VERSION.SDK_INT >= 33 && TaskStore.settings.value.onboarded &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestedTab.intValue = intent.getIntExtra(EXTRA_TAB, -1)
        setContent {
            val settings by TaskStore.settings.collectAsStateWithLifecycle()
            // 앱 테마를 시스템과 다르게 고르면 상태바·내비게이션 바 글씨 색도 앱 테마에 맞춥니다.
            val dark = settings.isDark()
            LaunchedEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(style, style)
            }
            NurseTodoTheme(settings) {
                if (settings.onboarded) Root(requestedTab.intValue) { requestedTab.intValue = -1 } else Onboarding()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab.intValue = intent.getIntExtra(EXTRA_TAB, -1)
    }

    companion object {
        private const val EXTRA_TAB = "tab"

        // 탭: 0 오늘, 1 포켓북, 2 캘린더, 3 계산기, 4 설정
        fun calcIntent(context: Context): Intent = tabIntent(context, 3)

        fun calendarIntent(context: Context): Intent = tabIntent(context, 2)

        fun settingsIntent(context: Context): Intent = tabIntent(context, 4)

        private fun tabIntent(context: Context, tab: Int) = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onResume() {
        super.onResume()
        // 날짜가 바뀐 뒤 처음 열었을 때 잠금화면 카드도 새 날짜로.
        (application as NurseTodoApp).refreshSurfaces()
    }
}

@Composable
private fun Root(requestedTab: Int, onTabShown: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(requestedTab) {
        if (requestedTab >= 0) {
            tab = requestedTab
            onTabShown()
        }
    }
    var editing by remember { mutableStateOf<Task?>(null) }
    // 탭을 옮겨 다녀도 각 탭의 입력값·선택한 날짜가 남도록
    val tabState = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val delete: (Task) -> Unit = { task ->
        TaskStore.delete(task.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("'${task.title}' 삭제함", actionLabel = "되돌리기", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) TaskStore.restore(task)
        }
    }

    // 전체 화면 편집기(프리셋 고치기, 포켓북 붙여넣기)는 아래 탭 위에 덮어 그립니다.
    val overlay = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    CompositionLocalProvider(LocalOverlay provides overlay) {
    Box {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // 아래 탭 5칸: 표시 56×32 primaryContainer, 아이콘 22, 이름 13/18
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf(
                    Triple("오늘", rememberVectorPainter(Icons.Outlined.Home), 0),
                    Triple("포켓북", painterResource(R.drawable.ic_memo), 1),
                    Triple("캘린더", rememberVectorPainter(Icons.Outlined.DateRange), 2),
                    Triple("계산기", painterResource(R.drawable.ic_calc), 3), // 기본 아이콘 묶음에 계산기가 없음
                    Triple("설정", rememberVectorPainter(Icons.Outlined.Settings), 4),
                ).forEach { (label, icon, index) ->
                    val on = tab == index
                    NavigationBarItem(
                        selected = on,
                        onClick = { tab = index },
                        icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            tabState.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> TodayScreen(onEdit = { editing = it }, onDelete = delete)
                    1 -> PocketScreen()
                    2 -> CalendarScreen(onEdit = { editing = it }, onDelete = delete)
                    3 -> CalcScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }

    overlay.value?.invoke()
    }
    }

    editing?.let { task -> TaskEditSheet(task, onDismiss = { editing = null }, onDelete = delete) }
}
