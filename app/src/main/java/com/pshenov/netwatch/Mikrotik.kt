package com.pshenov.netwatch

import java.io.Closeable
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

class RouterOsException(msg: String) : Exception(msg)

/**
 * Минимальный клиент MikroTik RouterOS API (порт 8728, или 8729 для API-SSL).
 * Протокол: «предложения» из слов с префиксом длины; ответы !re / !done / !trap / !fatal.
 */
class RouterOs(private val host: String, private val port: Int, private val tls: Boolean) : Closeable {
    private lateinit var sock: Socket
    private lateinit var inp: DataInputStream
    private lateinit var out: OutputStream

    fun connect(timeoutMs: Int = 4000): RouterOs {
        val target = InetSocketAddress(host, port)   // вне apply: внутри него `port` — это порт самого Socket
        val raw = Socket()
        raw.connect(target, timeoutMs); raw.soTimeout = 8000
        sock = if (tls) {
            // У RouterOS обычно самоподписанный сертификат — проверку цепочки здесь отключаем осознанно
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), null) }
            (ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket).apply { startHandshake() }
        } else raw
        inp = DataInputStream(sock.getInputStream().buffered())
        out = sock.getOutputStream().buffered()
        return this
    }

    override fun close() { try { sock.close() } catch (_: Exception) { } }

    // ---- кодирование длины слова ----
    companion object {
        fun encodeLen(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x4000 -> (n or 0x8000).let { byteArrayOf((it shr 8).toByte(), it.toByte()) }
            n < 0x200000 -> (n or 0xC00000).let { byteArrayOf((it shr 16).toByte(), (it shr 8).toByte(), it.toByte()) }
            n < 0x10000000 -> (n or 0xE0000000.toInt()).let { byteArrayOf((it ushr 24).toByte(), (it shr 16).toByte(), (it shr 8).toByte(), it.toByte()) }
            else -> byteArrayOf(0xF0.toByte(), (n ushr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
        }

        fun decodeLen(i: InputStream): Int {
            fun rd(): Int { val b = i.read(); if (b < 0) throw RouterOsException("Соединение закрыто роутером"); return b }
            val b = rd()
            return when {
                b and 0x80 == 0 -> b
                b and 0xC0 == 0x80 -> ((b and 0x3F) shl 8) or rd()
                b and 0xE0 == 0xC0 -> ((b and 0x1F) shl 16) or (rd() shl 8) or rd()
                b and 0xF0 == 0xE0 -> ((b and 0x0F) shl 24) or (rd() shl 16) or (rd() shl 8) or rd()
                b and 0xF8 == 0xF0 -> (rd() shl 24) or (rd() shl 16) or (rd() shl 8) or rd()
                else -> throw RouterOsException("Неверный префикс длины")
            }
        }

        fun decodeWord(b: ByteArray): String {
            val u = String(b, Charsets.UTF_8)
            // старые RouterOS отдают кириллицу в windows-1251
            return if (u.contains('�')) String(b, charset("windows-1251")) else u
        }
    }

    private fun writeSentence(words: List<String>) {
        for (w in words) { val b = w.toByteArray(Charsets.UTF_8); out.write(encodeLen(b.size)); out.write(b) }
        out.write(0); out.flush()
    }

    private fun readSentence(): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val n = decodeLen(inp)
            if (n == 0) return words
            val b = ByteArray(n); inp.readFully(b); words += decodeWord(b)
        }
    }

    /** Отправить команду и собрать все !re в список словарей. */
    @Synchronized
    fun talk(vararg words: String): List<Map<String, String>> {
        writeSentence(words.toList())
        val rows = mutableListOf<Map<String, String>>()
        var trap: String? = null
        while (true) {
            val s = readSentence()
            if (s.isEmpty()) continue
            val attrs = s.drop(1).filter { it.startsWith("=") }.associate { w ->
                val k = w.substring(1).substringBefore('='); k to w.substring(1).substringAfter('=', "")
            }
            when (s[0]) {
                "!re" -> rows += attrs
                "!trap" -> trap = attrs["message"] ?: "ошибка"
                "!fatal" -> throw RouterOsException(s.drop(1).joinToString(" "))
                "!done" -> { trap?.let { throw RouterOsException(it) }; if (attrs.isNotEmpty()) rows += attrs; return rows }
            }
        }
    }

    fun login(user: String, pass: String) {
        val r = talk("/login", "=name=$user", "=password=$pass")
        val challenge = r.firstOrNull()?.get("ret") ?: return   // RouterOS 6.43+: вошли сразу
        // Старый способ (до 6.43): MD5(0x00 + пароль + challenge)
        val ch = challenge.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val md = MessageDigest.getInstance("MD5").apply { update(0); update(pass.toByteArray()); update(ch) }.digest()
        talk("/login", "=name=$user", "=response=00" + md.joinToString("") { "%02x".format(it) })
    }

    /** Команда, которой может не быть на этой версии RouterOS — тогда null. */
    fun tryTalk(vararg words: String): List<Map<String, String>>? = try { talk(*words) } catch (e: RouterOsException) {
        if (e.message?.contains("no such command", true) == true || e.message?.contains("unknown", true) == true) null else throw e
    }
}

/* ---------- данные для экрана ---------- */

data class MtInfo(
    val identity: String = "", val board: String = "", val version: String = "", val uptime: String = "",
    val cpu: Int? = null, val memUsedPct: Int? = null, val temperature: String? = null,
)
data class MtIface(val name: String, val type: String, val running: Boolean, val disabled: Boolean, val rxBps: Double, val txBps: Double, val rxBytes: Long, val txBytes: Long)
data class MtLease(val ip: String, val mac: String, val host: String, val status: String, val comment: String)
data class MtWifiClient(val mac: String, val iface: String, val signal: String, val uptime: String, val rate: String, val host: String, val ip: String)
data class MtCap(val identity: String, val address: String, val board: String, val state: String)

object MtFetch {
    fun info(api: RouterOs): MtInfo {
        val id = api.talk("/system/identity/print").firstOrNull()?.get("name") ?: ""
        val r = api.talk("/system/resource/print").firstOrNull() ?: emptyMap()
        val total = r["total-memory"]?.toLongOrNull(); val free = r["free-memory"]?.toLongOrNull()
        val temp = try {
            api.tryTalk("/system/health/print")?.let { rows ->
                rows.firstOrNull { it["name"] == "temperature" || it["name"] == "cpu-temperature" }?.get("value")
                    ?: rows.firstOrNull()?.get("temperature")
            }
        } catch (_: Exception) { null }
        return MtInfo(
            identity = id, board = r["board-name"] ?: "", version = r["version"] ?: "", uptime = r["uptime"] ?: "",
            cpu = r["cpu-load"]?.toIntOrNull(),
            memUsedPct = if (total != null && free != null && total > 0) ((total - free) * 100 / total).toInt() else null,
            temperature = temp,
        )
    }

    fun ifaces(api: RouterOs): List<Map<String, String>> =
        api.talk("/interface/print", "=.proplist=name,type,running,disabled,rx-byte,tx-byte")

    fun leases(api: RouterOs): List<MtLease> = (api.tryTalk("/ip/dhcp-server/lease/print", "=.proplist=address,active-address,mac-address,host-name,status,comment") ?: emptyList())
        .map { MtLease(it["active-address"]?.takeIf { a -> a.isNotBlank() } ?: it["address"] ?: "", it["mac-address"] ?: "", it["host-name"] ?: "", it["status"] ?: "", it["comment"] ?: "") }

    fun arp(api: RouterOs): Map<String, String> = (api.tryTalk("/ip/arp/print", "=.proplist=address,mac-address") ?: emptyList())
        .mapNotNull { r -> val ip = r["address"]; val mac = r["mac-address"]; if (ip.isNullOrBlank() || mac.isNullOrBlank()) null else ip to mac }.toMap()

    /** Клиенты Wi-Fi: CAPsMAN (v6/v7), новый wifi (v7), wifiwave2, обычный wireless — что найдётся. */
    fun wifiClients(api: RouterOs): Pair<String, List<Map<String, String>>> {
        val paths = listOf(
            "/caps-man/registration-table/print" to "CAPsMAN",
            "/interface/wifi/registration-table/print" to "WiFi (v7)",
            "/interface/wifiwave2/registration-table/print" to "wifiwave2",
            "/interface/wireless/registration-table/print" to "Wireless",
        )
        val all = mutableListOf<Map<String, String>>(); val src = mutableListOf<String>()
        for ((p, name) in paths) {
            val rows = try { api.tryTalk(p) } catch (_: Exception) { null } ?: continue
            if (rows.isNotEmpty()) { all += rows; src += name }
        }
        return (src.joinToString(" + ").ifEmpty { "нет данных" }) to all
    }

    fun caps(api: RouterOs): List<MtCap> {
        val rows = (try { api.tryTalk("/caps-man/remote-cap/print") } catch (_: Exception) { null }).orEmpty() +
            (try { api.tryTalk("/interface/wifi/capsman/remote-cap/print") } catch (_: Exception) { null }).orEmpty()
        return rows.map { MtCap(it["identity"] ?: it["name"] ?: "", it["address"] ?: "", it["board"] ?: "", it["state"] ?: "") }
    }
}
