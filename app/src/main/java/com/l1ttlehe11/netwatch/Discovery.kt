package com.l1ttlehe11.netwatch

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Устройство, которое само назвалось в ответ на запрос обнаружения. */
data class Discovered(val ip: String, val name: String?, val kind: String?, val model: String?, val via: String)

/**
 * Обнаружение устройств, которые не отвечают на ping и закрывают обычные порты:
 * Android TV, Chromecast, Smart TV, принтеры, NAS, AirPlay — они сами отвечают на
 * mDNS (Bonjour) и SSDP (UPnP) и сообщают своё имя и модель.
 */
object Discovery {

    /* ---------------- mDNS ---------------- */

    val mdnsServices get() = linkedMapOf(
        "_androidtvremote2._tcp.local" to "Android TV",
        "_googlecast._tcp.local" to "Chromecast / Smart TV",
        "_airplay._tcp.local" to "Apple TV / AirPlay",
        "_raop._tcp.local" to tr(R.string.airplay_speaker),
        "_ipp._tcp.local" to tr(R.string.printer),
        "_printer._tcp.local" to tr(R.string.printer),
        "_pdl-datastream._tcp.local" to tr(R.string.printer),
        "_smb._tcp.local" to tr(R.string.computer_nas),
        "_afpovertcp._tcp.local" to "Mac / NAS",
        "_hap._tcp.local" to tr(R.string.smart_home_homekit),
        "_spotify-connect._tcp.local" to tr(R.string.spotify_speaker),
        "_workstation._tcp.local" to tr(R.string.computer),
        "_http._tcp.local" to null,
    )

    fun mdnsQuery(id: Int, names: Collection<String>): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        fun u16(v: Int) { o.write(v shr 8 and 0xff); o.write(v and 0xff) }
        u16(id); u16(0); u16(names.size); u16(0); u16(0); u16(0)
        for (n in names) {
            n.trimEnd('.').split('.').forEach { l -> val b = l.toByteArray(); o.write(b.size); o.write(b) }
            o.write(0); u16(12)      // PTR
            u16(0x8001)              // IN + «ответьте мне напрямую» (QU)
        }
        return o.toByteArray()
    }

    /** Имя DNS с поддержкой сжатия (указателей). Возвращает имя и позицию после него. */
    fun readName(b: ByteArray, start: Int, len: Int): Pair<String, Int> {
        val parts = mutableListOf<String>()
        var pos = start; var end = -1; var jumps = 0
        while (pos < len) {
            val l = b[pos].toInt() and 0xff
            when {
                l == 0 -> { pos++; break }
                l and 0xC0 == 0xC0 -> {
                    if (pos + 1 >= len || ++jumps > 20) break
                    if (end < 0) end = pos + 2
                    pos = ((l and 0x3F) shl 8) or (b[pos + 1].toInt() and 0xff)
                }
                else -> {
                    if (pos + 1 + l > len) break
                    parts += String(b, pos + 1, l, Charsets.UTF_8); pos += 1 + l
                }
            }
        }
        return parts.joinToString(".") to (if (end >= 0) end else pos)
    }

    data class MdnsRecords(val ptr: List<Pair<String, String>>, val txt: Map<String, Map<String, String>>, val a: Map<String, String>)

    /** Разобрать ответ mDNS: PTR (сервис → экземпляр), TXT (fn=, md=), A (имя хоста → IP). */
    fun parseMdns(b: ByteArray, len: Int): MdnsRecords {
        val ptr = mutableListOf<Pair<String, String>>(); val txt = mutableMapOf<String, Map<String, String>>(); val a = mutableMapOf<String, String>()
        if (len < 12) return MdnsRecords(ptr, txt, a)
        fun u16(p: Int) = ((b[p].toInt() and 0xff) shl 8) or (b[p + 1].toInt() and 0xff)
        val qd = u16(4); val total = u16(6) + u16(8) + u16(10)
        var pos = 12
        repeat(qd) { pos = readName(b, pos, len).second + 4 }
        repeat(total) {
            if (pos + 10 > len) return MdnsRecords(ptr, txt, a)
            val (name, p) = readName(b, pos, len)
            if (p + 10 > len) return MdnsRecords(ptr, txt, a)
            val type = u16(p); val rdl = u16(p + 8); val rd = p + 10
            if (rd + rdl > len) return MdnsRecords(ptr, txt, a)
            when (type) {
                12 -> ptr += name to readName(b, rd, len).first
                16 -> {
                    val m = mutableMapOf<String, String>(); var q = rd
                    while (q < rd + rdl) {
                        val l = b[q].toInt() and 0xff; if (q + 1 + l > rd + rdl) break
                        val s = String(b, q + 1, l, Charsets.UTF_8)
                        if ('=' in s) m[s.substringBefore('=').lowercase()] = s.substringAfter('=')
                        q += 1 + l
                    }
                    txt[name] = m
                }
                1 -> if (rdl == 4) a[name] = (0 until 4).joinToString(".") { (b[rd + it].toInt() and 0xff).toString() }
            }
            pos = rd + rdl
        }
        return MdnsRecords(ptr, txt, a)
    }

    /** Запросить сервисы и собрать ответы за timeoutMs. Ответы приходят на наш порт напрямую (QU). */
    fun mdns(timeoutMs: Int = 2500, groupAddr: String = "224.0.0.251", port: Int = 5353): List<Discovered> {
        val found = mutableMapOf<String, Discovered>()
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 250
                val q = mdnsQuery(0, mdnsServices.keys)
                val group = InetAddress.getByName(groupAddr)
                repeat(2) { s.send(DatagramPacket(q, q.size, group, port)) }
                val buf = ByteArray(9000)
                val deadline = System.currentTimeMillis() + timeoutMs
                var resent = false
                while (System.currentTimeMillis() < deadline) {
                    if (!resent && System.currentTimeMillis() > deadline - timeoutMs / 2) { s.send(DatagramPacket(q, q.size, group, port)); resent = true }
                    val pk = DatagramPacket(buf, buf.size)
                    try { s.receive(pk) } catch (_: SocketTimeoutException) { continue }
                    val ip = pk.address.hostAddress ?: continue
                    if (ip.contains(':')) continue
                    val r = parseMdns(pk.data, pk.length)
                    for ((svc, inst) in r.ptr) {
                        val key = mdnsServices.keys.firstOrNull { svc.equals(it, true) } ?: continue
                        val t = r.txt[inst].orEmpty()
                        val friendly = t["fn"] ?: t["n"] ?: inst.removeSuffix(".${key}").removeSuffix("." + key.lowercase())
                        val prev = found[ip]
                        val kind = mdnsServices[key]
                        // более конкретный тип важнее (Android TV > Chromecast > http)
                        val better = prev == null || (prev.kind == null && kind != null) ||
                            (kind != null && mdnsServices.values.indexOf(kind) < mdnsServices.values.indexOf(prev.kind))
                        if (better) found[ip] = Discovered(ip, friendly.takeIf { it.isNotBlank() } ?: prev?.name, kind ?: prev?.kind, t["md"] ?: t["model"] ?: prev?.model, "mDNS")
                        else if (prev.name == null) found[ip] = prev.copy(name = friendly)
                    }
                }
            }
        } catch (_: Exception) { }
        return found.values.toList()
    }

    /* ---------------- SSDP / UPnP ---------------- */

    fun parseSsdp(text: String): Map<String, String> = text.lines().drop(1).mapNotNull { l ->
        val i = l.indexOf(':'); if (i <= 0) null else l.substring(0, i).trim().uppercase() to l.substring(i + 1).trim()
    }.toMap()

    fun kindFromUpnp(deviceType: String?, server: String?, name: String?): String? {
        val d = deviceType.orEmpty().lowercase(); val s = server.orEmpty().lowercase(); val n = name.orEmpty().lowercase()
        return when {
            "internetgatewaydevice" in d || "wanconnection" in d -> tr(R.string.router_gateway)
            "printer" in d -> tr(R.string.printer)
            "mediaserver" in d -> tr(R.string.media_server_nas)
            "mediarenderer" in d || "dial" in d || "tv" in n || "android" in s || "webos" in s || "tizen" in s -> tr(R.string.smart_tv_media_player)
            "camera" in d || "digitalsecuritycamera" in d -> tr(R.string.ip_camera_nvr)
            else -> null
        }
    }

    private fun tag(xml: String, t: String) = Regex("<${t}>([^<]{1,120})</${t}>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)?.trim()

    fun ssdp(timeoutMs: Int = 2500): List<Discovered> {
        val loc = mutableMapOf<String, Map<String, String>>()
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 250
                val group = InetAddress.getByName("239.255.255.250")
                fun search(st: String) {
                    val m = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ${st}\r\n\r\n".toByteArray()
                    s.send(DatagramPacket(m, m.size, group, 1900))
                }
                search("ssdp:all"); search("urn:dial-multiscreen-org:service:dial:1"); search("upnp:rootdevice")
                val buf = ByteArray(4096)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val pk = DatagramPacket(buf, buf.size)
                    try { s.receive(pk) } catch (_: SocketTimeoutException) { continue }
                    val ip = pk.address.hostAddress ?: continue
                    if (ip.contains(':')) continue
                    val h = parseSsdp(String(pk.data, 0, pk.length, Charsets.UTF_8))
                    val prev = loc[ip]
                    // запоминаем первый ответ, но DIAL (телевизоры) важнее прочих
                    if (prev == null || h["ST"]?.contains("dial", true) == true) loc[ip] = h + (prev ?: emptyMap()).filterKeys { it !in h }
                }
            }
        } catch (_: Exception) { }
        // описание устройства по ссылке LOCATION: friendlyName, модель, тип
        val pool = Executors.newFixedThreadPool(8)
        val out = java.util.Collections.synchronizedList(mutableListOf<Discovered>())
        loc.forEach { (ip, h) ->
            pool.execute {
                var name: String? = null; var model: String? = null; var type: String? = null
                h["LOCATION"]?.let { l ->
                    try {
                        val c = (URL(l).openConnection() as HttpURLConnection).apply { connectTimeout = 1200; readTimeout = 1500 }
                        val xml = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                        c.disconnect()
                        name = tag(xml, "friendlyName"); type = tag(xml, "deviceType")
                        model = listOfNotNull(tag(xml, "manufacturer"), tag(xml, "modelName")).joinToString(" ").ifBlank { null }
                    } catch (_: Exception) { }
                }
                out += Discovered(ip, name, kindFromUpnp(type ?: h["ST"], h["SERVER"], name), model ?: h["SERVER"], "UPnP")
            }
        }
        pool.shutdown(); pool.awaitTermination(4, TimeUnit.SECONDS)
        return out.toList()
    }

    /** mDNS и SSDP параллельно; при совпадении IP предпочитаем mDNS (точнее тип) и дополняем данными UPnP. */
    fun all(timeoutMs: Int = 2500): Map<String, Discovered> {
        var m: List<Discovered> = emptyList(); var u: List<Discovered> = emptyList()
        val t1 = Thread { m = mdns(timeoutMs) }.apply { isDaemon = true; start() }
        val t2 = Thread { u = ssdp(timeoutMs) }.apply { isDaemon = true; start() }
        t1.join(timeoutMs + 5000L); t2.join(timeoutMs + 6000L)
        val res = mutableMapOf<String, Discovered>()
        u.forEach { res[it.ip] = it }
        m.forEach { d -> val p = res[d.ip]; res[d.ip] = if (p == null) d else d.copy(name = d.name ?: p.name, model = d.model ?: p.model, kind = d.kind ?: p.kind, via = "mDNS+UPnP") }
        return res
    }

    @Suppress("unused") private fun rid() = Random.nextInt(1, 0xffff)
}
