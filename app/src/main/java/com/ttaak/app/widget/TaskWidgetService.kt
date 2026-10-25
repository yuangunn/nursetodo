package com.ttaak.app.widget

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import com.ttaak.app.R
import com.ttaak.app.data.Day
import com.ttaak.app.data.Task
import com.ttaak.app.data.TaskStore
import com.ttaak.app.lock.LockActionReceiver
import com.ttaak.app.ui.Accents
import java.util.concurrent.CountDownLatch
import android.os.Handler
import android.os.Looper

class TaskWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var items: List<Task> = emptyList()
        private var today = Day.today()
        private var accent = 0

        override fun onCreate() {}

        override fun onDataSetChanged() {
            // TaskStore 는 메인 스레드 전용이므로 메인에서 읽어 옵니다.
            val load = {
                TaskStore.init(context)
                today = Day.today()
                items = TaskStore.tasksFor(today, today)
                val s = TaskStore.settings.value
                accent = Accents[s.accent.coerceIn(Accents.indices)].toArgb()
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
            val done = task.isDone(today)
            return RemoteViews(context.packageName, R.layout.widget_row).apply {
                val text: CharSequence = if (done) {
                    SpannableString(task.title).apply {
                        setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else task.title
                setTextViewText(R.id.text, text)
                if (done) setTextColor(R.id.text, 0xFF9E9E9E.toInt())
                setImageViewResource(R.id.check, if (done) R.drawable.ic_check_on else R.drawable.ic_check_off)
                setInt(R.id.check, "setColorFilter", if (done) accent else 0xFF9E9E9E.toInt())
                setOnClickFillInIntent(
                    R.id.row,
                    Intent().putExtra(LockActionReceiver.EXTRA_ID, task.id),
                )
            }
        }
    }
}
