package com.ttaak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfusionTest {
    private fun rate(base: String, chemo: String, priming: String, time: String, hours: Boolean) =
        (Infusion.calc(base, chemo, priming, time, hours) as Infusion.Rate).ccPerHr.toPlainString()

    private fun invalid(base: String, chemo: String, priming: String, time: String, hours: Boolean = false) =
        (Infusion.calc(base, chemo, priming, time, hours) as Infusion.Invalid).message

    @Test
    fun issueExample() {
        // Base 100 + 항암 50 + Priming 20 = 170cc, 2시간 → 85 cc/hr
        val r = Infusion.calc("100", "50", "20", "2", timeInHours = true) as Infusion.Rate
        assertEquals(170.0, r.totalCc, 0.0)
        assertEquals("85.0", r.ccPerHr.toPlainString())
        assertEquals("85.0", rate("100", "50", "20", "120", hours = false))
    }

    @Test
    fun minutesAreConvertedToHours() {
        assertEquals("100.0", rate("100", "30", "20", "90", hours = false)) // 150 ÷ 1.5
        assertEquals("102.0", rate("100", "50", "20", "100", hours = false)) // 170 ÷ 1.666…
        assertEquals("340.0", rate("100", "50", "20", "30", hours = false))
        assertEquals("113.3", rate("100", "50", "20", "1.5", hours = true))
    }

    @Test
    fun roundsHalfUpToOneDecimal() {
        assertEquals("1.2", rate("1.15", "0", "0", "1", hours = true))
        assertEquals("12.5", rate("12.5", "0", "0", "60", hours = false))
        assertEquals("0.0", rate("0.01", "0", "0", "1", hours = true))
    }

    @Test
    fun rejectsBadInput() {
        assertEquals("Base fluid 용량을 입력하세요", invalid("", "50", "20", "120"))
        assertEquals("Priming 용량을 입력하세요", invalid("100", "50", " ", "120"))
        assertEquals("투여시간을 입력하세요", invalid("100", "50", "20", ""))
        assertEquals("투여시간은 0보다 커야 해요", invalid("100", "50", "20", "0"))
        assertEquals("투여시간: 숫자만 입력하세요", invalid("100", "50", "20", "-30"))
        assertEquals("항암 fluid: 숫자만 입력하세요", invalid("100", "abc", "20", "120"))
        assertEquals("Base fluid: 숫자만 입력하세요", invalid("1,000", "50", "20", "120"))
        assertEquals("총 주입량이 0이에요", invalid("0", "0", "0", "120"))
    }

    @Test
    fun zeroPrimingIsAllowed() {
        assertTrue(Infusion.calc("100", "50", "0", "60", false) is Infusion.Rate)
    }

    @Test
    fun numberFormatting() {
        assertEquals("170", Infusion.num(170.0))
        assertEquals("12.5", Infusion.num(12.5))
        assertEquals("1.67", Infusion.num(100 / 60.0))
    }
}
