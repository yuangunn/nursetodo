package com.yuangunn.nursetodo.data

/**
 * 포켓북 하나(진단명·수술명·업무별로 사정할 때 볼 것). 붙여넣은 글([text]) 그대로 저장하고 볼 때마다 [parsePocket] 으로 읽습니다.
 * [checks] 는 체크한 항목의 [PocketNode.path]. 글을 고치면 자리가 바뀔 수 있어 비웁니다.
 */
data class Pocket(val id: Long, val text: String, val checks: Set<String> = emptySet()) {
    val doc: PocketDoc by lazy { parsePocket(text) }
}

enum class PocketKind { SECTION, ITEM, NOTE }

/** [SECTION] 은 `## 묶음`(접고 펴기, 체크 없음), [ITEM] 은 `- 체크 항목`, [NOTE] 는 그냥 줄(설명). [path] 는 '0.2.1' 같은 자리. */
data class PocketNode(val kind: PocketKind, val text: String, val path: String, val children: List<PocketNode> = emptyList())

data class PocketDoc(val title: String, val nodes: List<PocketNode>)

/** 이 아래(자기 포함) 체크 항목 수. */
fun PocketNode.itemCount(): Int = (if (kind == PocketKind.ITEM) 1 else 0) + children.sumOf { it.itemCount() }

/** 이 아래(자기 포함) 체크한 항목 수. */
fun PocketNode.doneCount(checks: Set<String>): Int =
    (if (kind == PocketKind.ITEM && path in checks) 1 else 0) + children.sumOf { it.doneCount(checks) }

/**
 * 붙여넣기 형식 읽기. `# 제목` 은 포켓북 이름, `## 묶음`, `- 항목`(두 칸 또는 탭만큼 들여 쓰면 위 항목의 하위), 그냥 줄은 설명.
 * `## 묶음` 다음 줄들은 다음 `##` 까지 그 묶음 안에 들어갑니다.
 */
fun parsePocket(source: String): PocketDoc {
    class B(val kind: PocketKind, val text: String) {
        val children = mutableListOf<B>()
    }
    var title = ""
    val top = mutableListOf<B>()
    var container = top
    val stack = ArrayDeque<Pair<Int, B>>() // (들여쓰기, 항목)
    for (raw in source.lines()) {
        if (raw.isBlank()) continue
        val lead = raw.takeWhile { it == ' ' || it == '\t' }
        val indent = (lead.count { it == ' ' } + 2 * lead.count { it == '\t' }) / 2
        val line = raw.trim()
        when {
            line.startsWith("## ") || line == "##" -> {
                val section = B(PocketKind.SECTION, line.removePrefix("##").trim())
                top += section
                container = section.children
                stack.clear()
            }
            line.startsWith("# ") -> if (title.isEmpty()) title = line.removePrefix("#").trim() else top += B(PocketKind.NOTE, line)
            else -> {
                val item = line.startsWith("- ") || line.startsWith("* ") || line == "-"
                val node = B(if (item) PocketKind.ITEM else PocketKind.NOTE, if (item) line.drop(1).trim() else line)
                while (stack.isNotEmpty() && stack.last().first >= indent) stack.removeLast()
                (stack.lastOrNull()?.second?.children ?: container) += node
                if (item) stack.addLast(indent to node)
            }
        }
    }
    fun B.freeze(path: String): PocketNode =
        PocketNode(kind, text, path, children.mapIndexed { i, c -> c.freeze("$path.$i") })
    return PocketDoc(title.ifEmpty { "포켓북" }, top.mapIndexed { i, b -> b.freeze("$i") })
}

/**
 * 붙여넣은 글을 포켓북마다 나눕니다(`# ` 로 시작하는 줄마다 새 포켓북). 엑셀 등에서 복사한 표(칸 사이 탭)면 먼저 글로 바꿉니다.
 * 표의 열: 포켓북 | 묶음 | 항목 | 하위 항목. 빈칸은 윗줄 값을 이어받고, 첫 줄이 '포켓북'으로 시작하면 머리글로 보고 건너뜁니다.
 */
fun splitPockets(pasted: String): List<String> {
    val text = if (pasted.lines().any { '\t' in it }) tableToText(pasted) else pasted
    val blocks = mutableListOf<StringBuilder>()
    for (line in text.lines()) {
        if (line.trimStart().startsWith("# ") || blocks.isEmpty()) blocks += StringBuilder()
        blocks.last().appendLine(line)
    }
    return blocks.map { it.toString().trim() }.filter { it.isNotEmpty() }
}

internal fun tableToText(table: String): String {
    val out = StringBuilder()
    var pocket = ""
    var section = ""
    table.lines().filter { it.isNotBlank() }.forEachIndexed { i, row ->
        val c = row.split('\t').map { it.trim() } + List(4) { "" }
        if (i == 0 && c[0] == "포켓북") return@forEachIndexed
        if (c[0].isNotEmpty() && c[0] != pocket) {
            pocket = c[0]
            section = ""
            out.appendLine().appendLine("# $pocket")
        }
        if (c[1].isNotEmpty() && c[1] != section) {
            section = c[1]
            out.appendLine("## $section")
        }
        if (c[2].isNotEmpty()) out.appendLine("- ${c[2]}")
        if (c[3].isNotEmpty()) out.appendLine("  - ${c[3]}")
    }
    return out.toString().trim()
}
