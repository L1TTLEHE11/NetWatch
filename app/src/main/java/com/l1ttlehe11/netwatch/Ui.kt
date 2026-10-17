package com.l1ttlehe11.netwatch

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Cp {
    val bg = Color(0xFF060204)
    val card = Color(0xFF0D0609)
    val inner = Color(0xFF040103)
    val border = Color(0xFFFF003C)
    val dim = Color(0xFF4A0B1D)
    val yellow = Color(0xFFFCEE0A)
    val cyan = Color(0xFF00F0FF)
    val red = Color(0xFFFF003C)
    val green = Color(0xFF00FF66)
    val text = Color(0xFFF0F6FC)
    val muted = Color(0xFF9C7484)
    val muted2 = Color(0xFFB08A98)
    val dark = Color(0xFF050204)
    val mono = FontFamily.Monospace
}

private val cut = CutCornerShape(topEnd = 12.dp, bottomStart = 6.dp)

/** Масштаб текста во всём приложении: обычный или «крупный шрифт» из настроек. */
val LocalUiScale = staticCompositionLocalOf { 1.12f }

/**
 * Текст приложения. Размеры масштабируются, а самые мелкие подписи
 * подтягиваются до читаемых; fixed = true — для меню и бейджей фиксированной высоты.
 */
@Composable
fun T(s: String, color: Color = Cp.text, size: TextUnit = 12.sp, bold: Boolean = false, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, fixed: Boolean = false) {
    val k = LocalUiScale.current
    val sz = if (fixed) size else (maxOf(size.value, 10.5f) * k).sp
    androidx.compose.foundation.text.BasicText(
        s, modifier,
        style = TextStyle(color = color, fontSize = sz, fontFamily = Cp.mono, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun CpButton(text: String, bg: Color, fg: Color = Cp.dark, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (enabled) bg else Cp.dim, cut)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) { T(text, if (enabled) fg else Cp.muted, 11.sp, bold = true, maxLines = 1) }
}

@Composable
fun CpField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, color: Color = Cp.cyan) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value, onChange,
        modifier = modifier.border(1.dp, Cp.red).background(Cp.inner).padding(horizontal = 8.dp, vertical = 7.dp),
        singleLine = true,
        textStyle = TextStyle(color = color, fontFamily = Cp.mono, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        cursorBrush = SolidColor(Cp.yellow),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
    )
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit) {
    BasicTextField(
        value, onChange,
        modifier = Modifier.fillMaxWidth().border(1.dp, Cp.red).background(Cp.inner).padding(horizontal = 8.dp, vertical = 7.dp),
        singleLine = true,
        textStyle = TextStyle(color = Cp.cyan, fontFamily = Cp.mono, fontWeight = FontWeight.Bold, fontSize = 14.sp),
        cursorBrush = SolidColor(Cp.yellow),
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
    )
}

@Composable
fun Card(borderColor: Color = Cp.border, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp)
            .background(Cp.card, cut).border(1.dp, borderColor, cut)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun CardTitle(s: String) = T("/// ${s}", Cp.yellow, 12.sp, bold = true, modifier = Modifier.padding(bottom = 6.dp))

/* ------------------------------ графики ------------------------------ */

private fun DrawScope.grid(left: Float) {
    var x = left
    while (x < size.width) { drawLine(Color(0xFF14080E), Offset(x, 0f), Offset(x, size.height)); x += 60.dp.toPx() / 2 * 2 }
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
    for (k in listOf(0.25f, 0.75f)) drawLine(Color(0xFF2B0C18), Offset(left, size.height * k), Offset(size.width, size.height * k), pathEffect = dash)
}

@Composable
fun Sparkline(data: List<Float>, color: Color, unit: String, isDbm: Boolean = false, height: Int = 70) {
    val tm = rememberTextMeasurer()
    val lbl = TextStyle(fontFamily = Cp.mono, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    Canvas(Modifier.fillMaxWidth().height(height.dp).padding(top = 6.dp).background(Cp.inner).border(1.dp, Cp.dim)) {
        val left = 44.dp.toPx()
        grid(left)
        if (data.size < 2) return@Canvas
        val (minV, maxV) = if (isDbm) -100f to -30f else {
            val v = data.filter { it > 0f }
            0f to (if (v.isEmpty()) 50f else maxOf(10f, v.max() * 1.3f))
        }
        drawText(tm, "${maxV.toInt()}${unit}", Offset(4.dp.toPx(), size.height * 0.12f), lbl.copy(color = Cp.yellow))
        drawText(tm, "${minV.toInt()}${unit}", Offset(4.dp.toPx(), size.height * 0.68f), lbl.copy(color = Cp.muted, fontWeight = FontWeight.Normal))
        val pad = 6.dp.toPx()
        val step = (size.width - left - pad) / (data.size - 1)
        val path = Path()
        data.forEachIndexed { i, raw ->
            val v = raw.coerceIn(minV, maxV)
            val r = (v - minV) / maxOf(1e-5f, maxV - minV)
            val x = left + i * step
            val y = size.height - pad - r * (size.height - 2 * pad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (i == data.lastIndex) {
                drawPath(path, Color(0xFF3B0A1E), style = Stroke(5.dp.toPx() / 2, join = StrokeJoin.Round))
                drawPath(path, color, style = Stroke(2f, join = StrokeJoin.Round))
                drawRect(Cp.yellow, Offset(x - 4f, y - 4f), androidx.compose.ui.geometry.Size(8f, 8f))
            }
        }
    }
}

@Composable
fun SpeedChart(dl: List<Float>, ul: List<Float>) {
    val tm = rememberTextMeasurer()
    val lbl = TextStyle(fontFamily = Cp.mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Cp.yellow)
    Canvas(Modifier.fillMaxWidth().height(200.dp).background(Cp.inner)) {
        val left = 52.dp.toPx(); val top = 16.dp.toPx(); val bottom = 12.dp.toPx(); val right = 8.dp.toPx()
        val h = size.height - top - bottom
        val maxV = maxOf(100f, ((dl + ul).maxOrNull() ?: 0f) * 1.2f)
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        var gx = left
        while (gx < size.width - right) { drawLine(Color(0xFF14080E), Offset(gx, top), Offset(gx, top + h)); gx += 40.dp.toPx() }
        for (i in 0..4) {
            val y = top + h * i / 4
            drawLine(Color(0xFF2E0C19), Offset(left, y), Offset(size.width - right, y), pathEffect = dash)
            drawText(tm, tr(R.string.mb, (maxV * (1 - i / 4f)).toInt()), Offset(2.dp.toPx(), y - 6.dp.toPx()), lbl)
        }
        fun series(d: List<Float>, c: Color) {
            if (d.size < 2) return
            val step = (size.width - left - right) / (d.size - 1)
            val p = Path()
            d.forEachIndexed { i, v -> val x = left + i * step; val y = top + h - (v / maxV) * h; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, c, style = Stroke(3.dp.toPx() / 1.5f, join = StrokeJoin.Round))
        }
        series(dl, Cp.cyan); series(ul, Cp.yellow)
    }
}

/* ------------------------------ экран ------------------------------ */

@Composable
fun NetWatchScreen(vm: NetVm) {
    var showReport by remember { mutableStateOf(false) }
    if (showReport) ReportDialog(vm) { showReport = false }
    androidx.activity.compose.BackHandler(enabled = vm.tab != TAB_DASH) { vm.back() }
    val tab = vm.tab
    val sec = vm.section
    CompositionLocalProvider(LocalUiScale provides if (vm.fontLarge) 1.3f else 1.12f) {
        Column(Modifier.fillMaxSize().background(Cp.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Cp.red))
            Header(vm) { showReport = true }
            when (sec) {
                Section.NET -> SubTabs(listOf(TAB_SCAN to tr(R.string.scanner), TAB_LOCAL to tr(R.string.ping_tcp), TAB_DIAG to tr(R.string.diagnostics_2)), tab) { vm.selectTab(it) }
                Section.WIFI -> SubTabs(listOf(TAB_WIFI to tr(R.string.spectrum), TAB_SURVEY to tr(R.string.site_survey_3)), tab) { vm.selectTab(it) }
                else -> {}
            }
            // каждая вкладка со своей прокруткой, чтобы при переключении не прыгать в чужую позицию
            val scroll = remember(tab) { androidx.compose.foundation.ScrollState(0) }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    when (tab) {
                        TAB_DASH -> HomeTab(vm)
                        TAB_SPEED -> SpeedTab(vm)
                        TAB_SCAN -> ScanTab(vm)
                        TAB_WIFI -> WifiTab(vm)
                        TAB_SURVEY -> SurveyTab(vm)
                        TAB_DIAG -> DiagTab(vm)
                        TAB_LOCAL -> LocalTab(vm)
                        TAB_MONITOR -> MonitorTab(vm)
                        TAB_MIKROTIK -> MikrotikTab(vm)
                        TAB_HISTORY -> HistoryTab(vm)
                        TAB_SETTINGS -> SettingsTab(vm)
                        else -> MoreMenu(vm) { showReport = true }
                    }
                }
            }
            BottomNav(sec) { vm.openSection(it) }
        }
    }
}

@Composable
fun DashboardTab(vm: NetVm) {
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val r = vm.radio

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CardTitle(if (r.status == RadioStatus.CELLULAR) tr(R.string.cellular_signal_scanner_dbm) else tr(R.string.wi_fi_signal_scanner_dbm))
                T(
                    when (r.status) {
                        RadioStatus.WIFI_OK -> "SSID: ${r.ssid}"
                        RadioStatus.WIFI_NO_PERMISSION, RadioStatus.WIFI_LOCATION_OFF -> tr(R.string.ssid_hidden_by_the_system)
                        RadioStatus.CELLULAR -> tr(R.string.carrier, r.operator ?: "—")
                        RadioStatus.ETHERNET -> tr(R.string.ethernet_cable)
                        RadioStatus.OFFLINE -> tr(R.string.no_connection)
                    }, Cp.cyan, 12.sp, bold = true, maxLines = 1
                )
            }
            if (r.status == RadioStatus.WIFI_NO_PERMISSION || r.status == RadioStatus.WIFI_LOCATION_OFF) {
                CpButton(tr(R.string.unlock), Color(0xFF260812), Cp.cyan) {
                    if (!NetInfo.hasLocationPermission(ctx))
                        permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    else ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val dbm = r.dbm
            val good = if (r.status == RadioStatus.CELLULAR) -85 else -60
            val ok = if (r.status == RadioStatus.CELLULAR) -100 else -75
            val c = when { dbm == null -> Cp.muted; dbm >= good -> Cp.green; dbm >= ok -> Cp.yellow; else -> Cp.red }
            T(dbm?.let { "${it} dBm" } ?: "N/A", c, 26.sp, bold = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(tr(R.string.quality, r.quality?.let { "${it}%" } ?: "--"), Cp.text, 11.sp, bold = true)
                val st = when {
                    r.status == RadioStatus.WIFI_NO_PERMISSION -> tr(R.string.location_access_needed)
                    r.status == RadioStatus.WIFI_LOCATION_OFF -> tr(R.string.turn_on_location_2)
                    dbm == null -> if (r.status == RadioStatus.ETHERNET) tr(R.string.wired_connection) else tr(R.string.waiting)
                    dbm >= good -> tr(R.string.excellent_signal)
                    dbm >= ok -> tr(R.string.fair_signal)
                    else -> tr(R.string.weak_signal)
                }
                T(st, if (dbm == null) Cp.yellow else c, 10.sp, bold = true)
            }
        }
        if (r.freqMhz != null || r.linkMbps != null) {
            T(
                listOfNotNull(
                    r.freqMhz?.let { tr(R.string.ch_2, NetInfo.band(it), NetInfo.channel(it)) },
                    r.linkMbps?.let { tr(R.string.link_mbit_s_2, it) },
                    r.bssid?.let { "BSSID ${it}" }
                ).joinToString("  |  "), Cp.muted, 10.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Sparkline(vm.radioHist, Cp.cyan, "dB", isDbm = true)
    }

    PingCardView(tr(R.string.ping_to_router_gateway_live), vm.gw, Cp.green, good = 15.0, mid = 50.0, okColor = Cp.green, label = tr(R.string.gateway_2),
        extra = { CpButton(tr(R.string.auto), Cp.cyan) { vm.autoGateway() } })
    PingCardView(tr(R.string.ping_to_external_host_live), vm.inet, Cp.yellow, good = 45.0, mid = 100.0, okColor = Cp.cyan, label = tr(R.string.host))

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CardTitle(tr(R.string.network_interfaces_and_routes)) }
            CpButton(tr(R.string.refresh), Cp.red) { vm.refreshReport() }
        }
        Box(Modifier.fillMaxWidth().background(Cp.inner).border(1.dp, Cp.dim).horizontalScroll(rememberScrollState()).padding(10.dp)) {
            SelectionContainer { T(vm.report, Cp.cyan, 11.sp) }
        }
    }
}

@Composable
fun PingCardView(title: String, card: PingCard, line: Color, good: Double, mid: Double, okColor: Color, label: String, extra: (@Composable () -> Unit)? = null) {
    Card {
        CardTitle(title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(tr(R.string.ip_host), Cp.muted, 10.sp, bold = true)
            Spacer(Modifier.width(6.dp))
            CpField(card.host, { card.host = it; card.reset() }, Modifier.weight(1f))
            if (extra != null) { Spacer(Modifier.width(8.dp)); extra() }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val v = card.rtt
            val c = when { v == null -> Cp.red; v < good -> okColor; v < mid -> Cp.yellow; else -> Cp.red }
            T(if (!card.started) tr(R.string.ms_2) else v?.let { tr(R.string.s_1f_ms).format(it) } ?: tr(R.string.timeout), c, 26.sp, bold = true)
            Spacer(Modifier.weight(1f))
            T(
                if (!card.started) tr(R.string.waiting) else if (v != null) tr(R.string.is_online, label, card.host) else tr(R.string.is_not_responding, label, card.host),
                if (!card.started) Cp.muted else if (v != null) okColor else Cp.red, 10.sp, bold = true, maxLines = 1
            )
        }
        Sparkline(card.history, line, tr(R.string.ms_3))
    }
}

/* -------- шкала спидометра: нелинейная, как у Интернетометра/Speedtest -------- */

private val gaugeMarks = listOf(0f, 5f, 10f, 25f, 50f, 100f, 250f, 500f, 1000f)

private fun gaugeFrac(v: Float): Float {
    if (v <= 0f) return 0f
    val seg = 1f / (gaugeMarks.size - 1)
    for (i in 0 until gaugeMarks.size - 1) {
        val a = gaugeMarks[i]; val b = gaugeMarks[i + 1]
        if (v <= b) return seg * i + seg * (v - a) / (b - a)
    }
    return 1f
}

/** Карточка скорости: бегущая цифра + горизонтальная нелинейная шкала. */
@Composable
fun SpeedBar(title: String, value: Double?, color: Color, active: Boolean, progress: Float) {
    Card(if (active) color else Cp.dim) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(title, if (active) color else Cp.muted, 11.sp, bold = true, modifier = Modifier.weight(1f))
            if (active) T("● LIVE", Cp.red, 10.sp, bold = true)
        }
        val v = value ?: 0.0
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 4.dp)) {
            T(if (value == null) "--" else if (v >= 100) "%.1f".format(v) else "%.2f".format(v), color, 34.sp, bold = true)
            T(tr(R.string.mbit_s), Cp.muted, 12.sp, bold = true, modifier = Modifier.padding(bottom = 6.dp))
        }
        val tm = rememberTextMeasurer()
        val frac = gaugeFrac(v.toFloat())
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val barH = 12.dp.toPx()
            drawRect(Color(0xFF1A070E), Offset.Zero, androidx.compose.ui.geometry.Size(size.width, barH))
            // засечки делений
            val n = gaugeMarks.size - 1
            for (i in 0..n) {
                val x = size.width * i / n
                drawLine(Cp.dim, Offset(x, 0f), Offset(x, barH), strokeWidth = 2f)
            }
            drawRect(color.copy(alpha = 0.25f), Offset.Zero, androidx.compose.ui.geometry.Size(size.width * frac, barH))
            drawRect(color, Offset(0f, barH * 0.25f), androidx.compose.ui.geometry.Size(size.width * frac, barH * 0.5f))
            if (frac > 0f) drawRect(Cp.yellow, Offset(size.width * frac - 3f, -2f), androidx.compose.ui.geometry.Size(6f, barH + 4f))
            val lbl = TextStyle(fontFamily = Cp.mono, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Cp.muted)
            gaugeMarks.forEachIndexed { i, m ->
                val t = tm.measure(m.toInt().toString(), lbl)
                val x = (size.width * i / n - t.size.width / 2).coerceIn(0f, size.width - t.size.width)
                drawText(t, topLeft = Offset(x, barH + 4.dp.toPx()))
            }
        }
        // прогресс фазы
        Box(Modifier.fillMaxWidth().height(3.dp).background(Cp.dim)) {
            Box(Modifier.fillMaxWidth(if (active) progress else if (value != null) 1f else 0f).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun ResultBox(title: String, value: String, unit: String, color: Color, active: Boolean, modifier: Modifier, valueSize: TextUnit = 18.sp) {
    Column(
        modifier.background(Cp.card, cut).border(1.dp, if (active) color else Cp.dim, cut).padding(10.dp)
    ) {
        T(title, Cp.muted, 9.sp, bold = true)
        T(value, color, valueSize, bold = true, maxLines = 1, modifier = Modifier.heightIn(min = 24.dp))
        T(unit, Cp.muted, 9.sp)
    }
}

@Composable
fun SpeedTab(vm: NetVm) {
    // анимация, чтобы цифры и стрелка плавно «бежали» между сэмплами
    val shown by androidx.compose.animation.core.animateFloatAsState(
        vm.live.toFloat(), androidx.compose.animation.core.tween(110, easing = androidx.compose.animation.core.LinearEasing), label = "speed"
    )
    val color = if (vm.phase == Phase.UPLOAD) Cp.yellow else Cp.cyan
    val label = when (vm.phase) {
        Phase.IDLE -> tr(R.string.ready)
        Phase.PING -> tr(R.string.ping_2)
        Phase.DOWNLOAD -> tr(R.string.download)
        Phase.UPLOAD -> tr(R.string.upload)
        Phase.DONE -> tr(R.string.finished)
        Phase.ERROR -> tr(R.string.no_connection_2)
    }
    T(tr(R.string.speed_test_multi_threaded), Cp.yellow, 12.sp, bold = true, modifier = Modifier.padding(bottom = 4.dp))
    T(tr(R.string.server, vm.server), Cp.muted, 10.sp)

    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        ResultBox(tr(R.string.ping), vm.pingMs?.let { "%.0f".format(it) } ?: "--", tr(R.string.ms_3), Cp.green, vm.phase == Phase.PING, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        ResultBox(tr(R.string.jitter), vm.jitterMs?.let { "%.1f".format(it) } ?: "--", tr(R.string.ms_3), Cp.green, vm.phase == Phase.PING, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        ResultBox(tr(R.string.status), label, "", if (vm.phase == Phase.ERROR) Cp.red else color, vm.testing, Modifier.weight(1.4f), valueSize = 14.sp)
    }

    val dlNow = vm.phase == Phase.DOWNLOAD
    val ulNow = vm.phase == Phase.UPLOAD
    SpeedBar(tr(R.string.download_speed_8_threads), if (dlNow) shown.toDouble() else vm.dl, Cp.cyan, dlNow, vm.progress)
    SpeedBar(tr(R.string.upload_speed_6_threads), if (ulNow) shown.toDouble() else vm.ul, Cp.yellow, ulNow, vm.progress)

    if (vm.testing) CpButton(tr(R.string.stop), Cp.red, modifier = Modifier.fillMaxWidth()) { vm.stopTest() }
    else CpButton(if (vm.phase == Phase.IDLE) tr(R.string.start_test) else tr(R.string.test_again), Cp.green, modifier = Modifier.fillMaxWidth()) { vm.startTest() }
    T(tr(R.string.one_test_uses_roughly_10_mb),
        Cp.muted, 9.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))

    if (vm.dlCurve.isNotEmpty() || vm.ulCurve.isNotEmpty()) Card {
        Row {
            T(tr(R.string.download_8_threads), Cp.cyan, 10.sp, bold = true)
            Spacer(Modifier.width(14.dp))
            T(tr(R.string.upload_6), Cp.yellow, 10.sp, bold = true)
        }
        Spacer(Modifier.height(6.dp))
        SpeedChart(vm.dlCurve, vm.ulCurve)
    }

    if (vm.results.isNotEmpty()) Card {
        CardTitle(tr(R.string.measurement_history))
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        vm.results.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                T(fmt.format(java.util.Date(r.time)), Cp.muted, 11.sp, modifier = Modifier.width(72.dp))
                T("⏱ ${r.ping?.let { "%.0f".format(it) } ?: "--"}", Cp.green, 11.sp, bold = true, modifier = Modifier.weight(0.8f))
                T("↓ %.1f".format(r.dl), Cp.cyan, 11.sp, bold = true, modifier = Modifier.weight(1f))
                T("↑ %.1f".format(r.ul), Cp.yellow, 11.sp, bold = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LocalTab(vm: NetVm) {
    val c = vm.custom
    Card {
        CardTitle(tr(R.string.live_ping_to_any_host_and))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(tr(R.string.target_ip), Cp.cyan, 11.sp, bold = true)
            Spacer(Modifier.width(8.dp))
            CpField(c.host, { c.host = it; c.reset(); vm.customManual = true }, Modifier.weight(1f), color = Cp.yellow)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            if (vm.customRunning) CpButton(tr(R.string.pause_ping), Cp.red, modifier = Modifier.weight(1f)) { vm.customRunning = false }
            else CpButton(tr(R.string.start_ping), Cp.green, modifier = Modifier.weight(1f)) { vm.customRunning = true }
            Spacer(Modifier.width(8.dp))
            CpButton(tr(R.string.reset), Color(0xFF240B15), Cp.text, modifier = Modifier.weight(1f)) { c.reset() }
        }
        Spacer(Modifier.height(8.dp))
        CpButton(if (vm.localBusy) tr(R.string.measuring_2) else tr(R.string.lan_speed_test_tcp), Cp.green, modifier = Modifier.fillMaxWidth(), enabled = !vm.localBusy) { vm.runLocalTest() }

        val v = c.rtt
        T(
            tr(R.string.current, c.host) + if (!c.started) tr(R.string.ms_2) else v?.let { tr(R.string.s_1f_ms).format(it) } ?: tr(R.string.timeout),
            if (c.started && v == null) Cp.red else Cp.green, 16.sp, bold = true, modifier = Modifier.padding(top = 10.dp)
        )
        val valid = c.valid
        val loss = c.lost * 100.0 / maxOf(1, c.sent)
        T(
            tr(R.string.min_1f_avg_1f_max_1f).format(
                valid.minOrNull() ?: 0f, if (valid.isEmpty()) 0f else valid.average().toFloat(), valid.maxOrNull() ?: 0f, loss, c.lost, c.sent
            ), Cp.cyan, 10.sp, bold = true, modifier = Modifier.padding(vertical = 4.dp)
        )
        Sparkline(c.history, Cp.red, tr(R.string.ms_3), height = 110)
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CardTitle(tr(R.string.diagnostics_log)) }
            CpButton(tr(R.string.clear), Color(0xFF240B15), Cp.text) { vm.clearLog() }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 90.dp).background(Cp.inner).border(1.dp, Cp.dim).padding(10.dp)) {
            SelectionContainer { T(vm.localLog, Cp.green, 11.sp) }
        }
    }
    WolCard(vm)
}
