package com.yuangunn.nursetodo.lock

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 알림 목록에서 손가락으로: 줄의 ✕ 는 그 알림만, '모두 지우기'는 둘 이상일 때만 보이고 전체를. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class NotiPanelTest {
    @get:Rule
    val rule = createComposeRule()

    private fun noti(key: String, app: String) = Noti(key, "x.$key", "제목 $key", "내용", 0L, null, false, app)

    @Test
    fun dismissOneOrAll() {
        val dismissed = mutableListOf<String>()
        var cleared = 0
        rule.setContent {
            NotiPanel(
                listOf(noti("a", "문자"), noti("b", "메신저")),
                onTap = {},
                onDismiss = { dismissed += it.key },
                onClearAll = { cleared++ },
                onClose = {},
            )
        }
        rule.onNodeWithContentDescription("메신저 알림 지우기").performClick()
        rule.onNodeWithText("모두 지우기").performClick()
        rule.waitForIdle()
        assertEquals(listOf("b"), dismissed)
        assertEquals(1, cleared)
    }

    @Test
    fun noClearAllForOne() {
        rule.setContent { NotiPanel(listOf(noti("a", "문자")), onTap = {}, onDismiss = {}, onClearAll = {}, onClose = {}) }
        rule.onNodeWithText("모두 지우기").assertDoesNotExist()
        rule.onNodeWithContentDescription("문자 알림 지우기").assertExists()
    }
}
