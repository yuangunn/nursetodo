package com.ttaak.app.data

import androidx.test.core.app.ApplicationProvider
import com.ttaak.app.lock.autoFit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/** 교대 프리셋: 어느 교대인지, 바뀔 때 목록 갈기, 저장. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PresetTest {
    private val day = LocalDateTime.of(2026, 10, 28, 0, 0)
    private fun at(h: Int, m: Int = 0, plusDays: Long = 0) = day.plusDays(plusDays).withHour(h).withMinute(m)
    private fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val presets = Preset.Defaults

    // TaskStore 는 앱 전체에 하나라 다른 시험에 남지 않게 앞뒤로 비웁니다.
    // 앱의 onChanged(→ refreshPreset)는 진짜 시계로 교대를 다시 불러와 시험의 시각을 덮으므로 뗍니다.
    @Before
    @After
    fun reset() {
        TaskStore.init(ApplicationProvider.getApplicationContext())
        TaskStore.onChanged = null
        TaskStore.tasks.value.forEach { TaskStore.delete(it.id) }
        TaskStore.updateSettings { Settings() }
    }

    @Test
    fun currentShiftFollowsStartTimes() {
        // 7시 전은 어제 23시 Night
        assertEquals("Night" to ms(at(23, plusDays = -1)), presets.currentShift(at(6, 59))?.let { it.first.name to it.second })
        assertEquals("Day" to ms(at(7)), presets.currentShift(at(7))?.let { it.first.name to it.second })
        assertEquals("Day", presets.currentShift(at(14, 59))?.first?.name)
        assertEquals("Evening", presets.currentShift(at(15))?.first?.name)
        assertEquals("Night" to ms(at(23)), presets.currentShift(at(23, 30))?.let { it.first.name to it.second })
        // 같은 시각이면 위의 것, 시각 없는 프리셋은 자동으로 안 불러옴
        val copy = presets[0].copy(id = 9, name = "Day 복사")
        assertEquals("Day", (presets + copy).currentShift(at(8))?.first?.name)
        assertEquals("Day", (presets + copy.copy(start = null)).currentShift(at(8))?.first?.name)
        assertEquals(null, listOf(copy.copy(start = null)).currentShift(at(8)))
    }

    @Test
    fun nextShiftIsNextStart() {
        assertEquals(ms(at(7)), presets.nextShift(at(6, 59)))
        assertEquals(ms(at(15)), presets.nextShift(at(7)))
        assertEquals(ms(at(7, plusDays = 1)), presets.nextShift(at(23, 30)))
    }

    @Test
    fun loadReplacesUnfinishedPresetTasksOnly() {
        val mine = TaskStore.add("직접 적은 일")!!
        val dayList = presets[0].copy(items = listOf("V/S", "투약", " "))
        TaskStore.loadPreset(dayList, 100)
        // 프리셋 할 일이 맨 위, 빈 줄은 뺌
        assertEquals(listOf("V/S", "투약", "직접 적은 일"), TaskStore.tasksFor(Day.today()).map { it.title })
        TaskStore.toggle(TaskStore.tasks.value.first { it.title == "V/S" }.id)

        // 같은 교대에서 다시 불러오면 끝낸 'V/S'는 다시 안 넣음
        TaskStore.loadPreset(dayList, 100)
        assertEquals(1, TaskStore.tasks.value.count { it.title == "투약" })
        assertEquals(1, TaskStore.tasks.value.count { it.title == "V/S" })

        // 다음 교대: 못 끝낸 '투약'은 사라지고 새 프리셋, 끝낸 기록과 직접 적은 일은 남음
        TaskStore.loadPreset(presets[1].copy(items = listOf("인계")), 200)
        val left = TaskStore.tasks.value.map { it.title }.sorted()
        assertEquals(listOf("V/S", "인계", "직접 적은 일"), left)
        assertEquals(200L, TaskStore.settings.value.presetApplied)
        assertTrue(TaskStore.get(mine.id) != null)
    }

    @Test
    fun editingLoadedPresetReloadsIt() {
        TaskStore.updateSettings { it.copy(presetMode = true, presets = presets) }
        val now = at(16)
        TaskStore.savePresets(presets, now = now) // 15시 Evening 을 불러옴
        assertEquals(presets[1].id, TaskStore.settings.value.presetLoaded)
        // 'Day'를 지금 불러온 뒤 Day 를 고치면 Day 가 다시 들어감 (Evening 이 아니라)
        TaskStore.loadPreset(presets[0], ms(at(15)))
        val day = presets[0].copy(items = listOf("새 일"))
        TaskStore.savePresets(listOf(day) + presets.drop(1), changed = day.id, now = now)
        assertEquals(listOf("새 일"), TaskStore.tasks.value.filter { it.shift != null }.map { it.title })
        // 불러오지 않은 Night 를 고치면 그대로
        TaskStore.savePresets(listOf(day, presets[1], presets[2].copy(items = listOf("밤 일"))), changed = presets[2].id, now = now)
        assertEquals(listOf("새 일"), TaskStore.tasks.value.filter { it.shift != null }.map { it.title })
    }

    @Test
    fun turningOffClearsUnfinishedPresetTasks() {
        TaskStore.updateSettings { it.copy(presets = presets.map { p -> p.copy(items = listOf("${p.name} 일")) }) }
        TaskStore.setPresetMode(true)
        assertEquals(1, TaskStore.tasks.value.count { it.shift != null })
        TaskStore.setPresetMode(false)
        assertEquals(0, TaskStore.tasks.value.count { it.shift != null })
    }

    @Test
    fun savedAndLoaded() = with(TaskStore) {
        val s = Settings(presetMode = true, presets = presets + Preset(9, "주말", null, listOf("a", "b")), presetApplied = 123, presetLoaded = 9)
        val back = s.toJson().toSettings()
        assertEquals(s.presets, back.presets)
        assertEquals(true, back.presetMode)
        assertEquals(123L, back.presetApplied)
        assertEquals(9L, back.presetLoaded)
        // 저장된 적 없으면 Day·Evening·Night, 꺼짐
        assertEquals(listOf("Day", "Evening", "Night"), org.json.JSONObject("{}").toSettings().presets.map { it.name })
        val t = Task(1, "V/S", createdDay = 20000, shift = 456)
        assertEquals(t, t.toJson().toTask())
    }

    @Test
    fun fewerTasksGetBiggerText() {
        val sizes = listOf(1, 3, 6, 10, 20).map { autoFit(500f, it).first }
        assertEquals(26f, sizes.first())
        assertTrue(sizes.zipWithNext().all { (a, b) -> a >= b })
        assertEquals(15f, sizes.last())
    }
}
