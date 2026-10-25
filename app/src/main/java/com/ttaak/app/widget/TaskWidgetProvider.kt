package com.ttaak.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.ttaak.app.R
import com.ttaak.app.data.Day
import com.ttaak.app.data.Fmt
import com.ttaak.app.data.TaskStore
import com.ttaak.app.data.cardTextSp
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

    /** 사용자가 위젯 크기를 바꾸면 계산기 버튼을 보이거나 숨깁니다. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        TaskStore.init(context)
        manager.updateAppWidget(id, build(context, id))
    }

    companion object {
        /** 이 높이(dp) 이상으로 키우면 아래에 계산기 버튼을 붙입니다. 기본 3x2 보다 한 칸 이상 클 때. */
        private const val CALC_MIN_HEIGHT_DP = 230

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
            val sp = settings.cardTextSp()
            val height = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

            return RemoteViews(context.packageName, R.layout.widget_tasks).apply {
                setTextViewText(R.id.title, Fmt.date(today))
                setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, sp - 1)
                setTextViewTextSize(R.id.progress, TypedValue.COMPLEX_UNIT_SP, sp - 3)
                setViewVisibility(R.id.calc, if (height >= CALC_MIN_HEIGHT_DP) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(
                    R.id.calc,
                    PendingIntent.getActivity(
                        context, 20, MainActivity.calcIntent(context),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
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
