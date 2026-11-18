package com.yuangunn.nursetodo.lock

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yuangunn.nursetodo.R
import com.yuangunn.nursetodo.data.Day
import com.yuangunn.nursetodo.data.Pocket
import com.yuangunn.nursetodo.data.Preset
import com.yuangunn.nursetodo.data.TaskStore
import com.yuangunn.nursetodo.data.currentShift
import com.yuangunn.nursetodo.data.splitPockets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 잠금화면 체험(처음 안내 · 설정 › 기타): 할 일 화면을 예시 할 일·근무·포켓북·알림으로 띄우고 위에 단계 안내([PracticeCoach])를 둡니다.
 * 데이터는 [TaskStore.beginPractice] 로 메모리에만 두어 체험 중 바꾼 것은 저장되지 않고, 화면이 닫히면 원래대로 돌아갑니다.
 */
object Practice {
    const val EXTRA = "practice"
    private const val KEYWORD_KEY = "practice|kw|1"
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active

    fun intent(context: Context): Intent = Intent(context, QuickAddActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(EXTRA, true)
        .putExtra(QuickAddActivity.EXTRA_LOOK_FIRST, true)

    fun begin(context: Context) {
        if (_active.value) return
        val real = TaskStore.settings.value
        val presets = listOf("Day" to 7 * 60, "Evening" to 15 * 60, "Night" to 23 * 60)
            .mapIndexed { i, (name, start) -> Preset(i + 1L, name, start, Preset.exampleItems(name).orEmpty()) }
        val pockets = runCatching {
            splitPockets(context.resources.openRawResource(R.raw.pocketbook_example).bufferedReader().readText())
        }.getOrDefault(emptyList()).take(2).mapIndexed { i, text -> Pocket(i + 1L, text) }
        TaskStore.beginPractice(
            real.copy(
                presetMode = true, presets = presets, presetApplied = 0L, presetLoaded = 0L, dutyShift = 0L,
                routineMuted = false, pageMask = 7, notiApps = emptySet(),
            ),
            pockets,
        )
        // 앞 근무를 받아 반쯤 끝낸 채로: 지금 근무를 받으면 못 끝낸 일을 가져올지 고르게 됨
        val now = LocalDateTime.now()
        presets.currentShift(now)?.let { (_, start) ->
            val before = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDateTime().minusMinutes(1)
            presets.currentShift(before)?.let { (prev, prevStart) ->
                TaskStore.receive(prev, prevStart, carry = false)
                TaskStore.tasks.value.filter { it.shift == prevStart }.forEachIndexed { i, t -> if (i % 2 == 0) TaskStore.toggle(t.id) }
            }
        }
        listOf("교육 자료 출력", "물품 청구서 내기", "보호자 면담 시간 잡기").forEach { TaskStore.add(it, top = true) }
        TaskStore.add("인계장 정리")?.let { TaskStore.toggle(it.id, Day.today()) }
        if (NotiListener.available(context)) NotiListener.practice.value = sampleNotis(context)
        _active.value = true
    }

    fun end(context: Context) {
        if (!_active.value) return
        _active.value = false
        NotiListener.practice.value = null
        KeywordAlert.dismiss(context, KEYWORD_KEY)
        TaskStore.endPractice()
    }

    /** 예시 알림을 보낸 앱: 기본 문자 앱(아이콘·이름이 실제처럼 보이게), 없으면 이 앱. */
    private fun smsApp(context: Context): Pair<String, String> {
        val pkg = Telephony.Sms.getDefaultSmsPackage(context) ?: context.packageName
        val pm = context.packageManager
        return pkg to runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault("문자")
    }

    private fun sampleNotis(context: Context): List<Noti> {
        val (pkg, app) = smsApp(context)
        val now = System.currentTimeMillis()
        return listOf(
            Triple("검사실", "CT 검사 30분 뒤에 내려 보내 주세요.", 2),
            Triple("약제부", "오늘 퇴원약 준비 다 됐어요.", 9),
            Triple("간호부", "내일 오후 2시 보수교육 안내드립니다.", 25),
        ).mapIndexed { i, (title, text, min) ->
            Noti("practice-$i", pkg, title, text, now - min * 60_000L, intent = null, autoCancel = false, app = app)
        }
    }

    /** 키워드 알림 예시: 실제처럼 진동하며 팝업이 뜨고 할 일 화면 맨 위에 붉은 카드가 남습니다. */
    fun sendKeyword(context: Context) {
        val (pkg, app) = smsApp(context)
        KeywordAlert.practice(
            context,
            KeywordAlert.Hit(
                "practice|$pkg|kw", pkg, app, "배정되었습니다", "병동 공지",
                "신규 입원 1명 배정되었습니다. 30분 뒤 올라옵니다.", System.currentTimeMillis(), null,
            ),
        )
    }
}

/** 단계마다 확인할 할 일 화면 상태. 단계가 시작될 때 값을 기억해 두고 늘거나 바뀌면 그 단계를 한 것으로 봅니다. */
internal data class PracticeSignals(
    val page: Int,
    val dutyShift: Long,
    val dutyDone: Int,
    val todoDone: Int,
    val todoCount: Int,
    val menuOpen: Boolean,
    val donePanel: Boolean,
    val notis: Int,
    val hits: Int,
    val alarms: Int,
    val shortcutPanel: Boolean,
)

/** [done] 은 (단계 시작 때 상태, 지금 상태, 키워드 예시를 받았는지). */
private class Step(val title: String, val body: String, val action: String? = null, val done: (PracticeSignals, PracticeSignals, Boolean) -> Boolean)

/**
 * 체험 안내: 시계 자리에 '잠금화면 체험 n/N' · 제목 · 설명 · (예시 알림 받기) · 다음/끝내기. 시킨 것을 하면 ✓ 를 보이고 다음 단계로.
 * 알림 단계(다른 앱 알림·키워드 알림)는 알림판에서만, 알람 단계는 바로가기에 알람이 있을 때 알람으로 안내합니다.
 */
@Composable
internal fun PracticeCoach(signals: PracticeSignals, pocketPage: Int, noti: Boolean, hasAlarm: Boolean, show: Boolean, onEnd: () -> Unit) {
    val context = LocalContext.current
    val steps = remember(noti, hasAlarm) {
        buildList {
            add(Step("교대 받기", "'근무' 페이지 맨 위 버튼을 눌러 지금 근무를 받아 보세요. 앞 근무에서 못 끝낸 일은 가져올지 골라요.") { a, b, _ -> b.dutyShift != a.dutyShift })
            add(Step("근무 할 일 체크", "동그라미를 눌러 체크해 보세요. 위 칸은 지금 할 일, 아래 칸은 이번 근무 동안 할 일이에요.") { a, b, _ -> b.dutyDone > a.dutyDone })
            add(Step("할 일 체크", "위 '할 일' 탭을 누르거나 옆으로 넘겨, 동그라미를 눌러 끝내 보세요. 잠깐 뜨는 '되돌리기'로 되돌려요.") { a, b, _ -> b.todoDone > a.todoDone })
            add(Step("할 일 적기", "아래 '할 일 추가'를 눌러 아무거나 적고 엔터를 눌러 보세요. 맨 위에 들어가요.") { a, b, _ -> b.todoCount > a.todoCount })
            add(Step("줄 메뉴", "할 일 글씨를 눌러 보세요. 타이머 맞추기 · 이름 고치기 · 지우기가 나와요.") { _, b, _ -> b.menuOpen })
            add(Step("끝낸 일", "카드 맨 아래 '완료' 줄을 누르면 오늘 끝낸 일이 보여요. 동그라미로 되돌려요.") { _, b, _ -> b.donePanel })
            if (noti) {
                add(Step("다른 앱 알림", "카드 오른쪽 아래 알림 줄(근무·포켓북에선 위 종 버튼)을 눌러 알림을 보고, ✕로 하나 지워 보세요.") { a, b, _ -> b.notis < a.notis })
                add(
                    Step(
                        "키워드 알림",
                        "고른 앱 알림에 키워드가 있으면 진동하고 맨 위에 붉은 카드로 남아요. 예시를 받아 '확인'이나 '할 일로 추가'를 눌러 보세요.",
                        action = "예시 알림 받기",
                    ) { _, b, sent -> sent && b.hits == 0 },
                )
            }
            add(Step("포켓북", "'포켓북' 탭으로 넘기면 라운딩 체크리스트를 보고 체크해요.") { _, b, _ -> b.page == pocketPage })
            add(
                if (hasAlarm) Step("알람 · 바로가기", "아래 '알람'을 눌러 몇 분 뒤 알람을 맞춰 보세요. 바로가기를 길게 눌러 끌면 자리가 바뀌어요.") { a, b, _ -> b.alarms > a.alarms || b.shortcutPanel }
                else Step("바로가기", "아래 바로가기를 길게 눌러 보세요. 끌어 놓으면 자리가 바뀌고, 그냥 떼면 편집이 열려요. 빈 칸 '+'로 모바일 EMR 같은 앱도 넣어요.") { _, b, _ -> b.shortcutPanel },
            )
            add(Step("잠금 해제", "다 했어요! 화면을 위로 밀면 잠금이 풀려요. 밀어서 체험을 끝내 보세요. 체험에서 바꾼 것은 저장되지 않아요.") { _, _, _ -> false })
        }
    }
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(steps.indices)]
    val start = remember(index) { signals }
    // 키워드 단계: 예시를 받은 뒤 카드를 '확인'하거나 할 일로 넣어 카드가 없어지면 끝
    var keywordSent by remember { mutableStateOf(false) }
    val did = step.done(start, signals, keywordSent)
    LaunchedEffect(index, did) {
        if (did && index < steps.lastIndex) {
            delay(900)
            index++
        }
    }
    // 적는 동안·알람 시트가 열린 동안은 안내만 숨김(단계와 시작 값은 그대로 — 안 그러면 할 일을 적고 나면 처음 단계로 돌아감)
    if (!show) return
    val accent = LocalLockAccent.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Lk.Toast, shape)
            .border(1.dp, accent.copy(alpha = 0.6f), shape)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("잠금화면 체험 · ${index + 1}/${steps.size}", style = Lk.Caption, color = Lk.Muted, modifier = Modifier.weight(1f))
            if (index < steps.lastIndex) CoachButton("다음 ›", Lk.Text) { index++ }
            CoachButton("끝내기", Lk.Muted, onEnd)
        }
        AnimatedContent(index, label = "coach") { i ->
            val s = steps[i]
            Column(Modifier.padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (did && i == index) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "했어요", tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(s.title, style = Lk.Title, fontWeight = Lk.Strong, color = Lk.Text)
                }
                Text(s.body, style = Lk.Body15, color = Lk.Body, modifier = Modifier.padding(top = 2.dp))
            }
        }
        step.action?.let { label ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                CoachButton(label, accent) {
                    keywordSent = true
                    Practice.sendKeyword(context)
                }
            }
        }
    }
}

@Composable
private fun CoachButton(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Press(onClick, Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(50), color = androidx.compose.ui.graphics.Color.Transparent) {
        Text(label, style = Lk.Body15, fontWeight = Lk.Strong, color = color, modifier = Modifier.padding(horizontal = 12.dp))
    }
}
