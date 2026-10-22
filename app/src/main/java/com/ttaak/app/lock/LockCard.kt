package com.ttaak.app.lock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ttaak.app.R
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import com.ttaak.app.ui.Accents
import com.ttaak.app.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId

/**
 * 잠금화면 카드.
 *
 * iOS 툭의 Live Activity 에 해당하는 것을 안드로이드에서는 "잠금화면 공개(PUBLIC) + 고정(ongoing)"
 * 알림으로 구현합니다. 각 줄의 체크 버튼은 브로드캐스트라서 잠금을 풀지 않아도 동작합니다.
 */
object LockCard {
    private const val CHANNEL_ID = "lock_card"
    private const val NOTIFICATION_ID = 1
    private const val MAX_ROWS = 6

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun update(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        val settings = TaskStore.settings.value
        if (!settings.lockEnabled) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        if (!nm.areNotificationsEnabled()) return
        try {
            nm.notify(NOTIFICATION_ID, build(context))
        } catch (_: SecurityException) {
            // 알림 권한이 아직 없음 — 앱을 열면 다시 요청합니다.
        }
    }

    private fun build(context: Context): Notification {
        val today = Day.today()
        val settings = TaskStore.settings.value
        val all = TaskStore.tasksFor(today, today)
        val doneCount = all.count { it.isDone(today) }
        val shown = if (settings.lockHideDone) all.filterNot { it.isDone(today) } else all
        val accent = Accents[settings.accent.coerceIn(Accents.indices)].toArgb()

        val title = Fmt.date(today)
        val progress = if (all.isEmpty()) "" else "$doneCount/${all.size}"
        val headline = Fmt.headline(settings, today)
        val remaining = all.filterNot { it.isDone(today) }

        val collapsed = RemoteViews(context.packageName, R.layout.lock_card_small).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.progress, progress)
            setTextViewText(
                R.id.summary,
                when {
                    all.isEmpty() -> context.getString(R.string.lock_empty)
                    remaining.isEmpty() -> context.getString(R.string.lock_all_done)
                    else -> remaining.first().title + if (remaining.size > 1) "  외 ${remaining.size - 1}개" else ""
                },
            )
            val first = remaining.firstOrNull()
            if (first != null) {
                setViewVisibility(R.id.check, android.view.View.VISIBLE)
                setInt(R.id.check, "setColorFilter", accent)
                setOnClickPendingIntent(R.id.check, toggleIntent(context, first.id))
            } else {
                setViewVisibility(R.id.check, android.view.View.GONE)
            }
            setOnClickPendingIntent(R.id.add, addIntent(context))
            setInt(R.id.add, "setColorFilter", accent)
        }

        val expanded = RemoteViews(context.packageName, R.layout.lock_card_big).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.progress, progress)
            setTextViewText(R.id.headline, headline)
            setViewVisibility(R.id.headline, if (headline.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            removeAllViews(R.id.rows)
            shown.take(MAX_ROWS).forEach { addView(R.id.rows, row(context, it, today, accent)) }
            val more = shown.size - MAX_ROWS
            setViewVisibility(R.id.more, if (more > 0) android.view.View.VISIBLE else android.view.View.GONE)
            setTextViewText(R.id.more, "+ ${more}개 더")
            setViewVisibility(R.id.empty, if (shown.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
            setTextViewText(
                R.id.empty,
                context.getString(if (all.isEmpty()) R.string.lock_empty else R.string.lock_all_done),
            )
            setOnClickPendingIntent(R.id.add, addIntent(context))
            setTextColor(R.id.add, accent)
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setColor(accent)
            .setContentTitle(title)
            .setContentText(remaining.firstOrNull()?.title ?: context.getString(R.string.lock_empty))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(open)
            .setDeleteIntent(broadcast(context, LockActionReceiver.ACTION_REPOST, 1))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSortKey("0")
            .build()
    }

    private fun row(context: Context, task: Task, today: Long, accent: Int): RemoteViews {
        val done = task.isDone(today)
        return RemoteViews(context.packageName, R.layout.lock_card_row).apply {
            val text: CharSequence = if (done) {
                SpannableString(task.title).apply {
                    setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else task.title
            setTextViewText(R.id.text, text)
            if (done) setTextColor(R.id.text, 0xFF9E9E9E.toInt())
            setImageViewResource(R.id.check, if (done) R.drawable.ic_check_on else R.drawable.ic_check_off)
            setInt(R.id.check, "setColorFilter", if (done) accent else 0xFF9E9E9E.toInt())
            setOnClickPendingIntent(R.id.row, toggleIntent(context, task.id))
        }
    }

    private fun toggleIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, LockActionReceiver::class.java)
            .setAction(LockActionReceiver.ACTION_TOGGLE)
            .putExtra(LockActionReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, (id % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun addIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 1,
        Intent(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, LockActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** 날짜가 바뀌면 카드를 새 날짜로 다시 그립니다. (정확한 알람 권한 없이 5분 창 안에서) */
    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli() + 1_000
        am.setWindow(
            AlarmManager.RTC, midnight, 5 * 60_000L,
            broadcast(context, LockActionReceiver.ACTION_REFRESH, 2),
        )
    }
}
