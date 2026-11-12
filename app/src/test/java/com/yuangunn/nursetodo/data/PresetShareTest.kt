package com.yuangunn.nursetodo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 교대 프리셋 글 주고받기: 그대로 돌아오기, 게시판이 앞 칸을 지워도, 머리글 없는 글은 무시, 같은 이름은 바꾸기. */
class PresetShareTest {
    private val day = Preset(1, "Day", 7 * 60, listOf("07:00 인계", "  환자상태 확인", "  V/S check", "08:00 아침식후약", "물품 정리"), quiet = setOf("물품 정리"))
    private val night = Preset(2, "Ward Night", null, listOf("23:00 인계"))

    @Test
    fun roundTrip() {
        val text = PresetShare.toText(listOf(day, night))
        val back = PresetShare.parse(text, idBase = 100)!!
        assertEquals(listOf(day.copy(id = 100), night.copy(id = 101)), back)
    }

    @Test
    fun survivesBoardsThatTrimSpacesAndAddChatter() {
        // 카톡 미리보기·게시판이 붙인 앞뒤 글, 하위 항목 앞 칸이 지워진 글
        val pasted = """
            [단톡방] 우리 병동 프리셋입니다
            [널스투두 교대 프리셋]
            # Day 07:00
            07:00 인계
            - 환자상태 확인
            - V/S check
            08:00 아침식후약
            물품 정리 (알림 끔)
            널스투두에서 받기: 이 글을 공유하기로 …
            오후 3:12
        """.trimIndent()
        assertEquals(listOf(day.copy(id = 7)), PresetShare.parse(pasted, idBase = 7))
    }

    @Test
    fun exportUsesMarksThatChatFormattingKeeps() {
        // 팀즈는 줄 앞 '#'·'-' 를 제목·글머리표로 바꾸므로 '▶'·'ㄴ' 으로 내보냄
        val text = PresetShare.toText(listOf(day))
        assertTrue("▶ Day 07:00" in text)
        assertTrue("ㄴ 환자상태 확인" in text)
        assertTrue(text.lines().none { it.startsWith("#") || it.trimStart().startsWith("- ") })
        // 붙여넣을 때 글머리표가 '•' 로 바뀌어 와도
        val bullets = text.replace("ㄴ ", "• ").replace("▶ ", "■ ")
        assertEquals(listOf(day.copy(id = 3)), PresetShare.parse(bullets, idBase = 3))
    }

    @Test
    fun ignoresOtherText() {
        assertNull(PresetShare.parse("# 간경화\n## 라운딩\n- 의식"))
        assertNull(PresetShare.parse("[널스투두 교대 프리셋]\n그냥 글"))
        // '#3 병실' 같은 할 일 줄은 머리가 아님
        assertEquals(listOf("#3 병실 확인"), PresetShare.parse("[널스투두 교대 프리셋]\n▶ Day\n#3 병실 확인")!!.single().items)
    }

    @Test
    fun mergeReplacesSameNameKeepsId() {
        val mine = listOf(Preset(1, "Day", 420, listOf("old")), Preset(2, "Evening", 900, emptyList()))
        val (list, replaced, added) = PresetShare.merge(mine, listOf(Preset(50, "Day", 400, listOf("new")), Preset(51, "OP", null, listOf("x"))))
        assertEquals(listOf(Preset(1, "Day", 400, listOf("new")), mine[1], Preset(51, "OP", null, listOf("x"))), list)
        assertEquals(1, replaced)
        assertEquals(1, added)
    }
}
