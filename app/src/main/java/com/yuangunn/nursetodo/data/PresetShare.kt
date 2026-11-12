package com.yuangunn.nursetodo.data

/**
 * 교대 프리셋을 글로 주고받는 형식. 팀즈 채널·카톡·밴드·카페 게시판에 그대로 붙여도 사람이 읽을 수 있게 씁니다.
 *
 * ```
 * [널스투두 교대 프리셋]
 *
 * ▶ Day 07:00
 * 07:00 인계
 * ㄴ 환자상태 확인
 * 22:30 I/O 마감 (알림 끔)
 * ```
 * - `▶ 이름 [HH:MM]` 이 프리셋 하나(시각은 근무 시작, 없어도 됨).
 * - 하위 항목은 `ㄴ `. 팀즈·게시판은 줄 앞 `#`·`-` 를 제목·글머리표로 바꿔 복사할 때 기호가 사라지므로 서식에 걸리지 않는 기호를 씁니다.
 *   읽을 때는 `# `·`■ ` 머리와 `- `·`· `·`• `·앞 칸 하위 줄도 받습니다.
 * - 근무 루틴 알림을 끈 줄은 끝에 ` (알림 끔)`.
 * 인터넷을 쓰지 않습니다: 보내기는 휴대폰 공유하기(팀즈는 채팅이나 채널을 고름), 받기는 공유하기로 널스투두를 고르거나 복사해 붙여넣기.
 */
object PresetShare {
    const val HEADER = "[널스투두 교대 프리셋]"
    private const val QUIET = " (알림 끔)"
    private const val FOOTER = "널스투두에서 받기:"
    private val Head = Regex("""^\s*(?:▶|■|#(?=\s))\s*(.+)$""")
    private val NameTime = Regex("""^(.*?)\s+(\d{1,2}):(\d{2})$""")
    private val Sub = Regex("""^\s*(?:ㄴ|-|·|•)\s+(.+)$""")

    fun toText(presets: List<Preset>): String = buildString {
        appendLine(HEADER)
        presets.forEach { p ->
            appendLine()
            append("▶ ").append(p.name)
            p.start?.let { append(" %02d:%02d".format(it / 60, it % 60)) }
            appendLine()
            p.items.filter { it.isNotBlank() }.forEach { line ->
                append(if (isSubLine(line)) "ㄴ " + line.trim() else line.trim())
                if (line in p.quiet) append(QUIET)
                appendLine()
            }
        }
        appendLine()
        append("$FOOTER 이 글을 길게 눌러 복사한 뒤 설정 › 교대 프리셋 › 붙여넣어 가져오기 (또는 공유하기로 널스투두)")
    }

    /** 글에서 프리셋을 읽습니다. 머리글이 없거나 프리셋이 하나도 없으면 null. id 는 [idBase] 부터 매깁니다. */
    fun parse(text: String, idBase: Long = System.currentTimeMillis()): List<Preset>? {
        val lines = text.replace("\r", "").lines()
        val from = lines.indexOfFirst { it.trim() == HEADER }.takeIf { it >= 0 } ?: return null
        val out = mutableListOf<Preset>()
        var name: String? = null
        var start: Int? = null
        val items = mutableListOf<String>()
        val quiet = mutableSetOf<String>()
        fun flush() {
            name?.let { out += Preset(idBase + out.size, it.ifBlank { "프리셋" }, start, items.toList(), quiet.toSet()) }
            items.clear()
            quiet.clear()
        }
        for (raw in lines.drop(from + 1)) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith(FOOTER)) break
            val head = Head.find(line)?.groupValues?.get(1)?.trim()
            if (head != null) {
                flush()
                val m = NameTime.find(head)
                val h = m?.groupValues?.get(2)?.toInt() ?: 99
                val min = m?.groupValues?.get(3)?.toInt() ?: 99
                if (m != null && h < 24 && min < 60) {
                    name = m.groupValues[1].trim()
                    start = h * 60 + min
                } else {
                    name = head
                    start = null
                }
                continue
            }
            if (name == null || line.isBlank()) continue
            val silent = line.endsWith(QUIET)
            val body = line.removeSuffix(QUIET)
            val sub = Sub.find(body)?.groupValues?.get(1)
            val item = when {
                sub != null -> "  " + sub.trim()
                isSubLine(body) -> "  " + body.trim()
                else -> body.trim()
            }
            if (item.isBlank()) continue
            items += item
            if (silent) quiet += item
        }
        flush()
        return out.takeIf { it.isNotEmpty() }
    }

    /** 가져오기: 같은 이름 프리셋은 내용을 바꾸고(id 는 그대로), 없는 이름은 뒤에 붙입니다. (합친 목록, 바꾼 수, 새로 넣은 수) */
    fun merge(existing: List<Preset>, incoming: List<Preset>): Triple<List<Preset>, Int, Int> {
        var replaced = 0
        var added = 0
        val list = existing.toMutableList()
        incoming.forEach { p ->
            val i = list.indexOfFirst { it.name == p.name }
            if (i >= 0) {
                list[i] = p.copy(id = list[i].id)
                replaced++
            } else {
                list += p
                added++
            }
        }
        return Triple(list, replaced, added)
    }
}
