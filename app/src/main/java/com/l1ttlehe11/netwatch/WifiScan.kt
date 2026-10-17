package com.l1ttlehe11.netwatch

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build

data class Ap(
    val ssid: String, val bssid: String, val dbm: Int, val freq: Int, val centerFreq: Int,
    val channel: Int, val band: Int, val widthMhz: Int, val security: String, val connected: Boolean,
)

data class ChannelAdvice(val band: Int, val best: Int, val bestScore: Double, val current: Int?, val currentScore: Double?, val note: String)

object WifiScan {
    private fun bandOf(f: Int) = when { f < 3000 -> 24; f < 5925 -> 5; else -> 6 }

    private fun width(r: ScanResult): Int = when (r.channelWidth) {
        ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        5 -> 320
        else -> 20
    }

    private fun security(caps: String) = when {
        caps.contains("SAE") || caps.contains("WPA3") -> "WPA3"
        caps.contains("WPA2") || caps.contains("RSN") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        else -> tr(R.string.open_3)
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun results(ctx: Context): List<Ap> {
        val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val myBssid = try { wm.connectionInfo?.bssid } catch (_: Exception) { null }
        return try {
            wm.scanResults.map { r ->
                val w = width(r)
                val center = if (w > 20 && r.centerFreq0 > 0) r.centerFreq0 else r.frequency
                val ssid = (if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString() else r.SSID)?.trim('"').orEmpty()
                Ap(
                    ssid = ssid.ifBlank { tr(R.string.hidden_network) }, bssid = r.BSSID ?: "", dbm = r.level, freq = r.frequency,
                    centerFreq = center, channel = NetInfo.channel(r.frequency), band = bandOf(r.frequency), widthMhz = w,
                    security = security(r.capabilities ?: ""), connected = r.BSSID != null && r.BSSID.equals(myBssid, true),
                )
            }.sortedByDescending { it.dbm }
        } catch (_: Exception) { emptyList() }
    }

    /** Запустить сканирование. Android ограничивает до ~4 сканов за 2 минуты — тогда вернётся false. */
    @Suppress("DEPRECATION")
    fun start(ctx: Context): Boolean = try {
        ctx.applicationContext.getSystemService(WifiManager::class.java).startScan()
    } catch (_: Exception) { false }

    fun receiver(onReady: () -> Unit) = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { onReady() }
    }

    val filter get() = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)

    /** Нагрузка на канал: сумма мощностей всех сетей, чьи полосы перекрываются с полосой канала. */
    fun load(aps: List<Ap>, centerMhz: Int, widthMhz: Int): Double = aps.sumOf { ap ->
        val overlap = minOf(centerMhz + widthMhz / 2, ap.centerFreq + ap.widthMhz / 2) - maxOf(centerMhz - widthMhz / 2, ap.centerFreq - ap.widthMhz / 2)
        if (overlap <= 0) 0.0 else Math.pow(10.0, (ap.dbm + 100) / 10.0) * overlap / widthMhz
    }

    private fun freqOf(ch: Int, band: Int) = when (band) { 24 -> if (ch == 14) 2484 else 2407 + ch * 5; 5 -> 5000 + ch * 5; else -> 5950 + ch * 5 }

    val ch24 = listOf(1, 6, 11)
    val ch5 = listOf(36, 40, 44, 48, 149, 153, 157, 161, 165)          // без DFS
    val ch5dfs = listOf(52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144)

    fun advice(aps: List<Ap>, band: Int, myChannel: Int?): ChannelAdvice? {
        val inBand = aps.filter { it.band == band && !it.connected }
        val candidates = when (band) { 24 -> ch24; 5 -> ch5; else -> return null }
        val scores = candidates.associateWith { load(inBand, freqOf(it, band), 20) }
        val best = scores.minByOrNull { it.value } ?: return null
        val cur = myChannel?.let { load(inBand, freqOf(it, band), 20) }
        val note = when {
            cur == null -> tr(R.string.recommended_channel_for_a_new_access)
            myChannel == best.key -> tr(R.string.your_channel_is_already_optimal)
            cur <= best.value * 1.5 + 1 -> tr(R.string.your_channel_is_fine_the_difference)
            else -> tr(R.string.switching_channel_will_noticeably_reduce_interference)
        }
        return ChannelAdvice(band, best.key, best.value, myChannel, cur, note)
    }
}
