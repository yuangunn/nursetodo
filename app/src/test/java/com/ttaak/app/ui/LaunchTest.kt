package com.ttaak.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.ttaak.app.data.TaskStore
import com.ttaak.app.lock.QuickAddActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.io.File

/** 실제 화면이 크래시 없이 뜨는지 확인합니다. TTAAK_SHOTS 경로가 있으면 스크린샷도 남깁니다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LaunchTest {
    private fun shot(view: View, name: String) {
        val dir = System.getenv("TTAAK_SHOTS") ?: return
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun mainActivityLaunches() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        TaskStore.add("보고서 제출")
        TaskStore.add("팀 회의 자료 정리")
        TaskStore.toggle(TaskStore.add("메일 답장")!!.id)
        shot(controller.get().window.decorView, "today")
        TaskStore.updateSettings { it.copy(setupDismissed = true) }
        shot(controller.get().window.decorView, "today-list")
    }

    @Test
    fun quickAddLaunches() {
        val controller = Robolectric.buildActivity(QuickAddActivity::class.java).setup()
        shot(controller.get().window.decorView, "quickadd")
    }
}
