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
    var dropped by mutableStateOf(0)
    /** Сколько устройств ответили на запросы обнаружения (mDNS/UPnP). */
    var discovered by mutableStateOf(0)
    var stage by mutableStateOf("")
    /** Ключи устройств, которых раньше в этой сети не было. */
    var newKeys by mutableStateOf(setOf<String>())
    /** Известные устройства, не найденные в этот раз. */
    var missing by mutableStateOf(listOf<KnownDevice>())
    var network by mutableStateOf("")
    /** Показан сохранённый результат, а не свежий скан. */
    var restored by mutableStateOf(false)
    private var job: Job? = null

    init { restoreLast() }

    /** Подгрузить последний скан этой сети из истории — чтобы список был виден сразу после запуска. */
    fun restoreLast() {
        val net = try { NetInfo.networkLabel(ctx) } catch (_: Exception) { return }
        val last = History.items.value.firstOrNull { it.kind == HKind.SCAN && it.network == net && it.csv != null } ?: return
        try {
            hosts = hostsFromJson(last.csv!!); network = net; finishedAt = last.time; restored = true
            subnet = last.summary.substringBefore(" · ")
        } catch (_: Exception) { }
    }

    fun start() {
        if (running) return
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork)
        val la = lp?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        if (la == null) { error = tr(R.string.no_ipv4_connection_to_a_local); return }
        val self = la.address.hostAddress!!
        val (list, _) = Ip.hostsOf(self, la.prefixLength)
        val gw = NetInfo.gateway(ctx)
        error = null; hosts = emptyList(); done = 0; total = list.size; running = true; finishedAt = null
        restored = false; newKeys = emptySet(); missing = emptyList(); network = NetInfo.networkLabel(ctx)
        subnet = "${self}/${la.prefixLength}" + if (la.prefixLength < 22) tr(R.string.scanning_the_22_around_your_address) else ""
        val k = known()
        stage = tr(R.string.asking_devices_mdns_upnp)
        job = scope.launch(Dispatchers.IO) {
            val me = coroutineContext[Job]!!
            // Сначала спрашиваем сеть «кто здесь?»: телевизоры, Chromecast, принтеры отвечают сами,
            // даже если молчат на ping и закрывают порты.
            val disc = try { Discovery.all(2500).filterKeys { it in list || it == self } } catch (_: Exception) { emptyMap() }
            onMain { discovered = disc.size; stage = tr(R.string.checking_addresses) }
            LanScanner.scan(list, gw, self, k, { me.isActive },
                onFound = { h -> scope.post { hosts = (hosts.filter { it.ip != h.ip } + h).sortedBy { Ip.sortKey(it.ip) } } },
                onProgress = { n -> if (n % 4 == 0 || n == list.size) scope.post { done = n } },
                discovered = disc)
            onMain {
                // Защита от роутеров, отвечающих отказом за любой адрес: если «слабых» находок
                // подозрительно много, это не устройства, а сам роутер — убираем их.
                val weak = hosts.count { it.weak }
                if (weak >= 8 && weak * 2 > hosts.size) { hosts = hosts.filter { !it.weak }; dropped = weak } else dropped = 0
                running = false; done = total; finishedAt = System.currentTimeMillis(); stage = ""
                save()
            }
        }
        job?.invokeOnCompletion { scope.post { running = false } }
    }

    fun stop() { job?.cancel(); running = false }

    private fun save() {
        val net = network
        val before = History.knownDevices(net)
        val fresh = History.rememberDevices(net, hosts)
        val seen = hosts.map { History.deviceKey(it) }.toSet()
        newKeys = fresh
        missing = before.values.filter { it.key !in seen }.sortedByDescending { it.lastSeen }
        History.add(HItem(History.nextId(), System.currentTimeMillis(), HKind.SCAN, net,
            tr(R.string.devices, hosts.size) + if (fresh.isNotEmpty()) tr(R.string.new_2, fresh.size) else "",
            "${subnet} · " + hosts.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }.take(4).joinToString(", ") { "${it.key}: ${it.value}" },
            text(), num1 = hosts.size.toDouble(), num2 = fresh.size.toDouble(), csv = hostsToJson(hosts)))
    }

    fun isNew(h: LanHost) = History.deviceKey(h) in newKeys

    fun text(): String = buildString {
        append(tr(R.string.subnet_devices_found, subnet, hosts.size))
        for (h in hosts) {
            append("%-15s  %-26s %s%s\n".format(h.ip, h.kind, listOfNotNull(h.name, h.title).joinToString(" · "), if (isNew(h)) tr(R.string.new_label) else ""))
            if (h.mac != null || h.ports.isNotEmpty())
                append(tr(R.string.ports, h.mac ?: "", h.ports.joinToString(", ") { "${it} ${PortInfo.label(it)}".trim() }))
        }
    }
}

fun hostsToJson(hosts: List<LanHost>): String = org.json.JSONArray().apply {
    hosts.forEach { h -> put(org.json.JSONObject().apply {
        put("ip", h.ip); h.rtt?.let { put("rtt", it) }; put("ports", org.json.JSONArray(h.ports)); h.name?.let { put("name", it) }
        h.title?.let { put("title", it) }; h.mac?.let { put("mac", it) }; put("kind", h.kind); put("gw", h.isGateway); put("self", h.isSelf)
    }) }
}.toString()

fun hostsFromJson(s: String): List<LanHost> {
    val a = org.json.JSONArray(s)
    return (0 until a.length()).map { i ->
        val o = a.getJSONObject(i); val ps = o.getJSONArray("ports")
        LanHost(o.getString("ip"), if (o.has("rtt")) o.getDouble("rtt") else null, (0 until ps.length()).map { ps.getInt(it) },
            if (o.has("name")) o.getString("name") else null, if (o.has("title")) o.getString("title") else null,
            if (o.has("mac")) o.getString("mac") else null, o.getString("kind"), o.getBoolean("gw"), o.getBoolean("self"))
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
        append(tr(R.string.networks_on_air, aps.size))
        for (b in listOf(24, 5, 6)) {
            val my = aps.firstOrNull { it.connected && it.band == b }?.channel
            WifiScan.advice(aps, b, my)?.let { append(tr(R.string.band_recommended_channel, bandName(b), it.best, it.current?.let { c -> tr(R.string.yours, c) } ?: "", it.note)) }
        }
        for (a in aps) append(tr(R.string.s_4d_dbm_ch_3d_6s_3d).format(a.dbm, a.channel, bandName(a.band), a.widthMhz, a.security, a.ssid, if (a.connected) tr(R.string.connected) else ""))
    }

    companion object { fun bandName(b: Int) = when (b) { 24 -> tr(R.string.s_2_4_ghz); 5 -> tr(R.string.s_5_ghz); else -> tr(R.string.s_6_ghz) } }
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
        busy = true; stage = tr(R.string.signal_2)
        scope.launch(Dispatchers.IO) {
            val r = NetInfo.readRadio(ctx)
            val gwHost = NetInfo.gateway(ctx)
            onMain { stage = tr(R.string.pinging_the_router) }
            val g = (1..5).map { Pinger.ping(gwHost) }
            onMain { stage = tr(R.string.pinging_the_internet) }
            val i = (1..5).map { Pinger.ping("8.8.8.8") }
            var dl: Double? = null
            if (withSpeed) { onMain { stage = tr(R.string.speed_3) }; dl = Speed.download({ true }, { _, _ -> }, threads = 4, durationMs = 4000) }
            fun avg(l: List<Double?>) = l.filterNotNull().takeIf { it.isNotEmpty() }?.average()
            fun loss(l: List<Double?>) = l.count { it == null } * 100 / l.size
            val p = SurveyPoint(System.currentTimeMillis(), name.ifBlank { tr(R.string.point, points.size + 1) }, r.ssid, r.bssid, r.dbm, r.freqMhz, r.linkMbps,
                avg(g), loss(g), avg(i), loss(i), dl)
            onMain { points = points + p; busy = false; stage = ""; save() }
        }
    }

    fun remove(p: SurveyPoint) { points = points - p; save() }

    /** Сохранить обход в историю под названием объекта и начать новый. */
    fun archive(objectName: String) {
        if (points.isEmpty()) return
        val bad = points.count { it.bad }
        History.add(HItem(History.nextId(), points.first().time, HKind.SURVEY, points.firstNotNullOfOrNull { it.ssid } ?: "—",
            objectName.ifBlank { tr(R.string.survey_2, SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(points.first().time))) },
            tr(R.string.points_problems_access_points, points.size, bad, points.mapNotNull { it.bssid }.distinct().size),
            text(), num1 = points.size.toDouble(), num2 = bad.toDouble(), csv = csv(), endTime = points.last().time))
        clear()
    }
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
        val head = tr(R.string.time_point_network_ssid_access_point)
        return head + "\n" + points.joinToString("\n") { p ->
            listOf(f.format(Date(p.time)), p.name, p.ssid, p.bssid, p.dbm, p.freq, p.freq?.let { NetInfo.channel(it) }, p.linkMbps,
                p.gwMs?.let { "%.1f".format(it) }, p.gwLoss, p.inetMs?.let { "%.1f".format(it) }, p.inetLoss, p.dlMbps?.let { "%.1f".format(it) },
                if (p.bad) tr(R.string.problem_2) else tr(R.string.ok)).joinToString(";") { Report.csvCell(it) }
        }
    }

    fun text(): String = buildString {
        val bad = points.count { it.bad }
        append(tr(R.string.survey_points_with_problems, points.size, bad))
        for (p in points) append(tr(R.string.s_22s_5s_dbm_router_5s_ms).format(
            p.name.take(22), p.dbm?.toString() ?: "—", p.gwMs?.let { "%.0f".format(it) } ?: "—", p.gwLoss,
            p.inetMs?.let { "%.0f".format(it) } ?: "—", p.inetLoss, p.dlMbps?.let { "  ↓%.1f".format(it) } ?: "",
            if (p.bad) tr(R.string.problem) else "", p.bssid ?: ""))
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
    var portSpec by mutableStateOf(tr(R.string.popular))
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
            onMain {
                traceMsg = msg; traceRunning = false
                if (hops.isNotEmpty()) History.add(HItem(History.nextId(), System.currentTimeMillis(), HKind.TRACE, NetInfo.networkLabel(ctx),
                    tr(R.string.to_hops, traceHost, hops.size), if (hops.lastOrNull()?.final == true) tr(R.string.target_reached) + (hops.last().ms?.let { tr(R.string.s_0f_ms_3).format(it) } ?: "") else (msg ?: tr(R.string.interrupted_2)),
                    text().substringAfter(tr(R.string.traceroute)).let { tr(R.string.traceroute_2, it) }.substringBefore(tr(R.string.dns_speed)), num1 = hops.size.toDouble(), num3 = hops.lastOrNull()?.ms))
            }
        }
    }

    fun dnsBench() {
        if (dnsRunning) return
        dnsRunning = true; dns = emptyList()
        scope.launch(Dispatchers.IO) {
            val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
            val own = cm.getLinkProperties(cm.activeNetwork)?.dnsServers.orEmpty().filterIsInstance<Inet4Address>()
                .map { it.hostAddress!! to tr(R.string.provider_router) }
            val all = (own + DnsBench.publicServers).distinctBy { it.first }
            val res = all.map { (s, l) -> scope.launch(Dispatchers.IO) { val r = DnsBench.bench(s, l) { true }; scope.post { dns = (dns + r).sortedBy { it.avgMs ?: 1e9 } } } }
            res.forEach { it.join() }
            onMain {
                dnsRunning = false
                val best = dns.firstOrNull { it.avgMs != null }
                if (dns.isNotEmpty()) History.add(HItem(History.nextId(), System.currentTimeMillis(), HKind.DNS, NetInfo.networkLabel(ctx),
                    tr(R.string.best, best?.label ?: "—") + (best?.avgMs?.let { tr(R.string.s_0f_ms).format(it) } ?: ""),
                    dns.take(4).joinToString(" · ") { "${it.label.substringBefore(' ')} ${it.avgMs?.let { v -> "%.0f".format(v) } ?: "✖"}" },
                    dns.joinToString("\n") { d -> tr(R.string.s_15s_20s_s_replies_d_d).format(d.server, d.label, d.avgMs?.let { tr(R.string.s_0f_ms_2).format(it) } ?: tr(R.string.no_reply), d.ok, d.total) },
                    num3 = best?.avgMs))
            }
        }
    }

    fun ports() {
        if (portRunning) { portJob?.cancel(); portRunning = false; return }
        val host = portHost.trim(); if (host.isEmpty()) { portMsg = tr(R.string.enter_an_address); return }
        val list = PortInfo.parse(portSpec)
        openPorts = emptyList(); portDone = 0; portTotal = list.size; portRunning = true; portMsg = null
        portJob = scope.launch(Dispatchers.IO) {
            val me = coroutineContext[Job]!!
            try {
                val n = PortScanner.scan(host, list, { me.isActive },
                    onOpen = { p, ms -> scope.post { openPorts = (openPorts + (p to ms)).sortedBy { it.first } } },
                    onProgress = { d -> if (d % 20 == 0 || d == list.size) scope.post { portDone = d } })
                onMain {
                    portMsg = tr(R.string.done_of_open, n, list.size)
                    History.add(HItem(History.nextId(), System.currentTimeMillis(), HKind.PORTS, NetInfo.networkLabel(ctx),
                        tr(R.string.open, host, n), tr(R.string.checked_ports, list.size, portSpec.trim()),
                        if (openPorts.isEmpty()) tr(R.string.no_open_ports_found) else openPorts.joinToString("\n") { (p, _) -> "%5d  %s".format(p, PortInfo.label(p)) },
                        num1 = n.toDouble()))
                }
            } catch (e: Exception) { onMain { portMsg = tr(R.string.error_3, e.message) } }
            onMain { portRunning = false }
        }
    }

    fun text(): String = buildString {
        if (hops.isNotEmpty()) {
            append(tr(R.string.traceroute_to, traceHost))
            hops.forEach { h -> append("%2d  %-15s %-8s %s\n".format(h.ttl, h.ip ?: "*", h.ms?.let { tr(R.string.s_0f_ms_2).format(it) } ?: "", h.name ?: "")) }
            traceMsg?.let { append("${it}\n") }
            append('\n')
        }
        if (dns.isNotEmpty()) {
            append(tr(R.string.dns_server_speed_2))
            dns.forEach { d -> append(tr(R.string.s_15s_20s_s_replies_d_d_2).format(d.server, d.label, d.avgMs?.let { tr(R.string.s_0f_ms_2).format(it) } ?: tr(R.string.no_reply), d.ok, d.total)) }
            append('\n')
        }
        if (openPorts.isNotEmpty()) {
            append(tr(R.string.open_ports, portHost))
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
        val m = WakeOnLan.normalizeMac(mac) ?: run { status = tr(R.string.invalid_mac_address); return false }
        devices = devices.filter { it.mac != m } + WolDevice(name.ifBlank { m }, m); save(); return true
    }
    fun remove(d: WolDevice) { devices = devices - d; save() }

    fun wake(mac: String) {
        val m = WakeOnLan.normalizeMac(mac) ?: run { status = tr(R.string.invalid_mac_address); return }
        Thread {
            val bc = try {
                val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
                cm.getLinkProperties(cm.activeNetwork)?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                    ?.let { Ip.hostsOf(it.address.hostAddress!!, it.prefixLength).second }
            } catch (_: Exception) { null }
            val msg = try { WakeOnLan.send(m, bc); tr(R.string.magic_packet_sent_to, m, bc ?: "255.255.255.255") } catch (e: Exception) { tr(R.string.send_error, e.message) }
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
        e is java.net.ConnectException && !isRefused(e) -> tr(R.string.address_unreachable_check_the_router_ip)
        e is java.net.ConnectException -> tr(R.string.the_router_refuses_connections_on_the)
        e is java.net.SocketTimeoutException -> tr(R.string.the_router_does_not_respond_timeout)
        e.message?.contains("invalid user", true) == true || e.message?.contains("cannot log in", true) == true -> tr(R.string.wrong_login_or_password)
        e is javax.net.ssl.SSLException -> tr(R.string.tls_error_api_ssl_needs_a)
        else -> e.message ?: e.toString()
    }

    fun disconnect() {
        job?.cancel(); job = null
        try { api?.close() } catch (_: Exception) { }
        api = null; connected = false
    }

    fun text(): String = if (!connected) "" else buildString {
        append("${info.identity} · ${info.board} · RouterOS ${info.version} · uptime ${info.uptime} · CPU ${info.cpu ?: "?"}% · RAM ${info.memUsedPct ?: "?"}%\n")
        append(tr(R.string.interfaces)); ifaces.filter { !it.disabled }.forEach { append("  %-18s %-8s %s ↓%s ↑%s\n".format(it.name, it.type, if (it.running) "UP  " else "DOWN", bps(it.rxBps), bps(it.txBps))) }
        if (caps.isNotEmpty()) { append(tr(R.string.capsman_access_points_2, caps.size)); caps.forEach { append("  ${it.identity}  ${it.address}  ${it.board}  ${it.state}\n") } }
        append(tr(R.string.wi_fi_clients_3, wifiSrc, wifi.size)); wifi.forEach { append("  ${it.mac}  ${it.signal.padEnd(10)} ${it.iface.padEnd(16)} ${it.host} ${it.ip}\n") }
        append(tr(R.string.dhcp_leases_2, leases.size)); leases.forEach { append("  %-15s %s  %s %s\n".format(it.ip, it.mac, it.host, it.comment)) }
    }

    companion object {
        fun bps(v: Double) = when { v >= 1e9 -> tr(R.string.s_2f_gbit_s).format(v / 1e9); v >= 1e6 -> tr(R.string.s_1f_mbit_s).format(v / 1e6); v >= 1e3 -> tr(R.string.s_0f_kbit_s).format(v / 1e3); else -> tr(R.string.s_0f_bit_s).format(v) }
    }
}

/* ============================ обновления ============================ */

enum class UpdPhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

class UpdateCtl(private val scope: CoroutineScope, private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("update", Context.MODE_PRIVATE)
    var phase by mutableStateOf(UpdPhase.IDLE)
    var info by mutableStateOf<UpdateInfo?>(null)
    var progress by mutableStateOf(0f)
    var error by mutableStateOf<String?>(null)
    var lastCheck by mutableStateOf(prefs.getLong("last", 0L))
    var auto by mutableStateOf(prefs.getBoolean("auto", true))
    /** Пользователь закрыл баннер этой версии на Главной. */
    var dismissed by mutableStateOf(prefs.getLong("dismissed", 0L))
    private var apk: File? = null
    private var job: Job? = null
    val current: Long = try { Updater.currentVersionCode(ctx) } catch (_: Exception) { 0 }
    val currentName: String = BuildConfigCompat.version(ctx)

    val hasUpdate get() = info?.let { it.versionCode > current } == true
    val showBanner get() = hasUpdate && info?.versionCode != dismissed

    /** Автопроверка при запуске — не чаще раза в 6 часов. */
    fun autoCheck() { if (auto && System.currentTimeMillis() - lastCheck > 6 * 3600_000L) check(silent = true) }

    fun check(silent: Boolean = false) {
        if (phase == UpdPhase.CHECKING || phase == UpdPhase.DOWNLOADING) return
        if (!silent) { phase = UpdPhase.CHECKING; error = null }
        scope.launch(Dispatchers.IO) {
            try {
                val i = Updater.fetch()
                onMain {
                    info = i; lastCheck = System.currentTimeMillis(); prefs.edit().putLong("last", lastCheck).apply()
                    phase = if (i.versionCode > current) UpdPhase.AVAILABLE else UpdPhase.UP_TO_DATE
                }
            } catch (e: Exception) {
                onMain { if (!silent) { phase = UpdPhase.ERROR; error = e.message } }
            }
        }
    }

    fun download() {
        val i = info ?: return
        if (phase == UpdPhase.DOWNLOADING) return
        phase = UpdPhase.DOWNLOADING; progress = 0f; error = null
        job = scope.launch(Dispatchers.IO) {
            val me = coroutineContext[Job]!!
            try {
                val dir = File(ctx.cacheDir, "updates")
                val f = try {
                    Updater.download(i, dir, { p -> scope.launch(Dispatchers.Main) { progress = p } }, { me.isActive })
                } catch (e: UpdateException) {
                    if (!me.isActive) throw e
                    // Описание могло устареть (вышла ещё более новая версия, а старый файл убран) —
                    // перечитываем update.json и пробуем ещё раз с актуальными данными.
                    val fresh = Updater.fetch()
                    if (fresh.versionCode <= current || (fresh.versionCode == i.versionCode && fresh.sha256 == i.sha256)) throw e
                    onMain { info = fresh; progress = 0f }
                    Updater.download(fresh, dir, { p -> scope.launch(Dispatchers.Main) { progress = p } }, { me.isActive })
                }
                onMain { apk = f; phase = UpdPhase.READY; install() }
            } catch (e: Exception) {
                onMain { phase = if (me.isActive) UpdPhase.ERROR else UpdPhase.AVAILABLE; error = if (me.isActive) e.message else null }
            }
        }
    }

    fun cancel() { job?.cancel(); phase = UpdPhase.AVAILABLE }

    /** Нужен ли сначала доступ «Установка неизвестных приложений». */
    val needsPermission get() = !Updater.canInstall(ctx)

    fun install() {
        val f = apk ?: return
        if (!Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx); return }
        try { Updater.install(ctx, f) } catch (e: Exception) { phase = UpdPhase.ERROR; error = tr(R.string.could_not_open_the_installer, e.message) }
    }

    fun setAutoCheck(v: Boolean) { auto = v; prefs.edit().putBoolean("auto", v).apply() }
    fun dismiss() { info?.let { dismissed = it.versionCode; prefs.edit().putLong("dismissed", it.versionCode).apply() } }
}
