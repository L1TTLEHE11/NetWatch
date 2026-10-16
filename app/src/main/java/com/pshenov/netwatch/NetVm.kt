package com.pshenov.netwatch

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class Phase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, ERROR }

data class SpeedResult(val time: Long, val ping: Double?, val dl: Double, val ul: Double)

class PingCard(initialHost: String) {
    var host by mutableStateOf(initialHost)
    var rtt by mutableStateOf<Double?>(null)
    var started by mutableStateOf(false)
    var history by mutableStateOf(List(40) { 0f })
    var sent by mutableStateOf(0)
    var lost by mutableStateOf(0)

    fun push(v: Double?) {
        rtt = v; started = true
        history = history.drop(1) + (v?.toFloat() ?: 0f)
        sent++; if (v == null) lost++
    }
    fun reset() { history = List(40) { 0f }; sent = 0; lost = 0 }
    val valid get() = history.filter { it > 0f }
}

const val TAB_DASH = 0; const val TAB_SPEED = 1; const val TAB_SCAN = 2; const val TAB_WIFI = 3; const val TAB_SURVEY = 4
const val TAB_DIAG = 5; const val TAB_LOCAL = 6; const val TAB_MONITOR = 7; const val TAB_MIKROTIK = 8

class NetVm(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    @Volatile var foreground = true

    var radio by mutableStateOf(RadioState())
    var radioHist by mutableStateOf(List(40) { -100f })
    var report by mutableStateOf("// ЗАГРУЗКА КОНФИГУРАЦИИ СЕТЕВЫХ АДАПТЕРОВ...")

    val gw = PingCard("192.168.1.1")
    val inet = PingCard("8.8.8.8")
    val custom = PingCard("192.168.10.1")
    var customRunning by mutableStateOf(true)

    // Замер скорости как в Интернетометре: старт по кнопке → пинг → скачивание → отдача
    var phase by mutableStateOf(Phase.IDLE)
    var live by mutableStateOf(0.0)          // текущая «бегущая» скорость на шкале
    var progress by mutableStateOf(0f)       // прогресс текущей фазы 0..1
    var pingMs by mutableStateOf<Double?>(null)
    var jitterMs by mutableStateOf<Double?>(null)
    var dl by mutableStateOf<Double?>(null)
    var ul by mutableStateOf<Double?>(null)
    var dlCurve by mutableStateOf(listOf<Float>())
    var ulCurve by mutableStateOf(listOf<Float>())
    var server by mutableStateOf("—")
    var results by mutableStateOf(listOf<SpeedResult>())
    private var testJob: Job? = null

    // новые модули
    val mt = MtCtl(viewModelScope, ctx)
    val scan = ScanCtl(viewModelScope, ctx) { mt.known() }
    val wifi = WifiCtl(viewModelScope, ctx)
    val survey = SurveyCtl(viewModelScope, ctx)
    val diag = DiagCtl(viewModelScope, ctx)
    val wol = WolCtl(ctx)

    /** Текущая вкладка — чтобы фоновые опросы работали только там, где их смотрят. */
    var tab by mutableStateOf(0)
    fun selectTab(i: Int) { tab = i; syncActive() }
    fun onAppVisible(v: Boolean) { foreground = v; syncActive() }
    private fun syncActive() {
        wifi.active = foreground && tab == TAB_WIFI
        mt.active = foreground && tab == TAB_MIKROTIK
        if (wifi.active) viewModelScope.launch(Dispatchers.IO) { wifi.scan() }
    }

    var localLog by mutableStateOf("Live-Пинг запущен автоматически! Введите любой IP в поле выше, чтобы видеть график отклика до устройства в реальном времени.\n")
    var localBusy by mutableStateOf(false)

    init {
        viewModelScope.launch(Dispatchers.IO) { detectGateway(); refreshReport() }
        loop(600) {
            val r = NetInfo.readRadio(ctx)
            ui { radio = r; radioHist = radioHist.drop(1) + (r.dbm?.toFloat() ?: -100f) }
        }
        loop(500) { val h = gw.host.trim(); val v = Pinger.ping(h); ui { gw.push(v) } }
        loop(500) { val h = inet.host.trim().ifEmpty { "8.8.8.8" }; val v = Pinger.ping(h); ui { inet.push(v) } }
        loop(500) {
            if (customRunning) { val h = custom.host.trim(); val v = Pinger.ping(h); ui { custom.push(v) } }
        }
    }

    private fun loop(periodMs: Long, block: suspend () -> Unit): Job = viewModelScope.launch(Dispatchers.IO) {
        while (isActive) {
            if (foreground) try { block() } catch (e: Exception) { if (!isActive) throw e }
            delay(periodMs)
        }
    }

    private suspend fun ui(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    private suspend fun detectGateway() {
        val g = NetInfo.gateway(ctx)
        if (g.isNotEmpty()) ui { gw.host = g; custom.host = g; gw.reset(); custom.reset() }
    }

    fun autoGateway() = viewModelScope.launch(Dispatchers.IO) { detectGateway() }

    fun refreshReport() = viewModelScope.launch(Dispatchers.IO) {
        val r = try { NetInfo.report(ctx) } catch (e: Exception) { "// ОШИБКА ПОЛУЧЕНИЯ СПИСКА АДАПТЕРОВ: $e" }
        ui { report = r }
    }

    fun runLocalTest() {
        val target = custom.host.trim()
        if (target.isEmpty() || localBusy) return
        localBusy = true
        viewModelScope.launch(Dispatchers.IO) {
            Speed.localTest(target) { line -> viewModelScope.launch(Dispatchers.Main) { localLog += line + "\n" } }
            ui { localBusy = false }
        }
    }

    fun clearLog() { localLog = "" }

    val testing get() = phase == Phase.PING || phase == Phase.DOWNLOAD || phase == Phase.UPLOAD

    fun startTest() {
        if (testing) return
        live = 0.0; progress = 0f; pingMs = null; jitterMs = null; dl = null; ul = null
        dlCurve = emptyList(); ulCurve = emptyList()
        phase = Phase.PING
        testJob = viewModelScope.launch(Dispatchers.IO) {
            val job = coroutineContext[Job]!!
            val alive = { job.isActive && foreground }
            val lat = Speed.latency(alive)
            ui { server = Speed.serverName(); pingMs = lat?.first; jitterMs = lat?.second }
            if (lat == null && Speed.serverName() == "—") { ui { phase = Phase.ERROR }; return@launch }
            if (!alive()) return@launch

            ui { phase = Phase.DOWNLOAD; progress = 0f }
            val d = Speed.download(alive, { v, p -> post { live = v; progress = p; dlCurve = dlCurve + v.toFloat() } })
            ui { dl = d; live = 0.0 }
            if (!alive()) return@launch

            ui { phase = Phase.UPLOAD; progress = 0f }
            val u = Speed.upload(alive, { v, p -> post { live = v; progress = p; ulCurve = ulCurve + v.toFloat() } })
            ui {
                ul = u; live = 0.0; progress = 1f; phase = Phase.DONE
                results = (listOf(SpeedResult(System.currentTimeMillis(), pingMs, d, u)) + results).take(10)
            }
        }
        // прервали (кнопкой или ушли в фон) — вернуть шкалу в покой
        testJob?.invokeOnCompletion {
            viewModelScope.launch(Dispatchers.Main) { if (testing) { live = 0.0; phase = if (dl != null) Phase.DONE else Phase.IDLE } }
        }
    }

    fun stopTest() {
        testJob?.cancel(); testJob = null
        live = 0.0; phase = if (dl != null) Phase.DONE else Phase.IDLE
    }

    private fun post(block: () -> Unit) { viewModelScope.launch(Dispatchers.Main) { if (testing) block() } }

    override fun onCleared() { mt.disconnect(); super.onCleared() }

    /** Сводный отчёт по всем модулям — для PDF/TXT. */
    fun fullReport(): String = buildString {
        val f = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
        append("Дата: ${f.format(java.util.Date())}\n\n")
        append("== СЕТЬ И ИНТЕРФЕЙСЫ ==\n"); append(report.trim()).append("\n\n")
        val r = radio
        append("== СИГНАЛ ==\n")
        append("Тип: ${r.status}  Сеть: ${r.ssid ?: r.operator ?: "—"}  Сигнал: ${r.dbm ?: "—"} dBm  Качество: ${r.quality ?: "—"}%")
        r.freqMhz?.let { append("  ${NetInfo.band(it)}, канал ${NetInfo.channel(it)}") }; r.linkMbps?.let { append("  линк $it Мбит/с") }
        append("\n\n== ПИНГИ (последние 40 замеров) ==\n")
        for ((n, c) in listOf("Роутер" to gw, "Интернет" to inet, "Узел" to custom)) {
            val v = c.valid
            append("%-9s %-16s мин %.1f / сред %.1f / макс %.1f мс, потери %d/%d\n".format(n, c.host, v.minOrNull() ?: 0f,
                if (v.isEmpty()) 0f else v.average().toFloat(), v.maxOrNull() ?: 0f, c.lost, c.sent))
        }
        if (results.isNotEmpty()) {
            append("\n== ЗАМЕРЫ СКОРОСТИ ==\n")
            results.forEach { x -> append("%s  пинг %s мс  ↓ %.1f Мбит/с  ↑ %.1f Мбит/с\n".format(f.format(java.util.Date(x.time)), x.ping?.let { "%.0f".format(it) } ?: "—", x.dl, x.ul)) }
        }
        if (scan.hosts.isNotEmpty()) { append("\n== УСТРОЙСТВА В СЕТИ ==\n"); append(scan.text()) }
        if (wifi.aps.isNotEmpty()) { append("\n== WI-FI ЭФИР ==\n"); append(wifi.text()) }
        if (survey.points.isNotEmpty()) { append("\n== ОБХОД ОБЪЕКТА ==\n"); append(survey.text()) }
        val d = diag.text(); if (d.isNotBlank()) { append("\n== ДИАГНОСТИКА ==\n"); append(d) }
        val m = Monitor.state.value
        if (m.startedAt > 0) {
            append("\n== МОНИТОРИНГ СВЯЗИ ==\n"); append(Monitor.summary(m)).append("\n")
            m.events.take(50).forEach { e -> append("${f.format(java.util.Date(e.time))}  ${e.text}\n") }
        }
        val t = mt.text(); if (t.isNotBlank()) { append("\n== MIKROTIK ==\n"); append(t) }
    }
}
