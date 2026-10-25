package com.ttaak.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import com.ttaak.app.R
import com.ttaak.app.TtaakApp
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            (application as TtaakApp).refreshSurfaces()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by TaskStore.settings.collectAsStateWithLifecycle()
            TtaakTheme(settings) { Root() }
        }
    }

    override fun onResume() {
        super.onResume()
        // 날짜가 바뀐 뒤 처음 열었을 때 잠금화면 카드도 새 날짜로.
        (application as TtaakApp).refreshSurfaces()
    }
}

@Composable
private fun Root() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("오늘", rememberVectorPainter(Icons.Default.Home), 0),
                    Triple("캘린더", rememberVectorPainter(Icons.Default.DateRange), 1),
                    Triple("계산기", painterResource(R.drawable.ic_calc), 2), // 기본 아이콘 묶음에 계산기가 없음
                    Triple("설정", rememberVectorPainter(Icons.Default.Settings), 3),
                ).forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            tabState.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> TodayScreen(onEdit = { editing = it }, onDelete = delete)
                    1 -> CalendarScreen(onEdit = { editing = it }, onDelete = delete)
                    2 -> CalcScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }

    editing?.let { task -> TaskEditSheet(task, onDismiss = { editing = null }, onDelete = delete) }
}
