package com.pshenov.netwatch

import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/* ============================ порты ============================ */

object PortInfo {
    val labels = linkedMapOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS", 80 to "HTTP (веб-интерфейс)",
        110 to "POP3", 123 to "NTP", 135 to "MS RPC", 139 to "NetBIOS", 143 to "IMAP", 161 to "SNMP",
        443 to "HTTPS", 445 to "SMB (общие папки)", 554 to "RTSP (IP-камера)", 631 to "IPP (принтер)",
        993 to "IMAPS", 1723 to "PPTP VPN", 1883 to "MQTT", 1900 to "UPnP", 3306 to "MySQL",
        3389 to "RDP (удалённый рабочий стол)", 5000 to "UPnP / Synology", 5060 to "SIP (IP-телефония)",
        5432 to "PostgreSQL", 5555 to "ADB (Android)", 5900 to "VNC", 8000 to "HTTP (камеры/NVR)",
        8008 to "HTTP (Chromecast)", 8009 to "Chromecast", 8080 to "HTTP-альт / прокси", 8291 to "Winbox (MikroTik)",
        8443 to "HTTPS-альт", 8728 to "MikroTik API", 8729 to "MikroTik API-SSL", 9100 to "Печать RAW (принтер)",
        34567 to "XMeye (камера/DVR)", 37777 to "Dahua (камера/NVR)", 62078 to "iPhone / iPad",
    )
    /** Быстрый набор для сканера сети. */
    val quick = listOf(22, 53, 80, 139, 443, 445, 554, 631, 3389, 5555, 8000, 8008, 8009, 8080, 8291, 8728, 9100, 34567, 37777, 62078)
    val popular: List<Int> get() = labels.keys.sorted()
    fun label(p: Int) = labels[p] ?: ""

    fun guessKind(ports: Collection<Int>, isGateway: Boolean, isSelf: Boolean): String = when {
        isSelf -> "Этот телефон"
        isGateway && (8291 in ports || 8728 in ports) -> "Роутер MikroTik (шлюз)"
        isGateway -> "Роутер / шлюз"
        8291 in ports || 8728 in ports -> "MikroTik"
        554 in ports || 37777 in ports || 34567 in ports -> "IP-камера / регистратор"
        9100 in ports || 631 in ports -> "Принтер"
        8009 in ports || 8008 in ports -> "Chromecast / Smart TV"
        62078 in ports -> "iPhone / iPad"
        5555 in ports -> "Android-устройство"
        3389 in ports || (445 in ports && 139 in ports) -> "Компьютер Windows"
        445 in ports -> "Компьютер / NAS"
        22 in ports -> "Linux / сервер"
        53 in ports -> "DNS / роутер"
        80 in ports || 443 in ports || 8080 in ports -> "Устройство с веб-интерфейсом"
        else -> "Устройство"
    }

    /** "22,80,443", "1-1024", "popular" → список портов. */
    fun parse(spec: String): List<Int> {
        val s = spec.trim().lowercase()
        if (s.isEmpty() || s.startsWith("поп") || s == "popular") return popular
        val out = sortedSetOf<Int>()
        for (part in s.split(',', ' ', ';').filter { it.isNotBlank() }) {
            val r = part.split('-')
            if (r.size == 2) {
                val a = r[0].trim().toIntOrNull() ?: continue; val b = r[1].trim().toIntOrNull() ?: continue
                for (p in minOf(a, b)..maxOf(a, b)) if (p in 1..65535) out += p
            } else part.trim().toIntOrNull()?.let { if (it in 1..65535) out += it }
        }
        return out.toList().take(20000)
    }
}

object PortScanner {
    /** Многопоточный TCP connect-скан. onOpen вызывается по мере нахождения. */
    fun scan(host: String, ports: List<Int>, running: () -> Boolean, onOpen: (Int, Double) -> Unit, onProgress: (Int) -> Unit, timeoutMs: Int = 450): Int {
        val addr = InetAddress.getByName(host)
        val pool = Executors.newFixedThreadPool(96)
        val done = AtomicInteger(0); val open = AtomicInteger(0)
        try {
            ports.forEach { p ->
                pool.execute {
                    if (running()) {
                        val t0 = System.nanoTime()
                        try {
                            Socket().use { it.connect(InetSocketAddress(addr, p), timeoutMs) }
                            open.incrementAndGet(); onOpen(p, (System.nanoTime() - t0) / 1e6)
                        } catch (_: Exception) { }
                    }
                    onProgress(done.incrementAndGet())
                }
            }
            pool.shutdown()
            pool.awaitTermination(10, TimeUnit.MINUTES)
        } finally { pool.shutdownNow() }
        return open.get()
    }
}

/* ============================ сканер локальной сети ============================ */

data class LanHost(
    val ip: String,
    val rtt: Double?,
    val ports: List<Int>,
    val name: String?,
    val title: String?,
    val mac: String?,
    val kind: String,
    val isGateway: Boolean,
    val isSelf: Boolean,
    /** Найден только по TCP-отказу (без ping, портов и имени) — слабое доказательство. */
    val weak: Boolean = false,
)

object Ip {
    fun toInt(a: InetAddress) = a.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xff) }
    fun toInt(s: String) = toInt(InetAddress.getByName(s))
    fun str(i: Int) = "${i ushr 24 and 255}.${i ushr 16 and 255}.${i ushr 8 and 255}.${i and 255}"
    fun sortKey(s: String) = try { toInt(s).toLong() and 0xffffffffL } catch (_: Exception) { 0L }

    /** Хосты подсети. Слишком большие сети ограничиваются /22 вокруг своего адреса (1022 хоста). */
    fun hostsOf(self: String, prefix: Int): Pair<List<String>, String> {
        val ip = toInt(self)
        val eff = prefix.coerceIn(22, 30)
        val mask = -1 shl (32 - eff)
        val net = ip and mask; val bc = net or mask.inv()
        val realMask = if (prefix <= 0) 0 else -1 shl (32 - prefix.coerceAtMost(32))
        val realBc = (ip and realMask) or realMask.inv()
        return (net + 1 until bc).map { str(it) } to str(realBc)
    }
}

object NetBios {
    /** NBSTAT-запрос (UDP 137): имя компьютера и MAC для Windows/Samba-устройств. */
    fun query(ip: String, timeoutMs: Int = 700): Pair<String?, String?>? {
        val q = java.io.ByteArrayOutputStream()
        val id = Random.nextInt(0, 0xffff)
        q.write(id shr 8); q.write(id and 0xff)
        listOf(0, 0, 0, 1, 0, 0, 0, 0, 0, 0).forEach { q.write(it) }
        q.write(0x20)
        val raw = ByteArray(16).also { it[0] = '*'.code.toByte() }
        for (b in raw) { q.write('A'.code + (b.toInt() shr 4 and 0xf)); q.write('A'.code + (b.toInt() and 0xf)) }
        q.write(0); q.write(0); q.write(0x21); q.write(0); q.write(1)
        val pkt = q.toByteArray()
        return try {
            DatagramSocket().use { s ->
                s.soTimeout = timeoutMs
                s.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(ip), 137))
                val buf = ByteArray(1024)
                val r = DatagramPacket(buf, buf.size); s.receive(r)
                parse(buf, r.length)
            }
        } catch (_: Exception) { null }
    }

    fun parse(b: ByteArray, len: Int): Pair<String?, String?>? {
        if (len < 57) return null
        val n = b[56].toInt() and 0xff
        var off = 57
        var name: String? = null
        repeat(n) {
            if (off + 18 > len) return@repeat
            val nm = String(b, off, 15, Charsets.ISO_8859_1).trim()
            val suffix = b[off + 15].toInt() and 0xff
            val flags = ((b[off + 16].toInt() and 0xff) shl 8) or (b[off + 17].toInt() and 0xff)
            if (name == null && suffix == 0 && flags and 0x8000 == 0 && nm.isNotBlank()) name = nm
            off += 18
        }
        val mac = if (off + 6 <= len) (0 until 6).joinToString(":") { "%02X".format(b[off + it]) }.takeIf { it != "00:00:00:00:00:00" } else null
        return name to mac
    }
}

object LanScanner {
    private val aliveProbe = intArrayOf(80, 443, 445, 62078, 554, 22, 8080)

    private fun icmp(ip: String): Double? = try {
        val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", ip).redirectErrorStream(true).start()
        if (!p.waitFor(1600, TimeUnit.MILLISECONDS)) { p.destroy(); null }
        else Regex("""time[=<]\s*([\d.,]+)""").find(p.inputStream.bufferedReader().readText())?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
    } catch (_: Exception) { null }

    /** Живой ли хост: ICMP, а если ping молчит (iPhone, Windows-фаервол) — быстрые TCP-пробы. Второе значение — «только отказ». */
    fun alive(ip: String): Pair<Double, Boolean>? {
        icmp(ip)?.let { return it to false }
        val addr = try { InetAddress.getByName(ip) } catch (_: Exception) { return null }
        var refusedMs: Double? = null
        for (p in aliveProbe) {
            val t0 = System.nanoTime()
            try { Socket().use { it.connect(InetSocketAddress(addr, p), 280) }; return (System.nanoTime() - t0) / 1e6 to false }
            catch (e: ConnectException) { if (isRefused(e) && refusedMs == null) refusedMs = (System.nanoTime() - t0) / 1e6 } // RST = хост, скорее всего, живой
            catch (_: Exception) { }
        }
        return refusedMs?.let { it to true }
    }

    private fun httpTitle(ip: String, port: Int): String? = try {
        val c = (URL("http://$ip:$port/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 700; readTimeout = 900; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NetWatch")
        }
        val server = c.getHeaderField("Server")
        val body = (try { c.inputStream } catch (_: Exception) { c.errorStream })?.use { s ->
            val buf = ByteArray(16384); var n = 0
            while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
            String(buf, 0, n, Charsets.UTF_8)
        } ?: ""
        c.disconnect()
        Regex("<title[^>]*>([^<]{1,80})</title>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim()
            ?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotBlank() } ?: server
    } catch (_: Exception) { null }

    private fun rdns(ip: String): String? = try {
        InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip && !it.matches(Regex("""[\d.]+""")) }
    } catch (_: Exception) { null }

    fun details(ip: String, rtt: Double, gateway: String, self: String, known: Map<String, Pair<String?, String?>>, weakAlive: Boolean = false): LanHost {
        val pool = Executors.newFixedThreadPool(PortInfo.quick.size)
        val open = try {
            pool.invokeAll(PortInfo.quick.map { p ->
                Callable {
                    try { Socket().use { it.connect(InetSocketAddress(ip, p), 350) }; p } catch (_: Exception) { null }
                }
            }, 3, TimeUnit.SECONDS).mapNotNull { f -> try { f.get() } catch (_: Exception) { null } }
        } finally { pool.shutdownNow() }
        val nb = if (139 in open || 445 in open) NetBios.query(ip) else NetBios.query(ip, 300)
        val title = open.firstOrNull { it in listOf(80, 8080, 8000) }?.let { httpTitle(ip, it) }
        val k = known[ip]
        val name = k?.second?.takeIf { it.isNotBlank() } ?: nb?.first ?: rdns(ip)
        return LanHost(
            ip = ip, rtt = rtt, ports = open.sorted(), name = name, title = title,
            mac = k?.first ?: nb?.second, isGateway = ip == gateway, isSelf = ip == self,
            kind = PortInfo.guessKind(open, ip == gateway, ip == self),
            weak = weakAlive && open.isEmpty() && name == null && title == null && k == null && ip != gateway && ip != self,
        )
    }

    /**
     * Сканирует подсеть: 64 потока прозванивают адреса, найденные хосты сразу уходят в onFound
     * (сначала без деталей), затем дополняются портами, именем и заголовком веб-интерфейса.
     * known: ip → (mac, hostname) — например, из DHCP/ARP MikroTik.
     */
    fun scan(
        hosts: List<String>, gateway: String, self: String, known: Map<String, Pair<String?, String?>>,
        running: () -> Boolean, onFound: (LanHost) -> Unit, onProgress: (Int) -> Unit,
    ) {
        val pool = Executors.newFixedThreadPool(64)
        val detailPool = Executors.newFixedThreadPool(12)
        val done = AtomicInteger(0)
        try {
            hosts.forEach { ip ->
                pool.execute {
                    if (running()) {
                        val a = if (ip == self) 0.0 to false else alive(ip)
                        if (a != null) {
                            val (rtt, weakAlive) = a
                            val k = known[ip]
                            onFound(LanHost(ip, rtt, emptyList(), k?.second, null, k?.first, PortInfo.guessKind(emptyList(), ip == gateway, ip == self), ip == gateway, ip == self,
                                weak = weakAlive && k == null && ip != gateway))
                            detailPool.execute { if (running()) try { onFound(details(ip, rtt, gateway, self, known, weakAlive)) } catch (_: Exception) { } }
                        }
                    }
                    onProgress(done.incrementAndGet())
                }
            }
            pool.shutdown(); pool.awaitTermination(10, TimeUnit.MINUTES)
            detailPool.shutdown(); detailPool.awaitTermination(3, TimeUnit.MINUTES)
        } finally { pool.shutdownNow(); detailPool.shutdownNow() }
    }
}

/* ============================ traceroute ============================ */

data class Hop(val ttl: Int, val ip: String?, val name: String?, val ms: Double?, val final: Boolean)

object Traceroute {
    private val fromRe = Regex("""[Ff]rom\s+(?:\S+\s+\()?(\d+\.\d+\.\d+\.\d+)""")
    private val replyRe = Regex("""bytes from\s+(?:\S+\s+\()?(\d+\.\d+\.\d+\.\d+)""")
    private val timeRe = Regex("""time[=<]\s*([\d.,]+)""")

    /** Трассировка через ping с растущим TTL. Для промежуточных узлов время — по часам (≈). */
    fun run(host: String, running: () -> Boolean, onHop: (Hop) -> Unit, maxHops: Int = 30): String? {
        val target = try { InetAddress.getByName(host).hostAddress } catch (e: Exception) { return "Не удалось разрешить имя: ${e.message}" }
        for (ttl in 1..maxHops) {
            if (!running()) return null
            var hop = Hop(ttl, null, null, null, false)
            for (attempt in 0 until 2) {
                val t0 = System.nanoTime()
                val out = try {
                    val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-t", "$ttl", "-W", "1", target!!).redirectErrorStream(true).start()
                    if (!p.waitFor(2500, TimeUnit.MILLISECONDS)) { p.destroy(); "" } else p.inputStream.bufferedReader().readText()
                } catch (e: Exception) { return "Системный ping недоступен на этом устройстве: ${e.message}" }
                val wall = (System.nanoTime() - t0) / 1e6
                val reply = replyRe.find(out)
                if (reply != null) {
                    hop = Hop(ttl, reply.groupValues[1], null, timeRe.find(out)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull(), true); break
                }
                val from = fromRe.find(out)
                if (from != null) { hop = Hop(ttl, from.groupValues[1], null, wall, false); break }
            }
            val named = hop.ip?.let { ip ->
                val n = try { InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip } } catch (_: Exception) { null }
                hop.copy(name = n)
            } ?: hop
            onHop(named)
            if (named.final) return null
        }
        return "Цель не достигнута за $maxHops прыжков"
    }
}

/* ============================ DNS-бенчмарк ============================ */

data class DnsResult(val server: String, val label: String, val avgMs: Double?, val minMs: Double?, val ok: Int, val total: Int)

object DnsBench {
    val publicServers = listOf(
        "8.8.8.8" to "Google", "1.1.1.1" to "Cloudflare", "77.88.8.8" to "Яндекс",
        "9.9.9.9" to "Quad9", "94.140.14.14" to "AdGuard",
    )
    val domains = listOf("yandex.ru", "google.com", "vk.com", "youtube.com", "wikipedia.org", "mail.ru")

    fun buildQuery(id: Int, domain: String): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        o.write(id shr 8 and 0xff); o.write(id and 0xff)
        o.write(0x01); o.write(0x00)              // RD
        o.write(0); o.write(1); repeat(6) { o.write(0) }
        domain.trimEnd('.').split('.').forEach { l -> val b = l.toByteArray(); o.write(b.size); o.write(b) }
        o.write(0); o.write(0); o.write(1); o.write(0); o.write(1) // A, IN
        return o.toByteArray()
    }

    /** Время ответа сервера на один запрос, null — таймаут/ошибка. */
    fun queryOnce(server: String, domain: String, timeoutMs: Int = 1500): Double? = try {
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs
            val id = Random.nextInt(1, 0xffff)
            val q = buildQuery(id, domain)
            val t0 = System.nanoTime()
            s.send(DatagramPacket(q, q.size, InetAddress.getByName(server), 53))
            val buf = ByteArray(1500)
            while (true) {
                val r = DatagramPacket(buf, buf.size); s.receive(r)
                val rid = ((buf[0].toInt() and 0xff) shl 8) or (buf[1].toInt() and 0xff)
                if (rid == id) {
                    val rcode = buf[3].toInt() and 0x0f
                    return@use if (rcode == 0 || rcode == 3) (System.nanoTime() - t0) / 1e6 else null
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    } catch (_: SocketTimeoutException) { null } catch (_: Exception) { null }

    fun bench(server: String, label: String, running: () -> Boolean): DnsResult {
        val times = mutableListOf<Double>()
        var total = 0
        for (d in domains) { if (!running()) break; total++; queryOnce(server, d)?.let { times += it } }
        return DnsResult(server, label, times.takeIf { it.isNotEmpty() }?.average(), times.minOrNull(), times.size, total)
    }
}

/* ============================ Wake-on-LAN ============================ */

object WakeOnLan {
    fun normalizeMac(s: String): String? {
        val hex = s.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length != 12 || !hex.all { it in "0123456789ABCDEF" }) return null
        return hex.chunked(2).joinToString(":")
    }

    fun packet(mac: String): ByteArray {
        val m = normalizeMac(mac)!!.split(':').map { it.toInt(16).toByte() }
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(96) { m[it % 6] }
    }

    /** Шлёт магический пакет на широковещательный адрес подсети и 255.255.255.255 (порты 9 и 7). */
    fun send(mac: String, broadcast: String?) {
        val p = packet(mac)
        DatagramSocket().use { s ->
            s.broadcast = true
            val targets = listOfNotNull(broadcast, "255.255.255.255").distinct()
            for (t in targets) for (port in intArrayOf(9, 7)) {
                s.send(DatagramPacket(p, p.size, InetAddress.getByName(t), port))
            }
        }
    }
}

fun isIpv4(a: InetAddress) = a is Inet4Address

/**
 * Настоящий отказ в соединении (TCP RST) — значит, устройство существует.
 * На Android под ConnectException попадают и EHOSTUNREACH / ENETUNREACH («нет такого узла»),
 * поэтому смотрим именно на код ошибки.
 */
fun isRefused(e: Exception): Boolean {
    val m = (e.message ?: "") + " " + (e.cause?.message ?: "")
    if (m.contains("UNREACH", true) || m.contains("No route", true) || m.contains("unreachable", true)) return false
    return m.contains("ECONNREFUSED") || m.contains("refused", true)
}
