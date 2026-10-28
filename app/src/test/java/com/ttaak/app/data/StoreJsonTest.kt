package com.ttaak.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 저장 파일(JSON) 읽기·쓰기. org.json 이 안드로이드 구현이라 Robolectric 으로 돌립니다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreJsonTest {
    @Test
    fun legacyRoutineDoneOnMovesToDoneDays() = with(TaskStore) {
        // 1.1 에서 저장한 루틴
        val t = JSONObject("""{"id":1,"title":"운동","created":20000,"repeat":127,"doneOn":20010,"order":0}""").toTask()
        assertEquals(setOf(20010L), t.doneDays)
        assertEquals(null, t.doneOn)
    }

    @Test
    fun shortcutsRoundTrip() = with(TaskStore) {
        val s = Settings(shortcuts = listOf(Shortcut(Shortcut.ALARM), Shortcut.app("com.example/.Main").copy(name = "EMR")))
        assertEquals(s.shortcuts, s.toJson().toSettings().shortcuts)
        // 저장된 적 없으면 기본: 항암계산기·알람·캘린더
        assertEquals(listOf(Shortcut(Shortcut.CALC), Shortcut(Shortcut.ALARM), Shortcut(Shortcut.CALENDAR)), JSONObject("{}").toSettings().shortcuts)
    }

    @Test
    fun notiAppsRoundTrip() = with(TaskStore) {
        val s = Settings(notiApps = setOf("com.microsoft.teams", "com.samsung.android.messaging"))
        assertEquals(s.notiApps, s.toJson().toSettings().notiApps)
        // 저장된 적 없으면 모든 앱
        assertEquals(emptySet<String>(), JSONObject("{}").toSettings().notiApps)
    }

    @Test
    fun shortcutStyleAndAddButton() = with(TaskStore) {
        val s = Settings(shortcutStyle = 2, addButton = 1)
        assertEquals(2 to 1, s.toJson().toSettings().let { it.shortcutStyle to it.addButton })
        assertEquals(8, s.shortcutSlots())
        // 1.3.0 처럼 저장된 적 없으면 이름 3줄(9칸)·카드 아래 버튼
        val d = JSONObject("{}").toSettings()
        assertEquals(0 to 0, d.shortcutStyle to d.addButton)
        assertEquals(9, d.shortcutSlots())
        assertEquals(2, JSONObject("""{"shortcutStyle":7}""").toSettings().shortcutStyle)
    }

    @Test
    fun cardSpFromLegacyIndex() = with(TaskStore) {
        // 1.3.0 까지의 '보통·크게·아주 크게' 순번이 같은 크기(sp)로 옮겨지는지
        assertEquals(16, JSONObject("""{"cardTextSize":0}""").toSettings().cardSp)
        assertEquals(23, JSONObject("""{"cardTextSize":2}""").toSettings().cardSp)
        assertEquals(19, JSONObject("{}").toSettings().cardSp)
        val s = Settings(cardSp = 30, rowGap = 4)
        assertEquals(s.cardSp to s.rowGap, s.toJson().toSettings().let { it.cardSp to it.rowGap })
    }

    @Test
    fun alarmRoundTrips() = with(TaskStore) {
        val a = Alarm(1_761_000_000_000, 1_761_001_800_000, "항암 끝")
        assertEquals(a, a.toJson().toAlarm())
        assertEquals(30L, a.minutes)
    }

    @Test
    fun doneAtRoundTrips() = with(TaskStore) {
        val t = Task(5, "채혈", createdDay = 20000, doneOn = 20010, doneAt = 1_761_000_000_000)
        assertEquals(t, t.toJson().toTask())
    }

    @Test
    fun legacyPlainTaskKeepsDoneOn() = with(TaskStore) {
        val t = JSONObject("""{"id":1,"title":"보고서","created":20000,"repeat":0,"doneOn":20010,"order":3}""").toTask()
        assertEquals(20010L, t.doneOn)
        assertEquals(emptySet<Long>(), t.doneDays)
    }

    @Test
    fun roundTrip() = with(TaskStore) {
        val t = Task(7, "물 마시기", createdDay = 20000, repeatMask = Task.EVERY_DAY, order = -2, doneDays = setOf(20001, 20003))
        assertEquals(t, t.toJson().toTask())
        val dated = Task(8, "회의", createdDay = 20000, date = 20005, doneOn = 20005)
        assertEquals(dated, dated.toJson().toTask())
    }
}
