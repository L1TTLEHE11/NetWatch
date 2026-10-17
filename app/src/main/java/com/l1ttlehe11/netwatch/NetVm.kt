package com.l1ttlehe11.netwatch

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
const val TAB_DIAG = 5; const val TAB_LOCAL = 6; const val TAB_MONITOR = 7; const val TAB_MIKROTIK = 8; const val TAB_HISTORY = 9
const val TAB_MORE = 10; const val TAB_SETTINGS = 11

/** Пять разделов нижнего меню; внутри «Сети» и «Wi-Fi» — переключатель, внутри «Ещё» — свои экраны. */
enum class Section(val idx: String, private val labelRes: Int, private val titleRes: Int, val root: Int, val tabs: List<Int>) {
    HOME("01", R.string.home, R.string.home_2, TAB_DASH, listOf(TAB_DASH)),
    SPEED("02", R.string.speed, R.string.speed_2, TAB_SPEED, listOf(TAB_SPEED)),
    NET("03", R.string.network, R.string.network_2, TAB_SCAN, listOf(TAB_SCAN, TAB_LOCAL, TAB_DIAG)),
    WIFI("04", 0, 0, TAB_WIFI, listOf(TAB_WIFI, TAB_SURVEY)),
    MORE("05", R.string.more, R.string.more_2, TAB_MORE, listOf(TAB_MORE, TAB_MONITOR, TAB_MIKROTIK, TAB_HISTORY, TAB_SETTINGS));
    // перевод на текущий язык — при каждом обращении (язык может смениться без перезапуска)
    val label: String get() = if (labelRes == 0) "WI-FI" else tr(labelRes)
    val title: String get() = if (titleRes == 0) "Wi-Fi" else tr(titleRes)
    companion object { fun of(tab: Int) = entries.firstOrNull { tab in it.tabs } ?: HOME }
}

class NetVm(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    @Volatile var foreground = true

    var radio by mutableStateOf(RadioState())
    var radioHist by mutableStateOf(List(40) { -100f })
    var report by mutableStateOf(tr(R.string.loading_network_adapter_configuration))

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
    init { History.init(ctx) }
    val mt = MtCtl(viewModelScope, ctx)
    val scan = ScanCtl(viewModelScope, ctx) { mt.known() }
    val wifi = WifiCtl(viewModelScope, ctx)
    val survey = SurveyCtl(viewModelScope, ctx)
    val diag = DiagCtl(viewModelScope, ctx)
    val wol = WolCtl(ctx)
    val upd = UpdateCtl(viewModelScope, ctx)

    /** Текущая вкладка — чтобы фоновые опросы работали только там, где их смотрят. */
    var tab by mutableStateOf(0)
    fun selectTab(i: Int) { tab = i; lastInSection[Section.of(i)] = i; syncActive(); saveInputs() }

    private val lastInSection = mutableMapOf<Section, Int>()
    val section get() = Section.of(tab)

    /** Нажатие в нижнем меню: повторное — к началу раздела, иначе — туда, где были в этом разделе. */
    fun openSection(s: Section) {
        if (s == section) selectTab(if (s == Section.MORE) TAB_MORE else tab)
        else selectTab(lastInSection[s]?.takeIf { s != Section.MORE } ?: s.root)
    }

    /** Системная «Назад»: из экрана внутри «Ещё» — в меню «Ещё», из любого раздела — на Главную. */
    fun back(): Boolean = when {
        section == Section.MORE && tab != TAB_MORE -> { selectTab(TAB_MORE); true }
        tab != TAB_DASH -> { selectTab(TAB_DASH); true }
        else -> false
    }

    var fontLarge by mutableStateOf(false)
    fun onAppVisible(v: Boolean) { foreground = v; syncActive(); if (!v) saveInputs() }

    /* ---------- запоминание введённых значений ---------- */
    private fun restoreInputs() {
        uiPrefs.getString("inet", null)?.let { inet.host = it }
        uiPrefs.getString("custom", null)?.let { custom.host = it }
        uiPrefs.getString("trace", null)?.let { diag.traceHost = it }
        uiPrefs.getString("portHost", null)?.let { diag.portHost = it }
        uiPrefs.getString("portSpec", null)?.let { diag.portSpec = it }
        survey.withSpeed = uiPrefs.getBoolean("surveySpeed", false)
        wifi.band = uiPrefs.getInt("band", 24)
        tab = uiPrefs.getInt("tab", 0).coerceIn(0, TAB_SETTINGS)
        fontLarge = uiPrefs.getBoolean("fontLarge", false)
        uiPrefs.getString("gwHost", null)?.let { gwManual = it }
        customManual = uiPrefs.getBoolean("customManual", false)
    }

    fun saveInputs() {
        uiPrefs.edit().putString("inet", inet.host).putString("custom", custom.host).putString("trace", diag.traceHost)
            .putString("portHost", diag.portHost).putString("portSpec", diag.portSpec).putBoolean("surveySpeed", survey.withSpeed)
            .putInt("band", wifi.band).putInt("tab", tab).putBoolean("customManual", customManual)
            .putBoolean("fontLarge", fontLarge).putString("gwHost", gwManual).apply()
    }


    private fun saveSpeed(d: Double, u: Double) {
        val net = NetInfo.networkLabel(ctx)
        History.add(HItem(History.nextId(), System.currentTimeMillis(), HKind.SPEED, net,
            tr(R.string.s_1f_1f_mbit_s).format(d, u),
            tr(R.string.ping_ms_jitter_ms, pingMs?.let { "%.0f".format(it) } ?: "—", jitterMs?.let { "%.1f".format(it) } ?: "—", server),
            tr(R.string.network_server_ping_jitter, net, server, pingMs?.let { tr(R.string.s_1f_ms).format(it) } ?: "—", jitterMs?.let { tr(R.string.s_1f_ms).format(it) } ?: "—") +
                tr(R.string.download_2f_mbit_s_8_threads).format(d, u),
            num1 = d, num2 = u, num3 = pingMs))
    }

    /** Сессия мониторинга, оборванная убийством процесса, помечается как прерванная. */
    private fun markInterruptedMonitor() {
        if (Monitor.state.value.running) return
        History.items.value.filter { it.kind == HKind.MONITOR && it.title.startsWith("● ") }.forEach {
            History.upsert(it.copy(title = it.title.removePrefix("● ").replace(tr(R.string.running_2), tr(R.string.interrupted))))
        }
    }
    private fun syncActive() {
        wifi.active = foreground && tab == TAB_WIFI
        mt.active = foreground && tab == TAB_MIKROTIK
        if (wifi.active) viewModelScope.launch(Dispatchers.IO) { wifi.scan() }
    }

    var localLog by mutableStateOf(tr(R.string.live_ping_started_automatically_enter_any))
    var localBusy by mutableStateOf(false)

    private val uiPrefs = ctx.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
    /** Адрес роутера, заданный вручную в настройках; null — определять автоматически. */
    var gwManual: String? = null
    /** Пользователь сам вписал адрес в Live-пинг — не перетирать его автоопределённым шлюзом. */
    var customManual = false

    init {
        History.init(ctx)
        restoreInputs()
        // последние замеры скорости — из истории, чтобы не пропадали после перезапуска
        results = History.items.value.filter { it.kind == HKind.SPEED }.take(10)
            .map { SpeedResult(it.time, it.num3, it.num1 ?: 0.0, it.num2 ?: 0.0) }
        markInterruptedMonitor()
        upd.autoCheck()
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
        gwManual?.takeIf { it.isNotBlank() }?.let { m -> ui { gw.host = m; gw.reset() }; return }
        val g = NetInfo.gateway(ctx)
        if (g.isNotEmpty()) ui { gw.host = g; gw.reset(); if (!customManual) { custom.host = g; custom.reset() } }
    }

    fun autoGateway() = viewModelScope.launch(Dispatchers.IO) { detectGateway() }

    fun refreshReport() = viewModelScope.launch(Dispatchers.IO) {
        val r = try { NetInfo.report(ctx) } catch (e: Exception) { tr(R.string.failed_to_read_adapters, e) }
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
                saveSpeed(d, u)
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
        append(tr(R.string.date, f.format(java.util.Date())))
        append(tr(R.string.network_and_interfaces)); append(report.trim()).append("\n\n")
        val r = radio
        append(tr(R.string.signal))
        append(tr(R.string.type_network_signal_dbm_quality, r.status, r.ssid ?: r.operator ?: "—", r.dbm ?: "—", r.quality ?: "—"))
        r.freqMhz?.let { append(tr(R.string.channel, NetInfo.band(it), NetInfo.channel(it))) }; r.linkMbps?.let { append(tr(R.string.link_mbit_s, it)) }
        append(tr(R.string.pings_last_40_samples))
        for ((n, c) in listOf(tr(R.string.router_2) to gw, tr(R.string.internet_3) to inet, tr(R.string.host) to custom)) {
            val v = c.valid
            append(tr(R.string.s_9s_16s_min_1f_avg_1f).format(n, c.host, v.minOrNull() ?: 0f,
                if (v.isEmpty()) 0f else v.average().toFloat(), v.maxOrNull() ?: 0f, c.lost, c.sent))
        }
        if (results.isNotEmpty()) {
            append(tr(R.string.speed_tests))
            results.forEach { x -> append(tr(R.string.s_ping_s_ms_1f_mbit).format(f.format(java.util.Date(x.time)), x.ping?.let { "%.0f".format(it) } ?: "—", x.dl, x.ul)) }
        }
        val sp = History.items.value.filter { it.kind == HKind.SPEED && it.time > System.currentTimeMillis() - 30 * 86_400_000L }
        if (sp.size > 1) {
            val d = sp.mapNotNull { it.num1 }; val u = sp.mapNotNull { it.num2 }
            append(tr(R.string.speed_over_30_days_tests, sp.size))
            append(tr(R.string.download_avg_1f_min_1f_max).format(d.average(), d.min(), d.max()))
            append(tr(R.string.upload_avg_1f_min_1f_max).format(u.average(), u.min(), u.max()))
        }
        if (scan.hosts.isNotEmpty()) { append(tr(R.string.devices_on_the_network)); append(scan.text()) }
        if (wifi.aps.isNotEmpty()) { append(tr(R.string.wi_fi_spectrum)); append(wifi.text()) }
        if (survey.points.isNotEmpty()) { append(tr(R.string.site_survey)); append(survey.text()) }
        val d = diag.text(); if (d.isNotBlank()) { append(tr(R.string.diagnostics)); append(d) }
        val m = Monitor.state.value
        if (m.startedAt > 0) {
            append(tr(R.string.connection_monitor)); append(Monitor.summary(m)).append("\n")
            m.events.take(50).forEach { e -> append("${f.format(java.util.Date(e.time))}  ${e.text}\n") }
        }
        val t = mt.text(); if (t.isNotBlank()) { append("\n== MIKROTIK ==\n"); append(t) }
    }
}
