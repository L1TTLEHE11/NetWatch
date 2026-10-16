package com.pshenov.netwatch

import android.Manifest
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val hhmmss = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun Hint(s: String) = T(s, Cp.muted, 10.sp, modifier = Modifier.padding(vertical = 4.dp))

@Composable
fun Progress(done: Int, total: Int, color: Color = Cp.cyan) {
    val f = if (total > 0) done.toFloat() / total else 0f
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(4.dp).background(Cp.dim)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(color))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(end = 6.dp).background(if (selected) Cp.cyan else Color(0xFF1A070E)).border(1.dp, Cp.cyan)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
        T(text, if (selected) Cp.dark else Cp.cyan, 11.sp, bold = true)
    }
}

/** Кнопка запроса геолокации — без неё Android не отдаёт SSID и результаты сканирования эфира. */
@Composable
fun LocationGate() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val hasPerm = NetInfo.hasLocationPermission(ctx)
    val on = NetInfo.isLocationOn(ctx)
    if (!hasPerm || !on) Card(Cp.yellow) {
        T(if (!hasPerm) "Android показывает сети Wi-Fi только приложениям с доступом к геолокации. Координаты не используются." else "Включите геолокацию — без неё Android не отдаёт результаты сканирования Wi-Fi.",
            Cp.yellow, 11.sp)
        Spacer(Modifier.height(6.dp))
        CpButton(if (!hasPerm) "[ 🔓 ДАТЬ ДОСТУП ]" else "[ ⚙ ВКЛЮЧИТЬ ГЕОЛОКАЦИЮ ]", Cp.yellow) {
            if (!hasPerm) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/* ============================ [03] сканер сети ============================ */

@Composable
fun ScanTab(vm: NetVm) {
    val s = vm.scan
    Card {
        CardTitle("СКАНЕР ЛОКАЛЬНОЙ СЕТИ")
        T(if (s.subnet.isNotEmpty()) "Подсеть: ${s.subnet}" else "Найдёт все устройства в вашей сети: роутеры, камеры, принтеры, компьютеры, телефоны.", Cp.muted, 10.sp)
        if (vm.mt.connected) T("✓ MikroTik подключён — MAC-адреса и имена берутся из DHCP/ARP роутера", Cp.green, 10.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(8.dp))
        if (s.running) CpButton("[ ⏹ ОСТАНОВИТЬ ]", Cp.red, modifier = Modifier.fillMaxWidth()) { s.stop() }
        else CpButton(if (s.hosts.isEmpty()) "[ ▶ СКАНИРОВАТЬ СЕТЬ ]" else "[ 🔄 ПЕРЕСКАНИРОВАТЬ ]", Cp.green, modifier = Modifier.fillMaxWidth()) { s.start() }
        s.error?.let { T(it, Cp.red, 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (s.running || s.total > 0) {
            Progress(s.done, s.total)
            T("Проверено ${s.done} из ${s.total} · найдено ${s.hosts.size}" + if (s.running) " · идёт сканирование…" else "", Cp.cyan, 10.sp, bold = true)
        }
    }
    if (s.hosts.isNotEmpty()) {
        val kinds = s.hosts.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }
        Card(Cp.dim) {
            CardTitle("ИТОГО: ${s.hosts.size} УСТРОЙСТВ")
            T(kinds.joinToString("  ·  ") { "${it.key}: ${it.value}" }, Cp.text, 10.sp)
        }
    }
    s.hosts.forEach { h -> HostCard(h, vm) }
    if (!s.running && s.finishedAt != null) Hint("Android 10+ не отдаёт приложениям MAC-адреса чужих устройств. Они видны для компьютеров Windows (через NetBIOS) и для всех устройств, если подключить MikroTik на вкладке [09].")
}

@Composable
private fun HostCard(h: LanHost, vm: NetVm) {
    val c = when { h.isSelf -> Cp.yellow; h.isGateway -> Cp.red; h.kind.contains("камера") -> Cp.green; else -> Cp.cyan }
    Card(if (h.isGateway || h.isSelf) c else Cp.dim) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(h.ip, c, 15.sp, bold = true, modifier = Modifier.weight(1f))
            T(h.rtt?.let { if (h.isSelf) "это вы" else "%.0f мс".format(it) } ?: "", Cp.muted, 10.sp)
        }
        T(h.kind, Cp.text, 11.sp, bold = true)
        listOfNotNull(h.name, h.title?.takeIf { it != h.name }).forEach { T(it, Cp.cyan, 10.sp, maxLines = 1) }
        h.mac?.let { T("MAC $it", Cp.muted, 10.sp) }
        if (h.ports.isNotEmpty()) T("Порты: " + h.ports.joinToString(", ") { p -> PortInfo.label(p).ifEmpty { "$p" }.let { "$p ${it.substringBefore(' ')}" } }, Cp.yellow, 10.sp)
        if (!h.isSelf) Row(Modifier.padding(top = 6.dp)) {
            CpButton("ПИНГ", Color(0xFF240B15), Cp.text) { vm.custom.host = h.ip; vm.custom.reset(); vm.selectTab(TAB_LOCAL) }
            Spacer(Modifier.width(6.dp))
            CpButton("ПОРТЫ", Color(0xFF240B15), Cp.text) { vm.diag.portHost = h.ip; vm.selectTab(TAB_DIAG) }
            if (8291 in h.ports || 8728 in h.ports) {
                Spacer(Modifier.width(6.dp))
                CpButton("MIKROTIK", Color(0xFF240B15), Cp.text) { vm.mt.host = h.ip; vm.selectTab(TAB_MIKROTIK) }
            }
        }
    }
}

/* ============================ [04] Wi-Fi эфир ============================ */

private val apColors = listOf(Cp.cyan, Cp.yellow, Cp.green, Color(0xFFFF6BD6), Color(0xFFFF8A00), Color(0xFF8C7BFF), Color(0xFF6CFFE0), Color(0xFFFF4D6D))

@Composable
fun WifiTab(vm: NetVm) {
    val w = vm.wifi
    LocationGate()
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CardTitle("АНАЛИЗАТОР WI-FI ЭФИРА") }
            CpButton("[ 🔄 СКАН ]", Cp.cyan) { w.scan() }
        }
        T("Сетей: ${w.aps.size}" + (w.lastScan?.let { " · обновлено ${hhmmss.format(Date(it))}" } ?: "") + " · автообновление 30 с", Cp.muted, 10.sp)
        if (w.throttled) T("Android ограничивает сканирование (~4 раза за 2 мин). Показаны последние данные.", Cp.yellow, 10.sp)
        Row(Modifier.padding(vertical = 8.dp)) {
            Chip("2.4 ГГц", w.band == 24) { w.band = 24 }; Chip("5 ГГц", w.band == 5) { w.band = 5 }; Chip("6 ГГц", w.band == 6) { w.band = 6 }
        }
        val inBand = w.aps.filter { it.band == w.band }
        ChannelChart(inBand, w.band)
        val my = w.aps.firstOrNull { it.connected && it.band == w.band }?.channel
        WifiScan.advice(w.aps, w.band, my)?.let { a ->
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().border(1.dp, Cp.green).padding(10.dp)) {
                Column {
                    T("РЕКОМЕНДУЕМЫЙ КАНАЛ: ${a.best}", Cp.green, 14.sp, bold = true)
                    a.current?.let { T("Ваш канал: $it", Cp.text, 11.sp) }
                    T(a.note, Cp.muted, 10.sp)
                    if (w.band == 5) T("Учитываются каналы без DFS (36–48, 149–165) — на них нет пауз из-за радаров.", Cp.muted, 9.sp)
                }
            }
        }
    }
    val inBand = w.aps.filter { it.band == w.band }
    inBand.forEachIndexed { i, a ->
        val col = apColors[i % apColors.size]
        Card(if (a.connected) Cp.green else Cp.dim) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(col))
                Spacer(Modifier.width(8.dp))
                T(a.ssid, if (a.connected) Cp.green else Cp.text, 13.sp, bold = true, modifier = Modifier.weight(1f), maxLines = 1)
                val c = when { a.dbm >= -60 -> Cp.green; a.dbm >= -75 -> Cp.yellow; else -> Cp.red }
                T("${a.dbm} dBm", c, 14.sp, bold = true)
            }
            T("Канал ${a.channel} · ${a.freq} МГц · ширина ${a.widthMhz} МГц · ${a.security}" + if (a.connected) " · ПОДКЛЮЧЕНЫ" else "", Cp.muted, 10.sp)
            T(a.bssid, Cp.muted, 9.sp)
        }
    }
    if (w.aps.isEmpty()) Hint("Нажмите «СКАН». Если список пуст — проверьте, что Wi-Fi включён и дан доступ к геолокации.")
}

@Composable
private fun ChannelChart(aps: List<Ap>, band: Int) {
    val tm = rememberTextMeasurer()
    val (fMin, fMax, marks) = when (band) {
        24 -> Triple(2400f, 2495f, (1..13).toList())
        5 -> Triple(5150f, 5895f, listOf(36, 44, 52, 60, 100, 108, 116, 124, 132, 140, 149, 157, 165))
        else -> Triple(5925f, 7125f, listOf(1, 33, 65, 97, 129, 161, 193, 225))
    }
    fun chFreq(ch: Int) = when (band) { 24 -> 2407 + ch * 5; 5 -> 5000 + ch * 5; else -> 5950 + ch * 5 }
    Canvas(Modifier.fillMaxWidth().height(200.dp).background(Cp.inner).border(1.dp, Cp.dim)) {
        val left = 30.dp.toPx(); val bottom = 20.dp.toPx(); val top = 8.dp.toPx()
        val h = size.height - bottom - top; val wdt = size.width - left - 6.dp.toPx()
        fun x(f: Float) = left + (f - fMin) / (fMax - fMin) * wdt
        fun y(dbm: Int) = top + h - ((dbm.coerceIn(-100, -20) + 100) / 80f) * h
        val lbl = TextStyle(fontFamily = Cp.mono, fontSize = 8.sp, color = Cp.muted)
        for (d in listOf(-90, -70, -50, -30)) {
            drawLine(Color(0xFF2B0C18), Offset(left, y(d)), Offset(size.width, y(d)))
            drawText(tm, "$d", Offset(2f, y(d) - 6.dp.toPx()), lbl)
        }
        for (ch in marks) {
            val xx = x(chFreq(ch).toFloat())
            drawLine(Color(0xFF14080E), Offset(xx, top), Offset(xx, top + h))
            val t = tm.measure("$ch", lbl); drawText(t, topLeft = Offset(xx - t.size.width / 2, top + h + 3.dp.toPx()))
        }
        aps.forEachIndexed { i, a ->
            val col = apColors[i % apColors.size]
            val x0 = x(a.centerFreq - a.widthMhz / 2f); val x1 = x(a.centerFreq + a.widthMhz / 2f)
            val yTop = y(a.dbm); val yb = top + h
            val p = Path().apply {
                moveTo(x0, yb)
                cubicTo(x0 + (x1 - x0) * 0.15f, yTop, x1 - (x1 - x0) * 0.15f, yTop, x1, yb)
            }
            drawPath(p, col.copy(alpha = 0.12f))
            drawPath(p, col, style = Stroke(if (a.connected) 4f else 2f))
            if (i < 8) {
                val t = tm.measure(a.ssid.take(12), TextStyle(fontFamily = Cp.mono, fontSize = 8.sp, color = col, fontWeight = FontWeight.Bold))
                val cx = ((x0 + x1) / 2 - t.size.width / 2).coerceIn(left, size.width - t.size.width)
                drawText(t, topLeft = Offset(cx, (yTop - t.size.height - 2f).coerceAtLeast(0f)))
            }
        }
    }
}

/* ============================ [05] обход объекта ============================ */

@Composable
fun SurveyTab(vm: NetVm) {
    val s = vm.survey
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    LocationGate()
    Card {
        CardTitle("ОБХОД ОБЪЕКТА С ЗАМЕРАМИ")
        Hint("Встаньте в точку (номер, коридор, ресепшн), введите название и нажмите «Замерить». Запишутся сигнал, точка доступа, пинг и потери.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Точка:", Cp.cyan, 11.sp, bold = true)
            Spacer(Modifier.width(8.dp))
            CpField(name, { name = it }, Modifier.weight(1f), color = Cp.yellow)
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(if (s.withSpeed) "✓ + скорость (4 с)" else "+ скорость (4 с)", s.withSpeed) { s.withSpeed = !s.withSpeed }
        }
        CpButton(if (s.busy) "⏳ Замер: ${s.stage}" else "[ ⚡ ЗАМЕРИТЬ ТОЧКУ ]", Cp.green, modifier = Modifier.fillMaxWidth(), enabled = !s.busy) {
            s.measure(name.trim()); name = ""
        }
        if (s.points.isNotEmpty()) Row(Modifier.padding(top = 8.dp)) {
            CpButton("📤 CSV / EXCEL", Cp.cyan, modifier = Modifier.weight(1f)) { Report.shareCsv(ctx, "NetWatch_обход", s.csv()) }
            Spacer(Modifier.width(6.dp))
            CpButton("📄 PDF", Cp.cyan, modifier = Modifier.weight(1f)) { Report.sharePdf(ctx, "NetWatch — обход объекта", s.text()) }
            Spacer(Modifier.width(6.dp))
            var confirm by remember { mutableStateOf(false) }
            CpButton(if (confirm) "ТОЧНО?" else "ОЧИСТИТЬ", if (confirm) Cp.red else Color(0xFF240B15), Cp.text) { if (confirm) { s.clear(); confirm = false } else confirm = true }
        }
    }
    if (s.points.isNotEmpty()) {
        val bad = s.points.count { it.bad }
        Card(if (bad > 0) Cp.red else Cp.green) {
            T("Точек: ${s.points.size} · проблемных: $bad", if (bad > 0) Cp.red else Cp.green, 13.sp, bold = true)
            val byAp = s.points.groupBy { it.bssid ?: "?" }
            T("Точек доступа встречено: ${byAp.size}", Cp.muted, 10.sp)
            Hint("Проблема = сигнал хуже −72 dBm, потери до роутера, пинг роутера > 30 мс или потери в интернет > 20%.")
        }
    }
    s.points.reversed().forEach { p ->
        Card(if (p.bad) Cp.red else Cp.dim) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(p.name, if (p.bad) Cp.red else Cp.text, 13.sp, bold = true, modifier = Modifier.weight(1f))
                T(hhmmss.format(Date(p.time)), Cp.muted, 9.sp)
                Spacer(Modifier.width(8.dp))
                T("✕", Cp.muted, 14.sp, modifier = Modifier.clickable { s.remove(p) })
            }
            val sigC = when { p.dbm == null -> Cp.muted; p.dbm >= -60 -> Cp.green; p.dbm >= -72 -> Cp.yellow; else -> Cp.red }
            Row {
                T("${p.dbm ?: "—"} dBm", sigC, 15.sp, bold = true)
                Spacer(Modifier.width(12.dp))
                T("роутер ${p.gwMs?.let { "%.0f".format(it) } ?: "—"} мс (${p.gwLoss}%)  ·  инет ${p.inetMs?.let { "%.0f".format(it) } ?: "—"} мс (${p.inetLoss}%)" +
                    (p.dlMbps?.let { "  ·  ↓%.1f".format(it) } ?: ""), Cp.cyan, 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
            T("${p.ssid ?: "—"} · ${p.bssid ?: ""}" + (p.freq?.let { " · кан. ${NetInfo.channel(it)}" } ?: ""), Cp.muted, 9.sp)
        }
    }
}

/* ============================ [06] диагностика ============================ */

@Composable
fun DiagTab(vm: NetVm) {
    val d = vm.diag
    Card {
        CardTitle("TRACEROUTE — ПУТЬ ДО УЗЛА")
        Hint("Показывает каждый узел по дороге в интернет: где растёт задержка или пропадает связь.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            CpField(d.traceHost, { d.traceHost = it }, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            CpButton(if (d.traceRunning) "⏹ СТОП" else "▶ СТАРТ", if (d.traceRunning) Cp.red else Cp.green) { d.trace() }
        }
        if (d.hops.isNotEmpty()) Spacer(Modifier.height(8.dp))
        d.hops.forEach { h ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                T("%2d".format(h.ttl), Cp.muted, 11.sp, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    T(h.ip ?: "* * *  (узел не отвечает)", if (h.ip == null) Cp.muted else if (h.final) Cp.green else Cp.text, 11.sp, bold = h.final)
                    h.name?.let { T(it, Cp.muted, 9.sp, maxLines = 1) }
                }
                val c = when { h.ms == null -> Cp.muted; h.ms < 30 -> Cp.green; h.ms < 100 -> Cp.yellow; else -> Cp.red }
                T(h.ms?.let { (if (h.final) "" else "≈") + "%.0f мс".format(it) } ?: "", c, 11.sp, bold = true)
            }
        }
        if (d.traceRunning) T("… трассировка", Cp.cyan, 10.sp)
        d.traceMsg?.let { T(it, Cp.yellow, 10.sp) }
    }

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CardTitle("СКОРОСТЬ DNS-СЕРВЕРОВ") }
            CpButton(if (d.dnsRunning) "⏳ ТЕСТ…" else "▶ ТЕСТ", Cp.green, enabled = !d.dnsRunning) { d.dnsBench() }
        }
        Hint("Медленный DNS — частая причина «сайты долго открываются». Сравниваем DNS провайдера с публичными.")
        val best = d.dns.firstOrNull { it.avgMs != null }
        d.dns.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(r.label + if (r == best) "  ★ быстрее всех" else "", if (r == best) Cp.green else Cp.text, 11.sp, bold = true)
                    T(r.server, Cp.muted, 9.sp)
                }
                val c = when { r.avgMs == null -> Cp.red; r.avgMs < 30 -> Cp.green; r.avgMs < 80 -> Cp.yellow; else -> Cp.red }
                Column(horizontalAlignment = Alignment.End) {
                    T(r.avgMs?.let { "%.0f мс".format(it) } ?: "нет ответа", c, 13.sp, bold = true)
                    T("${r.ok}/${r.total} ответов", Cp.muted, 9.sp)
                }
            }
        }
    }

    Card {
        CardTitle("СКАНЕР ПОРТОВ")
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Узел:", Cp.cyan, 11.sp, bold = true); Spacer(Modifier.width(8.dp))
            CpField(d.portHost, { d.portHost = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Порты:", Cp.cyan, 11.sp, bold = true); Spacer(Modifier.width(8.dp))
            CpField(d.portSpec, { d.portSpec = it }, Modifier.weight(1f), color = Cp.yellow)
        }
        Row(Modifier.padding(vertical = 6.dp)) {
            Chip("популярные", d.portSpec.startsWith("поп")) { d.portSpec = "популярные" }
            Chip("1-1024", d.portSpec == "1-1024") { d.portSpec = "1-1024" }
            Chip("1-65535", d.portSpec == "1-65535") { d.portSpec = "1-65535" }
        }
        CpButton(if (d.portRunning) "[ ⏹ ОСТАНОВИТЬ ]" else "[ ▶ СКАНИРОВАТЬ ПОРТЫ ]", if (d.portRunning) Cp.red else Cp.green, modifier = Modifier.fillMaxWidth()) { d.ports() }
        if (d.portRunning || d.portTotal > 0) { Progress(d.portDone, d.portTotal, Cp.yellow); T("${d.portDone}/${d.portTotal} · открыто ${d.openPorts.size}", Cp.yellow, 10.sp) }
        d.portMsg?.let { T(it, Cp.muted, 10.sp) }
        d.openPorts.forEach { (p, ms) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                T("%5d".format(p), Cp.green, 12.sp, bold = true, modifier = Modifier.width(56.dp))
                T(PortInfo.label(p).ifEmpty { "неизвестная служба" }, Cp.text, 11.sp, modifier = Modifier.weight(1f))
                T("%.0f мс".format(ms), Cp.muted, 9.sp)
            }
        }
    }
}

/* ============================ Wake-on-LAN (в вкладке «Локальная сеть») ============================ */

@Composable
fun WolCard(vm: NetVm) {
    val w = vm.wol
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    Card {
        CardTitle("WAKE-ON-LAN — ВКЛЮЧИТЬ КОМПЬЮТЕР ПО СЕТИ")
        Hint("На компьютере должен быть включён Wake-on-LAN в BIOS и в настройках сетевой карты.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            CpField(name, { name = it }, Modifier.weight(1f), color = Cp.yellow); Spacer(Modifier.width(6.dp))
            CpField(mac, { mac = it }, Modifier.weight(1.3f))
        }
        T("название · MAC (AA:BB:CC:DD:EE:FF)", Cp.muted, 9.sp)
        Row(Modifier.padding(top = 6.dp)) {
            CpButton("⚡ РАЗБУДИТЬ", Cp.green, modifier = Modifier.weight(1f)) { w.wake(mac) }
            Spacer(Modifier.width(6.dp))
            CpButton("＋ СОХРАНИТЬ", Color(0xFF240B15), Cp.text, modifier = Modifier.weight(1f)) { if (w.add(name.trim(), mac)) { name = ""; mac = "" } }
        }
        w.status?.let { T(it, if (it.startsWith("✓")) Cp.green else Cp.red, 10.sp, modifier = Modifier.padding(top = 4.dp)) }
        w.devices.forEach { dev ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T(dev.name, Cp.text, 11.sp, bold = true); T(dev.mac, Cp.muted, 9.sp) }
                CpButton("⚡", Cp.green) { w.wake(dev.mac) }
                Spacer(Modifier.width(6.dp))
                T("✕", Cp.muted, 14.sp, modifier = Modifier.clickable { w.remove(dev) }.padding(6.dp))
            }
        }
    }
}

/* ============================ [08] мониторинг ============================ */

@Composable
fun MonitorTab(vm: NetVm) {
    val ctx = LocalContext.current
    val m by Monitor.state.collectAsState()
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Monitor.start(ctx, vm.gw.host.trim(), vm.inet.host.trim()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(m.running) { while (m.running) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }

    Card {
        CardTitle("ФОНОВЫЙ МОНИТОРИНГ СВЯЗИ")
        Hint("Пингует роутер (${vm.gw.host}) и интернет (${vm.inet.host}) каждые 2 секунды, даже когда приложение свёрнуто. При обрыве и восстановлении — уведомление.")
        if (m.running) CpButton("[ ⏹ ОСТАНОВИТЬ МОНИТОРИНГ ]", Cp.red, modifier = Modifier.fillMaxWidth()) { Monitor.stop(ctx) }
        else CpButton("[ ▶ ЗАПУСТИТЬ МОНИТОРИНГ ]", Cp.green, modifier = Modifier.fillMaxWidth()) {
            if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) else Monitor.start(ctx, vm.gw.host.trim(), vm.inet.host.trim())
        }
        if (m.running) T("● идёт ${Monitor.fmtDur(now - m.startedAt)}", Cp.green, 11.sp, bold = true, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(4.dp))
        CpButton("⚙ Разрешить работу в фоне (энергосбережение)", Color(0xFF240B15), Cp.text, modifier = Modifier.fillMaxWidth()) {
            try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
        }
        Hint("На Xiaomi, Huawei, Samsung система может усыплять фоновые приложения — добавьте NetWatch в исключения энергосбережения.")
    }
    if (m.startedAt > 0) {
        for ((title, st, hist, line) in listOf(
            Quad("РОУТЕР", m.gw, m.gwHist, Cp.green), Quad("ИНТЕРНЕТ", m.inet, m.inetHist, Cp.cyan))) {
            Card(if (st.down) Cp.red else Cp.border) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("/// $title  ${st.host}", Cp.yellow, 12.sp, bold = true, modifier = Modifier.weight(1f))
                    T(if (st.down) "✖ НЕТ СВЯЗИ" else "● НА СВЯЗИ", if (st.down) Cp.red else Cp.green, 10.sp, bold = true)
                }
                Row(Modifier.padding(vertical = 6.dp)) {
                    MiniStat("ПОТЕРИ", "%.1f%%".format(st.lossPct), if (st.lossPct < 1) Cp.green else if (st.lossPct < 5) Cp.yellow else Cp.red, Modifier.weight(1f))
                    MiniStat("СРЕДНИЙ", "%.0f мс".format(st.avg), line, Modifier.weight(1f))
                    MiniStat("ОБРЫВОВ", "${st.outages}", if (st.outages == 0) Cp.green else Cp.red, Modifier.weight(1f))
                    MiniStat("БЕЗ СВЯЗИ", Monitor.fmtDur(st.currentDownMs(now)), if (st.downTotalMs == 0L && !st.down) Cp.green else Cp.red, Modifier.weight(1f))
                }
                T("Отправлено ${st.sent}, потеряно ${st.lost}, максимум %.0f мс".format(st.maxMs), Cp.muted, 9.sp)
                Sparkline(hist.takeLast(80).ifEmpty { listOf(0f, 0f) }, line, "мс")
            }
        }
        Card {
            CardTitle("ЖУРНАЛ СОБЫТИЙ")
            if (m.events.isEmpty()) Hint("Пока без происшествий")
            m.events.take(60).forEach { e ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    T(hhmmss.format(Date(e.time)), Cp.muted, 10.sp, modifier = Modifier.width(66.dp))
                    T(e.text, if (e.bad) Cp.red else Cp.green, 10.sp)
                }
            }
        }
    }
}

private data class Quad(val a: String, val b: TargetStats, val c: List<Float>, val d: Color)

@Composable
private fun MiniStat(t: String, v: String, c: Color, m: Modifier) = Column(m) { T(t, Cp.muted, 8.sp, bold = true); T(v, c, 13.sp, bold = true, maxLines = 1) }

/* ============================ [09] MikroTik ============================ */

@Composable
fun MikrotikTab(vm: NetVm) {
    val t = vm.mt
    if (!t.connected) { Card {
        CardTitle("ПОДКЛЮЧЕНИЕ К MIKROTIK (ROUTEROS API)")
        Hint("На роутере должен быть включён сервис api: IP → Services → api (порт 8728). Лучше создать отдельного пользователя с группой read.")
        LabeledField("Адрес", t.host, { t.host = it }, vm.gw.host)
        LabeledField("Порт", t.port, { t.port = it })
        LabeledField("Логин", t.user, { t.user = it })
        LabeledField("Пароль", t.pass, { t.pass = it }, password = true)
        Row(Modifier.padding(vertical = 6.dp)) {
            Chip(if (t.tls) "✓ API-SSL (8729)" else "API-SSL (8729)", t.tls) { t.tls = !t.tls; t.port = if (t.tls) "8729" else "8728" }
            Chip(if (t.remember) "✓ запомнить пароль" else "запомнить пароль", t.remember) { t.remember = !t.remember }
        }
        CpButton(if (t.connecting) "⏳ ПОДКЛЮЧЕНИЕ…" else "[ ▶ ПОДКЛЮЧИТЬСЯ ]", Cp.green, modifier = Modifier.fillMaxWidth(), enabled = !t.connecting) { t.connect(vm.gw.host) }
        t.error?.let { T("⚠ $it", Cp.red, 11.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (t.remember) Hint("Пароль хранится только на этом телефоне, в закрытой папке приложения.")
    }; return }
    val i = t.info
    Card(Cp.green) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T(i.identity.ifEmpty { t.host }, Cp.green, 16.sp, bold = true)
                T("${i.board} · RouterOS ${i.version}", Cp.text, 10.sp)
                T("Аптайм ${i.uptime}" + (i.temperature?.let { " · $it °C" } ?: ""), Cp.muted, 10.sp)
            }
            CpButton("ОТКЛ.", Cp.red) { t.disconnect() }
        }
        Row(Modifier.padding(top = 6.dp)) {
            MiniStat("CPU", "${i.cpu ?: "—"}%", if ((i.cpu ?: 0) < 60) Cp.green else Cp.red, Modifier.weight(1f))
            MiniStat("ПАМЯТЬ", "${i.memUsedPct ?: "—"}%", if ((i.memUsedPct ?: 0) < 80) Cp.green else Cp.red, Modifier.weight(1f))
            MiniStat("DHCP", "${t.leases.size}", Cp.cyan, Modifier.weight(1f))
            MiniStat("WI-FI", "${t.wifi.size}", Cp.cyan, Modifier.weight(1f))
        }
    }
    Card {
        CardTitle("ИНТЕРФЕЙСЫ (LIVE)")
        t.ifaces.filter { !it.disabled }.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                T(if (f.running) "●" else "○", if (f.running) Cp.green else Cp.muted, 11.sp, modifier = Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { T(f.name, Cp.text, 11.sp, bold = true, maxLines = 1); T(f.type, Cp.muted, 8.sp) }
                Column(horizontalAlignment = Alignment.End) {
                    T("↓ " + MtCtl.bps(f.rxBps), Cp.cyan, 10.sp, bold = true)
                    T("↑ " + MtCtl.bps(f.txBps), Cp.yellow, 10.sp, bold = true)
                }
            }
        }
    }
    if (t.caps.isNotEmpty()) Card {
        CardTitle("ТОЧКИ ДОСТУПА CAPsMAN: ${t.caps.size}")
        t.caps.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) { T(c.identity, Cp.text, 11.sp, bold = true); T("${c.address} · ${c.board}", Cp.muted, 9.sp) }
                T(c.state, if (c.state.contains("Run", true)) Cp.green else Cp.yellow, 10.sp, bold = true)
            }
        }
    }
    Card {
        CardTitle("КЛИЕНТЫ WI-FI: ${t.wifi.size}")
        T("Источник: ${t.wifiSrc}", Cp.muted, 9.sp)
        t.wifi.forEach { c ->
            val sig = c.signal.substringBefore('@').toIntOrNull()
            val col = when { sig == null -> Cp.muted; sig >= -65 -> Cp.green; sig >= -75 -> Cp.yellow; else -> Cp.red }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T(c.host.ifEmpty { c.mac }, Cp.text, 11.sp, bold = true, maxLines = 1)
                    T("${c.ip.ifEmpty { c.mac }} · ${c.iface} · ${c.uptime}", Cp.muted, 9.sp, maxLines = 1)
                }
                T(c.signal.ifEmpty { "—" }, col, 12.sp, bold = true)
            }
        }
    }
    Card {
        CardTitle("DHCP-АРЕНДЫ: ${t.leases.size}")
        t.leases.sortedBy { Ip.sortKey(it.ip) }.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                T(l.ip, Cp.cyan, 11.sp, bold = true, modifier = Modifier.width(118.dp))
                Column(Modifier.weight(1f)) {
                    T(l.host.ifEmpty { l.comment.ifEmpty { "—" } }, Cp.text, 10.sp, maxLines = 1)
                    T(l.mac + if (l.status.isNotEmpty()) " · ${l.status}" else "", Cp.muted, 9.sp)
                }
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit, hint: String = "", password: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, Cp.cyan, 11.sp, bold = true, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f)) {
            if (password) PasswordField(value, onChange) else CpField(value, onChange, Modifier.fillMaxWidth())
            if (value.isEmpty() && hint.isNotEmpty()) T(hint, Cp.muted, 12.sp, modifier = Modifier.padding(start = 9.dp, top = 8.dp))
        }
    }
}

/* ============================ отчёт ============================ */

@Composable
fun ReportDialog(vm: NetVm, onClose: () -> Unit) {
    val ctx = LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Column(Modifier.background(Cp.card).border(1.dp, Cp.red).padding(16.dp)) {
            T("/// ЭКСПОРТ ОТЧЁТА", Cp.yellow, 13.sp, bold = true)
            Hint("В отчёт попадут: сеть и интерфейсы, сигнал, пинги, замеры скорости, найденные устройства, Wi-Fi эфир, обход, диагностика, мониторинг и MikroTik — всё, что вы уже замерили.")
            Spacer(Modifier.height(8.dp))
            CpButton("📄 PDF-ОТЧЁТ", Cp.green, modifier = Modifier.fillMaxWidth()) { Report.sharePdf(ctx, "NetWatch — отчёт о диагностике сети", vm.fullReport()); onClose() }
            Spacer(Modifier.height(6.dp))
            CpButton("📝 ТЕКСТ (TXT)", Cp.cyan, modifier = Modifier.fillMaxWidth()) { Report.shareText(ctx, "NetWatch — отчёт о диагностике сети", vm.fullReport()); onClose() }
            Spacer(Modifier.height(6.dp))
            CpButton("ЗАКРЫТЬ", Color(0xFF240B15), Cp.text, modifier = Modifier.fillMaxWidth()) { onClose() }
        }
    }
}

@Suppress("unused")
@Composable
fun SelectableBlock(text: String) = Box(Modifier.fillMaxWidth().background(Cp.inner).border(1.dp, Cp.dim).horizontalScroll(rememberScrollState()).padding(10.dp)) {
    SelectionContainer { T(text, Cp.cyan, 11.sp) }
}
