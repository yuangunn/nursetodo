package com.yuangunn.nursetodo.lock

import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.yuangunn.nursetodo.data.Shortcut
import com.yuangunn.nursetodo.data.TaskStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 할 일 화면의 바로가기 편집 패널을 손가락 입력으로: 길게 눌러 끌어 순서, ✕ 빼기, + 넣기, 모양. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ShortcutPanelTest {
    @get:Rule
    val rule = createComposeRule()

    private fun ids() = TaskStore.settings.value.shortcuts.map { it.id }

    @Before
    fun reset() {
        TaskStore.init(ApplicationProvider.getApplicationContext())
        TaskStore.updateSettings { it.copy(shortcuts = listOf(Shortcut(Shortcut.CALC), Shortcut(Shortcut.ALARM), Shortcut(Shortcut.CALENDAR)), shortcutStyle = 0) }
        rule.setContent {
            val s by TaskStore.settings.collectAsStateWithLifecycle()
            ShortcutPanel(s, onClose = {})
        }
    }

    @Test
    fun longPressDragMovesRowUp() {
        rule.onNodeWithText("캘린더").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // 줄 높이 48 두 번만큼 위로, 조금씩
            repeat(20) { moveBy(Offset(0f, -48.dp.toPx() * 2 / 20)) }
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf(Shortcut.CALENDAR, Shortcut.CALC, Shortcut.ALARM), ids())
    }

    @Test
    fun removeAddAndShape() {
        rule.onNodeWithContentDescription("알람 빼기").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Shortcut.CALC, Shortcut.CALENDAR), ids())
        rule.onNodeWithContentDescription("할 일 추가 넣기").performClick()
        rule.waitForIdle()
        assertEquals(listOf(Shortcut.CALC, Shortcut.CALENDAR, Shortcut.ADD), ids())
        rule.onNodeWithText("아이콘만").performClick()
        rule.waitForIdle()
        assertEquals(2, TaskStore.settings.value.shortcutStyle)
    }
}
