package com.yuangunn.nursetodo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/**
 * 기기 내부 JSON 파일 하나에 모든 할 일과 설정을 저장합니다.
 * 네트워크도, 계정도 없습니다. 메인 스레드에서만 호출하세요.
 */
object TaskStore {
    private lateinit var file: File
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    private val _settings = MutableStateFlow(Settings())
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /** 맞춰 둔 'N분 뒤' 알람 (울릴 시각 순). 울리게 맞추는 건 [com.yuangunn.nursetodo.lock.Alarms] 가 합니다. */
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _pockets = MutableStateFlow<List<Pocket>>(emptyList())

    /** 포켓북 (만든 순서). */
    val pockets: StateFlow<List<Pocket>> = _pockets.asStateFlow()

    /** 데이터가 바뀔 때마다 호출 — 잠금화면 카드와 위젯을 갱신합니다. */
    var onChanged: (() -> Unit)? = null

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "nursetodo.json")
        // v1.6.1 까지의 저장 파일(옛 이름 딱)을 옮깁니다.
        val old = File(context.filesDir, "ttaak.json")
        val moved = !file.exists() && old.exists() && old.renameTo(file)
        load()
        // 코드 패키지가 바뀌어 시스템이 옛 상단바 타일을 지웠으므로 설정 안내에서 다시 추가하게 합니다.
        if (moved && _settings.value.tileAdded) updateSettings { it.copy(tileAdded = false) }
    }

    fun tasksFor(day: Long, today: Long = Day.today()): List<Task> =
        _tasks.value
            .filter { it.visibleOn(day, today) }
            .sortedWith(compareBy<Task>({ it.isDone(day) }, { it.order }))

    fun get(id: Long): Task? = _tasks.value.firstOrNull { it.id == id }

    /** [top] 이면 설정과 상관없이 맨 위에 (잠금화면에서 넣는 할 일). */
    fun add(title: String, day: Long = Day.today(), top: Boolean = false): Task? {
        val text = title.trim()
        if (text.isEmpty()) return null
        val list = _tasks.value
        val order = when {
            list.isEmpty() -> 0L
            top || _settings.value.newOnTop -> list.minOf { it.order } - 1
            else -> list.maxOf { it.order } + 1
        }
        val today = Day.today()
        val task = Task(
            id = System.currentTimeMillis().coerceAtLeast((list.maxOfOrNull { it.id } ?: 0) + 1),
            title = text,
            createdDay = today,
            date = if (day == today) null else day,
            order = order,
        )
        commit(list + task)
        return task
    }

    fun toggle(id: Long, day: Long = Day.today()) {
        commit(_tasks.value.map { if (it.id == id) it.toggled(day) else it })
    }

    /** 하위 항목 하나 체크/해제. 모두 끝나면 위 항목도 끝냄. */
    fun toggleSub(id: Long, index: Int, day: Long = Day.today()) {
        commit(_tasks.value.map { if (it.id == id) it.toggledSub(index, day) else it })
    }

    fun update(task: Task) {
        commit(_tasks.value.map { if (it.id == task.id) task else it })
    }

    fun delete(id: Long) {
        commit(_tasks.value.filterNot { it.id == id })
    }

    /** 지운 할 일을 되돌립니다 (순서·완료 기록 그대로). */
    fun restore(task: Task) {
        if (get(task.id) == null) commit(_tasks.value + task)
    }

    /** [day] 에 끝낸 일반 할 일을 한 번에 지우고, 되돌리기용으로 지운 것을 돌려줍니다([Task.clearableOn]). */
    fun deleteDone(day: Long): List<Task> {
        val gone = _tasks.value.filter { it.clearableOn(day) }
        if (gone.isNotEmpty()) commit(_tasks.value - gone.toSet())
        return gone
    }

    /** [deleteDone] 되돌리기. 이미 있는 것은 건너뜁니다. */
    fun restoreAll(tasks: List<Task>) {
        val have = _tasks.value.mapTo(HashSet()) { it.id }
        val back = tasks.filter { it.id !in have }
        if (back.isNotEmpty()) commit(_tasks.value + back)
    }

    /** 지난 날 완료한 일반 할 일을 지웁니다. 루틴은 남겨 둡니다. */
    fun clearOldDone(): Int {
        val today = Day.today()
        val keep = _tasks.value.filterNot { !it.isRoutine && it.doneOn != null && it.doneOn < today }
        val removed = _tasks.value.size - keep.size
        if (removed > 0) commit(keep)
        return removed
    }

    /** [ids] 순서대로 다시 정렬합니다. 목록에 없는 항목의 순서는 건드리지 않습니다. */
    fun reorder(ids: List<Long>) {
        val idSet = ids.toSet()
        val slots = _tasks.value.filter { it.id in idSet }.map { it.order }.sorted()
        if (slots.size != ids.size) return
        val newOrder = ids.zip(slots).toMap()
        commit(_tasks.value.map { t -> newOrder[t.id]?.let { t.copy(order = it) } ?: t })
    }

    /**
     * 교대 프리셋 불러오기: 프리셋에서 넣었다가 못 끝낸 할 일은 지우고 [preset] 항목을 맨 위에 새로 넣습니다(두 칸 들여 쓴 줄은 하위 항목).
     * 이번 교대([shift])에 이미 끝낸 항목과 이번 교대로 가져온 항목은 그대로 둡니다. 직접 추가한 할 일도 그대로.
     */
    fun loadPreset(preset: Preset, shift: Long) {
        val kept = _tasks.value.filterNot { it.shift != null && it.doneOn == null && !(it.carried != null && it.shift == shift) }
        val done = kept.filter { it.shift == shift }.map { it.title }.toSet()
        val rows = presetRows(preset.items).filter { it.first !in done }
        val top = (kept.minOfOrNull { it.order } ?: 0) - rows.size
        val firstId = System.currentTimeMillis().coerceAtLeast((_tasks.value.maxOfOrNull { it.id } ?: 0) + 1)
        val today = Day.today()
        val added = rows.mapIndexed { i, (t, subs) -> Task(id = firstId + i, title = t, createdDay = today, order = top + i, shift = shift, subs = subs) }
        _settings.value = _settings.value.copy(presetApplied = shift, presetLoaded = preset.id)
        commit(kept + added)
    }

    /**
     * 교대는 잠금화면에서 '근무 교대 받았습니다'를 눌러야 바뀝니다([receive]). 여기서는 교대 프리셋을 켰는데 아직 아무 근무도
     * 불러오지 않았을 때만 지금 시각의 근무를 불러옵니다. 화면이 켜질 때·앱을 열 때 부릅니다.
     */
    fun refreshPreset(now: LocalDateTime = LocalDateTime.now()) {
        val s = _settings.value
        if (!s.presetMode || s.presetApplied != 0L) return
        val (preset, shift) = s.presets.currentShift(now) ?: return
        loadPreset(preset, shift)
    }

    /** 교대 받기 전의 할 일·설정·포켓북(10초 되돌리기용). */
    class Snapshot internal constructor(internal val tasks: List<Task>, internal val settings: Settings, internal val pockets: List<Pocket>)

    fun snapshot() = Snapshot(_tasks.value, _settings.value, _pockets.value)

    fun restore(snap: Snapshot) {
        _tasks.value = snap.tasks
        _pockets.value = snap.pockets
        _settings.value = snap.settings
        save()
        onChanged?.invoke()
    }

    /**
     * '근무 교대 받았습니다'(또는 지금 시각의 근무 시작): [preset] 을 [shift] 근무로 불러오고 이 폰에서 그 근무의 루틴·키워드 알림을 켭니다.
     * 지금 목록과 다른 근무면 앞 근무에서 못 끝낸 항목은 [carry] 면 새 목록 맨 위로 가져오고(하위 체크 그대로), 아니면 지웁니다.
     * 공용폰을 다음 근무자가 받으므로 포켓북 체크를 지우고, 꺼 둔 루틴 알림을 다시 켭니다. 지운 포켓북 체크 수와 루틴 알림을 다시 켰는지.
     */
    fun receive(preset: Preset, shift: Long, carry: Boolean): Pair<Int, Boolean> {
        val s = _settings.value
        if (shift != s.presetApplied) {
            val from = s.presets.firstOrNull { it.id == s.presetLoaded }?.name ?: s.presets.forShift(s.presetApplied)?.name ?: ""
            val pending = _tasks.value.filter { it.shift != null && it.shift == s.presetApplied && it.doneOn == null }
            val kept = _tasks.value.filterNot { it.shift != null && it.doneOn == null }
            val rows = presetRows(preset.items)
            val carried = if (carry) pending.sortedWith(compareBy({ it.carried == null }, { it.due ?: Long.MAX_VALUE }, { it.order })) else emptyList()
            val top = (kept.minOfOrNull { it.order } ?: 0) - rows.size - carried.size
            val firstId = System.currentTimeMillis().coerceAtLeast((_tasks.value.maxOfOrNull { it.id } ?: 0) + 1)
            val today = Day.today()
            val moved = carried.mapIndexed { i, t -> t.copy(shift = shift, carried = from, order = top + i) }
            val added = rows.mapIndexed { i, (t, subs) ->
                Task(id = firstId + i, title = t, createdDay = today, order = top + carried.size + i, shift = shift, subs = subs)
            }
            _tasks.value = kept + moved + added
        }
        val cleared = _pockets.value.sumOf { it.checks.size }
        if (cleared > 0) _pockets.value = _pockets.value.map { it.copy(checks = emptySet()) }
        val unmuted = s.routineMuted
        _settings.value = s.copy(presetApplied = shift, presetLoaded = preset.id, dutyShift = shift, routineMuted = false)
        save()
        onChanged?.invoke()
        return cleared to unmuted
    }

    /** 붙여넣은 글로 포켓북을 만듭니다(여러 개면 여러 개). 만든 수. */
    fun addPockets(pasted: String): Int {
        val texts = splitPockets(pasted)
        val first = System.currentTimeMillis().coerceAtLeast((_pockets.value.maxOfOrNull { it.id } ?: 0) + 1)
        commitPockets(_pockets.value + texts.mapIndexed { i, t -> Pocket(first + i, t) })
        return texts.size
    }

    /** 글을 고치면 항목 자리가 바뀔 수 있어 체크는 비웁니다. */
    fun updatePocket(id: Long, text: String) =
        commitPockets(_pockets.value.map { if (it.id == id) Pocket(id, text.trim()) else it })

    fun deletePocket(id: Long) = commitPockets(_pockets.value.filterNot { it.id == id })

    /** 포켓북 순서 바꾸기([ids] 순서대로). 잠금화면 포켓북 페이지도 이 순서. */
    fun reorderPockets(ids: List<Long>) {
        val byId = _pockets.value.associateBy { it.id }
        val list = ids.mapNotNull { byId[it] }
        if (list.size == byId.size) commitPockets(list)
    }

    fun togglePocketCheck(id: Long, path: String) = commitPockets(
        _pockets.value.map { if (it.id == id) it.copy(checks = if (path in it.checks) it.checks - path else it.checks + path) else it },
    )

    fun clearPocketChecks(id: Long) = setPocketChecks(id, emptySet())

    /** 체크를 통째로 바꿉니다('체크 지우기' 되돌리기). */
    fun setPocketChecks(id: Long, checks: Set<String>) = commitPockets(_pockets.value.map { if (it.id == id) it.copy(checks = checks) else it })

    /** 근무 루틴 알림 '09:00 정규 라운딩(…)'이 울리면, 이름이 그렇게 시작하는 포켓북('정규 라운딩')의 체크를 새로 시작합니다. */
    fun restartPocketsFor(routine: String) {
        val text = itemText(routine)
        if (_pockets.value.none { it.checks.isNotEmpty() && text.startsWith(it.doc.title) }) return
        commitPockets(_pockets.value.map { if (text.startsWith(it.doc.title)) it.copy(checks = emptySet()) else it })
    }

    private fun commitPockets(list: List<Pocket>) {
        _pockets.value = list
        save()
    }

    /** 켜면 지금 교대의 프리셋을 바로 불러오고, 끄면 프리셋에서 넣었다가 못 끝낸 할 일을 치웁니다. */
    fun setPresetMode(on: Boolean) {
        updateSettings { it.copy(presetMode = on, presetApplied = 0) }
        if (on) refreshPreset() else commit(_tasks.value.filterNot { it.shift != null && it.doneOn == null })
    }

    /** 프리셋 목록 저장. 지금 불러와 둔 프리셋([changed])을 고쳤으면 그 근무로 다시 불러옵니다(끝낸 것·가져온 것은 그대로). */
    fun savePresets(list: List<Preset>, changed: Long? = null, now: LocalDateTime = LocalDateTime.now()) {
        updateSettings { it.copy(presets = list) }
        val s = _settings.value
        if (!s.presetMode) return
        if (s.presetApplied == 0L) return refreshPreset(now)
        if (changed == s.presetLoaded) list.firstOrNull { it.id == changed }?.let { loadPreset(it, s.presetApplied) }
    }

    /** 받은 근무의 루틴 알림 끄기·켜기. 다음에 교대를 받으면 다시 켜집니다. */
    fun muteRoutine(muted: Boolean) = updateSettings { it.copy(routineMuted = muted) }

    /** [at](ms)에 울릴 알람. '몇 분 뒤'도 '몇 시에'도 시각으로 받습니다. */
    fun addAlarm(at: Long, label: String): Alarm {
        val now = System.currentTimeMillis().coerceAtLeast((_alarms.value.maxOfOrNull { it.id } ?: 0) + 1)
        val alarm = Alarm(id = now, at = at, label = label.trim())
        _alarms.value = (_alarms.value + alarm).sortedBy { it.at }
        save()
        return alarm
    }

    /** 끈 알람을 그대로 되살립니다(되돌리기). */
    fun restoreAlarm(alarm: Alarm) {
        if (_alarms.value.any { it.id == alarm.id }) return
        _alarms.value = (_alarms.value + alarm).sortedBy { it.at }
        save()
    }

    fun removeAlarm(id: Long) {
        _alarms.value = _alarms.value.filterNot { it.id == id }
        save()
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.value = transform(_settings.value)
        save()
        onChanged?.invoke()
    }

    private fun commit(list: List<Task>) {
        _tasks.value = list
        save()
        onChanged?.invoke()
    }

    private fun save() {
        val root = JSONObject()
        root.put("tasks", JSONArray().apply { _tasks.value.forEach { put(it.toJson()) } })
        root.put("settings", _settings.value.toJson())
        root.put("alarms", JSONArray().apply { _alarms.value.forEach { put(it.toJson()) } })
        root.put(
            "pockets",
            JSONArray().apply {
                _pockets.value.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("checks", JSONArray(it.checks.sorted()))) }
            },
        )
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("tasks") ?: JSONArray()
            _tasks.value = (0 until arr.length()).map { arr.getJSONObject(it).toTask() }
            root.optJSONObject("settings")?.let { _settings.value = it.toSettings() }
            val alarms = root.optJSONArray("alarms") ?: JSONArray()
            _alarms.value = (0 until alarms.length()).map { alarms.getJSONObject(it).toAlarm() }.sortedBy { it.at }
            val pockets = root.optJSONArray("pockets") ?: JSONArray()
            _pockets.value = (0 until pockets.length()).map { i ->
                val o = pockets.getJSONObject(i)
                val checks = o.optJSONArray("checks")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                Pocket(o.getLong("id"), o.getString("text"), checks)
            }
        }.onFailure {
            // 읽지 못한 파일을 다음 저장 때 덮어쓰지 않도록 옆에 따로 남겨 둡니다.
            file.renameTo(File(file.parentFile, "nursetodo.broken-${System.currentTimeMillis()}.json"))
        }
    }

    internal fun Task.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("created", createdDay)
        date?.let { put("date", it) }
        put("repeat", repeatMask)
        doneOn?.let { put("doneOn", it) }
        put("order", order)
        if (doneDays.isNotEmpty()) put("doneDays", JSONArray(doneDays.sorted()))
        doneAt?.let { put("doneAt", it) }
        shift?.let { put("shift", it) }
        if (subs.isNotEmpty()) put("subs", JSONArray(subs))
        if (subsDone.isNotEmpty()) put("subsDone", JSONArray(subsDone.sorted()))
        carried?.let { put("carried", it) }
    }

    internal fun JSONObject.toTask(): Task {
        val repeat = optInt("repeat")
        val doneOn = if (has("doneOn")) getLong("doneOn") else null
        val doneDays = optJSONArray("doneDays")?.let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() }
        return Task(
            id = getLong("id"),
            title = getString("title"),
            createdDay = getLong("created"),
            date = if (has("date")) getLong("date") else null,
            repeatMask = repeat,
            // 1.1 이하는 루틴 완료도 doneOn 하나에 저장했습니다.
            doneOn = doneOn.takeIf { repeat == 0 },
            order = optLong("order"),
            doneDays = doneDays ?: if (repeat != 0 && doneOn != null) setOf(doneOn) else emptySet(),
            doneAt = if (has("doneAt")) getLong("doneAt") else null,
            shift = if (has("shift")) getLong("shift") else null,
            subs = optJSONArray("subs")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            subsDone = optJSONArray("subsDone")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: emptySet(),
            carried = if (has("carried")) getString("carried") else null,
        )
    }

    internal fun Alarm.toJson() = JSONObject().apply {
        put("id", id)
        put("at", at)
        put("label", label)
    }

    internal fun JSONObject.toAlarm() = Alarm(id = getLong("id"), at = getLong("at"), label = optString("label"))

    internal fun Settings.toJson() = JSONObject().apply {
        put("newOnTop", newOnTop)
        put("lockEnabled", lockEnabled)
        put("accent", accent)
        put("textSize", textSize)
        put("bold", bold)
        put("checkOnLeft", checkOnLeft)
        put("setupDismissed", setupDismissed)
        put("tileAdded", tileAdded)
        put("calcHours", calcHours)
        put("cardSp", cardSp)
        put("rowGap", rowGap)
        put("lockLive", lockLive)
        put("lockPage", lockPage)
        alarmSound?.let { put("alarmSound", it) }
        put("alarmVolume", alarmVolume)
        put("alarmMode", alarmMode)
        put("alarmAlwaysSound", alarmAlwaysSound)
        put("shortcuts", JSONArray().apply { shortcuts.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        put("notiApps", JSONArray(notiApps.sorted()))
        put("shortcutStyle", shortcutStyle)
        put("addButton", addButton)
        put("presetMode", presetMode)
        put("presets", JSONArray().apply { presets.forEach { put(it.toJson()) } })
        put("presetApplied", presetApplied)
        put("presetLoaded", presetLoaded)
        put("routineAlarm", routineAlarm)
        put("routineMuted", routineMuted)
        put("dutyShift", dutyShift)
        put("pageMask", pageMask)
        put("keywords", keywords)
        put("keywordOn", keywordOn)
        put("keywordSound", keywordSound)
        put("theme", theme)
        put("rateLimit", rateLimit)
        put("expiryPresets", JSONArray().apply { expiryPresets.forEach { p -> put(JSONObject().put("name", p.name).put("amount", p.amount).put("unit", p.unit)) } })
        put("expiryFirstDay", expiryFirstDay)
        put("keywordApps", JSONArray(keywordApps.sorted()))
        put("onboarded", onboarded)
    }

    internal fun Preset.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        start?.let { put("start", it) }
        put("items", JSONArray(items))
        if (quiet.isNotEmpty()) put("quiet", JSONArray(quiet.sorted()))
    }

    internal fun JSONObject.toPreset() = Preset(
        id = getLong("id"),
        name = getString("name"),
        start = if (has("start")) getInt("start").coerceIn(0, 24 * 60 - 1) else null,
        items = getJSONArray("items").let { a -> (0 until a.length()).map { a.getString(it) } },
        quiet = optJSONArray("quiet")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
    )

    internal fun JSONObject.toSettings() = Settings().let { d ->
        Settings(
            newOnTop = optBoolean("newOnTop", d.newOnTop),
            lockEnabled = optBoolean("lockEnabled", d.lockEnabled),
            accent = optInt("accent", d.accent),
            textSize = optInt("textSize", d.textSize),
            bold = optBoolean("bold", d.bold),
            checkOnLeft = optBoolean("checkOnLeft", d.checkOnLeft),
            setupDismissed = optBoolean("setupDismissed", d.setupDismissed),
            tileAdded = optBoolean("tileAdded", d.tileAdded),
            calcHours = optBoolean("calcHours", d.calcHours),
            // 1.3.0 까지는 '보통·크게·아주 크게' 순번(cardTextSize)으로 저장했습니다.
            cardSp = (if (has("cardSp")) getInt("cardSp") else listOf(16, 19, 23).getOrElse(optInt("cardTextSize", 1)) { d.cardSp })
                .coerceIn(CardSpRange),
            rowGap = optInt("rowGap", d.rowGap).coerceIn(RowGapRange),
            lockLive = optBoolean("lockLive", d.lockLive),
            lockPage = optBoolean("lockPage", d.lockPage),
            alarmSound = if (has("alarmSound")) getString("alarmSound") else null,
            alarmVolume = optInt("alarmVolume", d.alarmVolume).coerceIn(1, 10),
            alarmMode = optInt("alarmMode", d.alarmMode).coerceIn(0, 2),
            alarmAlwaysSound = optBoolean("alarmAlwaysSound", d.alarmAlwaysSound),
            shortcuts = optJSONArray("shortcuts")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { Shortcut(it.getString("id"), it.optString("name")) }
            } ?: d.shortcuts,
            shortcutStyle = optInt("shortcutStyle", d.shortcutStyle).coerceIn(0, 2),
            addButton = optInt("addButton", d.addButton).coerceIn(0, 1),
            notiApps = optJSONArray("notiApps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: d.notiApps,
            presetMode = optBoolean("presetMode", d.presetMode),
            presets = optJSONArray("presets")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).toPreset() } } ?: d.presets,
            presetApplied = optLong("presetApplied", d.presetApplied),
            presetLoaded = optLong("presetLoaded", d.presetLoaded),
            routineAlarm = optBoolean("routineAlarm", d.routineAlarm),
            routineMuted = optBoolean("routineMuted", d.routineMuted),
            dutyShift = optLong("dutyShift", d.dutyShift),
            // 1.9 까지는 하나만 고름(0 모두, 1 할 일, 2 근무, 3 포켓북)
            pageMask = if (has("pageMask")) optInt("pageMask", 7).coerceIn(1, 7) else listOf(7, 1, 2, 4)[optInt("lockPages", 0).coerceIn(0, 3)],
            keywords = optString("keywords", d.keywords),
            keywordOn = optBoolean("keywordOn", d.keywordOn),
            keywordSound = optBoolean("keywordSound", d.keywordSound),
            theme = optInt("theme", d.theme).coerceIn(0, 2),
            rateLimit = optInt("rateLimit", d.rateLimit).coerceIn(1, 99_999),
            expiryPresets = optJSONArray("expiryPresets")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                    ExpiryPreset(o.optString("name"), o.optInt("amount", 1).coerceIn(1, 999), o.optInt("unit", Expiry.DAYS).coerceIn(0, 3))
                }
            } ?: d.expiryPresets,
            expiryFirstDay = optBoolean("expiryFirstDay", d.expiryFirstDay),
            keywordApps = optJSONArray("keywordApps")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: d.keywordApps,
            // 2.1.0 전 저장 파일엔 없음 = 이미 쓰던 사람이라 안내를 건너뜀
            onboarded = optBoolean("onboarded", true),
        )
    }
}
