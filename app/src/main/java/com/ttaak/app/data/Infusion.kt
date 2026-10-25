package com.ttaak.app.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 항암약물 주입속도(cc/hr) 계산.
 *
 * priming 은 항암제가 아닌 별도 수액으로 line 을 채운 용량이라, 그 용량까지 환자에게 들어갑니다.
 * (항암제로 바로 priming 하면 간호사·환자가 노출될 수 있어서 쓰지 않는 방식)
 * priming 을 빼고 계산하고 싶을 때는 [calc] 의 includePriming 을 끕니다.
 *
 *   총 주입량(cc)   = Base fluid + 항암 fluid (+ Priming)
 *   주입속도(cc/hr) = 총 주입량 ÷ 투여시간(hr)
 */
object Infusion {
    sealed interface Result

    /** [ccPerHr] 는 소수 첫째 자리에서 반올림한 값. */
    data class Rate(val totalCc: Double, val hours: Double, val ccPerHr: BigDecimal) : Result

    data class Invalid(val message: String) : Result

    /** 화면 입력 글자 그대로 받아서 검증하고 계산합니다. */
    fun calc(
        base: String,
        chemo: String,
        priming: String,
        time: String,
        timeInHours: Boolean,
        includePriming: Boolean = true,
    ): Result {
        val fields = listOf("Base fluid" to base, "항암 fluid" to chemo) +
            listOfNotNull(("Priming" to priming).takeIf { includePriming })
        val volumes = fields.map { (name, text) ->
            parse(text) ?: return Invalid(if (text.isBlank()) "$name 용량을 입력하세요" else "$name: 숫자만 입력하세요")
        }
        val t = parse(time) ?: return Invalid(if (time.isBlank()) "투여시간을 입력하세요" else "투여시간: 숫자만 입력하세요")
        if (t <= 0) return Invalid("투여시간은 0보다 커야 해요")
        val total = volumes.sum()
        if (total <= 0) return Invalid("총 주입량이 0이에요")
        val hours = if (timeInHours) t else t / 60
        val rate = BigDecimal.valueOf(total / hours).setScale(1, RoundingMode.HALF_UP)
        return Rate(total, hours, rate)
    }

    /** 0 이상의 숫자만 받습니다. "1,000" 같은 쉼표는 소수점으로 오해할 수 있어서 받지 않습니다. */
    private fun parse(text: String): Double? =
        text.trim().takeIf { it.matches(Regex("""\d+(\.\d+)?|\.\d+""")) }?.toDoubleOrNull()

    /** 170.0 → "170", 12.5 → "12.5", 1.666… → "1.67" */
    fun num(x: Double): String =
        BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
