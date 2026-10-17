package com.l1ttlehe11.netwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val hmFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

/* ============================ [01] Главная — сводка всего приложения ============================ */

@Composable
fun HomeTab(vm: NetVm) {
    val r = vm.radio
    val hist by History.items.collectAsState()
    val mon by Monitor.state.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (vm.upd.showBanner) UpdateBanner(vm)
        // --- сигнал ---
        Tile(border = Cp.red, onClick = { vm.selectTab(TAB_WIFI) }) {
            val cell = r.status == RadioStatus.CELLULAR
            val good = if (cell) -85 else -60; val ok = if (cell) -100 else -75
            val d = r.dbm
            val c = when { d == null -> Cp.muted; d >= good -> Cp.green; d >= ok -> Cp.yellow; else -> Cp.red }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(when (r.status) {
                    RadioStatus.WIFI_OK -> "Wi-Fi · ${r.ssid}"
                    RadioStatus.WIFI_NO_PERMISSION, RadioStatus.WIFI_LOCATION_OFF -> tr(R.string.wi_fi_name_hidden_by_the)
                    RadioStatus.CELLULAR -> tr(R.string.mobile_3, r.operator ?: "—")
                    RadioStatus.ETHERNET -> "Ethernet"; RadioStatus.OFFLINE -> tr(R.string.not_connected)
                }, Cp.muted2, 12.sp, bold = true, maxLines = 1, modifier = Modifier.weight(1f))
                T(when { d == null -> ""; d >= good -> tr(R.string.excellent); d >= ok -> tr(R.string.fair); else -> tr(R.string.weak) }, c, 11.sp, bold = true)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(d?.toString() ?: "N/A", c, 36.sp, bold = true)
                T(if (d != null) "dBm" + (r.quality?.let { " · ${it}%" } ?: "") else "", Cp.muted2, 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            listOfNotNull(r.freqMhz?.let { tr(R.string.channel_2, NetInfo.band(it), NetInfo.channel(it)) }, r.linkMbps?.let { tr(R.string.link_mbit_s_2, it) })
                .takeIf { it.isNotEmpty() }?.let { T(it.joinToString(" · "), Cp.muted2, 11.sp) }
            if (r.status == RadioStatus.WIFI_NO_PERMISSION || r.status == RadioStatus.WIFI_LOCATION_OFF) T(tr(R.string.tap_to_allow_showing_the_network), Cp.yellow, 11.sp)
            Sparkline(vm.radioHist, Cp.cyan, "dB", isDbm = true, height = 52)
        }

        // --- пинги ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PingTile(tr(R.string.router), vm.gw, 15.0, 50.0, Cp.green, Modifier.weight(1f)) { vm.selectTab(TAB_SETTINGS) }
            PingTile(tr(R.string.internet_2), vm.inet, 60.0, 150.0, Cp.cyan, Modifier.weight(1f)) { vm.selectTab(TAB_SETTINGS) }
        }

        // --- быстрые действия ---
        SectionLabel(tr(R.string.quick_actions))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Ic.search, Cp.green, tr(R.string.scanner), Modifier.weight(1f)) { vm.selectTab(TAB_SCAN); if (!vm.scan.running) vm.scan.start() }
            QuickAction(Ic.speed, Cp.cyan, tr(R.string.speed_2), Modifier.weight(1f)) { vm.selectTab(TAB_SPEED); vm.startTest() }
            QuickAction(Ic.pulse, Cp.red, tr(R.string.monitor_2), Modifier.weight(1f)) { vm.selectTab(TAB_MONITOR) }
        }

        // --- последний замер скорости + неделя ---
        val speeds = hist.filter { it.kind == HKind.SPEED }
        val last = speeds.firstOrNull()
        Tile(onClick = { vm.selectTab(TAB_SPEED) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(tr(R.string.last_test), Cp.yellow, 12.sp, bold = true, modifier = Modifier.weight(1f))
                T(last?.let { History.dayLabel(it.time).lowercase() + " " + hmFmt.format(Date(it.time)) } ?: "", Cp.muted2, 11.sp)
            }
            if (last == null) T(tr(R.string.no_tests_yet_tap_speed_above), Cp.muted2, 12.sp)
            else Row {
                MiniNum(tr(R.string.mbit_s_3), last.num1?.let { "%.1f".format(it) } ?: "—", Cp.cyan, Modifier.weight(1f))
                MiniNum(tr(R.string.mbit_s_2), last.num2?.let { "%.1f".format(it) } ?: "—", Cp.yellow, Modifier.weight(1f))
                MiniNum(tr(R.string.ping_ms), last.num3?.let { "%.0f".format(it) } ?: "—", Cp.green, Modifier.weight(1f))
            }
            weekSummary(speeds)?.let { T(it, Cp.muted2, 11.sp) }
        }

        // --- устройства и мониторинг ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val sc = vm.scan
            val fresh = sc.hosts.count { sc.isNew(it) }
            Tile(Modifier.weight(1f), onClick = { vm.selectTab(TAB_SCAN) }) {
                T(tr(R.string.devices_2), Cp.muted2, 11.sp, bold = true)
                T(if (sc.hosts.isEmpty()) "—" else "${sc.hosts.size}", Cp.text, 22.sp, bold = true)
                T(when { sc.running -> tr(R.string.scanning_2); sc.hosts.isEmpty() -> tr(R.string.tap_to_scan); fresh > 0 -> tr(R.string.new_4, fresh); else -> tr(R.string.no_new_ones) },
                    if (fresh > 0) Cp.yellow else Cp.muted2, 11.sp, bold = fresh > 0, maxLines = 1)
            }
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(mon.running) { while (mon.running) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
            Tile(Modifier.weight(1f), border = if (mon.running) Cp.green else Cp.dim, onClick = { vm.selectTab(TAB_MONITOR) }) {
                T(tr(R.string.monitor), Cp.muted2, 11.sp, bold = true)
                T(if (mon.running) Monitor.fmtDur(now - mon.startedAt) else tr(R.string.off), if (mon.running) Cp.green else Cp.muted2, 22.sp, bold = true, maxLines = 1)
                val outs = mon.gw.outages + mon.inet.outages
                T(if (mon.running) tr(R.string.outages_2, outs) else tr(R.string.button_at_the_top), if (mon.running) (if (outs == 0) Cp.green else Cp.red) else Cp.muted2, 11.sp, bold = mon.running, maxLines = 1)
            }
        }
    }
}

/** «за неделю: в среднем ↓ 88 · вечером медленнее на 30%» */
private fun weekSummary(speeds: List<HItem>): String? {
    val week = speeds.filter { it.time > System.currentTimeMillis() - 7 * 86_400_000L }
    val d = week.mapNotNull { it.num1 }
    if (d.size < 2) return null
    val avg = d.average()
    val parts = mutableListOf(tr(R.string.this_week_average_0f).format(avg))
    val byPart = week.groupBy { Calendar.getInstance().apply { timeInMillis = it.time }.get(Calendar.HOUR_OF_DAY) / 6 }
    if (byPart.size > 1) {
        val names = listOf(tr(R.string.at_night), tr(R.string.in_the_morning), tr(R.string.in_the_afternoon), tr(R.string.in_the_evening))
        val worst = byPart.minByOrNull { (_, v) -> v.mapNotNull { it.num1 }.average() }!!
        val drop = (1 - worst.value.mapNotNull { it.num1 }.average() / avg) * 100
        if (drop >= 10) parts += tr(R.string.slower_by_0f, names[worst.key]).format(drop)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun PingTile(title: String, c: PingCard, good: Double, mid: Double, okColor: Color, modifier: Modifier, onClick: () -> Unit) {
    val v = c.rtt
    val col = when { !c.started -> Cp.muted2; v == null -> Cp.red; v < good -> okColor; v < mid -> Cp.yellow; else -> Cp.red }
    Tile(modifier, onClick = onClick) {
        T(title, Cp.muted2, 11.sp, bold = true)
        Row(verticalAlignment = Alignment.Bottom) {
            T(if (!c.started) "--" else v?.let { if (it < 10) "%.1f".format(it) else "%.0f".format(it) } ?: "✖", col, 26.sp, bold = true)
            T(if (v != null) tr(R.string.ms) else "", Cp.muted2, 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        T(c.host, Cp.muted2, 11.sp, maxLines = 1)
        Sparkline(c.history.takeLast(24), col, "", height = 34)
    }
}

@Composable
private fun QuickAction(icon: String, tint: Color, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 72.dp).background(Cp.card).border(1.dp, Cp.dim).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NwIcon(icon, tint, 26.dp)
        T(label, Cp.text, 12.sp, bold = true, maxLines = 1)
    }
}

@Composable
private fun MiniNum(t: String, v: String, c: Color, m: Modifier) = Column(m) { T(t, Cp.muted2, 10.sp); T(v, c, 22.sp, bold = true, maxLines = 1) }

/* ============================ [05] Ещё ============================ */

@Composable
fun MoreMenu(vm: NetVm, onReport: () -> Unit) {
    val mon by Monitor.state.collectAsState()
    val hist by History.items.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(mon.running) { while (mon.running) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val outs = mon.gw.outages + mon.inet.outages
        MenuRow(Ic.pulse, Cp.red, tr(R.string.monitoring),
            if (mon.running) tr(R.string.running_outages, Monitor.fmtDur(now - mon.startedAt), outs) else if (mon.startedAt > 0) tr(R.string.stopped_outages, outs) else tr(R.string.off_turn_it_on_with_the),
            subColor = if (mon.running) Cp.green else Cp.muted2, border = if (mon.running) Cp.red else Cp.dim) { vm.selectTab(TAB_MONITOR) }
        val t = vm.mt
        MenuRow(Ic.router, Cp.cyan, "MikroTik",
            if (t.connected) listOfNotNull(t.info.board.ifBlank { t.info.identity }.ifBlank { null }, tr(R.string.wi_fi_clients, t.wifi.size), t.info.cpu?.let { "CPU ${it}%" }).joinToString(" · ")
            else if (t.host.isNotBlank()) tr(R.string.not_connected_2, t.host) else tr(R.string.connect_a_router_via_api),
            subColor = if (t.connected) Cp.green else Cp.muted2) { vm.selectTab(TAB_MIKROTIK) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallMenu(Ic.doc, Cp.cyan, tr(R.string.report), "PDF / TXT", Modifier.weight(1f)) { onReport() }
            SmallMenu(Ic.gear, if (vm.upd.hasUpdate) Cp.yellow else Cp.muted2, tr(R.string.settings),
                if (vm.upd.hasUpdate) tr(R.string.version, vm.upd.info?.versionName) else if (vm.upd.enabled) tr(R.string.updates_2) else tr(R.string.settings_sub), Modifier.weight(1f)) { vm.selectTab(TAB_SETTINGS) }
        }

        // --- История с превью последних событий ---
        Column(Modifier.fillMaxWidth().background(Cp.card).border(1.dp, Cp.yellow)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { vm.selectTab(TAB_HISTORY) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NwIcon(Ic.history, Cp.yellow, 26.dp)
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    T(tr(R.string.history), Cp.text, 15.sp, bold = true)
                    T(tr(R.string.records_kept, hist.size, if (History.retentionDays == 0) tr(R.string.forever) else tr(R.string.days, History.retentionDays)), Cp.muted2, 12.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2B0C18)))
            if (hist.isEmpty()) T(tr(R.string.empty_for_now_tests_scans_and), Cp.muted2, 12.sp, modifier = Modifier.padding(14.dp))
            else Column(
                Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp).fillMaxWidth().drawBehind {
                    // тонкая линия таймлайна через центры ромбов
                    val x = 4.5.dp.toPx()
                    drawLine(Cp.dim, Offset(x, 10.dp.toPx()), Offset(x, size.height - 24.dp.toPx()), 1.dp.toPx())
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                hist.take(3).forEach { e -> TimelineItem(e) { vm.selectTab(TAB_HISTORY) } }
            }
            Box(
                Modifier.padding(14.dp).fillMaxWidth().height(44.dp).clip(cutShape(22f)).border(1.dp, Cp.yellow, cutShape(22f))
                    .clickable { vm.selectTab(TAB_HISTORY) },
                contentAlignment = Alignment.Center,
            ) { T(tr(R.string.full_history), Cp.yellow, 13.sp, bold = true, fixed = true) }
        }
        T("NetWatch ${BuildConfigCompat.version(LocalContext.current)} · Created by L1TTLEHE11", Cp.muted, 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
    }
}

@Composable
private fun TimelineItem(e: HItem, onClick: () -> Unit) {
    val c = when (e.kind) { HKind.SPEED -> Cp.cyan; HKind.SCAN -> Cp.green; HKind.MONITOR -> Cp.red; HKind.SURVEY -> Cp.yellow; else -> Color(0xFFB98CFF) }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 5.dp).size(9.dp).rotate(45f).background(c))
        Column(Modifier.weight(1f)) {
            Row {
                T(e.kind.label, Cp.text, 12.sp, bold = true, modifier = Modifier.weight(1f))
                T(History.dayLabel(e.time).let { if (it == tr(R.string.today)) "" else "${it} ".lowercase() } + hmFmt.format(Date(e.time)), Cp.muted2, 11.sp)
            }
            val line = if (e.kind == HKind.MONITOR) e.title.removePrefix("● ").removePrefix(tr(R.string.monitoring_2)) + " · " + e.summary.substringBefore(tr(R.string.down))
                else e.title.removePrefix("● ")
            T(line, if (e.kind == HKind.SPEED) Cp.cyan else Cp.muted2, 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SmallMenu(icon: String, tint: Color, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 64.dp).background(Cp.card).border(1.dp, Cp.dim).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NwIcon(icon, tint, 24.dp)
        Column { T(title, Cp.text, 14.sp, bold = true, maxLines = 1); T(sub, Cp.muted2, 11.sp, maxLines = 1) }
    }
}

fun openUrl(ctx: android.content.Context, url: String) {
    try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
}

object BuildConfigCompat {
    fun version(ctx: android.content.Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
}

/* ============================ обновления ============================ */

private val dateFmt = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

@Composable
private fun UpdateBanner(vm: NetVm) {
    val u = vm.upd
    val i = u.info ?: return
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF1A1600)).border(1.dp, Cp.yellow).clickable { vm.selectTab(TAB_SETTINGS) }.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NwIcon(Ic.share, Cp.yellow, 24.dp)
        Column(Modifier.weight(1f)) {
            T(tr(R.string.version_is_available, i.versionName), Cp.yellow, 14.sp, bold = true)
            T(i.notes.firstOrNull() ?: tr(R.string.tap_to_update), Cp.muted2, 11.sp, maxLines = 1)
        }
        Box(Modifier.size(44.dp).clickable { u.dismiss() }, contentAlignment = Alignment.Center) { T("✕", Cp.muted2, 14.sp, fixed = true) }
    }
}

@Composable
fun UpdateTile(vm: NetVm) {
    val u = vm.upd
    val i = u.info
    Tile(border = if (u.hasUpdate) Cp.yellow else Cp.dim) {
        T(tr(R.string.updates), Cp.yellow, 12.sp, bold = true)
        T(tr(R.string.installed_version, u.currentName) + if (u.lastCheck > 0) tr(R.string.checked, dateFmt.format(Date(u.lastCheck))) else "", Cp.muted2, 11.sp)
        when (u.phase) {
            UpdPhase.CHECKING -> T(tr(R.string.checking), Cp.cyan, 12.sp, modifier = Modifier.padding(top = 6.dp))
            UpdPhase.UP_TO_DATE -> T(tr(R.string.you_have_the_latest_version), Cp.green, 12.sp, bold = true, modifier = Modifier.padding(top = 6.dp))
            UpdPhase.ERROR -> T("⚠ ${u.error ?: tr(R.string.error_2)}", Cp.red, 12.sp, modifier = Modifier.padding(top = 6.dp))
            else -> {}
        }
        if (i != null && u.hasUpdate) {
            Spacer(Modifier.height(6.dp))
            T(tr(R.string.new_version, i.versionName) + (if (i.date.isNotBlank()) " · ${i.date}" else "") + (if (i.size > 0) tr(R.string.s_1f_mb).format(i.size / 1048576.0) else ""), Cp.yellow, 14.sp, bold = true)
            i.notes.take(8).forEach { n -> T("• ${n}", Cp.text, 11.sp, modifier = Modifier.padding(top = 2.dp)) }
            Spacer(Modifier.height(8.dp))
            when (u.phase) {
                UpdPhase.DOWNLOADING -> {
                    Progress((u.progress * 1000).toInt(), 1000, Cp.yellow)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(tr(R.string.downloading_0f).format(u.progress * 100), Cp.yellow, 12.sp, bold = true, modifier = Modifier.weight(1f))
                        CpButton(tr(R.string.cancel), Color(0xFF240B15), Cp.text) { u.cancel() }
                    }
                }
                UpdPhase.READY -> {
                    CpButton(tr(R.string.install), Cp.yellow, modifier = Modifier.fillMaxWidth()) { u.install() }
                    if (u.needsPermission) T(tr(R.string.allow_netwatch_to_install_unknown_apps), Cp.muted2, 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                else -> CpButton(tr(R.string.download_and_install), Cp.yellow, modifier = Modifier.fillMaxWidth()) { u.download() }
            }
            T(tr(R.string.the_file_is_verified_with_a), Cp.muted, 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (u.phase != UpdPhase.DOWNLOADING) CpButton(tr(R.string.check), Color(0xFF240B15), Cp.text) { u.check() }
            Spacer(Modifier.width(8.dp))
            Chip(if (u.auto) tr(R.string.auto_check_2) else tr(R.string.auto_check), u.auto) { u.setAutoCheck(!u.auto) }
        }
    }
}

/* ============================ язык ============================ */

@Composable
private fun LanguageTile() {
    val ctx = LocalContext.current
    val chosen = remember { Lang.chosen(ctx) }
    Tile {
        T(tr(R.string.lang_title), Cp.yellow, 12.sp, bold = true)
        Row(Modifier.padding(top = 6.dp)) {
            // названия языков — каждое на своём языке, чтобы их узнал любой пользователь
            listOf("" to tr(R.string.lang_system), "en" to "English", "ru" to "Русский").forEach { (tag, name) ->  // i18n-ignore
                Chip(name, chosen == tag) { if (chosen != tag) (ctx as? android.app.Activity)?.let { Lang.set(it, tag) } }
            }
        }
        T(tr(R.string.lang_hint), Cp.muted2, 11.sp)
    }
}

/* ============================ Настройки ============================ */

@Composable
fun SettingsTab(vm: NetVm) {
    val ctx = LocalContext.current
    var gwText by remember { mutableStateOf(vm.gwManual ?: "") }
    var retention by remember { mutableIntStateOf(History.retentionDays) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LanguageTile()
        Tile {
            T(tr(R.string.font_size), Cp.yellow, 12.sp, bold = true)
            Row(Modifier.padding(top = 6.dp)) {
                Chip(tr(R.string.normal), !vm.fontLarge) { vm.fontLarge = false; vm.saveInputs() }
                Chip(tr(R.string.large), vm.fontLarge) { vm.fontLarge = true; vm.saveInputs() }
            }
            T(tr(R.string.large_makes_all_text_about_15), Cp.muted2, 11.sp)
        }
        if (vm.upd.enabled) UpdateTile(vm)
        Tile {
            T(tr(R.string.router_address), Cp.yellow, 12.sp, bold = true)
            T(tr(R.string.empty_detected_automatically_now, vm.gw.host), Cp.muted2, 11.sp)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CpField(gwText, { gwText = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CpButton("OK", Cp.green) { vm.gwManual = gwText.trim().ifBlank { null }; vm.saveInputs(); vm.autoGateway() }
            }
        }
        Tile {
            T(tr(R.string.internet_check), Cp.yellow, 12.sp, bold = true)
            T(tr(R.string.the_host_pinged_on_home_and), Cp.muted2, 11.sp)
            CpField(vm.inet.host, { vm.inet.host = it; vm.inet.reset() }, Modifier.fillMaxWidth().padding(top = 6.dp))
            Row(Modifier.padding(top = 6.dp)) {
                listOf("8.8.8.8", "1.1.1.1", "77.88.8.8", "ya.ru").forEach { h -> Chip(h, vm.inet.host == h) { vm.inet.host = h; vm.inet.reset(); vm.saveInputs() } }
            }
        }
        Tile {
            T(tr(R.string.history_storage), Cp.yellow, 12.sp, bold = true)
            Row(Modifier.padding(top = 6.dp)) {
                listOf(7 to tr(R.string.s_7_d), 30 to tr(R.string.s_30_d), 90 to tr(R.string.s_90_d), 0 to tr(R.string.forever)).forEach { (d, l) -> Chip(l, retention == d) { History.setRetention(ctx, d); retention = d } }
            }
        }
        Tile {
            T(tr(R.string.about), Cp.yellow, 12.sp, bold = true)
            T("NetWatch: Network Scanner ${BuildConfigCompat.version(ctx)}", Cp.text, 12.sp)
            T("Created by L1TTLEHE11", Cp.muted2, 11.sp)
            T(tr(R.string.free_software_under_the_gnu_gpl), Cp.muted2, 11.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 6.dp)) {
                CpButton(tr(R.string.source_code), Color(0xFF240B15), Cp.cyan) { openUrl(ctx, "https://github.com/L1TTLEHE11/NetWatch") }
                Spacer(Modifier.width(8.dp))
                CpButton(tr(R.string.report_a_bug), Color(0xFF240B15), Cp.text) { openUrl(ctx, "https://github.com/L1TTLEHE11/NetWatch/issues") }
            }
        }
    }
}
