package com.ttaak.app.lock

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ttaak.app.data.Settings
import com.ttaak.app.data.TaskStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** '몇 분 뒤' 알람: 시계 앱 방식으로 맞춰지고, 울리면 목록에서 빠지며 서비스가 울리다 끄면 멈추는지. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun alarmIsScheduledThenRingsUntilStopped() {
        TaskStore.init(context)
        Alarms.createChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationsEnabled(true)

        Alarms.add(context, 30, "항암 끝")
        val alarm = TaskStore.alarms.value.single { it.label == "항암 끝" }
        assertEquals(30L, alarm.minutes)
        val next = context.getSystemService(AlarmManager::class.java).nextAlarmClock
        assertNotNull("시계 앱 방식(setAlarmClock)으로 맞춰져야 함", next)
        assertEquals(alarm.at, next.triggerTime)

        Alarms.ring(context, alarm.id)
        assertTrue(TaskStore.alarms.value.none { it.id == alarm.id })
        val started = shadowOf(context as Application).nextStartedService
        assertEquals(AlarmRingService::class.java.name, started.component?.className)

        val service = Robolectric.buildService(AlarmRingService::class.java, started).create().startCommand(0, 1)
        val n = shadowOf(nm).getNotification(AlarmRingService.NOTIFICATION_ID)
        assertNotNull("잠금 위에 알람 화면", n.fullScreenIntent)
        assertEquals("항암 끝", AlarmRingService.ringing.value)
        assertTrue(Alarms.ringing())

        service.destroy()
        assertEquals(null, AlarmRingService.ringing.value)
    }

    @Test
    fun soundOrVibrationFollowsSettings() {
        val s = Settings()
        // (소리, 진동)
        assertEquals(true to true, AlarmSound.plan(s, phoneSilent = true)) // 기본: 무음이어도 소리+진동
        assertEquals(false to true, AlarmSound.plan(s.copy(alarmAlwaysSound = false), phoneSilent = true)) // 폰 따라 진동만
        assertEquals(true to true, AlarmSound.plan(s.copy(alarmAlwaysSound = false), phoneSilent = false))
        assertEquals(true to false, AlarmSound.plan(s.copy(alarmMode = 1), phoneSilent = false)) // 소리만
        assertEquals(false to true, AlarmSound.plan(s.copy(alarmMode = 1, alarmAlwaysSound = false), phoneSilent = true)) // 못 내면 진동이라도
        assertEquals(false to true, AlarmSound.plan(s.copy(alarmMode = 2), phoneSilent = false)) // 진동만
    }
}
