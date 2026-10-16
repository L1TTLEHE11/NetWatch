package com.pshenov.netwatch

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
private fun CoroutineScope.post(block: () -> Unit) = launch(Dispatchers.Main) { block() }

/* ============================ сканер сети ============================ */

class ScanCtl(private val scope: CoroutineScope, private val ctx: Context, private val known: () -> Map<String, Pair<String?, String?>>) {
    var running by mutableStateOf(false)
    var done by mutableStateOf(0)
    var total by mutableStateOf(0)
    var hosts by mutableStateOf(listOf<LanHost>())
    var subnet by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var finishedAt by mutableStateOf<Long?>(null)
    private var job: Job? = null

    fun start() {
        if (running) return
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork)
        val la = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        if (la == null) { error = "Нет IPv4-подключения к локальной сети"; return }
        val self = la.address.hostAddress!!
        val (list, _) = Ip.hostsOf(self, la.prefixLength)
        val gw = NetInfo.gateway(ctx)
        error = null; hosts = emptyList(); done = 0; total = list.size; running = true; finishedAt = null
        subnet = "$self/${la.prefixLength}" + if (la.prefixLength < 22) " (сканируется /22 вокруг вашего адреса)" else ""
        val k = known()
        job = scope.launch(Dispatchers.IO) {
            val me = coroutineContext[Job]!!
            LanScanner.scan(list, gw, self, k, { me.isActive },
                onFound = { h -> scope.post { hosts = (hosts.filter { it.ip != h.ip } + h).sortedBy { Ip.sortKey(it.ip) } } },
                onProgress = { n -> if (n % 4 == 0 || n == list.size) scope.post { done = n } })
            onMain { running = false; done = total; finishedAt = System.currentTimeMillis() }
        }
        job?.invokeOnCompletion { scope.post { running = false } }
    }

    fun stop() { job?.cancel(); running = false }

    fun text(): String = buildString {
        append("Подсеть: $subnet · найдено устройств: ${hosts.size}\n")
        for (h in hosts) {
            append("%-15s  %-26s %s\n".format(h.ip, h.kind, listOfNotNull(h.name, h.title).joinToString(" · ")))
            if (h.mac != null || h.ports.isNotEmpty())
                append("                 ${h.mac ?: ""}  порты: ${h.ports.joinToString(", ") { "$it ${PortInfo.label(it)}".trim() }}\n")
        }
    }
}

/* ============================ Wi-Fi эфир ============================ */

class WifiCtl(private val scope: CoroutineScope, private val ctx: Context) {
    var aps by mutableStateOf(listOf<Ap>())
    var lastScan by mutableStateOf<Long?>(null)
    var throttled by mutableStateOf(false)
    var band by mutableStateOf(24)
    @Volatile var active = false

    private val receiver = WifiScan.receiver { reload() }
    init {
        try { androidx.core.content.ContextCompat.registerReceiver(ctx, receiver, WifiScan.filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) } catch (_: Exception) { }
        scope.launch(Dispatchers.IO) {
            while (isActive) { if (active) scan(); delay(30_000) }
        }
    }

    fun reload() { aps = WifiScan.results(ctx); lastScan = System.currentTimeMillis() }

    fun scan() {
        val ok = WifiScan.start(ctx)
        scope.post { throttled = !ok; reload() }
    }

    fun text(): String = buildString {
        append("Сетей в эфире: ${aps.size}\n")
        for (b in listOf(24, 5, 6)) {
            val my = aps.firstOrNull { it.connected && it.band == b }?.channel
            WifiScan.advice(aps, b, my)?.let { append("Диапазон ${bandName(b)}: рекомендуемый канал ${it.best}${it.current?.let { c -> ", ваш $c" } ?: ""} — ${it.note}\n") }
        }
        for (a in aps) append("%4d dBm  кан.%-3d %-6s %3d МГц  %-8s %s%s\n".format(a.dbm, a.channel, bandName(a.band), a.widthMhz, a.security, a.ssid, if (a.connected) "  ← подключены" else ""))
    }

    companion object { fun bandName(b: Int) = when (b) { 24 -> "2.4 ГГц"; 5 -> "5 ГГц"; else -> "6 ГГц" } }
}

/* ============================ обход объекта ============================ */

data class SurveyPoint(
    val time: Long, val name: String, val ssid: String?, val bssid: String?, val dbm: Int?, val freq: Int?, val linkMbps: Int?,
    val gwMs: Double?, val gwLoss: Int, val inetMs: Double?, val inetLoss: Int, val dlMbps: Double?,
) {
    val bad get() = (dbm != null && dbm < -72) || gwLoss > 0 || inetLoss > 20 || (gwMs ?: 0.0) > 30
}

class SurveyCtl(private val scope: CoroutineScope, private val ctx: Context) {
    var points by mutableStateOf(listOf<SurveyPoint>())
    var busy by mutableStateOf(false)
    var stage by mutableStateOf("")
    var withSpeed by mutableStateOf(false)
    private val file = File(ctx.filesDir, "survey.tsv")

    init { load() }

    fun measure(name: String) {
        if (busy) return
        busy = true; stage = "сигнал…"
        scope.launch(Dispatchers.IO) {
            val r = NetInfo.readRadio(ctx)
            val gwHost = NetInfo.gateway(ctx)
            onMain { stage = "пинг роутера…" }
            val g = (1..5).map { Pinger.ping(gwHost) }
            onMain { stage = "пинг интернета…" }
            val i = (1..5).map { Pinger.ping("8.8.8.8") }
            var dl: Double? = null
            if (withSpeed) { onMain { stage = "скорость…" }; dl = Speed.download({ true }, { _, _ -> }, threads = 4, durationMs = 4000) }
            fun avg(l: List<Double?>) = l.filterNotNull().takeIf { it.isNotEmpty() }?.average()
            fun loss(l: List<Double?>) = l.count { it == null } * 100 / l.size
            val p = SurveyPoint(System.currentTimeMillis(), name.ifBlank { "Точка ${points.size + 1}" }, r.ssid, r.bssid, r.dbm, r.freqMhz, r.linkMbps,
                avg(g), loss(g), avg(i), loss(i), dl)
            onMain { points = points + p; busy = false; stage = ""; save() }
        }
    }

    fun remove(p: SurveyPoint) { points = points - p; save() }
    fun clear() { points = emptyList(); save() }

    private fun save() = try {
        file.writeText(points.joinToString("\n") { p ->
            listOf(p.time, p.name.replace('\t', ' '), p.ssid, p.bssid, p.dbm, p.freq, p.linkMbps, p.gwMs, p.gwLoss, p.inetMs, p.inetLoss, p.dlMbps)
                .joinToString("\t") { it?.toString() ?: "" }
        })
    } catch (_: Exception) { }

    private fun load() { try {
        if (file.exists()) points = file.readLines().filter { it.isNotBlank() }.mapNotNull { l ->
            val c = l.split('\t'); if (c.size < 12) null else SurveyPoint(
                c[0].toLong(), c[1], c[2].ifEmpty { null }, c[3].ifEmpty { null }, c[4].toIntOrNull(), c[5].toIntOrNull(), c[6].toIntOrNull(),
                c[7].toDoubleOrNull(), c[8].toIntOrNull() ?: 0, c[9].toDoubleOrNull(), c[10].toIntOrNull() ?: 0, c[11].toDoubleOrNull())
        }
    } catch (_: Exception) { } }

    fun csv(): String {
        val f = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val head = "Время;Точка;Сеть (SSID);Точка доступа (BSSID);Сигнал, dBm;Частота, МГц;Канал;Линк, Мбит/с;Пинг роутера, мс;Потери роутер, %;Пинг интернет, мс;Потери интернет, %;Скачивание, Мбит/с;Оценка"
        return head + "\n" + points.joinToString("\n") { p ->
            listOf(f.format(Date(p.time)), p.name, p.ssid, p.bssid, p.dbm, p.freq, p.freq?.let { NetInfo.channel(it) }, p.linkMbps,
                p.gwMs?.let { "%.1f".format(it) }, p.gwLoss, p.inetMs?.let { "%.1f".format(it) }, p.inetLoss, p.dlMbps?.let { "%.1f".format(it) },
                if (p.bad) "ПРОБЛЕМА" else "норма").joinToString(";") { Report.csvCell(it) }
        }
    }

    fun text(): String = buildString {
        val bad = points.count { it.bad }
        append("Точек замера: ${points.size}, проблемных: $bad\n")
        for (p in points) append("%-22s %5s dBm  роутер %5s мс (%d%%)  интернет %5s мс (%d%%)%s%s  %s\n".format(
            p.name.take(22), p.dbm?.toString() ?: "—", p.gwMs?.let { "%.0f".format(it) } ?: "—", p.gwLoss,
            p.inetMs?.let { "%.0f".format(it) } ?: "—", p.inetLoss, p.dlMbps?.let { "  ↓%.1f".format(it) } ?: "",
            if (p.bad) "  ⚠ ПРОБЛЕМА" else "", p.bssid ?: ""))
    }
}

/* ============================ диагностика ============================ */

class DiagCtl(private val scope: CoroutineScope, private val ctx: Context) {
    var traceHost by mutableStateOf("ya.ru")
    var hops by mutableStateOf(listOf<Hop>())
    var traceRunning by mutableStateOf(false)
    var traceMsg by mutableStateOf<String?>(null)
    private var traceJob: Job? = null

    var dns by mutableStateOf(listOf<DnsResult>())
    var dnsRunning by mutableStateOf(false)

    var portHost by mutableStateOf("")
    var portSpec by mutableStateOf("популярные")
    var openPorts by mutableStateOf(listOf<Pair<Int, Double>>())
    var portDone by mutableStateOf(0)
    var portTotal by mutableStateOf(0)
    var portRunning by mutableStateOf(false)
    var portMsg by mutableStateOf<String?>(null)
    private var portJob: Job? = null

    fun trace() {
        if (traceRunning) { traceJob?.cancel(); traceRunning = false; return }
        hops = emptyList(); traceMsg = null; traceRunning = true
        traceJob = scope.launch(Dispatchers.IO) {
            val me = coroutineContext[Job]!!
            val msg = Traceroute.run(traceHost.trim(), { me.isActive }, { h -> scope.post { hops = hops + h } })
            onMain { traceMsg = msg; traceRunning = false }
        }
    }

    fun dnsBench() {
        if (dnsRunning) return
        dnsRunning = true; dns = emptyList()
        scope.launch(Dispatchers.IO) {
            val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
            val own = cm.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty().filterIsInstance<Inet4Address>()
                .map { it.hostAddress!! to "Провайдер / роутер" }
            val all = (own + DnsBench.publicServers).distinctBy { it.first }
            val res = all.map { (s, l) -> scope.launch(Dispatchers.IO) { val r = DnsBench.bench(s, l) { true }; scope.post { dns = (dns + r).sortedBy { it.avgMs ?: 1e9 } } } }
            res.forEach { it.join() }
            onMain { dnsRunning = false }
        }
    }

    fun ports() {
        if (portRunning) { portJob?.cancel(); portRunning = false; return }
        val host = portHost.trim(); if (host.isEmpty()) { portMsg = "Укажите адрес"; return }
        val list = PortInfo.parse(portSpec)
        openPorts = emptyList(); portDone = 0; portTotal = list.size; portRunning = true; portMsg = null
        portJob = scope.launch(Dispatchers.IO) {
            val me = coroutineContext[Job]!!
            try {
                val n = PortScanner.scan(host, list, { me.isActive },
                    onOpen = { p, ms -> scope.post { openPorts = (openPorts + (p to ms)).sortedBy { it.first } } },
                    onProgress = { d -> if (d % 20 == 0 || d == list.size) scope.post { portDone = d } })
                onMain { portMsg = "Готово: открыто $n из ${list.size}" }
            } catch (e: Exception) { onMain { portMsg = "Ошибка: ${e.message}" } }
            onMain { portRunning = false }
        }
    }

    fun text(): String = buildString {
        if (hops.isNotEmpty()) {
            append("■ Трассировка до $traceHost\n")
            hops.forEach { h -> append("%2d  %-15s %-8s %s\n".format(h.ttl, h.ip ?: "*", h.ms?.let { "%.0f мс".format(it) } ?: "", h.name ?: "")) }
            traceMsg?.let { append("$it\n") }
            append('\n')
        }
        if (dns.isNotEmpty()) {
            append("■ Скорость DNS-серверов\n")
            dns.forEach { d -> append("%-15s %-20s %s  ответов %d/%d\n".format(d.server, d.label, d.avgMs?.let { "%.0f мс".format(it) } ?: "нет ответа", d.ok, d.total)) }
            append('\n')
        }
        if (openPorts.isNotEmpty()) {
            append("■ Открытые порты $portHost\n")
            openPorts.forEach { (p, _) -> append("%5d  %s\n".format(p, PortInfo.label(p))) }
        }
    }
}

/* ============================ Wake-on-LAN ============================ */

data class WolDevice(val name: String, val mac: String)

class WolCtl(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("wol", Context.MODE_PRIVATE)
    var devices by mutableStateOf(load())
    var status by mutableStateOf<String?>(null)

    private fun load() = (prefs.getString("list", "") ?: "").lines().filter { '|' in it }.map { WolDevice(it.substringBefore('|'), it.substringAfter('|')) }
    private fun save() = prefs.edit().putString("list", devices.joinToString("\n") { "${it.name}|${it.mac}" }).apply()

    fun add(name: String, mac: String): Boolean {
        val m = WakeOnLan.normalizeMac(mac) ?: run { status = "Неверный MAC-адрес"; return false }
        devices = devices.filter { it.mac != m } + WolDevice(name.ifBlank { m }, m); save(); return true
    }
    fun remove(d: WolDevice) { devices = devices - d; save() }

    fun wake(mac: String) {
        val m = WakeOnLan.normalizeMac(mac) ?: run { status = "Неверный MAC-адрес"; return }
        Thread {
            val bc = try {
                val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
                cm.getLinkProperties(cm.activeNetwork)?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                    ?.let { Ip.hostsOf(it.address.hostAddress!!, it.prefixLength).second }
            } catch (_: Exception) { null }
            val msg = try { WakeOnLan.send(m, bc); "✓ Магический пакет отправлен на $m (${bc ?: "255.255.255.255"})" } catch (e: Exception) { "Ошибка отправки: ${e.message}" }
            android.os.Handler(android.os.Looper.getMainLooper()).post { status = msg }
        }.start()
    }
}

/* ============================ MikroTik ============================ */

class MtCtl(private val scope: CoroutineScope, ctx: Context) {
    private val prefs = ctx.getSharedPreferences("mikrotik", Context.MODE_PRIVATE)
    var host by mutableStateOf(prefs.getString("host", "") ?: "")
    var port by mutableStateOf(prefs.getString("port", "8728") ?: "8728")
    var user by mutableStateOf(prefs.getString("user", "admin") ?: "admin")
    var pass by mutableStateOf(prefs.getString("pass", "") ?: "")
    var remember by mutableStateOf(prefs.getBoolean("remember", false))
    var tls by mutableStateOf(prefs.getBoolean("tls", false))

    var connected by mutableStateOf(false)
    var connecting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var info by mutableStateOf(MtInfo())
    var ifaces by mutableStateOf(listOf<MtIface>())
    var leases by mutableStateOf(listOf<MtLease>())
    var arp by mutableStateOf(mapOf<String, String>())
    var wifiSrc by mutableStateOf("")
    var wifi by mutableStateOf(listOf<MtWifiClient>())
    var caps by mutableStateOf(listOf<MtCap>())
    @Volatile var active = false
    private var job: Job? = null
    private var api: RouterOs? = null

    /** ip → (mac, имя) для сканера сети. */
    fun known(): Map<String, Pair<String?, String?>> {
        if (!connected) return emptyMap()
        val m = mutableMapOf<String, Pair<String?, String?>>()
        arp.forEach { (ip, mac) -> m[ip] = mac to null }
        leases.forEach { l -> if (l.ip.isNotBlank()) m[l.ip] = (l.mac.ifBlank { m[l.ip]?.first }) to (l.host.ifBlank { l.comment }.ifBlank { null }) }
        return m
    }

    fun connect(defaultHost: String) {
        if (connecting) return
        if (host.isBlank()) host = defaultHost
        prefs.edit().putString("host", host).putString("port", port).putString("user", user).putBoolean("tls", tls)
            .putBoolean("remember", remember).putString("pass", if (remember) pass else "").apply()
        disconnect()
        connecting = true; error = null
        job = scope.launch(Dispatchers.IO) {
            try {
                val a = RouterOs(host.trim(), port.trim().toIntOrNull() ?: if (tls) 8729 else 8728, tls).connect()
                a.login(user.trim(), pass)
                api = a
                onMain { connected = true; connecting = false }
                var tick = 0
                var prev = mapOf<String, Pair<Long, Long>>(); var prevT = 0L
                while (coroutineContext[Job]!!.isActive) {
                    if (active || tick == 0) {
                        val now = System.nanoTime()
                        val rows = MtFetch.ifaces(a)
                        val dt = (now - prevT) / 1e9
                        val list = rows.map { r ->
                            val n = r["name"] ?: ""; val rx = r["rx-byte"]?.toLongOrNull() ?: 0; val tx = r["tx-byte"]?.toLongOrNull() ?: 0
                            val p = prev[n]
                            MtIface(n, r["type"] ?: "", r["running"] == "true", r["disabled"] == "true",
                                if (p != null && dt > 0) maxOf(0.0, (rx - p.first) * 8 / dt) else 0.0,
                                if (p != null && dt > 0) maxOf(0.0, (tx - p.second) * 8 / dt) else 0.0, rx, tx)
                        }
                        prev = list.associate { it.name to (it.rxBytes to it.txBytes) }; prevT = now
                        onMain { ifaces = list }
                        if (tick % 5 == 0) {
                            val i = MtFetch.info(a); val l = MtFetch.leases(a); val ar = MtFetch.arp(a)
                            val (src, w) = MtFetch.wifiClients(a); val c = MtFetch.caps(a)
                            val byMac = l.associateBy { it.mac.uppercase() }
                            val clients = w.map { r ->
                                val mac = (r["mac-address"] ?: "").uppercase()
                                MtWifiClient(mac, r["interface"] ?: "", r["signal"] ?: r["rx-signal"] ?: r["signal-strength"] ?: "",
                                    r["uptime"] ?: "", r["tx-rate"] ?: r["rx-rate"] ?: "", byMac[mac]?.host ?: "", byMac[mac]?.ip ?: "")
                            }.sortedBy { it.signal.substringBefore('@').toIntOrNull()?.let { s -> -s } ?: 999 }
                            onMain { info = i; leases = l; arp = ar; wifiSrc = src; wifi = clients; caps = c }
                        }
                        tick++
                    }
                    delay(2000)
                }
            } catch (e: Exception) {
                onMain { error = humanError(e); connected = false; connecting = false }
            } finally { try { api?.close() } catch (_: Exception) { } }
        }
    }

    private fun humanError(e: Exception): String = when {
        e is java.net.ConnectException -> "Роутер не принимает соединение на порту API. Включите: IP → Services → api (8728)"
        e is java.net.SocketTimeoutException -> "Роутер не отвечает (таймаут). Проверьте адрес и доступ к сервису api"
        e.message?.contains("invalid user", true) == true || e.message?.contains("cannot log in", true) == true -> "Неверный логин или пароль"
        e is javax.net.ssl.SSLException -> "Ошибка TLS. Для api-ssl на роутере нужен сертификат — или используйте порт 8728 без TLS"
        else -> e.message ?: e.toString()
    }

    fun disconnect() {
        job?.cancel(); job = null
        try { api?.close() } catch (_: Exception) { }
        api = null; connected = false
    }

    fun text(): String = if (!connected) "" else buildString {
        append("${info.identity} · ${info.board} · RouterOS ${info.version} · uptime ${info.uptime} · CPU ${info.cpu ?: "?"}% · RAM ${info.memUsedPct ?: "?"}%\n")
        append("Интерфейсы:\n"); ifaces.filter { !it.disabled }.forEach { append("  %-18s %-8s %s ↓%s ↑%s\n".format(it.name, it.type, if (it.running) "UP  " else "DOWN", bps(it.rxBps), bps(it.txBps))) }
        if (caps.isNotEmpty()) { append("Точки CAPsMAN: ${caps.size}\n"); caps.forEach { append("  ${it.identity}  ${it.address}  ${it.board}  ${it.state}\n") } }
        append("Клиенты Wi-Fi ($wifiSrc): ${wifi.size}\n"); wifi.forEach { append("  ${it.mac}  ${it.signal.padEnd(10)} ${it.iface.padEnd(16)} ${it.host} ${it.ip}\n") }
        append("DHCP-аренды: ${leases.size}\n"); leases.forEach { append("  %-15s %s  %s %s\n".format(it.ip, it.mac, it.host, it.comment)) }
    }

    companion object {
        fun bps(v: Double) = when { v >= 1e9 -> "%.2f Гбит/с".format(v / 1e9); v >= 1e6 -> "%.1f Мбит/с".format(v / 1e6); v >= 1e3 -> "%.0f Кбит/с".format(v / 1e3); else -> "%.0f бит/с".format(v) }
    }
}
