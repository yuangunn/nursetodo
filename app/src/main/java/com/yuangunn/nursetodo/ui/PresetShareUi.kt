package com.yuangunn.nursetodo.ui

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.data.Preset
import com.yuangunn.nursetodo.data.PresetShare
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.presetRows

private const val TEAMS = "com.microsoft.teams"

/** 팀즈 앱이 깔려 있는지(매니페스트 `<queries>` 의 런처 앱 목록으로 보임). */
fun teamsInstalled(context: Context): Boolean = context.packageManager.getLaunchIntentForPackage(TEAMS) != null

/**
 * 교대 프리셋을 글로 보냅니다. [toTeams] 면 팀즈의 공유 화면을 바로 열어 채팅이나 채널을 고르게 하고(팀즈는 채널 글쓰기 딥링크가 없어
 * 공유하기로 넘김), 아니면 휴대폰 공유 창. 인터넷은 쓰지 않습니다.
 */
fun sharePresets(context: Context, presets: List<Preset>, toTeams: Boolean = false) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "널스투두 교대 프리셋")
        .putExtra(Intent.EXTRA_TEXT, PresetShare.toText(presets))
    if (toTeams) {
        try {
            return context.startActivity(Intent(send).setPackage(TEAMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // 팀즈가 글 공유를 안 받으면 공유 창으로
        }
    }
    context.startActivity(Intent.createChooser(send, "교대 프리셋 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** 복사해 둔 글. 없으면 빈 글. */
fun clipboardText(context: Context): String =
    context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

private fun summary(p: Preset): String =
    "${p.name} · ${p.start?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "시각 없음"} · ${presetRows(p.items).size}개"

/** 프리셋마다 체크 줄. */
@Composable
private fun PresetChecks(list: List<Preset>, chosen: Set<Long>, note: (Preset) -> String?, onToggle: (Long) -> Unit) {
    list.forEach { p ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(p.id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = p.id in chosen, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp, top = 10.dp, bottom = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(summary(p), style = MaterialTheme.typography.bodyLarge)
                note(p)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** 보낼 프리셋 고르기(할 일이 있는 것만 처음에 체크) → 팀즈에 올리기 / 다른 앱으로 보내기. */
@Composable
fun PresetShareDialog(presets: List<Preset>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(presets.filter { it.items.any(String::isNotBlank) }.map { it.id }.toSet()) }
    val picked = presets.filter { it.id in chosen }
    val teams = remember { teamsInstalled(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교대 프리셋 공유") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "글로 보내요. 팀즈 채널이나 단톡방에 올리면 받은 사람이 복사해서 가져와요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PresetChecks(presets, chosen, note = { null }) { id -> chosen = if (id in chosen) chosen - id else chosen + id }
                Text(
                    "환자 이름·병원 이름처럼 밖에 나가면 안 되는 내용이 없는지 확인하고 보내세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (teams) {
                    TextButton(onClick = {
                        sharePresets(context, picked, toTeams = true)
                        onDismiss()
                    }, enabled = picked.isNotEmpty()) { Text("팀즈에 올리기") }
                }
                TextButton(onClick = {
                    sharePresets(context, picked)
                    onDismiss()
                }, enabled = picked.isNotEmpty()) { Text(if (teams) "다른 앱" else "보내기") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * 받은 프리셋 고르기: 이름 · 시작 시각 · 항목 수, 같은 이름이 있으면 '지금 것을 바꿈'. 가져오면 교대 프리셋에 합칩니다.
 * 이미 받은 근무의 할 일은 그대로이고, 다음에 교대를 받을 때부터 새 프리셋으로 불러옵니다. [onDone] 은 알려 줄 문장(취소면 null).
 */
@Composable
fun PresetImportDialog(incoming: List<Preset>, onDone: (String?) -> Unit) {
    val s by TaskStore.settings.collectAsStateWithLifecycle()
    var chosen by remember(incoming) { mutableStateOf(incoming.map { it.id }.toSet()) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text("교대 프리셋 가져오기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PresetChecks(incoming, chosen, note = { p ->
                    if (s.presets.any { it.name == p.name }) "같은 이름이 있어 지금 것을 바꿔요" else "새로 넣어요"
                }) { id -> chosen = if (id in chosen) chosen - id else chosen + id }
                Text(
                    "지금 받은 근무의 할 일은 그대로예요. 다음에 교대를 받을 때부터 새 프리셋으로 불러와요." +
                        if (!s.presetMode) " 설정 › 교대 프리셋 사용을 켜야 근무 페이지에 보여요." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val (list, replaced, added) = PresetShare.merge(s.presets, incoming.filter { it.id in chosen })
                TaskStore.savePresets(list)
                onDone(listOfNotNull(if (added > 0) "${added}개 넣음" else null, if (replaced > 0) "${replaced}개 바꿈" else null).joinToString(", ") + " — 교대 프리셋을 가져왔어요")
            }, enabled = chosen.isNotEmpty()) { Text("가져오기") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("취소") } },
    )
}
