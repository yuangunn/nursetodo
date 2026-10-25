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
        val t = Task(1, "운동", createdDay = today, repeatMask = thursdayBit, doneDays = setOf(today))
        assertTrue(t.visibleOn(today, today))
        assertTrue(t.isDone(today))
        assertFalse(t.visibleOn(today + 1, today))
        assertTrue(t.visibleOn(today + 7, today))
        assertFalse(t.isDone(today + 7))
        assertFalse(t.visibleOn(today - 7, today)) // 만들기 전에는 안 보임
    }

    @Test
    fun routineKeepsEachDaySeparately() {
        // 어제 체크한 것이 오늘 체크를 지우면 안 됨 (1.1 까지는 doneOn 하나라 덮어썼음)
        val t = Task(1, "물 마시기", createdDay = today - 7, repeatMask = Task.EVERY_DAY)
            .toggled(today).toggled(today - 1)
        assertTrue(t.isDone(today))
        assertTrue(t.isDone(today - 1))
        assertFalse(t.isDone(today - 2))
        assertFalse(t.toggled(today).isDone(today))
        assertTrue(t.toggled(today).isDone(today - 1))
    }

    @Test
    fun plainTaskToggle() {
        val t = Task(1, "a", createdDay = today).toggled(today)
        assertEquals(today, t.doneOn)
        assertEquals(null, t.toggled(today).doneOn)
    }

    @Test
    fun doneTimeIsRecorded() {
        val now = java.time.LocalDate.ofEpochDay(today).atTime(14, 5)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val t = Task(now - 3_600_000, "a", createdDay = today).toggled(today, now)
        assertEquals(now, t.doneAtOn(today))
        assertEquals(null, t.toggled(today).doneAt) // 체크 해제하면 지움
        assertEquals("오늘 14:05", Fmt.stamp(now, today))
        assertEquals("오늘 13:05", Fmt.stamp(t.createdAt, today))
        assertEquals("어제 14:05", Fmt.stamp(now, today + 1))
        // 루틴은 마지막 체크가 그날일 때만 시각을 압니다.
        val r = Task(1, "r", createdDay = today, repeatMask = Task.EVERY_DAY).toggled(today - 1, now - 86_400_000).toggled(today, now)
        assertEquals(now, r.doneAtOn(today))
        assertEquals(null, r.doneAtOn(today - 1))
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
