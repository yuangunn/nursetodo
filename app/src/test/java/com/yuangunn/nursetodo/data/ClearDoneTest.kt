package com.yuangunn.nursetodo.data

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 완료 모두 지우기: 그날 끝낸 일반 할 일만 지우고 루틴·근무 할 일·안 끝낸 일·다른 날 끝낸 일은 남김, 되돌리면 그대로. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClearDoneTest {
    @Before
    @After
    fun reset() {
        TaskStore.init(ApplicationProvider.getApplicationContext())
        TaskStore.onChanged = null
        TaskStore.tasks.value.forEach { TaskStore.delete(it.id) }
    }

    @Test
    fun clearsOnlyPlainDoneOfThatDay() {
        val today = Day.today()
        val done = TaskStore.add("끝낸 일")!!
        val open = TaskStore.add("남은 일")!!
        val routine = TaskStore.add("매일 루틴")!!.also { TaskStore.update(it.copy(repeatMask = Task.EVERY_DAY)) }
        val duty = TaskStore.add("근무 할 일")!!.also { TaskStore.update(it.copy(shift = 1L)) }
        val yesterday = TaskStore.add("어제 끝낸 일")!!
        listOf(done, routine, duty).forEach { TaskStore.toggle(it.id, today) }
        TaskStore.update(TaskStore.get(yesterday.id)!!.copy(doneOn = today - 1))
        val before = TaskStore.tasks.value

        val gone = TaskStore.deleteDone(today)

        assertEquals(listOf(done.id), gone.map { it.id })
        assertEquals(setOf(open.id, routine.id, duty.id, yesterday.id), TaskStore.tasks.value.map { it.id }.toSet())
        TaskStore.restoreAll(gone)
        assertEquals(before.sortedBy { it.id }, TaskStore.tasks.value.sortedBy { it.id })
    }
}
