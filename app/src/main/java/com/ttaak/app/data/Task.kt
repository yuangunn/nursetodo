package com.ttaak.app.data

import java.time.LocalDate

/**
 * 할 일 하나.
 *
 * - [date] 가 null 이면 "오늘 할 일" — 끝낼 때까지 매일 오늘 목록에 남습니다.
 * - [date] 가 지정되면 그 날짜에 보이고, 지나도록 못 끝내면 오늘 목록으로 넘어옵니다.
 * - [repeatMask] 가 0이 아니면 루틴. 월(bit0)~일(bit6) 중 켜진 요일마다 다시 나타납니다.
 * - [doneOn] 은 체크한 날(epochDay). 루틴은 그날만 완료로 취급합니다.
 */
data class Task(
    val id: Long,
    val title: String,
    val createdDay: Long,
    val date: Long? = null,
    val repeatMask: Int = 0,
    val doneOn: Long? = null,
    val order: Long = 0,
) {
    val isRoutine: Boolean get() = repeatMask != 0

    fun isDone(day: Long): Boolean = if (isRoutine) doneOn == day else doneOn != null

    fun repeatsOn(day: Long): Boolean = repeatMask and weekdayBit(day) != 0

    fun visibleOn(day: Long, today: Long): Boolean {
        if (isRoutine) return day >= createdDay && repeatsOn(day)
        doneOn?.let { return it == day }
        val due = date ?: return day == today
        return due == day || (day == today && due < today)
    }

    companion object {
        const val EVERY_DAY = 0b1111111

        fun weekdayBit(day: Long): Int = 1 shl (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)
    }
}

/** 화면/잠금화면 설정. */
data class Settings(
    val newOnTop: Boolean = false,
    val lockEnabled: Boolean = true,
    val lockHideDone: Boolean = false,
    val lockText: String = "",
    val ddayLabel: String = "",
    val ddayDate: Long? = null,
    val accent: Int = 0,
    val textSize: Int = 1,
    val bold: Boolean = false,
    val checkOnLeft: Boolean = true,
    val setupDismissed: Boolean = false,
    val tileAdded: Boolean = false,
)

object Day {
    fun today(): Long = LocalDate.now().toEpochDay()
}
