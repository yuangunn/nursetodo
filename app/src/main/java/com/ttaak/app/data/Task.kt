package com.ttaak.app.data

import android.os.Build
import java.time.Instant
import java.time.LocalDate
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
    /** 잠금화면 카드·홈 위젯 글자 크기 ([CardTextSizes] 의 순번). 앱 목록 글자([textSize])와 따로 둡니다. */
    val cardTextSize: Int = 1,
    /** 잠금화면 카드를 안드로이드 16+ 라이브 알림(Live Updates)으로 표시해 늘 펼쳐 보이게 합니다. 16+ 에서 기본. */
    val lockLive: Boolean = Build.VERSION.SDK_INT >= 36,
    /** 화면이 켜질 때마다 잠금화면 위에 딱 화면을 먼저 띄움 (LockPageService). 기본 방식. */
    val lockPage: Boolean = true,
    /** 알람음(content:// URI). null 이면 폰의 기본 알람음. */
    val alarmSound: String? = null,
    /** 알람 크기 1~10. 울리는 동안만 폰의 알람 음량을 이만큼으로 맞췄다가 끄면 되돌립니다. */
    val alarmVolume: Int = 7,
    /** 울리는 방식: 0 소리+진동, 1 소리만, 2 진동만. */
    val alarmMode: Int = 0,
    /** 폰이 무음·진동 모드여도 소리로 울리기. 끄면 그땐 진동만 합니다. */
    val alarmAlwaysSound: Boolean = true,
    /** 딱 화면 아래 바로가기 (최대 [Shortcut.MAX]개, 3×3). */
    val shortcuts: List<Shortcut> = listOf(Shortcut(Shortcut.CALC), Shortcut(Shortcut.ALARM)),
)

/**
 * 딱 화면 아래 바로가기 하나. [id] 는 [CALC]·[ALARM] 이거나 "app:패키지/액티비티".
 * [name] 이 비어 있으면 앱 이름(또는 '계산기'·'알람')을 씁니다.
 */
data class Shortcut(val id: String, val name: String = "") {
    companion object {
        const val CALC = "calc"
        const val ALARM = "alarm"
        const val MAX = 9
        fun app(component: String) = Shortcut("app:$component")
    }
}

/** 잠금화면 카드·위젯 글자 크기(sp). 기본 '크게' 수준이 1.1 의 14sp 보다 큽니다. */
val CardTextSizes = listOf("보통" to 16f, "크게" to 19f, "아주 크게" to 23f)

fun Settings.cardTextSp(): Float = CardTextSizes[cardTextSize.coerceIn(CardTextSizes.indices)].second

object Day {
    fun today(): Long = LocalDate.now().toEpochDay()

    /** 시각(ms)이 속한 날. */
    fun of(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
}
