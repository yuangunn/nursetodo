package com.yuangunn.nursetodo.lock

import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Fmt
import com.yuangunn.nursetodo.data.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 잠금화면 표시 규칙: 남은 시간 표기, 근무 줄 미체크·다음, 키워드 알림을 할 일로 넣을 문장. */
class LockLogicTest {
    @Test
    fun leftTimeHasNoColon() {
        assertEquals("45초", fmtLeft(44_200))
        assertEquals("28분", fmtLeft(27 * 60_000L + 30_000))
        assertEquals("1시간", fmtLeft(60 * 60_000L))
        assertEquals("1시간 12분", fmtLeft(72 * 60_000L))
        assertEquals("0초", fmtLeft(-5_000))
    }

    @Test
    fun dutyRowsMissedAfterGraceThenNext() {
        fun ms(h: Int, m: Int, plusDays: Long = 0) =
            LocalDateTime.of(2026, 11, 12, h, m).plusDays(plusDays).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val shift = ms(23, 0)
        val rows = listOf("23:00 인계", "00:00 라운딩", "00:30 수액", "02:00 라운딩", "물품 정리").mapIndexed { i, t ->
            Task(i.toLong(), t, 0, shift = shift, doneOn = if (i == 0) 1L else null)
        }
        // 00:40: 00:00 은 30분 넘게 지나 미체크, 00:30 은 유예 안이라 다음, 그 뒤는 나머지. 자정을 넘겨도 순서대로.
        val st = dutyStates(rows, ms(0, 40, plusDays = 1))
        assertEquals(listOf(Duty.DONE, Duty.MISSED, Duty.NEXT, Duty.REST, Duty.REST), rows.map { st[it.id] })
        // 다 지나면 모두 미체크, 시각 없는 줄은 그대로
        val late = dutyStates(rows, ms(5, 0, plusDays = 1))
        assertEquals(listOf(Duty.DONE, Duty.MISSED, Duty.MISSED, Duty.MISSED, Duty.REST), rows.map { late[it.id] })
    }

    @Test
    fun doneSectionInOrderOfFinishing() {
        val today = 20_000L
        val tasks = listOf(
            Task(1, "늦게 끝냄", 0, doneOn = today, doneAt = 3_000),
            Task(2, "먼저 끝냄", 0, doneOn = today, doneAt = 1_000),
            Task(3, "아직", 0),
            Task(4, "방금 끝냄(토스트 중)", 0, doneOn = today, doneAt = 5_000),
            Task(5, "근무 할 일", 0, doneOn = today, doneAt = 2_000, shift = 9),
        )
        // 끝낸 순서대로, 되돌리기 토스트가 떠 있는 것은 아직 제자리
        assertEquals(listOf(2L, 5L, 1L), doneToday(tasks, today, recent = setOf(4L)).map { it.id })
        // 교대 프리셋이면 근무 할 일은 근무 페이지에만
        assertEquals(listOf(2L, 1L), doneToday(tasks, today, recent = setOf(4L), preset = true).map { it.id })
    }

    @Test
    fun rowTimeIsShortClock() {
        fun ms(d: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, d, h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = Day.of(ms(12, 12, 0))
        assertEquals("09:05", Fmt.clock(ms(12, 9, 5), today))
        assertEquals("어제", Fmt.clock(ms(11, 23, 59), today))
        assertEquals("11/10", Fmt.clock(ms(10, 8, 0), today))
    }

    @Test
    fun keywordTodoIsFirstSentenceUpTo40() {
        fun hit(text: String, title: String = "병동") = KeywordAlert.Hit("k", "p", "문자", "응급", title, text, 0, null)
        assertEquals("응급실에서 703호로 입원 올라옵니다", KeywordAlert.todoText(hit("응급실에서 703호로 입원 올라옵니다. 14시 30분 예정입니다.")))
        assertEquals("705호 입원 배정되었습니다", KeywordAlert.todoText(hit("705호 입원 배정되었습니다")))
        assertEquals(40, KeywordAlert.todoText(hit("가".repeat(60))).length)
        assertEquals("병동", KeywordAlert.todoText(hit("")))
    }

    @Test
    fun carriedRowsStayOnTopInHandoverOrder() {
        val shift = LocalDateTime.of(2026, 11, 12, 17, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rows = listOf(
            Task(1, "18:00 라운딩", 0, order = 5, shift = shift),
            Task(2, "16:00 라운딩", 0, order = 1, shift = shift, carried = "Evening"),
            Task(3, "15:30 수액", 0, order = 0, shift = shift, carried = "Evening"),
            Task(4, "17:00 인계", 0, order = 4, shift = shift),
            Task(5, "물품", 0, order = 3, shift = shift),
        )
        assertEquals(listOf(3L, 2L, 4L, 1L, 5L), dutyOrder(rows).map { it.id })
        assertEquals("Night로", withRo("Night"))
        assertEquals("Evening으로", withRo("Evening"))
        assertEquals("데이로", withRo("데이"))
        assertEquals("야간으로", withRo("야간"))
        assertEquals("오후 근무로", withRo("오후 근무"))
    }
}
