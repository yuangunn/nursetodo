package com.ttaak.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 기기 내부 JSON 파일 하나에 모든 할 일과 설정을 저장합니다.
 * 네트워크도, 계정도 없습니다. 메인 스레드에서만 호출하세요.
 */
object TaskStore {
    private lateinit var file: File
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    private val _settings = MutableStateFlow(Settings())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    val settings: StateFlow<Settings> = _settings.asStateFlow()

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
        )
    }

    private fun Settings.toJson() = JSONObject().apply {
        put("newOnTop", newOnTop)
        put("lockEnabled", lockEnabled)
        put("lockHideDone", lockHideDone)
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
        put("calcFluid", calcFluid)
        put("cardTextSize", cardTextSize)
        put("lockLive", lockLive)
    }

    private fun JSONObject.toSettings() = Settings().let { d ->
        Settings(
            newOnTop = optBoolean("newOnTop", d.newOnTop),
            lockEnabled = optBoolean("lockEnabled", d.lockEnabled),
            lockHideDone = optBoolean("lockHideDone", d.lockHideDone),
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
            calcFluid = optInt("calcFluid", d.calcFluid),
            cardTextSize = optInt("cardTextSize", d.cardTextSize),
            lockLive = optBoolean("lockLive", d.lockLive),
        )
    }
}
