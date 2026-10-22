package com.ttaak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskTest {
    // 2026-10-22 는 목요일
    private val today = LocalDate.of(2026, 10, 22).toEpochDay()

    @Test
    fun undatedTaskStaysOnTodayUntilDone() {
        val t = Task(1, "a", createdDay = today - 3)
        assertTrue(t.visibleOn(today, today))
        assertFalse(t.visibleOn(today + 1, today))
        val done = t.copy(doneOn = today)
        assertTrue(done.isDone(today))
        assertTrue(done.visibleOn(today, today))
        assertFalse(done.visibleOn(today + 1, today + 1))
    }

    @Test
    fun overdueDatedTaskCarriesOverToToday() {
        val t = Task(1, "a", createdDay = today - 5, date = today - 2)
        assertTrue(t.visibleOn(today - 2, today))
        assertTrue(t.visibleOn(today, today))
        assertFalse(t.visibleOn(today - 1, today))
    }

    @Test
    fun futureTaskShowsOnlyOnItsDate() {
        val t = Task(1, "a", createdDay = today, date = today + 3)
        assertFalse(t.visibleOn(today, today))
        assertTrue(t.visibleOn(today + 3, today))
    }

    @Test
    fun routineRepeatsOnSelectedWeekdaysAndResetsDaily() {
        val thursdayBit = 1 shl 3
        val t = Task(1, "운동", createdDay = today, repeatMask = thursdayBit, doneOn = today)
        assertTrue(t.visibleOn(today, today))
        assertTrue(t.isDone(today))
        assertFalse(t.visibleOn(today + 1, today))
        assertTrue(t.visibleOn(today + 7, today))
        assertFalse(t.isDone(today + 7))
        assertFalse(t.visibleOn(today - 7, today)) // 만들기 전에는 안 보임
    }

    @Test
    fun formatting() {
        assertEquals("D-3", Fmt.dday(today + 3, today))
        assertEquals("D-DAY", Fmt.dday(today, today))
        assertEquals("D+2", Fmt.dday(today - 2, today))
        assertEquals("평일", Fmt.repeat(0b0011111))
        assertEquals("월·수·금", Fmt.repeat(0b0010101))
        assertEquals("10월 22일 (목)", Fmt.date(today))
        assertEquals("수능 D-3 · 화이팅", Fmt.headline(Settings(ddayLabel = "수능", ddayDate = today + 3, lockText = "화이팅"), today))
    }
}
