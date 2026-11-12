package com.yuangunn.nursetodo.lock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.forShift
import com.yuangunn.nursetodo.data.reminders

/**
 * 근무 루틴 알림 (gentle remind). 교대 프리셋에서 '07:00 정규 라운딩'처럼 시각이 붙은 항목을, '근무 교대 받았습니다'를 누른
 * 폰에서만 그 시각에 알립니다. 병동 공용폰은 근무 인원보다 많을 수 있어서 받은 폰만 울립니다.
 * 소리 없이 짧은 진동과 팝업(완료 · 10분 뒤)만. 화면이 꺼져 있으면 잠깐 켜서 할 일 화면의 근무 페이지를 보여 줍니다.
 * 직접 맞춘 알람([Alarms])과 달리 끌 때까지 울리지 않습니다. 놓친 항목은 근무 페이지에서 빨갛게 보입니다.
 */
object Routine {
    const val CHANNEL_ID = "routine"
    private const val ACTION_FIRE = "com.yuangunn.nursetodo.ROUTINE_FIRE"
    private const val ACTION_DONE = "com.yuangunn.nursetodo.ROUTINE_DONE"
    private const val ACTION_SNOOZE = "com.yuangunn.nursetodo.ROUTINE_SNOOZE"
    private const val EXTRA_SHIFT = "shift"
    private const val EXTRA_INDEX = "index"

    // ponytail: 교대 하나에 시각 있는 항목 100개까지. 넘으면 MAX 를 늘림
    private const val MAX = 100
    private const val SNOOZE_MINUTES = 10

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "근무 루틴 알림", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "교대 프리셋에서 시각을 붙인 할 일 (소리 없이 진동과 팝업)"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 180, 120, 180)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** 받은 교대의 남은 알림을 다시 맞춥니다. 전체 알림을 껐거나 받은 교대가 없으면 모두 지웁니다. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        repeat(MAX) { i -> pending(context, ACTION_FIRE, i, 0, PendingIntent.FLAG_NO_CREATE)?.let(am::cancel) }
        val s = TaskStore.settings.value
        s.reminders(System.currentTimeMillis()).filter { it.index < MAX }.forEach { r ->
            Alarms.setClock(context, r.at, pending(context, ACTION_FIRE, r.index, s.dutyShift)!!)
        }
    }

    /** 시각이 됐을 때: 이미 끝낸 항목이면 넘어가고, 아니면 진동·팝업, 화면이 꺼져 있으면 잠깐 켭니다. */
    internal fun fire(context: Context, shift: Long, index: Int) {
        val s = TaskStore.settings.value
        if (!s.presetMode || !s.routineAlarm || s.dutyShift != shift) return
        val text = s.presets.forShift(shift)?.items?.getOrNull(index) ?: return
        if (TaskStore.tasks.value.any { it.shift == shift && it.title == text && it.doneOn != null }) return
        // 같은 이름 포켓북('정규 라운딩')의 체크를 새로 시작합니다.
        TaskStore.restartPocketsFor(text)
        notify(context, shift, index, text)
        wake(context)
    }

    /** 화면이 꺼져 있으면 잠깐 켜서 할 일 화면의 근무 페이지를 보여 줍니다. 알람이 울리거나 통화 중이면 그대로. 키워드 알림도 씁니다. */
    internal fun wake(context: Context) {
        val screenOn = context.getSystemService(PowerManager::class.java).isInteractive
        if (!screenOn && Settings.canDrawOverlays(context) && !Alarms.ringing() && !LockPageService.inCall(context)) {
            try {
                context.startActivity(QuickAddActivity.dutyIntent(context))
            } catch (_: Exception) {
                // 백그라운드에서 화면을 못 띄우면 팝업과 진동만
            }
        }
    }

    private fun notify(context: Context, shift: Long, index: Int, text: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(text)
            .setContentText("근무 루틴")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 7, QuickAddActivity.dutyIntent(context, wake = false),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(0, "완료", pending(context, ACTION_DONE, index, shift))
            .addAction(0, "${SNOOZE_MINUTES}분 뒤", pending(context, ACTION_SNOOZE, index, shift))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_BASE + index, n)
    }

    /** 액션마다 요청 번호를 나눠 같은 항목의 알림·완료·10분 뒤가 서로 덮어쓰지 않게 합니다. */
    private fun pending(context: Context, action: String, index: Int, shift: Long, flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? {
        val base = when (action) {
            ACTION_FIRE -> 200_000
            ACTION_DONE -> 210_000
            else -> 220_000
        }
        return PendingIntent.getBroadcast(
            context, base + index,
            Intent(context, Receiver::class.java).setAction(action).putExtra(EXTRA_SHIFT, shift).putExtra(EXTRA_INDEX, index),
            PendingIntent.FLAG_IMMUTABLE or flags,
        )
    }

    private const val NOTIFICATION_BASE = 4_000

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            TaskStore.init(context)
            val shift = intent.getLongExtra(EXTRA_SHIFT, 0)
            val index = intent.getIntExtra(EXTRA_INDEX, -1)
            when (intent.action) {
                ACTION_FIRE -> fire(context, shift, index)
                ACTION_DONE -> {
                    context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + index)
                    val text = TaskStore.settings.value.presets.forShift(shift)?.items?.getOrNull(index) ?: return
                    TaskStore.tasks.value.firstOrNull { it.shift == shift && it.title == text && it.doneOn == null }
                        ?.let { TaskStore.toggle(it.id) }
                }
                ACTION_SNOOZE -> {
                    context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + index)
                    // 같은 항목의 원래 알림과 겹치지 않게 '완료'와 다른 번호대(230_000)로 맞춥니다.
                    val again = PendingIntent.getBroadcast(
                        context, 230_000 + index,
                        Intent(context, Receiver::class.java).setAction(ACTION_FIRE)
                            .putExtra(EXTRA_SHIFT, shift).putExtra(EXTRA_INDEX, index),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    Alarms.setClock(context, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, again)
                }
            }
        }
    }
}
