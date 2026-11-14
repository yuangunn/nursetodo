package com.yuangunn.nursetodo.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 근무 프리셋 두 칸: 시각 유무로 나누고 하위 항목은 위 줄을 따라감, 반복해서 넣기(자정 넘김 포함). */
class PresetSplitTest {
    @Test
    fun splitKeepsSubLinesWithParent() {
        val items = listOf(
            "  위 줄 없는 하위",
            "08:00 정규 라운딩",
            "  환자상태 확인",
            "차팅",
            "  간호기록",
            "12:00 혈당측정",
            "익일 식사 넣기",
        )
        val (timed, other) = splitPresetItems(items)
        assertEquals(listOf("08:00 정규 라운딩", "  환자상태 확인", "12:00 혈당측정"), timed)
        assertEquals(listOf("  위 줄 없는 하위", "차팅", "  간호기록", "익일 식사 넣기"), other)
    }

    @Test
    fun repeatEveryTwoHoursAndAcrossMidnight() {
        assertEquals(
            listOf("08:00 라운딩", "10:00 라운딩", "12:00 라운딩", "14:00 라운딩"),
            repeatLines(" 라운딩 ", 8 * 60, 120, 14 * 60),
        )
        assertEquals(listOf("23:00 V/S", "01:00 V/S", "03:00 V/S", "05:00 V/S"), repeatLines("V/S", 23 * 60, 120, 5 * 60))
        // 끝이 간격에 딱 안 맞으면 끝 전까지만
        assertEquals(listOf("09:00 I/O", "09:30 I/O", "10:00 I/O"), repeatLines("I/O", 9 * 60, 30, 10 * 60 + 15))
        assertEquals(emptyList<String>(), repeatLines("", 8 * 60, 60, 10 * 60))
    }

    @Test
    fun examplesMatchShiftByNameOrStart() {
        // 예시는 시간별 칸엔 시각 줄만, 이번 근무 할 일 칸엔 시각 없는 줄만
        Preset.Examples.forEach { (name, ex) ->
            assertEquals(name, ex.first to ex.second, splitPresetItems(ex.first + ex.second))
        }
        assertEquals("Evening", Preset.exampleFor(Preset(9, "evening", 900, emptyList())))
        assertEquals("Night", Preset.exampleFor(Preset(9, "N근무", 22 * 60, emptyList())))
        assertEquals("Night", Preset.exampleFor(Preset(9, "새벽", 60, emptyList())))
        assertEquals("Day", Preset.exampleFor(Preset(9, "오전", 8 * 60, emptyList())))
        assertEquals("Evening", Preset.exampleFor(Preset(9, "오후", 14 * 60, emptyList())))
    }

    @Test
    fun sameTimeLinesMerge() {
        // 예시엔 같은 시각 줄이 없음
        Preset.Examples.values.forEach { (timed, _) -> assertEquals(timed.size, timed.map { itemMinute(it) }.toSet().size) }
        val now = listOf("07:00 정규라운딩", "  환자상태 확인", "09:00 정규라운딩", "인계")
        // 같은 시각은 그 줄에 ', 내용'(하위 줄은 그대로), 이미 있는 내용은 그대로, 새 시각은 맨 뒤
        assertEquals(
            listOf("07:00 정규라운딩, V/S", "  환자상태 확인", "09:00 정규라운딩", "인계", "11:00 V/S"),
            mergeTimed(now, listOf("07:00 V/S", "09:00 정규라운딩", "11:00 V/S")),
        )
        assertEquals(listOf("17:00 정규라운딩, 혈당 측정"), mergeTimed(listOf("17:00 정규라운딩"), listOf("17:00 혈당 측정", "17:00 혈당 측정")))
    }
}
