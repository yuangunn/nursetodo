package com.ttaak.app.data

import java.time.LocalDate

/**
 * 할 일 하나.
 *
 * - [date] 가 null 이면 "오늘 할 일" — 끝낼 때까지 매일 오늘 목록에 남습니다.
 * - [date] 가 지정되면 그 날짜에 보이고, 지나도록 못 끝내면 오늘 목록으로 넘어옵니다.
 * - [repeatMask] 가 0이 아니면 루틴. 월(bit0)~일(bit6) 중 켜진 요일마다 다시 나타납니다.
 * - [doneOn] 은 일반 할 일을 체크한 날(epochDay).
 * - [doneDays] 는 루틴을 체크한 날들. 날마다 따로 기록해서 캘린더에서 지난 날도 맞게 보입니다.
 */
data class Task(
    val id: Long,
    val title: String,
    val createdDay: Long,
    val date: Long? = null,
    val repeatMask: Int = 0,
    val doneOn: Long? = null,
    val order: Long = 0,
    // ponytail: 매일 루틴이면 1년에 365개씩 쌓임. 수천 개가 돼도 JSON 몇 KB라 정리하지 않음
    val doneDays: Set<Long> = emptySet(),
) {
    val isRoutine: Boolean get() = repeatMask != 0

    fun isDone(day: Long): Boolean = if (isRoutine) day in doneDays else doneOn != null

    /** [day] 에 체크/체크 해제한 결과. */
    fun toggled(day: Long): Task = when {
        isRoutine -> copy(doneDays = if (day in doneDays) doneDays - day else doneDays + day)
        doneOn != null -> copy(doneOn = null)
        else -> copy(doneOn = day)
    }

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
    /** 계산기: 투여시간 단위(true=시간, false=분)와 Base fluid 종류(0=NS, 1=5DW). 용량은 저장하지 않습니다. */
    val calcHours: Boolean = false,
    val calcFluid: Int = 0,
)

object Day {
    fun today(): Long = LocalDate.now().toEpochDay()
}
