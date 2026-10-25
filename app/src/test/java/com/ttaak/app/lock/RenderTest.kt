package com.ttaak.app.lock

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.ttaak.app.R
import com.ttaak.app.data.Day
import com.ttaak.app.data.TaskStore
import com.ttaak.app.widget.TaskWidgetService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 잠금화면 카드·위젯의 RemoteViews 가 실제 프레임워크에서 inflate/apply 되는지 확인합니다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun inflate(views: RemoteViews) = views.apply(context, FrameLayout(context))

    private fun seed() {
        TaskStore.init(context)
        repeat(8) { TaskStore.add("할 일 $it") }
        TaskStore.toggle(TaskStore.tasksFor(Day.today()).first().id)
    }

    @Test
    fun lockCardIsPostedAndRenders() {
        seed()
        val nm = context.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationsEnabled(true)
        LockCard.update(context)

        val posted = shadowOf(nm).getNotification(LockCard.NOTIFICATION_ID)
        assertNotNull("잠금화면 카드가 게시되어야 함", posted)
        assertEquals(Notification.VISIBILITY_PUBLIC, posted.visibility)
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)

        val n = LockCard.build(context)
        val builder = Notification.Builder.recoverBuilder(context, n)
        inflate(builder.createContentView())
        val big = inflate(builder.createBigContentView())
        val rows = big.findViewById<android.widget.LinearLayout>(R.id.rows)
        assertEquals(5, rows.childCount) // 기본 글자 '크게'는 5줄까지
    }

    @Test
    fun liveModeIsPlainPromotedNotification() {
        seed()
        TaskStore.updateSettings { it.copy(lockLive = true) }
        val n = LockCard.build(context)
        // 라이브 알림(Live Updates)은 커스텀 화면이 있으면 시스템이 올려 주지 않습니다.
        assertEquals(null, n.contentView)
        assertTrue(n.extras.getBoolean("android.requestPromotedOngoing"))
        assertEquals(1, n.actions.size)
        TaskStore.updateSettings { it.copy(lockLive = false) }
    }

    @Test
    fun widgetRowsRender() {
        seed()
        val factory = Robolectric.setupService(TaskWidgetService::class.java)
            .onGetViewFactory(android.content.Intent())
        factory.onCreate()
        factory.onDataSetChanged()
        assertEquals(8, factory.count)
        repeat(factory.count) { inflate(factory.getViewAt(it)) }
        inflate(RemoteViews(context.packageName, R.layout.widget_tasks))
    }
}
