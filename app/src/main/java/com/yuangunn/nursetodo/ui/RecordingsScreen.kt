package com.yuangunn.nursetodo.ui

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.lock.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 설정 › 녹음: 맨 위 녹음 안내(법규·환자 개인정보 반출 금지), 녹음 중이면 멈춤 줄, 녹음 목록(새것 먼저).
 * 줄마다 듣기(누르면 아래 막대로 옮겨 듣기) · 보내기(공유 창) · 지우기(확인 후, 되돌릴 수 없음).
 */
@Composable
internal fun RecordingsScreen() {
    val context = LocalContext.current
    val since by Recorder.since.collectAsStateWithLifecycle()
    var version by remember { mutableIntStateOf(0) }
    val files = remember(since, version) { Recorder.list(context) }
    val durations by produceState(emptyMap<File, Long?>(), files) {
        value = withContext(Dispatchers.IO) { files.associateWith { durationMs(it) } }
    }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf<File?>(null) }
    var paused by remember { mutableStateOf(false) }
    var pos by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<File?>(null) }
    val stopPlay = {
        player?.release()
        player = null
        playing = null
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    // 앱을 벗어나면 멈춤(다시 누르면 이어서)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player?.takeIf { it.isPlaying }?.pause()
        if (player != null) paused = true
    }
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            pos = runCatching { p.currentPosition }.getOrDefault(pos)
            delay(200)
        }
    }
    val play = { f: File ->
        when {
            playing == f && !paused -> {
                player?.pause()
                paused = true
            }
            playing == f -> {
                player?.start()
                paused = false
            }
            else -> {
                stopPlay()
                runCatching {
                    MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        prepare()
                        setOnCompletionListener { stopPlay() }
                        start()
                    }
                }.onSuccess {
                    player = it
                    playing = f
                    paused = false
                    pos = 0
                }
            }
        }
    }

    Group("", foot = "") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("녹음하기 전에 확인하세요", style = MaterialTheme.typography.titleSmall)
            Notice("내가 참여한 대화만 녹음하세요. 다른 사람끼리 나누는 대화를 몰래 녹음하면 통신비밀보호법 위반이에요.")
            Notice("개인 메신저·메일·클라우드로 보내지 마세요. 개인정보 보호법·의료법 위반이 될 수 있어요.", strong = "환자 개인정보(이름·등록번호·진단·검사 결과 등)가 담긴 녹음은 외부 반출 금지예요. ")
            Notice("녹음은 이 폰의 널스투두 안에만 저장되고 저절로 지워지지 않아요. 다 쓴 녹음은 지워 주세요.")
        }
    }
    since?.let { start ->
        val now by produceState(System.currentTimeMillis(), start) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1000)
            }
        }
        Group("지금 녹음 중") {
            SetRow("녹음 중 ${Recorder.elapsed(now - start)}", "잠금화면 '녹음'이나 알림에서도 멈출 수 있어요") {
                TextButton(onClick = { Recorder.stop(context) }) { Text("멈춤") }
            }
        }
    }
    Group("녹음", count = if (files.isEmpty()) "" else "${files.size}개") {
        if (files.isEmpty()) {
            SetRow("아직 녹음이 없어요", "잠금화면 바로가기 빈 칸 '+'에서 '녹음'을 넣으면 한 번 눌러 녹음해요")
        }
        files.forEach { f ->
            val total = durations[f]
            val on = playing == f
            Column(Modifier.fillMaxWidth().rowLine(start = 64.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(start = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { play(f) }) {
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            if (on && !paused) {
                                Icon(painterResource(R.drawable.ic_pause), contentDescription = "멈춤", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = "듣기", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(title(f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (on) "${Recorder.elapsed(pos.toLong())} / ${total?.let(Recorder::elapsed) ?: "—"}"
                            else listOfNotNull(total?.let(Recorder::elapsed), Formatter.formatShortFileSize(context, f.length())).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { context.startActivity(Recorder.shareIntent(context, f)) }) {
                        Icon(Icons.Default.Share, contentDescription = "보내기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { deleting = f }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "지우기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (on && total != null && total > 0) {
                    Slider(
                        value = pos.toFloat().coerceIn(0f, total.toFloat()),
                        onValueChange = {
                            pos = it.toInt()
                            player?.seekTo(it.toInt())
                        },
                        valueRange = 0f..total.toFloat(),
                        // 슬라이더 안쪽 여백(6)만큼 덜 띄워 막대 시작을 위 제목 글자와 맞춤
                        modifier = Modifier.padding(start = 58.dp, end = 16.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }

    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("이 녹음을 지울까요?") },
            text = { Text("${title(f)} 녹음\n지우면 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    if (playing == f) stopPlay()
                    f.delete()
                    deleting = null
                    version++
                }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun Notice(text: String, strong: String = "") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // [strong] 은 앞에 굵게·진하게(반출 금지처럼 꼭 봐야 할 말)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) { append(strong) }
                append(text)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun title(f: File): String = SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREAN).format(Date(Recorder.startedAt(f)))

private fun durationMs(f: File): Long? = runCatching {
    MediaMetadataRetriever().run {
        try {
            setDataSource(f.absolutePath)
            extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            release()
        }
    }
}.getOrNull()
