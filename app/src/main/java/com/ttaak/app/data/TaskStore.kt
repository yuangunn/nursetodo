package com.ttaak.app.data

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

    /** 맞춰 둔 'N분 뒤' 알람 (울릴 시각 순). 울리게 맞추는 건 [com.ttaak.app.lock.Alarms] 가 합니다. */
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    /** 데이터가 바뀔 때마다 호출 — 잠금화면 카드와 위젯을 갱신합니다. */
    var onChanged: (() -> Unit)? = null

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "ttaak.json")
        load()
    }

    fun tasksFor(day: Long, today: Long = Day.today()): List<Task> =
        _tasks.value
            .filter { it.visibleOn(day, today) }
            .sortedWith(compareBy<Task>({ it.isDone(day) }, { it.order }))

    fun get(id: Long): Task? = _tasks.value.firstOrNull { it.id == id }

    fun add(title: String, day: Long = Day.today()): Task? {
        val text = title.trim()
        if (text.isEmpty()) return null
        val list = _tasks.value
        val order = when {
            list.isEmpty() -> 0L
            _settings.value.newOnTop -> list.minOf { it.order } - 1
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
     * 교대 프리셋 불러오기: 프리셋에서 넣었다가 못 끝낸 할 일은 지우고 [preset] 항목을 맨 위에 새로 넣습니다.
     * 이번 교대([shift])에 이미 끝낸 항목은 다시 넣지 않습니다. 직접 추가한 할 일은 그대로 둡니다.
     */
    fun loadPreset(preset: Preset, shift: Long) {
        val kept = _tasks.value.filterNot { it.shift != null && it.doneOn == null }
        val done = kept.filter { it.shift == shift }.map { it.title }.toSet()
        val titles = preset.items.map { it.trim() }.filter { it.isNotEmpty() && it !in done }
        val top = (kept.minOfOrNull { it.order } ?: 0) - titles.size
        val firstId = System.currentTimeMillis().coerceAtLeast((_tasks.value.maxOfOrNull { it.id } ?: 0) + 1)
        val today = Day.today()
        val added = titles.mapIndexed { i, t -> Task(id = firstId + i, title = t, createdDay = today, order = top + i, shift = shift) }
        _settings.value = _settings.value.copy(presetApplied = shift, presetLoaded = preset.id)
        commit(kept + added)
    }

    /** 교대 프리셋을 켜 두었고 교대가 바뀌었으면 새 교대의 프리셋을 불러옵니다. 화면이 켜질 때·앱을 열 때·교대 시각 알람에서 부릅니다. */
    fun refreshPreset(now: LocalDateTime = LocalDateTime.now()) {
        val s = _settings.value
        if (!s.presetMode) return
        val (preset, shift) = s.presets.currentShift(now) ?: return
        if (shift != s.presetApplied) loadPreset(preset, shift)
    }

    /** 켜면 지금 교대의 프리셋을 바로 불러오고, 끄면 프리셋에서 넣었다가 못 끝낸 할 일을 치웁니다. */
    fun setPresetMode(on: Boolean) {
        updateSettings { it.copy(presetMode = on, presetApplied = 0) }
        if (on) refreshPreset() else commit(_tasks.value.filterNot { it.shift != null && it.doneOn == null })
    }

    /** 프리셋 목록 저장. 시각을 바꿔 교대가 달라졌거나 지금 불러와 둔 프리셋([changed])을 고쳤으면 바로 다시 불러옵니다. */
    fun savePresets(list: List<Preset>, changed: Long? = null, now: LocalDateTime = LocalDateTime.now()) {
        updateSettings { it.copy(presets = list) }
        val s = _settings.value
        if (!s.presetMode) return
        val (scheduled, shift) = list.currentShift(now) ?: return
        when {
            shift != s.presetApplied -> loadPreset(scheduled, shift)
            changed == s.presetLoaded -> list.firstOrNull { it.id == changed }?.let { loadPreset(it, shift) }
        }
    }

    fun addAlarm(minutes: Int, label: String): Alarm {
        val now = System.currentTimeMillis().coerceAtLeast((_alarms.value.maxOfOrNull { it.id } ?: 0) + 1)
        val alarm = Alarm(id = now, at = now + minutes * 60_000L, label = label.trim())
        _alarms.value = (_alarms.value + alarm).sortedBy { it.at }
        save()
        return alarm
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
        }.onFailure {
            // 읽지 못한 파일을 다음 저장 때 덮어쓰지 않도록 옆에 따로 남겨 둡니다.
            file.renameTo(File(file.parentFile, "ttaak.broken-${System.currentTimeMillis()}.json"))
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
        put("lockText", lockText)
        put("ddayLabel", ddayLabel)
        ddayDate?.let { put("ddayDate", it) }
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
    }

    internal fun Preset.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        start?.let { put("start", it) }
        put("items", JSONArray(items))
    }

    internal fun JSONObject.toPreset() = Preset(
        id = getLong("id"),
        name = getString("name"),
        start = if (has("start")) getInt("start").coerceIn(0, 24 * 60 - 1) else null,
        items = getJSONArray("items").let { a -> (0 until a.length()).map { a.getString(it) } },
    )

    internal fun JSONObject.toSettings() = Settings().let { d ->
        Settings(
            newOnTop = optBoolean("newOnTop", d.newOnTop),
            lockEnabled = optBoolean("lockEnabled", d.lockEnabled),
            lockText = optString("lockText", d.lockText),
            ddayLabel = optString("ddayLabel", d.ddayLabel),
            ddayDate = if (has("ddayDate")) getLong("ddayDate") else null,
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
        )
    }
}
