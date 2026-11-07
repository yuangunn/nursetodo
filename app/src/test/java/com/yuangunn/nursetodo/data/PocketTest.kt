package com.yuangunn.nursetodo.data

import androidx.test.core.app.ApplicationProvider
import com.yuangunn.nursetodo.data.PocketKind.ITEM
import com.yuangunn.nursetodo.data.PocketKind.NOTE
import com.yuangunn.nursetodo.data.PocketKind.SECTION
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** 포켓북: 붙여넣기 형식·표 읽기, 예시 파일, 같은 이름 근무 루틴 알림 때 체크 새로 시작. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PocketTest {
    @Test
    fun readsSectionsItemsAndNotes() {
        val doc = parsePocket(
            """
            # LAR (저위전방절제술)
            예시입니다.
            ## POD#1
            - 걸터앉기
            - 출혈 징후
              - 혈변
            설명 줄
            ## 의료진에게 알릴 때
            38도 이상 발열
            """.trimIndent(),
        )
        assertEquals("LAR (저위전방절제술)", doc.title)
        assertEquals(listOf(NOTE, SECTION, SECTION), doc.nodes.map { it.kind })
        val pod1 = doc.nodes[1]
        assertEquals(listOf("걸터앉기", "출혈 징후", "설명 줄"), pod1.children.map { it.text })
        assertEquals(listOf(ITEM, ITEM, NOTE), pod1.children.map { it.kind })
        // 두 칸 들여 쓴 항목은 위 항목의 하위
        assertEquals("1.1.0", pod1.children[1].children.single().path)
        assertEquals(3, doc.nodes.sumOf { it.itemCount() })
        assertEquals(1, pod1.doneCount(setOf("1.1.0", "2.0")))
    }

    @Test
    fun splitsPastedTextAndTables() {
        assertEquals(listOf("A", "B"), splitPockets("# A\n- a\n\n# B\n- b").map { parsePocket(it).title })
        // 엑셀 표: 빈칸은 윗줄 값을 이어받고 머리글은 건너뜀
        val table = splitPockets("포켓북\t묶음\t항목\t하위 항목\nTKA\tPOD#1\tCPM 시작\t\n\t\t걸터앉기\t\n정규 라운딩\t\tV/S check\t\n")
        assertEquals(listOf("TKA", "정규 라운딩"), table.map { parsePocket(it).title })
        val pod1 = parsePocket(table[0]).nodes.single()
        assertEquals("POD#1", pod1.text)
        assertEquals(listOf("CPM 시작", "걸터앉기"), pod1.children.map { it.text })
        assertEquals(listOf("V/S check"), parsePocket(table[1]).nodes.map { it.text })
    }

    @Test
    fun bundledExamplesParse() {
        val text = File("src/main/res/raw/pocketbook_example.txt").readText()
        val docs = splitPockets(text).map { parsePocket(it) }
        assertEquals(listOf("정규 라운딩", "간경화 (Liver cirrhosis)", "LAR (저위전방절제술, 직장암)"), docs.map { it.title })
        docs.forEach { assert(it.nodes.sumOf { n -> n.itemCount() } > 0) }
        val table = splitPockets(File("src/main/res/raw/pocketbook_table.tsv").readText()).map { parsePocket(it).title }
        assertEquals(listOf("정규 라운딩", "TKA (인공슬관절 전치환술)"), table)
    }

    @Test
    fun routineRestartsSameNamePocket() {
        TaskStore.init(ApplicationProvider.getApplicationContext())
        TaskStore.onChanged = null
        TaskStore.pockets.value.forEach { TaskStore.deletePocket(it.id) }
        TaskStore.addPockets("# 정규 라운딩\n- V/S check\n# 간경화\n- 체중")
        val (round, liver) = TaskStore.pockets.value
        TaskStore.togglePocketCheck(round.id, "0")
        TaskStore.togglePocketCheck(liver.id, "0")
        TaskStore.restartPocketsFor("09:00 정규 라운딩(환자상태 확인, 수액 라운딩)")
        assertEquals(listOf(emptySet(), setOf("0")), TaskStore.pockets.value.map { it.checks })
        // 글을 고치면 자리가 바뀔 수 있어 체크를 비움
        TaskStore.updatePocket(liver.id, "# 간경화\n- 복부둘레\n- 체중")
        assertEquals(emptySet<String>(), TaskStore.pockets.value.last().checks)
        TaskStore.pockets.value.forEach { TaskStore.deletePocket(it.id) }
    }
}
