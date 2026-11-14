package com.yuangunn.nursetodo.lock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import com.yuangunn.nursetodo.data.Preset
import com.yuangunn.nursetodo.data.Settings
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.currentShift
import com.yuangunn.nursetodo.data.keywordHit
import com.yuangunn.nursetodo.data.keywordList
import com.yuangunn.nursetodo.data.keywordActive
import com.yuangunn.nursetodo.data.onDuty
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** 키워드 알림: 키워드 비교, 근무 받은 폰만, 대화방의 지난 메시지로 다시 울리지 않기, 팝업 버튼. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeywordTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val sms = "com.google.android.apps.messaging"

    @Before
    @After
    fun reset() {
        TaskStore.init(context)
        TaskStore.onChanged = null
        TaskStore.tasks.value.forEach { TaskStore.delete(it.id) }
        TaskStore.updateSettings { Settings() }
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun onDuty() {
        val shift = Preset.Defaults.currentShift(LocalDateTime.now())!!.second
        TaskStore.updateSettings { it.copy(presetMode = true, dutyShift = shift, keywords = "배정되었습니다, 입원") }
    }

    private fun sbn(n: Notification, pkg: String = sms, id: Int = 1) =
        @Suppress("DEPRECATION")
        StatusBarNotification(pkg, pkg, id, null, Process.myUid(), 0, 0, n, Process.myUserHandle(), System.currentTimeMillis())

    private fun chat(vararg lines: Pair<String, Long>): Notification {
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("나").build())
        lines.forEach { (text, at) -> style.addMessage(text, at, Person.Builder().setName("병동").build()) }
        return NotificationCompat.Builder(context, "x").setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("병동").setContentText(lines.last().first).setStyle(style)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)).build()
    }

    @Test
    fun matchesIgnoringSpacesAndCase() {
        val keys = Settings(keywords = " 배정되었습니다 , , ICU ").keywordList
        assertEquals(listOf("배정되었습니다", "ICU"), keys)
        assertEquals("배정되었습니다", keywordHit(keys, "702호 배정 되었습니다"))
        assertEquals("ICU", keywordHit(keys, "icu 전동"))
        assertNull(keywordHit(keys, "배정 취소"))
        assertNull(keywordHit(emptyList(), "배정되었습니다"))
    }

    @Test
    fun onDutyUntilNextShift() {
        val now = LocalDateTime.of(2026, 11, 12, 10, 0)
        fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val day = Settings(presetMode = true, dutyShift = ms(now.withHour(7)))
        assertTrue(day.onDuty(ms(now)))
        assertFalse(day.onDuty(ms(now.withHour(15))))
        assertFalse(day.copy(dutyShift = 0).onDuty(ms(now)))
        assertFalse(day.copy(presetMode = false).onDuty(ms(now)))
        // 다음 교대를 1시간 일찍 받았으면 받은 때부터 울림
        assertTrue(day.copy(dutyShift = ms(now.withHour(15))).onDuty(ms(now.withHour(14))))
    }

    @Test
    fun keywordActiveAlwaysWithoutPresets() {
        val now = LocalDateTime.of(2026, 11, 12, 10, 0)
        fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // 교대 프리셋을 안 쓰면 근무를 받았든 말든 늘
        assertTrue(Settings(presetMode = false).keywordActive(ms(now)))
        assertTrue(Settings(presetMode = false, dutyShift = ms(now.withHour(7))).keywordActive(ms(now.withHour(20))))
        // 쓰면 받은 근무 동안만
        val day = Settings(presetMode = true, dutyShift = ms(now.withHour(7)))
        assertTrue(day.keywordActive(ms(now)))
        assertFalse(day.keywordActive(ms(now.withHour(15))))
        assertFalse(day.copy(dutyShift = 0).keywordActive(ms(now)))
    }

    @Test
    fun alertsWithoutPresetsWhenNoShiftReceived() {
        TaskStore.updateSettings { it.copy(presetMode = false, dutyShift = 0, keywords = "배정되었습니다") }
        val s = sbn(chat("702호 배정되었습니다" to 50L))
        KeywordAlert.check(context, s)
        assertNotNull(nm.getNotification(s.key, 5_000))
    }

    @Test
    fun latestTakesNewestChatMessage() {
        assertEquals(20L to "점심 드세요", KeywordAlert.latest(chat("702호 배정되었습니다" to 10L, "점심 드세요" to 20L)))
        val plain = NotificationCompat.Builder(context, "x").setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentText("짧은 글").setStyle(NotificationCompat.BigTextStyle().bigText("긴 글 전부")).setWhen(5L).build()
        assertEquals(5L to "긴 글 전부", KeywordAlert.latest(plain))
    }

    @Test
    fun alertsOnceOnDutyForChosenApps() {
        val hit = chat("702호 배정되었습니다" to 10L)
        // 교대 프리셋을 쓰는데 근무를 안 받았으면 울리지 않음
        TaskStore.updateSettings { it.copy(presetMode = true, keywords = "배정되었습니다") }
        KeywordAlert.check(context, sbn(hit))
        assertEquals(0, nm.size())

        onDuty()
        KeywordAlert.check(context, sbn(hit, pkg = "com.example.other"))
        assertEquals(0, nm.size())
        val first = sbn(hit)
        KeywordAlert.check(context, first)
        val popup = nm.getNotification(first.key, 5_000)
        assertNotNull(popup)
        assertEquals(listOf("확인", "열기", "할 일로 추가"), popup.actions.map { it.title.toString() })
        // 같은 알림이 그대로 다시 올라오거나, 키워드 없는 새 메시지가 붙으면 다시 울리지 않음
        context.getSystemService(NotificationManager::class.java).cancelAll()
        KeywordAlert.check(context, sbn(hit))
        KeywordAlert.check(context, sbn(chat("702호 배정되었습니다" to 10L, "점심 드세요" to 20L)))
        assertEquals(0, nm.size())
        // 같은 문구라도 새 메시지면 다시
        KeywordAlert.check(context, sbn(chat("702호 배정되었습니다" to 10L, "703호 배정되었습니다" to 30L)))
        assertEquals(1, nm.size())
    }

    @Test
    fun addButtonMakesTaskAndClosesPopup() {
        onDuty()
        val s = sbn(chat("702호 배정되었습니다" to 40L))
        KeywordAlert.check(context, s)
        val add = nm.getNotification(s.key, 5_000).actions.first { it.title == "할 일로 추가" }
        // 할 일 화면 카드도 같은 팝업을 읽음
        assertEquals(listOf("702호 배정되었습니다"), KeywordAlert.active(context).map { it.text })
        KeywordAlert.Receiver().onReceive(context, shadowOf(add.actionIntent).savedIntent)
        assertEquals(listOf("702호 배정되었습니다"), TaskStore.tasks.value.map { it.title })
        assertNull(nm.getNotification(s.key, 5_000))
        assertEquals(emptyList<KeywordAlert.Hit>(), KeywordAlert.active(context))
    }
}
