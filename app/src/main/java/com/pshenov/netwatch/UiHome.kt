package com.pshenov.netwatch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                    RadioStatus.WIFI_NO_PERMISSION, RadioStatus.WIFI_LOCATION_OFF -> "Wi-Fi · имя скрыто системой"
                    RadioStatus.CELLULAR -> "Мобильная · ${r.operator ?: "—"}"
                    RadioStatus.ETHERNET -> "Ethernet"; RadioStatus.OFFLINE -> "Нет подключения"
                }, Cp.muted2, 12.sp, bold = true, maxLines = 1, modifier = Modifier.weight(1f))
                T(when { d == null -> ""; d >= good -> "ОТЛИЧНО"; d >= ok -> "НОРМАЛЬНО"; else -> "СЛАБЫЙ" }, c, 11.sp, bold = true)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(d?.toString() ?: "N/A", c, 36.sp, bold = true)
                T(if (d != null) "dBm" + (r.quality?.let { " · $it%" } ?: "") else "", Cp.muted2, 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            listOfNotNull(r.freqMhz?.let { "${NetInfo.band(it)} · канал ${NetInfo.channel(it)}" }, r.linkMbps?.let { "линк $it Мбит/с" })
                .takeIf { it.isNotEmpty() }?.let { T(it.joinToString(" · "), Cp.muted2, 11.sp) }
            if (r.status == RadioStatus.WIFI_NO_PERMISSION || r.status == RadioStatus.WIFI_LOCATION_OFF) T("Нажмите, чтобы разрешить показ имени сети", Cp.yellow, 11.sp)
            Sparkline(vm.radioHist, Cp.cyan, "dB", isDbm = true, height = 52)
        }

        // --- пинги ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PingTile("РОУТЕР", vm.gw, 15.0, 50.0, Cp.green, Modifier.weight(1f)) { vm.selectTab(TAB_SETTINGS) }
            PingTile("ИНТЕРНЕТ", vm.inet, 60.0, 150.0, Cp.cyan, Modifier.weight(1f)) { vm.selectTab(TAB_SETTINGS) }
        }

        // --- быстрые действия ---
        SectionLabel("БЫСТРЫЕ ДЕЙСТВИЯ")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Ic.search, Cp.green, "Сканер", Modifier.weight(1f)) { vm.selectTab(TAB_SCAN); if (!vm.scan.running) vm.scan.start() }
            QuickAction(Ic.speed, Cp.cyan, "Скорость", Modifier.weight(1f)) { vm.selectTab(TAB_SPEED); vm.startTest() }
            QuickAction(Ic.pulse, Cp.red, "Монитор", Modifier.weight(1f)) { vm.selectTab(TAB_MONITOR) }
        }

        // --- последний замер скорости + неделя ---
        val speeds = hist.filter { it.kind == HKind.SPEED }
        val last = speeds.firstOrNull()
        Tile(onClick = { vm.selectTab(TAB_SPEED) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("/// ПОСЛЕДНИЙ ЗАМЕР", Cp.yellow, 12.sp, bold = true, modifier = Modifier.weight(1f))
                T(last?.let { History.dayLabel(it.time).lowercase() + " " + hmFmt.format(Date(it.time)) } ?: "", Cp.muted2, 11.sp)
            }
            if (last == null) T("Замеров ещё не было — нажмите «Скорость» выше", Cp.muted2, 12.sp)
            else Row {
                MiniNum("↓ МБИТ/С", last.num1?.let { "%.1f".format(it) } ?: "—", Cp.cyan, Modifier.weight(1f))
                MiniNum("↑ МБИТ/С", last.num2?.let { "%.1f".format(it) } ?: "—", Cp.yellow, Modifier.weight(1f))
                MiniNum("ПИНГ, МС", last.num3?.let { "%.0f".format(it) } ?: "—", Cp.green, Modifier.weight(1f))
            }
            weekSummary(speeds)?.let { T(it, Cp.muted2, 11.sp) }
        }

        // --- устройства и мониторинг ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val sc = vm.scan
            val fresh = sc.hosts.count { sc.isNew(it) }
            Tile(Modifier.weight(1f), onClick = { vm.selectTab(TAB_SCAN) }) {
                T("УСТРОЙСТВА", Cp.muted2, 11.sp, bold = true)
                T(if (sc.hosts.isEmpty()) "—" else "${sc.hosts.size}", Cp.text, 22.sp, bold = true)
                T(when { sc.running -> "идёт скан…"; sc.hosts.isEmpty() -> "нажмите для скана"; fresh > 0 -> "★ $fresh новое"; else -> "новых нет" },
                    if (fresh > 0) Cp.yellow else Cp.muted2, 11.sp, bold = fresh > 0, maxLines = 1)
            }
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(mon.running) { while (mon.running) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
            Tile(Modifier.weight(1f), border = if (mon.running) Cp.green else Cp.dim, onClick = { vm.selectTab(TAB_MONITOR) }) {
                T("МОНИТОР", Cp.muted2, 11.sp, bold = true)
                T(if (mon.running) Monitor.fmtDur(now - mon.startedAt) else "выкл.", if (mon.running) Cp.green else Cp.muted2, 22.sp, bold = true, maxLines = 1)
                val outs = mon.gw.outages + mon.inet.outages
                T(if (mon.running) "● $outs обрывов" else "кнопка в шапке", if (mon.running) (if (outs == 0) Cp.green else Cp.red) else Cp.muted2, 11.sp, bold = mon.running, maxLines = 1)
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
    val parts = mutableListOf("за неделю: в среднем ↓ %.0f".format(avg))
    val byPart = week.groupBy { Calendar.getInstance().apply { timeInMillis = it.time }.get(Calendar.HOUR_OF_DAY) / 6 }
    if (byPart.size > 1) {
        val names = listOf("ночью", "утром", "днём", "вечером")
        val worst = byPart.minByOrNull { (_, v) -> v.mapNotNull { it.num1 }.average() }!!
        val drop = (1 - worst.value.mapNotNull { it.num1 }.average() / avg) * 100
        if (drop >= 10) parts += "${names[worst.key]} медленнее на %.0f%%".format(drop)
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
            T(if (v != null) " мс" else "", Cp.muted2, 12.sp, modifier = Modifier.padding(bottom = 4.dp))
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
        MenuRow(Ic.pulse, Cp.red, "Мониторинг",
            if (mon.running) "● идёт ${Monitor.fmtDur(now - mon.startedAt)} · обрывов $outs" else if (mon.startedAt > 0) "остановлен · обрывов было $outs" else "выключен — включается кнопкой в шапке",
            subColor = if (mon.running) Cp.green else Cp.muted2, border = if (mon.running) Cp.red else Cp.dim) { vm.selectTab(TAB_MONITOR) }
        val t = vm.mt
        MenuRow(Ic.router, Cp.cyan, "MikroTik",
            if (t.connected) listOfNotNull(t.info.board.ifBlank { t.info.identity }.ifBlank { null }, "${t.wifi.size} клиентов Wi-Fi", t.info.cpu?.let { "CPU $it%" }).joinToString(" · ")
            else if (t.host.isNotBlank()) "не подключён · ${t.host}" else "подключить роутер по API",
            subColor = if (t.connected) Cp.green else Cp.muted2) { vm.selectTab(TAB_MIKROTIK) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallMenu(Ic.doc, Cp.cyan, "Отчёт", "PDF / TXT", Modifier.weight(1f)) { onReport() }
            SmallMenu(Ic.gear, if (vm.upd.hasUpdate) Cp.yellow else Cp.muted2, "Настройки",
                if (vm.upd.hasUpdate) "⬆ версия ${vm.upd.info?.versionName}" else "шрифт · обновления", Modifier.weight(1f)) { vm.selectTab(TAB_SETTINGS) }
        }

        // --- История с превью последних событий ---
        Column(Modifier.fillMaxWidth().background(Cp.card).border(1.dp, Cp.yellow)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { vm.selectTab(TAB_HISTORY) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NwIcon(Ic.history, Cp.yellow, 26.dp)
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    T("История", Cp.text, 15.sp, bold = true)
                    T("${hist.size} записей · хранится ${if (History.retentionDays == 0) "всегда" else "${History.retentionDays} дней"}", Cp.muted2, 12.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2B0C18)))
            if (hist.isEmpty()) T("Пока пусто: замеры, сканы и сессии мониторинга появятся здесь сами.", Cp.muted2, 12.sp, modifier = Modifier.padding(14.dp))
            else Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                Box(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 14.dp).width(1.dp).matchParentSize().background(Cp.dim).align(Alignment.TopStart))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    hist.take(3).forEach { e -> TimelineItem(e) { vm.selectTab(TAB_HISTORY) } }
                }
            }
            Box(
                Modifier.padding(14.dp).fillMaxWidth().height(44.dp).clip(cutShape(22f)).border(1.dp, Cp.yellow, cutShape(22f))
                    .clickable { vm.selectTab(TAB_HISTORY) },
                contentAlignment = Alignment.Center,
            ) { T("ВСЯ ИСТОРИЯ →", Cp.yellow, 13.sp, bold = true, fixed = true) }
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
                T(History.dayLabel(e.time).let { if (it == "Сегодня") "" else "$it ".lowercase() } + hmFmt.format(Date(e.time)), Cp.muted2, 11.sp)
            }
            T(e.title.removePrefix("● "), if (e.kind == HKind.SPEED) Cp.cyan else Cp.muted2, 12.sp, maxLines = 1)
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
            T("Доступна версия ${i.versionName}", Cp.yellow, 14.sp, bold = true)
            T(i.notes.firstOrNull() ?: "Нажмите, чтобы обновить", Cp.muted2, 11.sp, maxLines = 1)
        }
        Box(Modifier.size(44.dp).clickable { u.dismiss() }, contentAlignment = Alignment.Center) { T("✕", Cp.muted2, 14.sp, fixed = true) }
    }
}

@Composable
fun UpdateTile(vm: NetVm) {
    val u = vm.upd
    val i = u.info
    Tile(border = if (u.hasUpdate) Cp.yellow else Cp.dim) {
        T("ОБНОВЛЕНИЯ", Cp.yellow, 12.sp, bold = true)
        T("Установлена версия ${u.currentName}" + if (u.lastCheck > 0) " · проверено ${dateFmt.format(Date(u.lastCheck))}" else "", Cp.muted2, 11.sp)
        when (u.phase) {
            UpdPhase.CHECKING -> T("⏳ Проверяю…", Cp.cyan, 12.sp, modifier = Modifier.padding(top = 6.dp))
            UpdPhase.UP_TO_DATE -> T("✓ У вас последняя версия", Cp.green, 12.sp, bold = true, modifier = Modifier.padding(top = 6.dp))
            UpdPhase.ERROR -> T("⚠ ${u.error ?: "Ошибка"}", Cp.red, 12.sp, modifier = Modifier.padding(top = 6.dp))
            else -> {}
        }
        if (i != null && u.hasUpdate) {
            Spacer(Modifier.height(6.dp))
            T("Новая версия ${i.versionName}" + (if (i.date.isNotBlank()) " · ${i.date}" else "") + (if (i.size > 0) " · %.1f МБ".format(i.size / 1048576.0) else ""), Cp.yellow, 14.sp, bold = true)
            i.notes.take(8).forEach { n -> T("• $n", Cp.text, 11.sp, modifier = Modifier.padding(top = 2.dp)) }
            Spacer(Modifier.height(8.dp))
            when (u.phase) {
                UpdPhase.DOWNLOADING -> {
                    Progress((u.progress * 1000).toInt(), 1000, Cp.yellow)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Загрузка %.0f%%".format(u.progress * 100), Cp.yellow, 12.sp, bold = true, modifier = Modifier.weight(1f))
                        CpButton("ОТМЕНА", Color(0xFF240B15), Cp.text) { u.cancel() }
                    }
                }
                UpdPhase.READY -> {
                    CpButton("⬆ УСТАНОВИТЬ", Cp.yellow, modifier = Modifier.fillMaxWidth()) { u.install() }
                    if (u.needsPermission) T("Разрешите NetWatch «Установку неизвестных приложений» в открывшихся настройках, вернитесь и нажмите ещё раз.", Cp.muted2, 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                else -> CpButton("⬆ СКАЧАТЬ И УСТАНОВИТЬ", Cp.yellow, modifier = Modifier.fillMaxWidth()) { u.download() }
            }
            T("Файл проверяется по контрольной сумме SHA-256. Данные и настройки сохранятся.", Cp.muted, 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (u.phase != UpdPhase.DOWNLOADING) CpButton("🔄 ПРОВЕРИТЬ", Color(0xFF240B15), Cp.text) { u.check() }
            Spacer(Modifier.width(8.dp))
            Chip(if (u.auto) "✓ автопроверка" else "автопроверка", u.auto) { u.setAutoCheck(!u.auto) }
        }
    }
}

/* ============================ Настройки ============================ */

@Composable
fun SettingsTab(vm: NetVm) {
    val ctx = LocalContext.current
    var gwText by remember { mutableStateOf(vm.gwManual ?: "") }
    var retention by remember { mutableIntStateOf(History.retentionDays) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile {
            T("РАЗМЕР ШРИФТА", Cp.yellow, 12.sp, bold = true)
            Row(Modifier.padding(top = 6.dp)) {
                Chip("Обычный", !vm.fontLarge) { vm.fontLarge = false; vm.saveInputs() }
                Chip("Крупный", vm.fontLarge) { vm.fontLarge = true; vm.saveInputs() }
            }
            T("Крупный — увеличивает весь текст примерно на 15%.", Cp.muted2, 11.sp)
        }
        UpdateTile(vm)
        Tile {
            T("АДРЕС РОУТЕРА", Cp.yellow, 12.sp, bold = true)
            T("Пусто — определяется автоматически (сейчас ${vm.gw.host}).", Cp.muted2, 11.sp)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CpField(gwText, { gwText = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CpButton("OK", Cp.green) { vm.gwManual = gwText.trim().ifBlank { null }; vm.saveInputs(); vm.autoGateway() }
            }
        }
        Tile {
            T("ПРОВЕРКА ИНТЕРНЕТА", Cp.yellow, 12.sp, bold = true)
            T("Узел, до которого меряется пинг на Главной и в мониторинге.", Cp.muted2, 11.sp)
            CpField(vm.inet.host, { vm.inet.host = it; vm.inet.reset() }, Modifier.fillMaxWidth().padding(top = 6.dp))
            Row(Modifier.padding(top = 6.dp)) {
                listOf("8.8.8.8", "1.1.1.1", "77.88.8.8", "ya.ru").forEach { h -> Chip(h, vm.inet.host == h) { vm.inet.host = h; vm.inet.reset(); vm.saveInputs() } }
            }
        }
        Tile {
            T("ХРАНЕНИЕ ИСТОРИИ", Cp.yellow, 12.sp, bold = true)
            Row(Modifier.padding(top = 6.dp)) {
                listOf(7 to "7 дн", 30 to "30 дн", 90 to "90 дн", 0 to "всегда").forEach { (d, l) -> Chip(l, retention == d) { History.setRetention(ctx, d); retention = d } }
            }
        }
        Tile {
            T("О ПРИЛОЖЕНИИ", Cp.yellow, 12.sp, bold = true)
            T("NetWatch: Network Scanner ${BuildConfigCompat.version(ctx)}", Cp.text, 12.sp)
            T("Created by L1TTLEHE11 · github.com/L1TTLEHE11/NetWatch", Cp.muted2, 11.sp)
        }
    }
}
