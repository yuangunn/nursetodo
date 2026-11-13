package com.yuangunn.nursetodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** 사용기한: 기본(다음 날이 1일째)·시작일 1일째, 주·개월(달 말일), 시간, 제조사 유효기간이 더 빠를 때. */
class ExpiryTest {
    private val jan1 = LocalDateTime.of(2026, 1, 1, 14, 20)

    @Test
    fun daysCountFromNextDayByDefault() {
        assertEquals(LocalDate.of(2026, 1, 29), Expiry.until(jan1, 28, Expiry.DAYS, firstDay = false).toLocalDate())
        assertEquals(LocalDate.of(2026, 1, 28), Expiry.until(jan1, 28, Expiry.DAYS, firstDay = true).toLocalDate())
    }

    @Test
    fun weeksMonthsAndHours() {
        assertEquals(LocalDate.of(2026, 1, 29), Expiry.until(jan1, 4, Expiry.WEEKS, false).toLocalDate())
        assertEquals(LocalDate.of(2026, 2, 28), Expiry.until(LocalDateTime.of(2026, 1, 31, 9, 0), 1, Expiry.MONTHS, false).toLocalDate())
        assertEquals(LocalDate.of(2026, 6, 30), Expiry.until(jan1, 6, Expiry.MONTHS, true).toLocalDate())
        // 시간은 시작한 날 셈과 상관없이 그대로
        assertEquals(LocalDateTime.of(2026, 1, 2, 14, 20), Expiry.until(jan1, 24, Expiry.HOURS, true))
    }

    @Test
    fun makerExpiryWinsWhenEarlier() {
        val until = Expiry.until(jan1, 28, Expiry.DAYS, false)
        val (early, cut) = Expiry.capped(until, LocalDate.of(2026, 1, 20))
        assertTrue(cut)
        assertEquals(LocalDate.of(2026, 1, 20), early.toLocalDate())
        val (same, kept) = Expiry.capped(until, LocalDate.of(2026, 3, 1))
        assertFalse(kept)
        assertEquals(until, same)
    }

    @Test
    fun presetLabel() {
        assertEquals("28일", ExpiryPreset("", 28, Expiry.DAYS).label)
        assertEquals("인슐린 펜 · 4주", ExpiryPreset(" 인슐린 펜 ", 4, Expiry.WEEKS).label)
    }
}
