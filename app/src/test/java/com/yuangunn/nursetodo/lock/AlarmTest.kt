package com.yuangunn.nursetodo.lock

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.yuangunn.nursetodo.data.Settings
import com.yuangunn.nursetodo.data.TaskStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** '몇 분 뒤' 알람: 정확한 알람으로 맞춰지고, 울리면 목록에서 빠지며 서비스가 울리다 끄면 멈추는지. */
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

        Alarms.add(context, System.currentTimeMillis() + 30 * 60_000L, "항암 끝")
        val alarm = TaskStore.alarms.value.single { it.label == "항암 끝" }
        assertEquals(30L, alarm.minutes)
        val am = context.getSystemService(AlarmManager::class.java)
        // 깊은 절전 중에도 제시간에 울리게 시계 앱 방식으로 (상태바 ⏰)
        assertEquals(alarm.at, am.nextAlarmClock?.triggerTime)
        val scheduled = shadowOf(am).scheduledAlarms.single { it.triggerAtTime == alarm.at }
        assertEquals(AlarmManager.RTC_WAKEUP, scheduled.type)

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
    fun exactAlarmIsAskedFromTheUserAndRearmedWhenAllowed() {
        // Play 정책상 할 일 앱은 USE_EXACT_ALARM(설치 때 자동 허용)을 못 씁니다.
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions!!.toList()
        assertTrue("android.permission.SCHEDULE_EXACT_ALARM" in requested)
        assertTrue("android.permission.USE_EXACT_ALARM" !in requested)
        // 허용하면 시스템이 이 방송을 보냅니다. 받는 곳이 없으면 맞춰 둔 알람이 늦는 채로 남습니다.
        val allowed = Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED).setPackage(context.packageName)
        assertEquals(
            LockActionReceiver::class.java.name,
            context.packageManager.queryBroadcastReceivers(allowed, 0).single().activityInfo.name,
        )
    }

    @Test
    fun clockDigitsBecomeNextTime() {
        val now = java.time.LocalDateTime.of(2026, 11, 8, 13, 0)
        fun at(d: String) = clockTarget(d, now)?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
        assertEquals(now.withHour(14).withMinute(30), at("1430"))
        assertEquals(now.withHour(21).withMinute(5), at("2105"))
        // 지난 시각이면 내일, 숫자 한두 개는 정각, 세 개는 h:mm
        assertEquals(now.plusDays(1).withHour(9).withMinute(30), at("930"))
        assertEquals(now.plusDays(1).withHour(7), at("7"))
        assertEquals(null, at("2460"))
        assertEquals(null, at(""))
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
