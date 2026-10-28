package com.ttaak.app.lock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ttaak.app.R
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import com.ttaak.app.data.cardTextSp
import com.ttaak.app.ui.Accents
import java.time.LocalDate
import java.time.ZoneId

/**
 * 잠금화면 카드.
 *
 * iOS 툭의 Live Activity 에 해당하는 것을 안드로이드에서는 "잠금화면 공개(PUBLIC) + 고정(ongoing)"
 * 알림으로 구현합니다. 각 줄의 체크 버튼은 브로드캐스트라서 잠금을 풀지 않아도 동작합니다.
 */
object LockCard {
    const val CHANNEL_ID = "lock_card"
    const val NOTIFICATION_ID = 1

    /**
     * 딱 화면(A안) 상시 서비스용 조용한 알림. 안드로이드가 상시 서비스 알림을 MIN 으로 못 두게 해서(LOW 로 올림)
     * 상태바에 아이콘이 남습니다. 사용자가 이 알림 종류를 끄면 아이콘·알림은 사라지고 서비스는 그대로 돕니다(실기기·에뮬 확인).
     */
    const val PAGE_CHANNEL_ID = "lock_page"

    /**
     * 화면을 켜면 딱 화면이 뜨는 상태(A안)인지. 이때는 잠금화면 카드·라이브 알림이 겹치므로 띄우지 않고,
     * 서비스가 꼭 가져야 하는 알림만 조용히 둡니다.
     */
    fun pageMode(context: Context): Boolean =
        TaskStore.settings.value.lockPage && android.provider.Settings.canDrawOverlays(context)

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
        val page = NotificationChannel(PAGE_CHANNEL_ID, "딱 화면 유지", NotificationManager.IMPORTANCE_MIN).apply {
            description = "화면을 켜면 딱 화면을 띄우려고 늘 켜 두는 알림. 꺼도 딱 화면은 그대로 동작해요."
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, page))
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

    internal fun build(context: Context): Notification {
        if (pageMode(context)) return buildPage(context)
        val today = Day.today()
        val settings = TaskStore.settings.value
        val all = TaskStore.tasksFor(today, today)
        val doneCount = all.count { it.isDone(today) }
        val accent = Accents[settings.accent.coerceIn(Accents.indices)].toArgb()

        val title = Fmt.date(today)
        val progress = if (all.isEmpty()) "" else "$doneCount/${all.size}"
        val headline = Fmt.headline(settings, today)
        // 끝낸 일은 잠금화면에서 사라지고 앱의 '완료된 할 일'에서만 보입니다.
        val remaining = all.filterNot { it.isDone(today) }
        if (settings.lockLive) return buildLive(context, progress, headline, all, remaining, accent)

        // 펼친 카드 높이는 시스템이 약 256dp 로 자르므로 글자가 클수록 줄 수를 줄입니다.
        val sp = settings.cardTextSp()
        val maxRows = (100 / sp).toInt().coerceIn(2, 7) // 16sp 6줄, 19sp 5줄, 23sp 4줄

        val collapsed = RemoteViews(context.packageName, R.layout.lock_card_small).apply {
            setTextViewText(R.id.title, title)
            setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, sp - 2)
            setTextViewText(R.id.progress, progress)
            setTextViewTextSize(R.id.summary, TypedValue.COMPLEX_UNIT_SP, sp)
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
            setInt(R.id.add, "setColorFilter", accent)
        }

        val expanded = RemoteViews(context.packageName, R.layout.lock_card_big).apply {
            setTextViewText(R.id.title, title)
            setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, sp)
            setTextViewText(R.id.progress, progress)
            setTextViewText(R.id.headline, headline)
            setViewVisibility(R.id.headline, if (headline.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            removeAllViews(R.id.rows)
            remaining.take(maxRows).forEach { addView(R.id.rows, row(context, it, sp)) }
            val more = remaining.size - maxRows
            setViewVisibility(R.id.more, if (more > 0) android.view.View.VISIBLE else android.view.View.GONE)
            setTextViewText(R.id.more, "+ ${more}개 더")
            setViewVisibility(R.id.empty, if (remaining.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE)
            setTextViewText(
                R.id.empty,
                context.getString(if (all.isEmpty()) R.string.lock_empty else R.string.lock_all_done),
            )
            setTextColor(R.id.add, accent)
            setTextViewTextSize(R.id.add, TypedValue.COMPLEX_UNIT_SP, sp - 2)
            setTextViewTextSize(R.id.empty, TypedValue.COMPLEX_UNIT_SP, sp)
        }

        // `＋ 추가` 에 PendingIntent 를 따로 걸면 시스템이 잠금 해제(PIN)부터 요구합니다.
        // 카드 전체 탭(contentIntent)은 showWhenLocked 화면이면 잠금 위에 바로 열어 주므로,
        // `＋ 추가` 는 클릭을 받지 않고 카드 탭으로 넘깁니다.
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setColor(accent)
            .setContentTitle(title)
            .setContentText(remaining.firstOrNull()?.title ?: context.getString(R.string.lock_empty))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(pageIntent(context))
            .setDeleteIntent(broadcast(context, LockActionReceiver.ACTION_REPOST, 1))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSortKey("0")
            .build()
    }

    private fun buildPage(context: Context): Notification =
        NotificationCompat.Builder(context, PAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("딱 화면 켜짐")
            .setContentText("화면을 켜면 오늘 할 일이 먼저 보여요")
            .setContentIntent(pageIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

    /**
     * 라이브 알림 (안드로이드 16+ Live Updates). 시스템이 잠금화면 맨 위에 늘 펼쳐 보여 주는 대신 커스텀 화면을
     * 못 써서(넣으면 시스템이 라이브로 올려 주지 않음) 줄마다 체크는 안 됩니다. 버튼은 시스템이 3개까지만 받아서
     * 남은 할 일 위에서 3개까지 완료 버튼을 둡니다. 그 아래는 카드를 눌러 딱 화면에서 체크합니다.
     * 앱 아이콘·이름 줄은 시스템이 강제로 붙입니다.
     */
    private fun buildLive(
        context: Context,
        progress: String,
        headline: String,
        all: List<Task>,
        remaining: List<Task>,
        accent: Int,
    ): Notification {
        val empty = context.getString(if (all.isEmpty()) R.string.lock_empty else R.string.lock_all_done)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setColor(accent)
            // 제목은 시스템이 꼭 요구해서 날짜 대신 진행률만 둡니다.
            .setContentTitle(if (remaining.isEmpty()) empty else progress)
            .setContentText(remaining.firstOrNull()?.title)
            .setSubText(headline.ifEmpty { null })
            .setStyle(
                if (remaining.isEmpty()) null
                else NotificationCompat.BigTextStyle().bigText(remaining.joinToString("\n") { "○  ${it.title}" }),
            )
            .apply { remaining.take(3).forEach { addAction(0, "✓ ${it.title}", toggleIntent(context, it.id)) } }
            .setContentIntent(pageIntent(context))
            .setDeleteIntent(broadcast(context, LockActionReceiver.ACTION_REPOST, 1))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addExtras(
                Bundle().apply {
                    // Notification.EXTRA_REQUEST_PROMOTED_ONGOING / EXTRA_SHORT_CRITICAL_TEXT (API 36)
                    putBoolean("android.requestPromotedOngoing", true)
                    if (progress.isNotEmpty()) putCharSequence("android.shortCriticalText", progress)
                },
            )
            .build()
    }

    private fun row(context: Context, task: Task, sp: Float): RemoteViews =
        RemoteViews(context.packageName, R.layout.lock_card_row).apply {
            setTextViewText(R.id.text, task.title)
            setTextViewTextSize(R.id.text, TypedValue.COMPLEX_UNIT_SP, sp)
            setImageViewResource(R.id.check, R.drawable.ic_check_off)
            setInt(R.id.check, "setColorFilter", 0xFF9E9E9E.toInt())
            setOnClickPendingIntent(R.id.row, toggleIntent(context, task.id))
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

    /** 카드를 누르면 딱 화면을 목록부터 (키보드 없이). 추가는 화면 안의 '할 일 추가'로. */
    private fun pageIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 3,
        Intent(context, QuickAddActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(QuickAddActivity.EXTRA_LOOK_FIRST, true),
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
