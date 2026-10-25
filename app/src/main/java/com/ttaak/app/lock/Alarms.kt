package com.ttaak.app.lock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ttaak.app.data.Alarm
import com.ttaak.app.data.TaskStore

/**
 * 'N분 뒤' 알람 (병동 타이머). 시계 앱처럼 [AlarmManager.setAlarmClock] 으로 맞춰서 잠자기 중에도 정시에 울립니다.
 * 울리면 [AlarmRingService] 가 설정한 알람음·크기·방식으로 울리고 잠금 위에 알람 화면([AlarmActivity])을 띄웁니다.
 * 부팅·업데이트 뒤에는 [scheduleAll] 이 다시 맞춥니다.
 */
object Alarms {
    /** 알람 알림 채널. 소리·진동은 서비스가 직접 내므로 채널은 조용합니다. */
    const val CHANNEL_ID = "alarm_ring"
    const val EXTRA_ID = "alarmId"
    const val EXTRA_LABEL = "alarmLabel"
    private const val ACTION_FIRE = "com.ttaak.app.ALARM_FIRE"
    private const val ACTION_STOP = "com.ttaak.app.ALARM_STOP"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // 시험판에서 소리가 붙은 채로 만든 채널. 채널 소리는 나중에 못 바꿔서 새 채널로 옮깁니다.
        nm.deleteNotificationChannel("alarm")
        val channel = NotificationChannel(CHANNEL_ID, "알람", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "'몇 분 뒤' 알람이 울릴 때 (소리·진동은 설정 → 알람)"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun add(context: Context, minutes: Int, label: String) = schedule(context, TaskStore.addAlarm(minutes, label))

    fun cancel(context: Context, id: Long) {
        TaskStore.removeAlarm(id)
        context.getSystemService(AlarmManager::class.java).cancel(fireIntent(context, id))
    }

    /** 저장된 알람을 다시 맞춥니다. 꺼져 있던 사이에 지난 알람은 바로 울립니다. */
    fun scheduleAll(context: Context) = TaskStore.alarms.value.forEach { schedule(context, it) }

    /** 지금 울리고 있는지. 울리는 동안엔 화면이 켜져도 딱 화면을 띄우지 않습니다. */
    fun ringing(): Boolean = AlarmRingService.ringing.value != null

    /** 울리는 알람을 끕니다 (소리·진동·알림·알람 화면 모두). */
    fun stop(context: Context) {
        context.stopService(Intent(context, AlarmRingService::class.java))
    }

    /** 알림의 '끄기' 버튼. */
    fun stopIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 5,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun requestCode(id: Long): Int = 1000 + (id % 100_000).toInt()

    private fun schedule(context: Context, alarm: Alarm) {
        val am = context.getSystemService(AlarmManager::class.java)
        // 상태바 알람 아이콘을 누르면 딱 화면으로
        val show = PendingIntent.getActivity(
            context, 4,
            Intent(context, QuickAddActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(QuickAddActivity.EXTRA_LOOK_FIRST, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.at, show), fireIntent(context, alarm.id))
        } catch (_: SecurityException) {
            // 정확한 알람 권한이 없으면(안드로이드 12) 조금 늦을 수 있는 방식으로라도
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.at, fireIntent(context, alarm.id))
        }
    }

    private fun fireIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode(id),
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 시간이 됐을 때: 목록에서 빼고 울리기 시작합니다. */
    internal fun ring(context: Context, id: Long) {
        val alarm = TaskStore.alarms.value.firstOrNull { it.id == id } ?: return
        TaskStore.removeAlarm(id)
        val intent = Intent(context, AlarmRingService::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_LABEL, alarm.label.ifEmpty { "${alarm.minutes}분 알람" })
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // 백그라운드 시작이 막힌 경우(드묾). 정확한 알람으로 깨어난 직후라 보통은 허용됩니다.
        }
    }

    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            TaskStore.init(context)
            when (intent.action) {
                ACTION_FIRE -> ring(context, intent.getLongExtra(EXTRA_ID, -1))
                ACTION_STOP -> stop(context)
            }
        }
    }
}
