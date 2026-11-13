package com.yuangunn.nursetodo.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    fun weekday(day: Long): String =
        LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

    /** 남은 시간: 4:05 / 1:02:03. 지났으면 0:00 */
    fun countdown(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** 오늘 14:05 / 어제 09:30 / 10월 22일 14:05 */
    fun stamp(ms: Long, today: Long = Day.today()): String {
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        val time = "%02d:%02d".format(t.hour, t.minute)
        return when (t.toLocalDate().toEpochDay() - today) {
            0L -> "오늘 $time"
            -1L -> "어제 $time"
            else -> "${t.monthValue}월 ${t.dayOfMonth}일 $time"
        }
    }

    /** 줄 오른쪽 작은 시각: 오늘이면 14:05, 어제, 그 전은 11/10 */
    fun clock(ms: Long, today: Long = Day.today()): String {
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return when (t.toLocalDate().toEpochDay() - today) {
            0L -> "%02d:%02d".format(t.hour, t.minute)
            -1L -> "어제"
            else -> "${t.monthValue}/${t.dayOfMonth}"
        }
    }

    /** 10월 22일 (수) */
    fun date(day: Long): String {
        val d = LocalDate.ofEpochDay(day)
        return "${d.monthValue}월 ${d.dayOfMonth}일 (${weekday(day)})"
    }

    /** 오늘 / 내일 / 어제 / 10월 22일 (수) */
    fun relative(day: Long, today: Long = Day.today()): String = when (day - today) {
        0L -> "오늘"
        1L -> "내일"
        -1L -> "어제"
        else -> date(day)
    }

    /** 매일 / 평일 / 주말 / 월·수·금 */
    fun repeat(mask: Int): String = when (mask) {
        Task.EVERY_DAY -> "매일"
        0b0011111 -> "평일"
        0b1100000 -> "주말"
        else -> WEEKDAYS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString("·")
    }
}
