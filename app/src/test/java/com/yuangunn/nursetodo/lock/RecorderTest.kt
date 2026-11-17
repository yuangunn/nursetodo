package com.yuangunn.nursetodo.lock

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.yuangunn.nursetodo.data.Shortcut
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 녹음 파일 이름 ↔ 시작 시각, 목록 순서(새것 먼저, 녹음 중인 것·다른 파일 뺌), 걸린 시간 글, 기본 버튼 목록. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecorderTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        Recorder.current = null
        Recorder.dir(context).deleteRecursively()
    }

    @Test
    fun fileNameKeepsStartAndListIsNewestFirst() {
        val t1 = 1_763_340_000_000L // 초 단위로 떨어지는 시각
        val t2 = t1 + 3_600_000
        val t3 = t2 + 60_000
        val a = Recorder.fileFor(context, t1).apply { writeText("a") }
        val b = Recorder.fileFor(context, t2).apply { writeText("b") }
        val c = Recorder.fileFor(context, t3).apply { writeText("c") }
        java.io.File(Recorder.dir(context), "memo.txt").writeText("x")
        assertTrue(a.name.startsWith("녹음_") && a.name.endsWith(".aac"))
        assertEquals(t1, Recorder.startedAt(a))
        assertEquals(listOf(c, b, a), Recorder.list(context))
        // 녹음 중인 파일은 목록에 없음
        Recorder.current = c
        assertEquals(listOf(b, a), Recorder.list(context))
    }

    @Test
    fun elapsedText() {
        assertEquals("0:00", Recorder.elapsed(-5))
        assertEquals("0:09", Recorder.elapsed(9_999))
        assertEquals("12:05", Recorder.elapsed(725_000))
        assertEquals("1:02:03", Recorder.elapsed(3_723_000))
    }

    @Test
    fun recordIsBuiltInButNotDefault() {
        assertTrue(Shortcuts.builtIns(context).any { it.id == Shortcut.RECORD })
        assertEquals("녹음", Shortcuts.name(context, Shortcut(Shortcut.RECORD)))
        assertTrue(com.yuangunn.nursetodo.data.Settings().shortcuts.none { it.id == Shortcut.RECORD })
    }
}
