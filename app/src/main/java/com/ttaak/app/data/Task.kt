package com.ttaak.app.data

import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 할 일 하나.
 *
 * - [date] 가 null 이면 "오늘 할 일" — 끝낼 때까지 매일 오늘 목록에 남습니다.
 * - [date] 가 지정되면 그 날짜에 보이고, 지나도록 못 끝내면 오늘 목록으로 넘어옵니다.
 * - [repeatMask] 가 0이 아니면 루틴. 월(bit0)~일(bit6) 중 켜진 요일마다 다시 나타납니다.
 * - [doneOn] 은 일반 할 일을 체크한 날(epochDay).
 * - [doneDays] 는 루틴을 체크한 날들. 날마다 따로 기록해서 캘린더에서 지난 날도 맞게 보입니다.
 * - [doneAt] 은 마지막으로 체크한 시각(ms). 앱의 '완료된 할 일'에 보여 줍니다. 1.2 이전에 체크한 것은 없습니다.
 * - [shift] 는 교대 프리셋에서 넣은 할 일이면 그 교대가 시작된 시각(ms). 다음 교대까지 못 끝내면 사라집니다.
 */
data class Task(
    val id: Long,
    val title: String,
    val createdDay: Long,
    val date: Long? = null,
    val repeatMask: Int = 0,
    val doneOn: Long? = null,
    val order: Long = 0,
    // ponytail: 매일 루틴이면 1년에 365개씩 쌓임. 수천 개가 돼도 JSON 몇 KB라 정리하지 않음
    val doneDays: Set<Long> = emptySet(),
    val doneAt: Long? = null,
    val shift: Long? = null,
) {
    val isRoutine: Boolean get() = repeatMask != 0

    /** 만든 시각(ms). id 를 만든 순간의 시각으로 정하므로 따로 저장하지 않습니다. */
    val createdAt: Long get() = id

    /** [day] 에 체크한 시각. 루틴은 마지막 체크가 그날일 때만 압니다. */
    fun doneAtOn(day: Long): Long? = doneAt?.takeIf { isDone(day) && (!isRoutine || Day.of(it) == day) }

    fun isDone(day: Long): Boolean = if (isRoutine) day in doneDays else doneOn != null

    /** [day] 에 체크/체크 해제한 결과. */
    fun toggled(day: Long, now: Long = System.currentTimeMillis()): Task = when {
        isRoutine -> if (day in doneDays) copy(doneDays = doneDays - day) else copy(doneDays = doneDays + day, doneAt = now)
        doneOn != null -> copy(doneOn = null, doneAt = null)
        else -> copy(doneOn = day, doneAt = now)
    }

    fun repeatsOn(day: Long): Boolean = repeatMask and weekdayBit(day) != 0

    fun visibleOn(day: Long, today: Long): Boolean {
        if (isRoutine) return day >= createdDay && repeatsOn(day)
        doneOn?.let { return it == day }
        val due = date ?: return day == today
        return due == day || (day == today && due < today)
    }

    companion object {
        const val EVERY_DAY = 0b1111111

        fun weekdayBit(day: Long): Int = 1 shl (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)
    }
}

/** [at](ms)에 울릴 'N분 뒤' 알람. [id] 는 맞춘 순간의 시각(ms)이라 몇 분짜리였는지도 알 수 있습니다. */
data class Alarm(val id: Long, val at: Long, val label: String = "") {
    val minutes: Long get() = (at - id + 30_000) / 60_000
}

/** 화면/잠금화면 설정. */
data class Settings(
    val newOnTop: Boolean = false,
    val lockEnabled: Boolean = true,
    val lockText: String = "",
    val ddayLabel: String = "",
    val ddayDate: Long? = null,
    val accent: Int = 0,
    val textSize: Int = 1,
    val bold: Boolean = false,
    val checkOnLeft: Boolean = true,
    val setupDismissed: Boolean = false,
    val tileAdded: Boolean = false,
    /** 계산기: 투여시간 단위(true=시간, false=분). 용량은 저장하지 않습니다. */
    val calcHours: Boolean = false,
    /** 할 일 화면·잠금화면 카드·홈 위젯 글자 크기(sp, [CardSpRange]). 앱 목록 글자([textSize])와 따로 둡니다. */
    val cardSp: Int = 19,
    /** 할 일 화면 할 일 줄 사이 여백(dp, [RowGapRange]). 줄마다 글자 위아래로 이만큼. */
    val rowGap: Int = 12,
    /** 잠금화면 카드를 안드로이드 16+ 라이브 알림(Live Updates)으로 표시해 늘 펼쳐 보이게 합니다. 16+ 에서 기본. */
    val lockLive: Boolean = Build.VERSION.SDK_INT >= 36,
    /** 화면이 켜질 때마다 잠금화면 위에 할 일 화면을 먼저 띄움 (LockPageService). 기본 방식. */
    val lockPage: Boolean = true,
    /** 알람음(content:// URI). null 이면 폰의 기본 알람음. */
    val alarmSound: String? = null,
    /** 알람 크기 1~10. 울리는 동안만 폰의 알람 음량을 이만큼으로 맞췄다가 끄면 되돌립니다. */
    val alarmVolume: Int = 7,
    /** 울리는 방식: 0 소리+진동, 1 소리만, 2 진동만. */
    val alarmMode: Int = 0,
    /** 폰이 무음·진동 모드여도 소리로 울리기. 끄면 그땐 진동만 합니다. */
    val alarmAlwaysSound: Boolean = true,
    /** 할 일 화면 아래 바로가기 (최대 [Shortcut.MAX]개 저장, 보이는 건 [shortcutStyle] 칸 수만큼). */
    val shortcuts: List<Shortcut> = listOf(Shortcut(Shortcut.CALC), Shortcut(Shortcut.ALARM), Shortcut(Shortcut.CALENDAR)),
    /** 바로가기 모양: 0 이름 3줄(9칸), 1 이름 2줄(6칸), 2 아이콘만 4개씩 2줄(8칸). */
    val shortcutStyle: Int = 0,
    /** 할 일 화면 '할 일 추가' 버튼: 0 카드 아래 넓은 줄, 1 카드 안 오른쪽 아래 작은 메모 버튼. 바로가기에 [Shortcut.ADD] 가 보이면 둘 다 숨김. */
    val addButton: Int = 0,
    /** 할 일 화면에 알림을 보여 줄 앱(패키지 이름). 비어 있으면 모든 앱. */
    val notiApps: Set<String> = emptySet(),
    /** 교대 프리셋: 켜면 프리셋 시각마다 할 일 목록이 바뀌고, 할 일 화면은 할 일이 가운데를 다 채웁니다. */
    val presetMode: Boolean = false,
    val presets: List<Preset> = Preset.Defaults,
    /** 마지막으로 불러온 교대가 시작된 시각(ms). 이 값이 지금 교대와 다르면 새로 불러옵니다. */
    val presetApplied: Long = 0,
    /** 마지막으로 불러온 프리셋([Preset.id]). '지금 불러오기'로 시각과 다른 프리셋을 불렀을 수 있습니다. */
    val presetLoaded: Long = 0,
)

/** 교대 프리셋 하나. [start] 는 매일 불러올 시각(자정부터 분), null 이면 자동으로 불러오지 않습니다. */
data class Preset(val id: Long, val name: String, val start: Int?, val items: List<String>) {
    companion object {
        val Defaults = listOf(
            Preset(1, "Day", 7 * 60, emptyList()),
            Preset(2, "Evening", 15 * 60, emptyList()),
            Preset(3, "Night", 23 * 60, emptyList()),
        )
    }
}

/**
 * [now] 가 속한 교대: 시작 시각이 지난 프리셋 중 가장 늦은 것과 그 시작 시각(ms). 오늘 아직 아무것도 시작 안 했으면
 * 어제 마지막 교대(자정 넘긴 Night). 같은 시각이면 목록에서 위의 것.
 */
fun List<Preset>.currentShift(now: LocalDateTime): Pair<Preset, Long>? {
    val timed = filter { it.start != null }
    val minute = now.hour * 60 + now.minute
    val today = timed.filter { it.start!! <= minute }.maxByOrNull { it.start!! }
    val p = today ?: timed.maxByOrNull { it.start!! } ?: return null
    val date = if (today != null) now.toLocalDate() else now.toLocalDate().minusDays(1)
    return p to date.atStartOfDay().plusMinutes(p.start!!.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** [now] 다음에 교대가 바뀌는 시각(ms). */
fun List<Preset>.nextShift(now: LocalDateTime): Long? {
    val starts = mapNotNull { it.start }
    val minute = now.hour * 60 + now.minute
    val next = starts.filter { it > minute }.minOrNull()
    val at = if (next != null) now.toLocalDate().atStartOfDay().plusMinutes(next.toLong())
    else now.toLocalDate().plusDays(1).atStartOfDay().plusMinutes(starts.minOrNull()?.toLong() ?: return null)
    return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/**
 * 할 일 화면 아래 바로가기 하나. [id] 는 [CALC]·[ALARM]·[ADD]·[CALENDAR] 이거나 "app:패키지/액티비티".
 * [name] 이 비어 있으면 앱 이름(또는 '계산기'·'알람')을 씁니다.
 */
data class Shortcut(val id: String, val name: String = "") {
    companion object {
        const val CALC = "calc"
        const val ALARM = "alarm"
        const val ADD = "add"
        const val CALENDAR = "calendar"
        const val MAX = 9
        fun app(component: String) = Shortcut("app:$component")
    }
}

val CardSpRange = 12..36
val RowGapRange = 0..24

fun Settings.cardTextSp(): Float = cardSp.coerceIn(CardSpRange).toFloat()

/** 할 일 화면에 보이는 바로가기 칸 수. */
fun Settings.shortcutSlots(): Int = listOf(9, 6, 8)[shortcutStyle.coerceIn(0, 2)]

object Day {
    fun today(): Long = LocalDate.now().toEpochDay()

    /** 시각(ms)이 속한 날. */
    fun of(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
}
