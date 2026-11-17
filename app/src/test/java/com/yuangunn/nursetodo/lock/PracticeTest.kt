package com.yuangunn.nursetodo.lock

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.yuangunn.nursetodo.data.Settings
import com.yuangunn.nursetodo.data.TaskStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 잠금화면 체험: 예시 데이터로 바꾸고, 체험 중 바꾼 것은 파일에 쓰지 않으며, 끝내면 원래 데이터로 돌아감. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PracticeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    // 저장소는 프로세스에 한 번만 초기화되어 파일은 처음 시험의 폴더에 있음
    private val file get() = TaskStore::class.java.getDeclaredField("file").apply { isAccessible = true }.get(TaskStore) as File
    private lateinit var prior: TaskStore.Snapshot

    @Before
    fun reset() {
        TaskStore.init(context)
        prior = TaskStore.snapshot()
        TaskStore.onChanged = null
        TaskStore.tasks.value.forEach { TaskStore.delete(it.id) }
        TaskStore.pockets.value.forEach { TaskStore.deletePocket(it.id) }
        TaskStore.updateSettings { Settings(presetMode = false, accent = 3) }
        TaskStore.add("실제 할 일")
    }

    // 다른 시험(위젯 줄 수 등)이 같은 저장소를 쓰므로 원래대로
    @After
    fun tearDown() {
        Practice.end(context)
        TaskStore.onChanged = null
        TaskStore.restore(prior)
    }

    @Test
    fun practiceUsesSamplesAndRestoresWithoutWriting() {
        val before = TaskStore.tasks.value
        val saved = file.readText()
        var surfaces = 0
        TaskStore.onChanged = { surfaces++ }

        Practice.begin(context)
        assertTrue(Practice.active.value && TaskStore.practicing)
        val s = TaskStore.settings.value
        // 교대 프리셋 예시 · 앞 근무를 받아 둔 상태 · 설정의 나머지(강조색)는 그대로
        assertTrue(s.presetMode && s.presets.map { it.name } == listOf("Day", "Evening", "Night") && s.dutyShift != 0L)
        assertEquals(3, s.accent)
        assertTrue(TaskStore.tasks.value.none { it.title == "실제 할 일" })
        assertTrue(TaskStore.tasks.value.any { it.shift == null && it.doneOn != null })
        assertTrue(TaskStore.pockets.value.isNotEmpty())

        // 체험 중 바꾼 것은 파일·잠금화면 카드에 안 감
        TaskStore.add("체험에서 적은 것")
        TaskStore.updateSettings { it.copy(accent = 5) }
        assertEquals(saved, file.readText())
        assertEquals(0, surfaces)

        Practice.end(context)
        assertFalse(Practice.active.value || TaskStore.practicing)
        assertEquals(before, TaskStore.tasks.value)
        assertEquals(3, TaskStore.settings.value.accent)
        assertFalse(TaskStore.settings.value.presetMode)
        assertFalse(file.readText().contains("체험에서 적은 것"))
        assertEquals(1, surfaces)
    }

    @Test
    fun practiceNotificationsDismissOnlySamples() {
        Practice.begin(context)
        val n = NotiListener.practice.value
        // 알림판이 아니면(서비스 선언 없음) 예시 알림 없음
        if (NotiListener.available(context)) {
            assertEquals(3, n!!.size)
            NotiListener.dismiss(listOf(n.first().key))
            assertEquals(2, NotiListener.practice.value!!.size)
        } else {
            assertEquals(null, n)
        }
        Practice.end(context)
        assertEquals(null, NotiListener.practice.value)
    }
}
