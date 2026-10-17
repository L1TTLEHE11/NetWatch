package com.l1ttlehe11.netwatch

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HKind(private val labelRes: Int, val icon: String) {
    SPEED(R.string.speed_2, "⚡"), MONITOR(R.string.monitoring, "🚨"), SCAN(R.string.network_scan, "🔍"),
    TRACE(0, "🛰"), DNS(R.string.dns_test, "🌐"), PORTS(R.string.ports_3, "🚪"), SURVEY(R.string.survey, "🗺");
    val label: String get() = if (labelRes == 0) "Traceroute" else tr(labelRes)
}

/**
 * Запись истории. num1..num3 — ключевые числа для графиков и сортировки
 * (скорость: ↓, ↑, пинг; скан: устройств, новых; мониторинг: потери %, обрывов, секунд без связи).
 */
data class HItem(
    val id: Long, val time: Long, val kind: HKind, val network: String,
    val title: String, val summary: String, val details: String,
    val num1: Double? = null, val num2: Double? = null, val num3: Double? = null,
    val csv: String? = null, val endTime: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("t", time); put("k", kind.name); put("net", network)
        put("title", title); put("sum", summary); put("det", details)
        num1?.let { put("n1", it) }; num2?.let { put("n2", it) }; num3?.let { put("n3", it) }
        csv?.let { put("csv", it) }; endTime?.let { put("end", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = HItem(
            o.getLong("id"), o.getLong("t"), HKind.valueOf(o.getString("k")), o.optString("net"),
            o.optString("title"), o.optString("sum"), o.optString("det"),
            o.optNum("n1"), o.optNum("n2"), o.optNum("n3"),
            if (o.has("csv")) o.getString("csv") else null, if (o.has("end")) o.getLong("end") else null,
        )
        private fun JSONObject.optNum(k: String) = if (has(k)) getDouble(k) else null
    }
}

/** Устройство, когда-либо найденное сканером в этой сети. */
data class KnownDevice(val key: String, val ip: String, val name: String?, val kind: String, val firstSeen: Long, val lastSeen: Long)

/**
 * Хранилище истории: один файл JSON Lines в закрытой папке приложения.
 * Работает и из Activity, и из сервиса мониторинга (общий объект в процессе).
 */
object History {
    private const val MAX_ITEMS = 3000
    private var dir: File? = null
    private val _items = MutableStateFlow<List<HItem>>(emptyList())
    val items: StateFlow<List<HItem>> = _items
    var retentionDays = 90; private set

    private val file get() = File(dir!!, "history.jsonl")
    private val devFile get() = File(dir!!, "devices.json")

    @Synchronized
    fun init(ctx: Context) = init(ctx.filesDir, ctx.getSharedPreferences("history", Context.MODE_PRIVATE).getInt("days", 90))

    @Synchronized
    fun init(base: File, days: Int = 90) {
        if (dir == base) return
        dir = base; retentionDays = days
        _items.value = load()
        prune()
    }

    private fun load(): List<HItem> = try {
        if (!file.exists()) emptyList() else file.readLines().mapNotNull { l ->
            if (l.isBlank()) null else try { HItem.fromJson(JSONObject(l)) } catch (_: Exception) { null }
        }.sortedByDescending { it.time }
    } catch (_: Exception) { emptyList() }

    private fun rewrite(list: List<HItem>) {
        val tmp = File(dir!!, "history.jsonl.tmp")
        tmp.writeText(list.joinToString("\n") { it.toJson().toString() } + if (list.isEmpty()) "" else "\n")
        tmp.renameTo(file)
    }

    /** Добавить запись или заменить существующую с тем же id (для живой сессии мониторинга). */
    @Synchronized
    fun upsert(item: HItem) {
        if (dir == null) return
        val cur = _items.value
        val exists = cur.any { it.id == item.id }
        val next = (listOf(item) + cur.filter { it.id != item.id }).sortedByDescending { it.time }.take(MAX_ITEMS)
        _items.value = next
        try {
            if (exists || next.size < cur.size + 1) rewrite(next) else file.appendText(item.toJson().toString() + "\n")
        } catch (_: Exception) { }
    }

    fun add(item: HItem) = upsert(item)

    @Synchronized
    fun remove(id: Long) { _items.value = _items.value.filter { it.id != id }; try { rewrite(_items.value) } catch (_: Exception) { } }

    @Synchronized
    fun clear(kind: HKind? = null) {
        _items.value = if (kind == null) emptyList() else _items.value.filter { it.kind != kind }
        try { rewrite(_items.value) } catch (_: Exception) { }
        if (kind == null || kind == HKind.SCAN) try { devFile.delete() } catch (_: Exception) { }
    }

    fun setRetention(ctx: Context, days: Int) {
        retentionDays = days
        ctx.getSharedPreferences("history", Context.MODE_PRIVATE).edit().putInt("days", days).apply()
        prune()
    }

    /** Удалить записи старше срока хранения (0 = хранить всегда). */
    @Synchronized
    fun prune(now: Long = System.currentTimeMillis()) {
        if (retentionDays <= 0 || dir == null) return
        val border = now - retentionDays * 86_400_000L
        val keep = _items.value.filter { it.time >= border }
        if (keep.size != _items.value.size) { _items.value = keep; try { rewrite(keep) } catch (_: Exception) { } }
    }

    fun sizeBytes(): Long = try { (dir?.let { File(it, "history.jsonl").length() + File(it, "devices.json").length() }) ?: 0 } catch (_: Exception) { 0 }

    fun nextId(): Long = System.currentTimeMillis() * 10 + (0..9).random()

    /* ---------- известные устройства по сетям ---------- */

    fun deviceKey(h: LanHost): String = h.mac?.uppercase() ?: (h.ip + "|" + (h.name ?: ""))

    @Synchronized
    fun knownDevices(network: String): Map<String, KnownDevice> = try {
        if (dir == null || !devFile.exists()) emptyMap() else {
            val arr = JSONObject(devFile.readText()).optJSONArray(network) ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it) }.associate { o ->
                o.getString("key") to KnownDevice(o.getString("key"), o.optString("ip"), if (o.has("name")) o.getString("name") else null,
                    o.optString("kind"), o.getLong("first"), o.getLong("last"))
            }
        }
    } catch (_: Exception) { emptyMap() }

    /** Запомнить найденные устройства; возвращает ключи тех, кого раньше в этой сети не было. */
    @Synchronized
    fun rememberDevices(network: String, hosts: List<LanHost>, now: Long = System.currentTimeMillis()): Set<String> {
        if (dir == null) return emptySet()
        val old = knownDevices(network)
        val firstScan = old.isEmpty()
        val merged = old.toMutableMap(); val fresh = mutableSetOf<String>()
        for (h in hosts) {
            val k = deviceKey(h); val prev = merged[k]
            if (prev == null && !firstScan) fresh += k
            merged[k] = KnownDevice(k, h.ip, h.name ?: prev?.name, h.kind, prev?.firstSeen ?: now, now)
        }
        try {
            val root = if (devFile.exists()) JSONObject(devFile.readText()) else JSONObject()
            root.put(network, JSONArray().apply {
                merged.values.forEach { d -> put(JSONObject().apply {
                    put("key", d.key); put("ip", d.ip); d.name?.let { put("name", it) }; put("kind", d.kind); put("first", d.firstSeen); put("last", d.lastSeen)
                }) }
            })
            devFile.writeText(root.toString())
        } catch (_: Exception) { }
        return fresh
    }

    /* ---------- форматирование ---------- */

    fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val d = Calendar.getInstance().apply { timeInMillis = t }
        fun same(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        if (same(c, d)) return tr(R.string.today)
        c.add(Calendar.DAY_OF_YEAR, -1)
        if (same(c, d)) return tr(R.string.yesterday)
        return SimpleDateFormat(if (d.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) "d MMMM, EEEE" else "d MMMM yyyy", Lang.locale).format(Date(t))
    }

    fun speedCsv(items: List<HItem>): String {
        val f = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return tr(R.string.date_network_ping_ms_download_mbit) + items.filter { it.kind == HKind.SPEED }.sortedBy { it.time }.joinToString("\n") {
            listOf(f.format(Date(it.time)), it.network, it.num3?.let { v -> "%.0f".format(v) }, it.num1?.let { v -> "%.1f".format(v) }, it.num2?.let { v -> "%.1f".format(v) })
                .joinToString(";") { c -> Report.csvCell(c) }
        }
    }
}
