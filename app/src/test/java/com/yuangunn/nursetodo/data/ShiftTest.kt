package com.yuangunn.nursetodo.data

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** 교대는 받아야 바뀜: 받기 전·늦음·두 근무 지남, 미체크 가져오기·지우기, 포켓북 체크·루틴 알림, 되돌리기, 하위 항목. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShiftTest {
    private val day = LocalDateTime.of(2026, 11, 7, 0, 0)
    private fun at(h: Int, m: Int = 0, plusDays: Long = 0) = day.plusDays(plusDays).withHour(h).withMinute(m)
    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val presets = listOf(
        Preset(1, "Day", 7 * 60, listOf("07:00 인계")),
        Preset(2, "Evening", 15 * 60, listOf("15:00 인계", "21:00 취침 전 투약", "22:30 I/O 마감")),
        Preset(3, "Night", 23 * 60, listOf("23:00 인계", "02:00 정규 라운딩", "  환자상태 확인", "  수액 라운딩", "  배액관 양상 확인")),
    )

    @Before
    @After
    fun reset() {
        TaskStore.init(ApplicationProvider.getApplicationContext())
        TaskStore.onChanged = null
        TaskStore.tasks.value.forEach { TaskStore.delete(it.id) }
        TaskStore.pockets.value.forEach { TaskStore.deletePocket(it.id) }
        TaskStore.updateSettings { Settings(presetMode = true, presets = presets) }
    }

    private fun evening() = TaskStore.loadPreset(presets[1], ms(at(15)))
    private fun dutyTitles() = TaskStore.tasks.value.filter { it.shift == TaskStore.settings.value.presetApplied }.map { it.title }

    @Test
    fun headerFollowsHandoverRule() {
        evening()
        val s = { TaskStore.settings.value }
        // 받기 전: 지금 Evening 을 받을 차례
        assertEquals(DutyHead(presets[1], presets[1], ms(at(15)), received = false), s().dutyHead(at(16)))
        TaskStore.receive(presets[1], ms(at(15)), carry = false)
        assertTrue(s().dutyHead(at(16))!!.received)
        // 23시 1시간 전부터 Night 를 받을 차례, 지나면 늦음
        assertEquals(DutyHead(presets[1], presets[2], ms(at(23)), received = false), s().dutyHead(at(22, 5)))
        assertTrue(s().dutyHead(at(23, 20))!!.late)
        // Night 도 07:00 에 끝나도록 안 받으면 지금 시각의 Day 를 시작할지 물음
        val stale = s().dutyHead(at(7, 40, plusDays = 1))!!
        assertEquals("Day", stale.target.name)
        assertEquals("Night" to ms(at(7, plusDays = 1)), stale.stale)
        // 받기 전까지 목록은 Evening 그대로 (시각이 지나도 저절로 안 바뀜)
        TaskStore.refreshPreset(at(7, 40, plusDays = 1))
        assertEquals(ms(at(15)), s().presetApplied)
    }

    @Test
    fun receivingCarriesOrDropsUnchecked() {
        evening()
        TaskStore.toggle(TaskStore.tasks.value.first { it.title == "15:00 인계" }.id)
        TaskStore.updateSettings { it.copy(routineMuted = true) }
        TaskStore.addPockets("# 정규 라운딩\n- V/S\n- 투약")
        TaskStore.togglePocketCheck(TaskStore.pockets.value.first().id, "0")
        val before = TaskStore.snapshot()

        val (cleared, unmuted) = TaskStore.receive(presets[2], ms(at(23)), carry = true)
        assertEquals(1, cleared)
        assertTrue(unmuted)
        val s = TaskStore.settings.value
        assertEquals(ms(at(23)), s.presetApplied)
        assertEquals(ms(at(23)), s.dutyShift)
        assertFalse(s.routineMuted)
        // 못 끝낸 Evening 두 개가 Night 맨 위로, 'Evening'에서 넘어옴
        val night = TaskStore.tasks.value.filter { it.shift == ms(at(23)) }.sortedBy { it.order }
        assertEquals(listOf("21:00 취침 전 투약", "22:30 I/O 마감", "23:00 인계", "02:00 정규 라운딩"), night.map { it.title })
        assertEquals(listOf("Evening", "Evening", null, null), night.map { it.carried })
        // 하위 항목은 위 줄에 묶임
        assertEquals(listOf("환자상태 확인", "수액 라운딩", "배액관 양상 확인"), night.last().subs)
        assertTrue(TaskStore.pockets.value.first().checks.isEmpty())

        // 10초 되돌리기: 받기 전 그대로
        TaskStore.restore(before)
        assertEquals(ms(at(15)), TaskStore.settings.value.presetApplied)
        assertTrue(TaskStore.settings.value.routineMuted)
        assertEquals(setOf("0"), TaskStore.pockets.value.first().checks)

        // 지우고 받기: 끝낸 기록만 남고 못 끝낸 것은 사라짐
        TaskStore.receive(presets[2], ms(at(23)), carry = false)
        assertEquals(listOf("23:00 인계", "02:00 정규 라운딩"), dutyTitles())
        assertEquals(1, TaskStore.tasks.value.count { it.title == "15:00 인계" && it.doneOn != null })
    }

    @Test
    fun subItemsCheckTogether() {
        TaskStore.receive(presets[2], ms(at(23)), carry = false)
        val round = TaskStore.tasks.value.first { it.subs.isNotEmpty() }
        TaskStore.toggleSub(round.id, 0)
        TaskStore.toggleSub(round.id, 1)
        assertEquals(null, TaskStore.get(round.id)!!.doneOn)
        TaskStore.toggleSub(round.id, 2)
        assertTrue(TaskStore.get(round.id)!!.doneOn != null)
        // 위 동그라미는 모두 풀기 / 모두 체크
        TaskStore.toggle(round.id)
        assertEquals(emptySet<Int>(), TaskStore.get(round.id)!!.subsDone)
        TaskStore.toggle(round.id)
        assertEquals(setOf(0, 1, 2), TaskStore.get(round.id)!!.subsDone)
        // 저장해도 그대로
        with(TaskStore) {
            val t = get(round.id)!!.copy(carried = "Evening")
            assertEquals(t, t.toJson().toTask())
        }
        // 하위 줄은 루틴 알림이 아님
        val armed = TaskStore.settings.value
        assertEquals(listOf("23:00 인계", "02:00 정규 라운딩"), armed.reminders(ms(at(22))).map { it.text })
        assertEquals(emptyList<Reminder>(), armed.copy(routineMuted = true).reminders(ms(at(22))))
    }

    @Test
    fun presetRowsGroupIndentedLines() {
        // 위에 항목이 없는 하위 줄은 항목으로, 빈 줄은 뺌, 탭도 들여쓰기
        assertEquals(
            listOf("c0" to emptyList(), "a" to listOf("a1", "a2"), "b" to emptyList<String>()),
            presetRows(listOf("  c0", "a", "  a1", "\ta2", "", "b")),
        )
    }
}
