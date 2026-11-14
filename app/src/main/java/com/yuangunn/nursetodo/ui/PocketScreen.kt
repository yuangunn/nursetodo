package com.yuangunn.nursetodo.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.Pocket
import com.yuangunn.nursetodo.data.PocketKind
import com.yuangunn.nursetodo.data.PocketNode
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.itemCount
import com.yuangunn.nursetodo.data.parsePocket
import com.yuangunn.nursetodo.data.splitPockets

/**
 * 포켓북 탭: 잠금화면 포켓북 페이지에 보일 순서대로(손잡이를 끌어 옮김), 누르면 글 그대로 고치기.
 * 비어 있으면 예시 넣기, 아래에 붙여 넣는 형식과 붙여넣기 버튼.
 */
@Composable
fun PocketScreen() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val pockets by TaskStore.pockets.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Pocket?>(null) }
    var pasting by remember { mutableStateOf(false) }
    val raw = { id: Int -> context.resources.openRawResource(id).bufferedReader().use { it.readText() } }
    val saver = { id: Int ->
        { uri: Uri? ->
            uri?.let { u -> context.contentResolver.openOutputStream(u)?.use { it.write(raw(id).toByteArray()) } }
            if (uri != null) Toast.makeText(context, "저장했어요", Toast.LENGTH_SHORT).show()
        }
    }
    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), saver(R.raw.pocketbook_example))
    val saveTable = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values"), saver(R.raw.pocketbook_table))
    val loadExamples = {
        // 이미 있는 이름은 건너뜁니다.
        val have = pockets.map { it.doc.title }.toSet()
        val texts = splitPockets(raw(R.raw.pocketbook_example)).filter { parsePocket(it).title !in have }
        val n = if (texts.isEmpty()) 0 else TaskStore.addPockets(texts.joinToString("\n\n"))
        Toast.makeText(context, if (n > 0) "예시 ${n}개를 넣었어요" else "예시가 이미 있어요", Toast.LENGTH_SHORT).show()
    }
    // 손잡이로 끌어 순서 바꾸기
    var order by remember { mutableStateOf<List<Pocket>?>(null) }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val shown = order ?: pockets
    val listState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
            item(key = "title") {
                Column {
                    ScreenTitle("포켓북")
                    if (pockets.isNotEmpty()) {
                        Text(
                            "잠금화면 포켓북 페이지에 이 순서로 보여요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                }
            }
            items(shown, key = { it.id }) { p ->
                val dragging = p.id == dragId
                Row(
                    Modifier
                        .animateItem(placementSpec = if (dragging) null else androidx.compose.animation.core.spring())
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragY else 0f }
                        .background(MaterialTheme.colorScheme.background)
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(start = 6.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(40.dp, 48.dp)
                            .semantics { contentDescription = "${p.doc.title} 순서 옮기기" }
                            .pointerInput(p.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        order = pockets
                                        dragId = p.id
                                        dragY = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val list = order ?: return@detectDragGestures
                                        dragY += amount.y
                                        val idx = list.indexOfFirst { it.id == p.id }
                                        val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == p.id }?.size ?: return@detectDragGestures
                                        val to = when {
                                            dragY > size / 2f && idx + 1 < list.size -> idx + 1
                                            dragY < -size / 2f && idx > 0 -> idx - 1
                                            else -> return@detectDragGestures
                                        }
                                        order = list.toMutableList().apply { add(to, removeAt(idx)) }
                                        dragY += if (to > idx) -size else size
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    onDragEnd = {
                                        order?.let { list -> TaskStore.reorderPockets(list.map { it.id }) }
                                        order = null
                                        dragId = null
                                        dragY = 0f
                                    },
                                    onDragCancel = {
                                        order = null
                                        dragId = null
                                        dragY = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .clickable { editing = p }
                            .padding(start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.doc.title, style = MaterialTheme.typography.bodyLarge)
                            val sections = p.doc.nodes.count { it.kind == PocketKind.SECTION }
                            Text(
                                listOfNotNull(if (sections > 0) "묶음 ${sections}개" else null, "체크 항목 ${p.doc.nodes.sumOf { it.itemCount() }}개").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            if (pockets.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_memo), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Text("포켓북이 없어요", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "진단명·수술명마다 사정할 때 볼 것을 적어 두면 잠금화면 포켓북 페이지에서 바로 체크해요. 아래 형식으로 붙여 넣으면 만들어져요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        LineButton("예시 포켓북 3개 넣기", loadExamples)
                    }
                }
            }
            item(key = "format") {
                Column(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                        .padding(16.dp),
                ) {
                    Text("붙여 넣는 형식", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
                    listOf("# 이름" to "포켓북 이름", "## 묶음" to "접고 펴는 묶음", "- 항목" to "체크 항목", "  - 항목" to "두 칸 들여 쓰면 하위 항목", "그냥 줄" to "설명 (체크 없음)").forEach { (code, what) ->
                        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(72.dp))
                            Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "여러 개를 한 번에 붙여 넣어도 되고, 엑셀 표(포켓북 | 묶음 | 항목 | 하위 항목)를 복사해 붙여 넣어도 돼요. 체크는 교대를 받을 때와 같은 이름의 근무 루틴 알림이 울릴 때 새로 시작해요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    // 글 버튼 안쪽 여백(12)만큼 당겨 글자를 위 글과 같은 왼쪽 끝에
                    Row(Modifier.offset(x = (-12).dp)) {
                        TextButton(onClick = { saveText.launch("포켓북-양식.txt") }) { Text("양식 저장 (글)") }
                        TextButton(onClick = { saveTable.launch("포켓북-양식.tsv") }) { Text("양식 저장 (표)") }
                        if (pockets.isNotEmpty()) TextButton(onClick = loadExamples) { Text("예시 넣기") }
                    }
                }
            }
        }
        FillButton(
            "붙여넣기",
            { pasting = true },
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 12.dp),
            colors = ButtonDefaults.buttonColors(),
        )
    }
    if (pasting) {
        PocketEditor("포켓북 붙여넣기", "", onDelete = null) { text ->
            if (!text.isNullOrBlank()) {
                val n = TaskStore.addPockets(text)
                Toast.makeText(context, "포켓북 ${n}개를 만들었어요", Toast.LENGTH_SHORT).show()
            }
            pasting = false
        }
    }
    editing?.let { p ->
        PocketEditor("포켓북 고치기", p.text, onDelete = { TaskStore.deletePocket(p.id) }) { text ->
            if (text != null) TaskStore.updatePocket(p.id, text)
            editing = null
        }
    }
}

/**
 * 포켓북 글 편집(전체 화면): 글 · 미리 보기 세그먼트, 입력 칸, 읽은 결과, 아래 기호 키(# 이름 · ## 묶음 · - 체크 · 들여쓰기)는
 * 커서가 있는 줄 앞에 붙입니다. [onDone] 에 고친 글, 닫으면 null.
 */
@Composable
private fun PocketEditor(title: String, initial: String, onDelete: (() -> Unit)?, onDone: (String?) -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    var preview by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val docs = remember(value.text) { splitPockets(value.text).map { parsePocket(it) } }
    FullScreen(onClose = { onDone(null) }) {
        TopBar(title, onClose = { onDone(null) }) {
            if (onDelete != null) {
                TextButton(onClick = {
                    if (deleting) {
                        onDelete()
                        onDone(null)
                    } else {
                        deleting = true
                    }
                }) { Text(if (deleting) "정말 삭제" else "삭제", color = MaterialTheme.colorScheme.error) }
            }
            FillButton("저장", { onDone(value.text) }, enabled = value.text.isNotBlank())
        }
        Segmented(listOf("글", "미리 보기"), if (preview) 1 else 0, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) { preview = it == 1 }
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .padding(12.dp),
        ) {
            if (preview) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    docs.forEach { d ->
                        Text("# ${d.title}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        d.nodes.forEach { PreviewNode(it, 0) }
                    }
                }
            } else {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box {
                            if (value.text.isEmpty()) {
                                Text("# 이름\n## 묶음\n- 체크 항목\n  - 하위 항목\n설명", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                )
            }
        }
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                "읽음: 포켓북 ${docs.size}개 · 묶음 ${docs.sumOf { d -> d.nodes.count { it.kind == PocketKind.SECTION } }}개 · 체크 항목 ${docs.sumOf { d -> d.nodes.sumOf { it.itemCount() } }}개",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!preview) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("# 이름" to "# ", "## 묶음" to "## ", "- 체크" to "- ", "들여쓰기" to "  ").forEach { (label, mark) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .clickable { value = insertAtLineStart(value, mark) },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
                }
            }
        }
    }
}

/** 커서가 있는 줄 맨 앞에 [mark] 를 붙이고 커서를 그만큼 옮깁니다. */
internal fun insertAtLineStart(v: TextFieldValue, mark: String): TextFieldValue {
    val at = v.selection.start.coerceIn(0, v.text.length)
    val lineStart = v.text.lastIndexOf('\n', at - 1) + 1
    val text = v.text.substring(0, lineStart) + mark + v.text.substring(lineStart)
    return TextFieldValue(text, TextRange(at + mark.length))
}

@Composable
private fun PreviewNode(n: PocketNode, depth: Int) {
    val pad = (depth * 16).dp
    when (n.kind) {
        PocketKind.SECTION -> Text("## ${n.text}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        PocketKind.ITEM -> Text("○ ${n.text}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = pad))
        PocketKind.NOTE -> Text(n.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = pad))
    }
    n.children.forEach { PreviewNode(it, if (n.kind == PocketKind.SECTION) depth else depth + 1) }
}
