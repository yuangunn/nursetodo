package com.ttaak.app.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.ttaak.app.R
import com.ttaak.app.data.Day
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import com.ttaak.app.data.cardTextSp
import com.ttaak.app.lock.LockActionReceiver
import java.util.concurrent.CountDownLatch
import android.os.Handler
import android.os.Looper

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var items: List<Task> = emptyList()
        private var today = Day.today()
        private var sp = 16f

        override fun onCreate() {}

        override fun onDataSetChanged() {
            // TaskStore 는 메인 스레드 전용이므로 메인에서 읽어 옵니다.
            val load = {
                TaskStore.init(context)
                today = Day.today()
                // 끝낸 일은 위젯에서 사라지고 앱의 '완료된 할 일'에서만 보입니다.
                items = TaskStore.tasksFor(today, today).filterNot { it.isDone(today) }
                val s = TaskStore.settings.value
                sp = s.cardTextSp()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                load()
            } else {
                val latch = CountDownLatch(1)
                Handler(Looper.getMainLooper()).post { load(); latch.countDown() }
                latch.await()
            }
        }

        override fun onDestroy() {}
        override fun getCount() = items.size
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = items.getOrNull(position)?.id ?: position.toLong()
        override fun hasStableIds() = true

        override fun getViewAt(position: Int): RemoteViews {
            val task = items.getOrNull(position)
                ?: return RemoteViews(context.packageName, R.layout.widget_row)
            return RemoteViews(context.packageName, R.layout.widget_row).apply {
                setTextViewText(R.id.text, task.title)
                setTextViewTextSize(R.id.text, TypedValue.COMPLEX_UNIT_SP, sp)
                setImageViewResource(R.id.check, R.drawable.ic_check_off)
                setInt(R.id.check, "setColorFilter", 0xFF9E9E9E.toInt())
                setOnClickFillInIntent(
                    R.id.row,
                    Intent().putExtra(LockActionReceiver.EXTRA_ID, task.id),
                )
            }
        }
    }
}
