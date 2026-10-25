package com.ttaak.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    fun weekday(day: Long): String =
        LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

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

    /** D-3, D-DAY, D+2 */
    fun dday(target: Long, today: Long = Day.today()): String {
        val diff = target - today
        return when {
            diff > 0 -> "D-$diff"
            diff == 0L -> "D-DAY"
            else -> "D+${-diff}"
        }
    }

    /** 매일 / 평일 / 주말 / 월·수·금 */
    fun repeat(mask: Int): String = when (mask) {
        Task.EVERY_DAY -> "매일"
        0b0011111 -> "평일"
        0b1100000 -> "주말"
        else -> WEEKDAYS.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString("·")
    }

    /** 잠금화면/위젯 상단에 붙는 문구: "합격 D-12 · 오늘도 화이팅" */
    fun headline(s: Settings, today: Long = Day.today()): String = listOfNotNull(
        s.ddayDate?.let { listOf(s.ddayLabel.trim(), dday(it, today)).filter { p -> p.isNotEmpty() }.joinToString(" ") },
        s.lockText.trim().ifEmpty { null },
    ).joinToString(" · ")
}
