package com.yuangunn.nursetodo.lock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.yuangunn.nursetodo.data.Settings
import com.yuangunn.nursetodo.data.shortcutCells
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 할 일 화면 바로가기 칸을 손가락으로: 누르기, 빈 칸 '+', 길게 눌러 끌어 다른 칸에 놓기, 길게 눌렀다 떼기(편집). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ShortcutGridTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<String>()

    @Before
    fun show() {
        // 기본: 윗줄 빈 칸 3, 아랫줄 간호계산기 · 알람 · 손전등
        val cells = Settings().shortcutCells()
        rule.setContent {
            ShortcutGrid(
                cells, 0,
                onTap = { i, sc -> events += "tap $i ${sc.id}" },
                onEdit = { events += "edit" },
                onAdd = { i -> events += "add $i" },
                onMove = { a, b -> events += "move $a $b" },
            )
        }
    }

    private fun center(desc: String, index: Int = 0) =
        rule.onAllNodesWithContentDescription(desc)[index].fetchSemanticsNode().boundsInRoot.center

    @Test
    fun tapAndPlus() {
        rule.onNodeWithContentDescription("간호계산기").performClick()
        rule.onAllNodesWithContentDescription("바로가기 넣기")[1].performClick()
        rule.waitForIdle()
        assertEquals(listOf("tap 3 calc", "add 1"), events)
    }

    @Test
    fun longPressDragDropsOnOtherCell() {
        // 간호계산기(4번 칸)를 끌어 윗줄 첫 '+'(1번 칸)에
        val delta = center("바로가기 넣기", 0) - center("간호계산기")
        rule.onNodeWithContentDescription("간호계산기").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, -1f))
        }
        repeat(20) {
            rule.onNodeWithContentDescription("간호계산기").performTouchInput { moveBy(delta / 20f) }
            rule.waitForIdle()
        }
        rule.onNodeWithContentDescription("간호계산기").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(listOf("move 3 0"), events)
    }

    @Test
    fun longPressWithoutMovingOpensEdit() {
        rule.onNodeWithContentDescription("알람").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 0.5f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("edit"), events)
    }
}
