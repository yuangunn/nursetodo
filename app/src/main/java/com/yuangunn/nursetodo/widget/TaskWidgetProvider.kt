package com.yuangunn.nursetodo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Fmt
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.lock.LockActionReceiver
import com.yuangunn.nursetodo.lock.LockCard
import android.content.res.Configuration
import com.yuangunn.nursetodo.ui.AccentDark
import com.yuangunn.nursetodo.ui.AccentLight
import com.yuangunn.nursetodo.ui.MainActivity

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
            // + 원은 고른 강조색(다크 바탕이면 밝은 값), 글씨 크기는 시안 그대로(17 · 13 · 15)
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val i = settings.accent.coerceIn(AccentLight.indices)
            val accent = (if (night) AccentDark[i] else AccentLight[i]).toArgb()
            val height = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

            return RemoteViews(context.packageName, R.layout.widget_tasks).apply {
                setTextViewText(R.id.date, Fmt.date(today))
                setViewVisibility(R.id.calc, if (height >= CALC_MIN_HEIGHT_DP) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(
                    R.id.calc,
                    PendingIntent.getActivity(
                        context, 20, MainActivity.calcIntent(context),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                setTextViewText(R.id.progress, if (all.isEmpty()) "" else "${all.size}개 중 ${all.count { it.isDone(today) }}개 끝냄")
                setViewVisibility(R.id.progress, if (all.isEmpty()) View.GONE else View.VISIBLE)
                setInt(R.id.add_dot, "setColorFilter", accent)
                setInt(R.id.add_icon, "setColorFilter", if (night && i == 0) 0xFF001B3D.toInt() else if (night) 0xFF0A0A0A.toInt() else 0xFFFFFFFF.toInt())
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
