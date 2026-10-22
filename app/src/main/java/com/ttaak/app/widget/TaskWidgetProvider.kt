package com.ttaak.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.ttaak.app.R
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.TaskStore
import com.ttaak.app.lock.LockActionReceiver
import com.ttaak.app.lock.LockCard
import com.ttaak.app.ui.Accents
import com.ttaak.app.ui.MainActivity

/** 홈 화면 위젯 — 오늘 할 일 목록, 탭하면 바로 체크. */
class TaskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        TaskStore.init(context)
        ids.forEach { manager.updateAppWidget(it, build(context, it)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.list)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { manager.updateAppWidget(it, build(context, it)) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.list)
        }

        private fun build(context: Context, widgetId: Int): RemoteViews {
            val today = Day.today()
            val settings = TaskStore.settings.value
            val all = TaskStore.tasksFor(today, today)
            val accent = Accents[settings.accent.coerceIn(Accents.indices)].toArgb()

            return RemoteViews(context.packageName, R.layout.widget_tasks).apply {
                setTextViewText(R.id.title, Fmt.date(today))
                setTextViewText(R.id.progress, if (all.isEmpty()) "" else "${all.count { it.isDone(today) }}/${all.size}")
                setInt(R.id.add, "setColorFilter", accent)
                setOnClickPendingIntent(R.id.add, LockCard.addIntent(context))
                setOnClickPendingIntent(
                    R.id.title,
                    PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )

                val service = Intent(context, TaskWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                service.data = Uri.parse(service.toUri(Intent.URI_INTENT_SCHEME))
                setRemoteAdapter(R.id.list, service)
                setEmptyView(R.id.list, R.id.empty)

                // 각 줄의 fillInIntent 가 할 일 id 를 채웁니다.
                val template = PendingIntent.getBroadcast(
                    context, 10,
                    Intent(context, LockActionReceiver::class.java).setAction(LockActionReceiver.ACTION_TOGGLE),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                setPendingIntentTemplate(R.id.list, template)
            }
        }
    }
}
