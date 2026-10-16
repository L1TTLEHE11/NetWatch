package com.pshenov.netwatch

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

enum class RadioStatus { WIFI_OK, WIFI_NO_PERMISSION, WIFI_LOCATION_OFF, CELLULAR, ETHERNET, OFFLINE }

data class RadioState(
    val status: RadioStatus = RadioStatus.OFFLINE,
    val ssid: String? = null,
    val bssid: String? = null,
    val dbm: Int? = null,
    val quality: Int? = null,
    val linkMbps: Int? = null,
    val freqMhz: Int? = null,
    val operator: String? = null,
)

/** Пинг: системный /system/bin/ping, при его отсутствии — замер TCP-рукопожатия. */
object Pinger {
    private val timeRe = Regex("""time[=<]\s*([\d.,]+)""", RegexOption.IGNORE_CASE)
    @Volatile private var icmpBroken = false

    fun ping(host: String): Double? {
        if (host.isBlank()) return null
        if (!icmpBroken) {
            try {
                val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", host)
                    .redirectErrorStream(true).start()
                val done = p.waitFor(1600, TimeUnit.MILLISECONDS)
                if (!done) { p.destroy(); return null }
                val out = p.inputStream.bufferedReader().readText()
                timeRe.find(out)?.let { return it.groupValues[1].replace(",", ".").toDoubleOrNull() }
                if (out.contains("permission", true) || out.contains("not permitted", true)) {
                    icmpBroken = true
                } else return null
            } catch (_: Exception) {
                icmpBroken = true
            }
        }
        return tcpPing(host)
    }

    /** Время TCP-соединения. Отказ в соединении (RST) — тоже ответ живого узла. */
    fun tcpPing(host: String): Double? {
        val addr = try { InetAddress.getByName(host) } catch (_: Exception) { return null }
        for (port in intArrayOf(80, 443, 53, 22, 8291)) {
            val t0 = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(addr, port), 900) }
                return (System.nanoTime() - t0) / 1e6
            } catch (_: ConnectException) {
                val ms = (System.nanoTime() - t0) / 1e6
                if (ms < 900) return ms
            } catch (_: Exception) { }
        }
        return null
    }
}

object NetInfo {

    private fun cm(ctx: Context) = ctx.getSystemService(ConnectivityManager::class.java)

    fun hasLocationPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationOn(ctx: Context): Boolean {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun readRadio(ctx: Context): RadioState {
        val cm = cm(ctx)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return RadioState(RadioStatus.OFFLINE)
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
                val info = wm.connectionInfo
                val rssi = info.rssi.takeIf { it in -120..-5 }
                var ssid: String? = info.ssid?.trim('"')
                if (ssid == null || ssid == "<unknown ssid>" || ssid.isBlank()) ssid = null
                val bssid = info.bssid?.takeIf { it != "02:00:00:00:00:00" }
                val status = when {
                    ssid != null -> RadioStatus.WIFI_OK
                    !hasLocationPermission(ctx) -> RadioStatus.WIFI_NO_PERMISSION
                    !isLocationOn(ctx) -> RadioStatus.WIFI_LOCATION_OFF
                    else -> RadioStatus.WIFI_OK
                }
                return RadioState(
                    status = status,
                    ssid = ssid ?: if (status == RadioStatus.WIFI_OK) "Wi-Fi (Активная сеть)" else null,
                    bssid = bssid,
                    dbm = rssi,
                    quality = rssi?.let { (2 * (it + 100)).coerceIn(0, 100) },
                    linkMbps = info.linkSpeed.takeIf { it > 0 },
                    freqMhz = info.frequency.takeIf { it > 0 },
                )
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                var dbm: Int? = null
                var op: String? = null
                try {
                    val tm = ctx.getSystemService(TelephonyManager::class.java)
                    op = tm.networkOperatorName?.takeIf { it.isNotBlank() }
                    if (Build.VERSION.SDK_INT >= 29) {
                        dbm = tm.signalStrength?.cellSignalStrengths
                            ?.map { it.dbm }?.firstOrNull { it in -150..-20 }
                    }
                } catch (_: Exception) { }
                return RadioState(
                    status = RadioStatus.CELLULAR, dbm = dbm, operator = op,
                    quality = dbm?.let { ((it + 120) * 100 / 70).coerceIn(0, 100) }
                )
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> return RadioState(RadioStatus.ETHERNET)
        }
        return RadioState(RadioStatus.OFFLINE)
    }

    @Suppress("DEPRECATION")
    fun gateway(ctx: Context): String {
        val cm = cm(ctx)
        val lp = cm.getLinkProperties(cm.activeNetwork)
        lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress?.let { if (it != "0.0.0.0") return it }
        return try {
            val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
            val g = wm.dhcpInfo.gateway
            if (g == 0) "" else "${g and 0xff}.${g shr 8 and 0xff}.${g shr 16 and 0xff}.${g shr 24 and 0xff}"
        } catch (_: Exception) { "" }
    }

    /** Аналог ipconfig /all для Android. */
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun report(ctx: Context): String {
        val cm = cm(ctx)
        val sb = StringBuilder()
        val active = cm.activeNetwork
        sb.append("// УСТРОЙСТВО: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n")
        val nets = cm.allNetworks
        if (nets.isEmpty()) sb.append("// АКТИВНЫХ СЕТЕЙ НЕТ\n")
        for (n in nets) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            val lp = cm.getLinkProperties(n) ?: continue
            val kinds = buildList {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WI-FI")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("МОБИЛЬНАЯ")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("BLUETOOTH")
            }.ifEmpty { listOf("ДРУГОЕ") }
            sb.append("▌ ${kinds.joinToString("+")} — ${lp.interfaceName ?: "?"}")
            if (n == active) sb.append("  [АКТИВНАЯ]")
            sb.append('\n')
            for (la in lp.linkAddresses) sb.append("   Адрес ........ ${la.address.hostAddress}/${la.prefixLength}\n")
            lp.routes.filter { it.isDefaultRoute && it.gateway != null }.forEach {
                sb.append("   Шлюз ......... ${it.gateway?.hostAddress}\n")
            }
            for (d in lp.dnsServers) sb.append("   DNS .......... ${d.hostAddress}\n")
            lp.domains?.takeIf { it.isNotBlank() }?.let { sb.append("   Домен ........ $it\n") }
            if (Build.VERSION.SDK_INT >= 29 && lp.mtu > 0) sb.append("   MTU .......... ${lp.mtu}\n")
            sb.append("   Интернет ..... ${if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "ДА (проверен)" else "НЕТ / не проверен"}\n")
            sb.append("   Тарификация .. ${if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "безлимит" else "ЛИМИТНАЯ"}\n")
            val down = caps.linkDownstreamBandwidthKbps; val up = caps.linkUpstreamBandwidthKbps
            if (down > 0) sb.append("   Канал (оценка) ↓${down / 1000} / ↑${up / 1000} Мбит/с\n")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wi = ctx.applicationContext.getSystemService(WifiManager::class.java).connectionInfo
                sb.append("   SSID ......... ${wi.ssid?.trim('"')}\n")
                sb.append("   BSSID ........ ${wi.bssid}\n")
                sb.append("   Частота ...... ${wi.frequency} МГц (${band(wi.frequency)}, канал ${channel(wi.frequency)})\n")
                sb.append("   Скорость ..... ${wi.linkSpeed} Мбит/с\n")
                sb.append("   RSSI ......... ${wi.rssi} dBm\n")
                if (Build.VERSION.SDK_INT >= 30) sb.append("   Стандарт ..... ${wifiStd(wi.wifiStandard)}\n")
            }
            sb.append('\n')
        }
        sb.append("▌ ИНТЕРФЕЙСЫ СИСТЕМЫ\n")
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.filter { it.isUp }?.forEach { ni ->
                val ips = ni.inetAddresses.toList().joinToString(", ") { it.hostAddress?.substringBefore('%') ?: "" }
                sb.append("   ${ni.name.padEnd(10)} ${if (ni.isLoopback) "(loopback) " else ""}$ips\n")
            }
        } catch (e: Exception) { sb.append("   ошибка: ${e.message}\n") }
        return sb.toString()
    }

    fun band(f: Int) = when { f in 2400..2500 -> "2.4 ГГц"; f in 4900..5900 -> "5 ГГц"; f in 5925..7125 -> "6 ГГц"; else -> "?" }
    fun channel(f: Int) = when {
        f == 2484 -> 14
        f in 2412..2472 -> (f - 2407) / 5
        f in 5000..5900 -> (f - 5000) / 5
        f in 5955..7115 -> (f - 5950) / 5
        else -> 0
    }
    private fun wifiStd(s: Int) = when (s) { 1 -> "802.11a/b/g"; 4 -> "Wi-Fi 4 (n)"; 5 -> "Wi-Fi 5 (ac)"; 6 -> "Wi-Fi 6 (ax)"; 7 -> "WiGig (ad)"; 8 -> "Wi-Fi 7 (be)"; else -> "?" }
}

/** Многопоточные замеры, повторяют логику десктопной версии. */
object Speed {
    private val mirrors = listOf(
        "https://mirror.yandex.ru/ubuntu/ls-lR.gz",
        "https://mirror.selectel.ru/ubuntu/ls-lR.gz",
        "https://speed.cloudflare.com/__down?bytes=25000000",
        "https://speed.hetzner.de/100MB.bin",
    )
    private val upTargets = listOf(
        "https://speed.cloudflare.com/__up",
        "https://mirror.yandex.ru/",
        "https://httpbin.org/post",
    )
    @Volatile private var cachedMirror: String? = null

    private fun open(url: String, timeout: Int = 3000) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeout; readTimeout = timeout
        useCaches = false
        setRequestProperty("User-Agent", "Mozilla/5.0 NetWatch")
        setRequestProperty("Cache-Control", "no-cache")
        setRequestProperty("Accept-Encoding", "identity")
    }

    private fun pickMirror(): String? {
        cachedMirror?.let { return it }
        for (u in mirrors) {
            try {
                val c = open(u, 2000)
                c.inputStream.use { it.read(ByteArray(1024)) }
                c.disconnect()
                cachedMirror = u
                return u
            } catch (_: Exception) { }
        }
        return null
    }

    /**
     * Живой замер: N потоков качают/льют без остановки, отдельный сэмплер каждые 100 мс
     * считает скорость по скользящему окну и отдаёт её в onLive — так цифры «бегут».
     * Итог — средняя скорость без первых 25% времени (разгон TCP не учитывается).
     */
    private fun liveMeasure(
        threads: Int, durationMs: Long, running: () -> Boolean,
        onLive: (mbps: Double, progress: Float) -> Unit,
        worker: (deadlineNs: Long, total: AtomicLong) -> Unit,
    ): Double {
        val total = AtomicLong(0)
        val t0 = System.nanoTime()
        val deadline = t0 + durationMs * 1_000_000
        val ws = (0 until threads).map {
            thread(isDaemon = true) {
                while (System.nanoTime() < deadline && running()) {
                    try { worker(deadline, total) } catch (_: Exception) { Thread.sleep(150) }
                }
            }
        }
        val window = ArrayDeque<Pair<Long, Long>>()
        val warmNs = t0 + durationMs * 250_000
        var warmBytes = -1L
        var warmAt = 0L
        while (System.nanoTime() < deadline && running()) {
            Thread.sleep(100)
            val now = System.nanoTime(); val b = total.get()
            if (warmBytes < 0 && now >= warmNs) { warmBytes = b; warmAt = now }
            window.addLast(now to b)
            while (window.size > 2 && now - window.first().first > 800_000_000L) window.removeFirst()
            val (ft, fb) = window.first()
            val live = if (now > ft) (b - fb) * 8 / ((now - ft) / 1e9) / 1e6 else 0.0
            onLive(live, ((now - t0) / (durationMs * 1e6)).toFloat().coerceIn(0f, 1f))
        }
        val endNs = System.nanoTime(); val endBytes = total.get()
        ws.forEach { it.join(3000) }
        if (warmBytes < 0) { warmBytes = 0; warmAt = t0 }
        val el = (endNs - warmAt) / 1e9
        return if (el > 0.2 && endBytes > warmBytes) (endBytes - warmBytes) * 8 / el / 1e6 else 0.0
    }

    fun download(running: () -> Boolean, onLive: (Double, Float) -> Unit, threads: Int = 8, durationMs: Long = 8000): Double {
        val url = pickMirror() ?: return 0.0
        val sep = if ('?' in url) '&' else '?'
        return liveMeasure(threads, durationMs, running, onLive) { deadline, total ->
            val c = open("$url${sep}r=${System.nanoTime()}")
            try {
                val ins = c.inputStream
                val buf = ByteArray(65536)
                while (System.nanoTime() < deadline && running()) {
                    val r = ins.read(buf); if (r < 0) break
                    total.addAndGet(r.toLong())
                }
            } finally { try { c.disconnect() } catch (_: Exception) { } }
        }
    }

    fun upload(running: () -> Boolean, onLive: (Double, Float) -> Unit, threads: Int = 6, durationMs: Long = 7000): Double {
        val target = upTargets.firstOrNull { u ->
            try { Socket().use { it.connect(InetSocketAddress(URL(u).host, 443), 1500) }; true } catch (_: Exception) { false }
        } ?: return 0.0
        val chunk = ByteArray(32768) { 'A'.code.toByte() }
        val perConn = 52_428_800L
        return liveMeasure(threads, durationMs, running, onLive) { deadline, total ->
            val c = open(target, 3000).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(perConn)
            }
            try {
                val out = c.outputStream
                var sent = 0L
                while (System.nanoTime() < deadline && running() && sent + chunk.size <= perConn) {
                    out.write(chunk); sent += chunk.size
                    total.addAndGet(chunk.size.toLong())
                }
            } finally { try { c.disconnect() } catch (_: Exception) { } }
        }
    }

    /** Пинг до сервера замера: 6 попыток → минимум и джиттер. */
    fun latency(running: () -> Boolean): Pair<Double, Double>? {
        val host = URL(pickMirror() ?: return null).host
        val rs = mutableListOf<Double>()
        repeat(6) { if (running()) Pinger.ping(host)?.let { rs += it } }
        if (rs.isEmpty()) return null
        val jitter = if (rs.size > 1) rs.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average() else 0.0
        return rs.min() to jitter
    }

    fun serverName(): String = cachedMirror?.let { URL(it).host } ?: "—"

    /** Локальный TCP-тест до устройства в сети. Возвращает строки для журнала. */
    fun localTest(target: String, log: (String) -> Unit) {
        log("\n--- Многопоточный тест локальной пропускной способности до $target ---")
        val addr = try { InetAddress.getByName(target) } catch (e: Exception) { log("⚠️ Не удалось разрешить адрес: ${e.message}"); return }
        var open: Int? = null
        for (p in intArrayOf(80, 445, 443, 8291, 8080, 22, 139)) {
            try { Socket().use { it.connect(InetSocketAddress(addr, p), 600) }; open = p; break } catch (_: Exception) { }
        }
        if (open != null) {
            val n = 6; val dur = 1500L
            val payload = ByteArray(65536)
            val total = AtomicLong(0)
            val t0 = System.nanoTime()
            (0 until n).map {
                thread(isDaemon = true) {
                    var local = 0L
                    while (System.nanoTime() - t0 < dur * 1_000_000) {
                        try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(addr, open), 1000)
                                s.soTimeout = 1000
                                val o = s.getOutputStream()
                                repeat(10) { o.write(payload); local += payload.size }
                            }
                        } catch (_: Exception) { break }
                    }
                    total.addAndGet(local)
                }
            }.forEach { it.join(6000) }
            val el = (System.nanoTime() - t0) / 1e9
            val mbps = total.get() * 8 / (el * 1e6)
            log("✓ Открыт TCP-порт $open. Прокачано ${"%.2f".format(total.get() / 1048576.0)} МБ в $n потоков за ${"%.2f".format(el)} сек.")
            log("✓ Реальная измеренная скорость локального TCP-обмена с $target: ${"%.2f".format(mbps)} Мбит/с")
        } else {
            log("Открытые TCP-порты не найдены. Запуск нагрузочного ICMP-теста (15 × 1400 байт)...")
            try {
                val t0 = System.nanoTime()
                val p = ProcessBuilder("/system/bin/ping", "-c", "15", "-i", "0.2", "-s", "1400", "-W", "1", target)
                    .redirectErrorStream(true).start()
                if (!p.waitFor(10, TimeUnit.SECONDS)) { p.destroy(); throw RuntimeException("timeout") }
                val out = p.inputStream.bufferedReader().readText()
                val el = (System.nanoTime() - t0) / 1e9
                val got = Regex("""(\d+) (packets )?received""").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                log(out.trim())
                if (got == 0) log("⚠️ Узел $target не отвечает или блокирует крупные пакеты.")
                else log("✓ Скорость обмена ICMP-пакетами: ${"%.2f".format(got * 1400 * 2 * 8 / (el * 1e6))} Мбит/с (ограничено интервалом ping)")
            } catch (_: Exception) {
                log("⚠️ Узел $target не отвечает или блокирует крупные пакеты.")
            }
        }
    }
}
