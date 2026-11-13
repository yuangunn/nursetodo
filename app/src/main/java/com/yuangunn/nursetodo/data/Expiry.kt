package com.yuangunn.nursetodo.data

import java.time.LocalDate
import java.time.LocalDateTime

/** 사용기한 계산기의 자주 쓰는 기한. [name] 이 비면 '28일'처럼 기간만 보입니다. */
data class ExpiryPreset(val name: String, val amount: Int, val unit: Int) {
    val label: String get() = Expiry.period(amount, unit).let { if (name.isBlank()) it else "${name.trim()} · $it" }
}

/**
 * 개봉·멸균·조제한 때부터 '○일 / ○주 / ○개월 / ○시간 이내 사용'의 기한.
 * 단위 값은 저장 파일에 들어가므로 바꾸지 않습니다.
 */
object Expiry {
    const val HOURS = 0
    const val DAYS = 1
    const val WEEKS = 2
    const val MONTHS = 3
    val UNITS = listOf("시간", "일", "주", "개월")

    /** 처음 목록: 이름 없는 기간만(제품·기관마다 달라서 이름 붙인 기한은 사용자가 저장). */
    val DEFAULTS = listOf(
        ExpiryPreset("", 24, HOURS),
        ExpiryPreset("", 7, DAYS),
        ExpiryPreset("", 28, DAYS),
        ExpiryPreset("", 1, MONTHS),
        ExpiryPreset("", 3, MONTHS),
        ExpiryPreset("", 6, MONTHS),
    )

    fun period(amount: Int, unit: Int) = "$amount${UNITS[unit.coerceIn(0, 3)]}"

    /**
     * [start] 부터 [amount][unit] 뒤 기한.
     * - 기본은 시작한 다음 날이 1일째: 28일 → 시작일 + 28일(1일 개봉이면 29일). 다회용 바이알 '첫 천공 후 28일'(USP 797) 셈과 같습니다.
     * - [firstDay] 면 시작한 날이 1일째: 28일 → 시작일 + 27일(1일 개봉이면 28일). 기관 지침이 이렇게 세면 켭니다.
     * 시간은 그대로 더하고, 개월은 달력 달로 더합니다(1월 31일 + 1개월 = 2월 말일).
     */
    fun until(start: LocalDateTime, amount: Int, unit: Int, firstDay: Boolean): LocalDateTime {
        val back = if (firstDay && unit != HOURS) 1L else 0L
        val n = amount.toLong()
        return when (unit) {
            HOURS -> start.plusHours(n)
            WEEKS -> start.plusWeeks(n).minusDays(back)
            MONTHS -> start.plusMonths(n).minusDays(back)
            else -> start.plusDays(n - back)
        }
    }

    /** 계산한 기한과 제조사 유효기간 중 빠른 날. 제조사 날이 더 빠르면 그 날 하루 끝까지. */
    fun capped(until: LocalDateTime, maker: LocalDate?): Pair<LocalDateTime, Boolean> =
        if (maker != null && maker.isBefore(until.toLocalDate())) maker.atTime(23, 59) to true else until to false
}
