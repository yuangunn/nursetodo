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
