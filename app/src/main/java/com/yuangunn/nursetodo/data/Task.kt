package com.yuangunn.nursetodo.data

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
 * - [shift] 는 교대 프리셋에서 넣은 할 일이면 그 교대가 시작된 시각(ms). 교대를 받을 때 못 끝낸 것은 가져오거나 지웁니다.
 * - [subs] 는 프리셋에서 두 칸 들여 쓴 하위 항목, [subsDone] 은 그중 끝낸 자리. 하위 항목이 있으면 모두 끝냈을 때만 끝낸 것입니다.
 * - [carried] 는 앞 근무에서 못 끝내 가져온 항목이면 그 근무 이름. 새 근무 시각표와 상관없이 미체크로 보입니다.
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
    val subs: List<String> = emptyList(),
    val subsDone: Set<Int> = emptySet(),
    val carried: String? = null,
) {
    val isRoutine: Boolean get() = repeatMask != 0

    /**
     * '완료 모두 지우기'로 지울 수 있는지: [day] 에 끝낸 일반 할 일만. 루틴은 지우면 반복이 사라지고(오늘 체크는 내일 풀림),
     * 근무(교대 프리셋) 할 일은 끝낸 기록으로 근무 루틴 알림을 건너뛰므로(Routine) 남깁니다. 다음 교대를 받으면 바뀝니다.
     */
    // 일반 할 일의 isDone 은 날짜를 보지 않으므로, 그날 목록의 '완료'에 보이는 것(doneOn == day)만 고릅니다.
    fun clearableOn(day: Long): Boolean = !isRoutine && shift == null && doneOn == day

    /** 만든 시각(ms). id 를 만든 순간의 시각으로 정하므로 따로 저장하지 않습니다. */
    val createdAt: Long get() = id

    /** [day] 에 체크한 시각. 루틴은 마지막 체크가 그날일 때만 압니다. */
    fun doneAtOn(day: Long): Long? = doneAt?.takeIf { isDone(day) && (!isRoutine || Day.of(it) == day) }

    fun isDone(day: Long): Boolean = if (isRoutine) day in doneDays else doneOn != null

    /** [day] 에 체크/체크 해제한 결과. 하위 항목이 있으면 모두 체크하거나 모두 풉니다. */
    fun toggled(day: Long, now: Long = System.currentTimeMillis()): Task = when {
        isRoutine -> if (day in doneDays) copy(doneDays = doneDays - day) else copy(doneDays = doneDays + day, doneAt = now)
        doneOn != null -> copy(doneOn = null, doneAt = null, subsDone = emptySet())
        else -> copy(doneOn = day, doneAt = now, subsDone = subs.indices.toSet())
    }

    /** 하위 항목 [index] 를 체크/해제한 결과. 모두 끝나면 위 항목도 그 시각에 끝냄. */
    fun toggledSub(index: Int, day: Long, now: Long = System.currentTimeMillis()): Task {
        val done = if (index in subsDone) subsDone - index else subsDone + index
        val all = subs.isNotEmpty() && done.size == subs.size
        return copy(subsDone = done, doneOn = if (all) day else null, doneAt = if (all) now else null)
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
    val alarmVolume: Int = 3,
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
    /** 근무 루틴 알림 전체 켜기. 시각이 붙은 프리셋 항목을 '근무 교대 받았습니다'를 누른 폰에서만 진동·팝업으로 알립니다. */
    val routineAlarm: Boolean = true,
    /** 받은 근무에서 '루틴 알림 끄기'를 눌렀음. 다음에 교대를 받으면 다시 켜집니다. */
    val routineMuted: Boolean = false,
    /** '근무 교대 받았습니다'를 누른 교대의 시작 시각(ms). 0 이면 아무 교대도 받지 않음(알림 꺼짐). */
    val dutyShift: Long = 0,
    /** 할 일 화면에 보일 페이지(비트: 1 할 일, 2 근무, 4 포켓북). 하나 이상. 근무는 교대 프리셋, 포켓북은 포켓북이 있을 때만 보입니다. */
    val pageMask: Int = 7,
    /** 키워드 알림(알림판): 한 줄에 하나(예전 저장분은 쉼표). [keywordApps] 의 새 알림에 들어 있으면 진동·팝업으로 알립니다. */
    val keywords: String = "",
    /** 키워드 알림 받기. 꺼도 키워드는 남습니다. */
    val keywordOn: Boolean = true,
    /** 키워드 알림을 소리와 진동으로(기본은 진동만). 소리는 휴대폰 알림 설정에서 고릅니다. */
    val keywordSound: Boolean = false,
    /** 앱 테마: 0 시스템 따라감, 1 라이트, 2 다크. 잠금화면은 늘 어둡습니다. */
    val theme: Int = 0,
    /** 항암계산기 주입속도 상한(cc/hr). 넘으면 결과를 주황으로. ponytail: 1000 은 임시값, 기관·펌프 기준으로 정할 것 */
    val rateLimit: Int = 1000,
    /** 사용기한 계산기의 자주 쓰는 기한(칩). */
    val expiryPresets: List<ExpiryPreset> = Expiry.DEFAULTS,
    /** 사용기한을 셀 때 시작한 날을 1일째로(기관 지침에 따라). 끄면 다음 날이 1일째. */
    val expiryFirstDay: Boolean = false,
    /** 키워드 알림을 볼 앱(패키지 이름). 처음엔 삼성 메시지·구글 메시지·팀즈. 비우면 울리지 않음. */
    val keywordApps: Set<String> = setOf("com.samsung.android.messaging", "com.google.android.apps.messaging", "com.microsoft.teams"),
    /** 첫 실행 안내를 끝냈거나 건너뜀. 새로 깔면 false, 2.1.0 전부터 쓰던 설치(저장 파일에 이 값이 없음)는 true. */
    val onboarded: Boolean = false,
)

/** [Settings.keywords] 를 줄(또는 쉼표)로 나눈 목록. */
val Settings.keywordList: List<String> get() = keywords.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** [text] 에 들어 있는 첫 키워드. 띄어쓰기는 빼고 대소문자 없이 비교합니다('배정 되었습니다'도 '배정되었습니다'로). */
fun keywordHit(keywords: List<String>, text: String): String? {
    val squeezed = text.filterNot(Char::isWhitespace)
    return keywords.firstOrNull { k -> k.filterNot(Char::isWhitespace).let { it.isNotEmpty() && squeezed.contains(it, ignoreCase = true) } }
}

/**
 * 교대 프리셋 하나. [start] 는 매일 불러올 시각(자정부터 분), null 이면 자동으로 불러오지 않습니다.
 * [items] 는 한 줄에 하나, '07:00 정규 라운딩'처럼 앞에 시각이 있으면 그 시각에 근무 루틴 알림. [quiet] 는 알림을 끈 줄.
 */
data class Preset(val id: Long, val name: String, val start: Int?, val items: List<String>, val quiet: Set<String> = emptySet()) {
    companion object {
        val Defaults = listOf(
            Preset(1, "Day", 7 * 60, emptyList()),
            Preset(2, "Evening", 15 * 60, emptyList()),
            Preset(3, "Night", 23 * 60, emptyList()),
        )

        /** 프리셋 고치기의 '예시 불러오기': 근무마다 시간별 할 일과 이번 근무 할 일. 이름은 [Defaults] 와 같음. */
        val Examples: Map<String, Pair<List<String>, List<String>>> = mapOf(
            "Day" to (
                listOf(
                    "07:00 정규라운딩",
                    "07:00 혈당측정, V/S CHECK",
                    "09:00 정규라운딩",
                    "11:00 정규라운딩",
                    "13:00 정규라운딩",
                    "13:00 I/O Check",
                ) to listOf("루틴 차팅", "시계열", "처치물품fee")
                ),
            "Evening" to (
                listOf(
                    "15:00 정규라운딩",
                    "15:00 V/S check",
                    "17:00 정규라운딩",
                    "17:00 혈당 측정",
                    "19:00 정규라운딩",
                    "19:00 V/S check",
                    "21:00 정규라운딩",
                    "21:00 I/O check, 혈당측정",
                ) to listOf("루틴 차팅", "시계열", "신환fee", "식사 처방", "익일 검사 설명")
                ),
            "Night" to (
                listOf(
                    "23:00 정규라운딩",
                    "01:00 정규라운딩",
                    "03:00 정규라운딩",
                    "05:00 정규라운딩",
                    "05:00 I/O check",
                ) to listOf(
                    "루틴 차팅",
                    "중앙부멸균물품 신청",
                    "각종 기기 충전",
                    "오더 확인 및 시계열 리뷰",
                    "익일 아침약 확인",
                    "익일 수술환자 전처치 확인",
                    "비급여동의서 서식 작성",
                    "미시행 검사 확인",
                    "익일 검사 확인 및 SBAR출력(이송신청)",
                    "식사 확인",
                    "간호필요도 입력",
                    "조무사워크시트 작성",
                    "(주말) 드레싱장부 작성",
                    "신환 EMR누락 확인",
                    "처치fee 누락 확인",
                    "처치전담/정맥전담 의뢰",
                    "퇴원환자 퇴원봉투 정리",
                    "POCT QC test",
                    "약 반환",
                    "카트채우기 및 환경청소",
                    "필수기록서식 확인",
                )
                ),
        )

        /**
         * [p] 에 맞는 예시 이름: 같은 이름(대소문자 무시)이 있으면 그것, 없으면 근무 시작 시각이 가장 가까운 기본 근무
         * (07:00 Day · 15:00 Evening · 23:00 Night, 자정을 넘겨 잼).
         */
        fun exampleFor(p: Preset): String {
            Examples.keys.firstOrNull { it.equals(p.name.trim(), ignoreCase = true) }?.let { return it }
            val start = p.start ?: return "Day"
            return Defaults.minBy { d -> (((d.start!! - start) % 1440 + 1440) % 1440).let { minOf(it, 1440 - it) } }.name
        }
    }
}

private val ItemTime = Regex("""^(\d{1,2}):(\d{2})(?=\s|$)""")

/** '07:00 정규 라운딩'처럼 맨 앞에 시각이 있으면 그 시각(자정부터 분). */
fun itemMinute(text: String): Int? {
    val (h, m) = ItemTime.find(text.trim())?.destructured ?: return null
    return (h.toInt() * 60 + m.toInt()).takeIf { h.toInt() < 24 && m.toInt() < 60 }
}

/** 맨 앞 시각을 뺀 내용('07:00 정규 라운딩' → '정규 라운딩'). 근무 페이지는 시각을 따로 한 칸에 보여 줍니다. */
fun itemText(text: String): String = text.trim().replaceFirst(Regex("""^\d{1,2}:\d{2}\s*"""), "")

/** 교대([shift] 시작 ms) 안의 [minute] 시각(ms). 교대 시작보다 이른 시각은 자정을 넘긴 다음 날입니다(Night 의 01:00). */
fun shiftTime(shift: Long, minute: Int): Long {
    val start = Instant.ofEpochMilli(shift).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val day = if (minute < start.hour * 60 + start.minute) start.toLocalDate().plusDays(1) else start.toLocalDate()
    return day.atStartOfDay().plusMinutes(minute.toLong()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** 교대 프리셋에서 넣은 할 일 중 시각이 붙은 것이면 그 시각(ms). 지나도록 안 끝내면 빨갛게 보입니다. */
val Task.due: Long? get() = shift?.let { s -> itemMinute(title)?.let { shiftTime(s, it) } }

/** [shift](시작 ms)에 시작하는 프리셋. */
fun List<Preset>.forShift(shift: Long): Preset? {
    val t = Instant.ofEpochMilli(shift).atZone(ZoneId.systemDefault()).toLocalTime()
    return firstOrNull { it.start == t.hour * 60 + t.minute }
}

/**
 * '근무 교대 받았습니다'로 받을 교대와 그 시작 시각(ms). 인계는 교대 시각 전에 받으므로 다음 교대가 [early] 안에 시작하면
 * 다음 교대를, 아니면 지금 교대를.
 */
fun List<Preset>.dutyTarget(now: LocalDateTime, early: Long = 60 * 60_000L): Pair<Preset, Long>? {
    val ms = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    nextShift(now)?.takeIf { it - ms <= early }?.let { next -> forShift(next)?.let { return it to next } }
    return currentShift(now)
}

/** 두 칸(또는 탭) 들여 쓴 프리셋 줄: 위 줄의 하위 항목. */
fun isSubLine(line: String): Boolean = line.startsWith("  ") || line.startsWith("\t")

/**
 * 프리셋 줄을 시간별 할 일(앞에 시각)과 이번 근무 할 일(시각 없음)로 나눕니다. 저장 형식은 그대로(시각 유무로만 갈림)라
 * 옛 프리셋·주고받은 글도 바로 두 칸으로 보입니다. 하위 항목(두 칸 들여 쓴 줄)은 위 줄을 따라가고, 위 줄이 없으면 근무 할 일로.
 */
fun splitPresetItems(items: List<String>): Pair<List<String>, List<String>> {
    val timed = mutableListOf<String>()
    val other = mutableListOf<String>()
    var into = other
    items.filter { it.isNotBlank() }.forEach { line ->
        if (!isSubLine(line)) into = if (itemMinute(line) != null) timed else other
        into += line
    }
    return timed to other
}

/**
 * 반복해서 넣기: [start] 부터 [every] 분마다 [end] 까지(둘 다 포함) '08:00 라운딩' 줄들. 끝이 시작보다 이르면 자정을 넘깁니다(Night 23:00 → 05:00).
 * 회차마다 따로 체크하고 따로 알리도록 줄을 나눠 넣습니다.
 */
fun repeatLines(text: String, start: Int, every: Int, end: Int): List<String> {
    if (every <= 0 || text.isBlank()) return emptyList()
    val span = ((end - start) % 1440 + 1440) % 1440
    return (0..span step every).map { val m = (start + it) % 1440; "%02d:%02d %s".format(m / 60, m % 60, text.trim()) }
}

/** 프리셋 줄을 (항목, 하위 항목들)로 묶습니다. 빈 줄은 빼고, 위에 항목이 없는 하위 줄은 항목으로. */
fun presetRows(items: List<String>): List<Pair<String, List<String>>> {
    val rows = mutableListOf<Pair<String, MutableList<String>>>()
    items.filter { it.isNotBlank() }.forEach { line ->
        if (isSubLine(line) && rows.isNotEmpty()) rows.last().second += line.trim() else rows += line.trim() to mutableListOf()
    }
    return rows
}

/**
 * 근무 페이지 맨 위에 보일 것. [loaded] 는 지금 목록의 근무(안 받았으면 앞 근무가 그대로 남음).
 * [target] 을 [shift] 근무로 받을 차례면 [received] 가 false: 교대 1시간 전부터 다음 근무, 시작 시각이 지나면 [late].
 * 받은 근무 뒤로 근무 시작 시각이 두 번 지나면(다음 근무도 끝남) 지금 시각에 맞는 근무를 시작할지 묻고([stale] = 끝난 근무 이름과 끝난 시각).
 */
data class DutyHead(
    val loaded: Preset?,
    val target: Preset,
    val shift: Long,
    val received: Boolean,
    val late: Boolean = false,
    val stale: Pair<String, Long>? = null,
)

fun Settings.dutyHead(now: LocalDateTime, early: Long = 60 * 60_000L): DutyHead? {
    if (!presetMode) return null
    val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val loaded = presets.firstOrNull { it.id == presetLoaded } ?: presets.forShift(presetApplied)
    val current = presets.currentShift(now)
    if (presetApplied == 0L || loaded == null) return current?.let { (p, at) -> DutyHead(null, p, at, received = false) }
    val toLocal = { ms: Long -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    val n1 = presets.nextShift(toLocal(presetApplied)) ?: return DutyHead(loaded, loaded, presetApplied, dutyShift == presetApplied)
    val n2 = presets.nextShift(toLocal(n1)) ?: n1
    return when {
        nowMs >= n2 && current != null && current.second != presetApplied ->
            DutyHead(loaded, current.first, current.second, received = false, stale = (presets.forShift(n1)?.name ?: "") to n2)
        nowMs >= n1 - early -> presets.forShift(n1)?.let { DutyHead(loaded, it, n1, received = dutyShift == n1 && presetApplied == n1, late = nowMs >= n1) }
            ?: DutyHead(loaded, loaded, presetApplied, dutyShift == presetApplied)
        else -> DutyHead(loaded, loaded, presetApplied, received = dutyShift == presetApplied)
    }
}

/** 근무 루틴 알림 하나: 프리셋 [index] 번째 줄 [text] 를 [at](ms)에. */
data class Reminder(val index: Int, val at: Long, val text: String)

/**
 * 받은 교대([Settings.dutyShift])에서 [now] 뒤에 남은 근무 루틴 알림. 전체 알림을 껐거나 받은 교대가 없으면 없음.
 * 다음 교대가 시작한 뒤의 시각은 빼서, 교대가 끝나면 이 폰의 알림도 저절로 끝납니다.
 */
fun Settings.reminders(now: Long): List<Reminder> {
    if (!presetMode || !routineAlarm || routineMuted || dutyShift == 0L) return emptyList()
    val p = presets.forShift(dutyShift) ?: return emptyList()
    val end = dutyEnd()
    return p.items.mapIndexedNotNull { i, line ->
        // 하위 항목(두 칸 들여 씀)은 시각을 따로 두지 않습니다.
        if (isSubLine(line)) return@mapIndexedNotNull null
        val m = itemMinute(line) ?: return@mapIndexedNotNull null
        Reminder(i, shiftTime(dutyShift, m), line).takeIf { line !in p.quiet && it.at > now && it.at < end }
    }.sortedBy { it.at }
}

/** 받은 교대가 끝나는 시각(다음 교대 시작, ms). */
private fun Settings.dutyEnd(): Long =
    presets.nextShift(Instant.ofEpochMilli(dutyShift).atZone(ZoneId.systemDefault()).toLocalDateTime()) ?: Long.MAX_VALUE

/** 이 폰이 [now] 에 근무를 받은 상태인지: '근무 교대 받았습니다'를 누른 교대가 끝나기 전. */
fun Settings.onDuty(now: Long): Boolean = presetMode && dutyShift != 0L && now < dutyEnd()

/**
 * 키워드 알림이 [now] 에 울릴지(켜기·키워드·볼 앱은 따로 봄). 교대 프리셋을 쓰면 공용폰 여러 대 중 근무를 받은 폰만([onDuty]),
 * 안 쓰면 '근무를 받은 폰'이 없으니 늘. 예전엔 프리셋을 끄면 영영 안 울렸음.
 */
fun Settings.keywordActive(now: Long): Boolean = !presetMode || onDuty(now)

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
