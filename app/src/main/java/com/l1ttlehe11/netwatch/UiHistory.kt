package com.l1ttlehe11.netwatch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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

private val hm = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dm = SimpleDateFormat("dd.MM", Locale.getDefault())

private fun kindColor(k: HKind) = when (k) {
    HKind.SPEED -> Cp.cyan; HKind.MONITOR -> Cp.red; HKind.SCAN -> Cp.green; HKind.SURVEY -> Cp.yellow
    else -> Color(0xFFB98CFF)
}

@Composable
fun HistoryTab(vm: NetVm) {
    val ctx = LocalContext.current
    val all by History.items.collectAsState()
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var net by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableIntStateOf(30) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var retention by remember { mutableIntStateOf(History.retentionDays) }

    val now = System.currentTimeMillis()
    val networks = all.map { it.network }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    val list = all.filter { (kind == null || it.kind.name == kind) && (net == null || it.network == net) && (period == 0 || it.time >= now - period * 86_400_000L) }

    Card {
        CardTitle(tr(R.string.measurement_history))
        T(tr(R.string.records_kb_kept, all.size, "%.0f".format(History.sizeBytes() / 1024.0), if (retention == 0) tr(R.string.forever) else tr(R.string.days, retention)), Cp.muted, 10.sp)
        Spacer(Modifier.height(6.dp))
        T(tr(R.string.type), Cp.muted, 9.sp, bold = true)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            Chip(tr(R.string.all), kind == null) { kind = null }
            HKind.entries.filter { k -> all.any { it.kind == k } }.forEach { k ->
                Chip("${k.icon} ${k.label} ${all.count { it.kind == k }}", kind == k.name) { kind = if (kind == k.name) null else k.name }
            }
        }
        if (networks.size > 1) {
            T(tr(R.string.network_2), Cp.muted, 9.sp, bold = true)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                Chip(tr(R.string.all_networks), net == null) { net = null }
                networks.forEach { n -> Chip(n, net == n) { net = if (net == n) null else n } }
            }
        }
        T(tr(R.string.period), Cp.muted, 9.sp, bold = true)
        Row(Modifier.padding(vertical = 4.dp)) {
            listOf(1 to tr(R.string.day), 7 to tr(R.string.week), 30 to tr(R.string.month), 0 to tr(R.string.all_2)).forEach { (d, l) -> Chip(l, period == d) { period = d } }
        }
    }

    // --- сводка по скорости: главный аргумент для провайдера ---
    val speed = list.filter { it.kind == HKind.SPEED }.sortedBy { it.time }
    if (speed.size >= 2 && (kind == null || kind == HKind.SPEED.name)) Card(Cp.cyan) {
        CardTitle(tr(R.string.speed_over_period_tests, speed.size))
        val d = speed.mapNotNull { it.num1 }; val u = speed.mapNotNull { it.num2 }; val p = speed.mapNotNull { it.num3 }
        Row {
            Stat(tr(R.string.average_3), "%.1f".format(d.average()), Cp.cyan, Modifier.weight(1f))
            Stat(tr(R.string.min), "%.1f".format(d.min()), if (d.min() < d.average() * 0.5) Cp.red else Cp.cyan, Modifier.weight(1f))
            Stat(tr(R.string.average_2), "%.1f".format(u.average()), Cp.yellow, Modifier.weight(1f))
            Stat(tr(R.string.ping), if (p.isEmpty()) "—" else "%.0f".format(p.average()), Cp.green, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        SpeedHistoryChart(speed)
        // худшее время суток
        val byHour = speed.groupBy { java.util.Calendar.getInstance().apply { timeInMillis = it.time }.get(java.util.Calendar.HOUR_OF_DAY) / 6 }
        if (byHour.size > 1) {
            val names = listOf(tr(R.string.at_night), tr(R.string.in_the_morning), tr(R.string.in_the_afternoon), tr(R.string.in_the_evening))
            val worst = byHour.minByOrNull { (_, v) -> v.mapNotNull { it.num1 }.average() }!!
            T(tr(R.string.slowest_on_average_1f_mbit_s, names[worst.key]).format(worst.value.mapNotNull { it.num1 }.average()), Cp.muted, 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        CpButton(tr(R.string.export_to_excel_csv), Cp.cyan, modifier = Modifier.fillMaxWidth()) { Report.shareCsv(ctx, tr(R.string.netwatch_speed), History.speedCsv(speed)) }
    }

    if (list.isEmpty()) Card(Cp.dim) {
        T(if (all.isEmpty()) tr(R.string.empty_for_now_speed_tests_network)
          else tr(R.string.nothing_matches_the_selected_filters), Cp.muted, 11.sp)
    }

    var lastDay = ""
    list.forEach { item ->
        val day = History.dayLabel(item.time, now)
        if (day != lastDay) { lastDay = day; T(day.uppercase(), Cp.yellow, 11.sp, bold = true, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)) }
        HistoryCard(item, expanded == item.id, { expanded = if (expanded == item.id) null else item.id })
    }

    Card(Cp.dim) {
        CardTitle(tr(R.string.storage))
        T(tr(R.string.keep_records_for), Cp.muted, 9.sp, bold = true)
        Row(Modifier.padding(vertical = 4.dp)) {
            listOf(7 to tr(R.string.s_7_days), 30 to tr(R.string.s_30_days), 90 to tr(R.string.s_90_days), 0 to tr(R.string.forever)).forEach { (d, l) -> Chip(l, retention == d) { History.setRetention(ctx, d); retention = d } }
        }
        Hint(tr(R.string.everything_is_stored_only_on_this))
        Row(Modifier.padding(top = 4.dp)) {
            CpButton(tr(R.string.full_history_as_pdf), Cp.cyan, modifier = Modifier.weight(1f)) {
                Report.sharePdf(ctx, tr(R.string.netwatch_measurement_history), list.reversed().joinToString("\n\n") { textOf(it) })
            }
            Spacer(Modifier.width(6.dp))
            CpButton(if (confirmClear) tr(R.string.really_clear) else tr(R.string.clear_all), if (confirmClear) Cp.red else Color(0xFF240B15), Cp.text, modifier = Modifier.weight(1f)) {
                if (confirmClear) { History.clear(); confirmClear = false } else confirmClear = true
            }
        }
    }
}

private fun textOf(i: HItem): String {
    val f = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return "■ ${i.kind.label}: ${i.title}\n${f.format(Date(i.time))} · ${i.network}\n${i.summary}\n${i.details}"
}

@Composable
private fun Stat(t: String, v: String, c: Color, m: Modifier) = Column(m) { T(t, Cp.muted, 8.sp, bold = true); T(v, c, 16.sp, bold = true, maxLines = 1) }

@Composable
private fun HistoryCard(i: HItem, open: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    val c = kindColor(i.kind)
    var confirm by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).background(Cp.card).border(1.dp, if (open) c else Cp.dim)
            .clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(i.kind.icon, c, 14.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                T(i.title, if (i.title.startsWith("● ")) Cp.green else Cp.text, 12.sp, bold = true, maxLines = 1)
                T("${hm.format(Date(i.time))} · ${i.network}", Cp.muted, 9.sp, maxLines = 1)
            }
            T(if (open) "▲" else "▼", Cp.muted, 10.sp)
        }
        if (i.summary.isNotBlank()) T(i.summary, c, 10.sp, maxLines = if (open) Int.MAX_VALUE else 1, modifier = Modifier.padding(top = 3.dp))
        if (open) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().background(Cp.inner).border(1.dp, Cp.dim).horizontalScroll(rememberScrollState()).padding(8.dp)) {
                SelectionContainer { T(i.details, Cp.cyan, 10.sp) }
            }
            Row(Modifier.padding(top = 6.dp)) {
                CpButton("📄 PDF", Cp.cyan) { Report.sharePdf(ctx, "NetWatch — ${i.kind.label} ${dm.format(Date(i.time))}", textOf(i)) }
                if (i.csv != null && i.kind != HKind.SCAN) {
                    Spacer(Modifier.width(6.dp))
                    CpButton("📊 CSV", Cp.cyan) { Report.shareCsv(ctx, "NetWatch_${i.kind.label}", i.csv) }
                }
                Spacer(Modifier.weight(1f))
                CpButton(if (confirm) tr(R.string.delete) else "🗑", if (confirm) Cp.red else Color(0xFF240B15), Cp.text) {
                    if (confirm) History.remove(i.id) else confirm = true
                }
            }
        }
    }
}

/** График скорости во времени: точки замеров соединены линией, ось X — даты. */
@Composable
private fun SpeedHistoryChart(items: List<HItem>) {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(170.dp).background(Cp.inner).border(1.dp, Cp.dim)) {
        val left = 40.dp.toPx(); val right = 8.dp.toPx(); val top = 10.dp.toPx(); val bottom = 18.dp.toPx()
        val w = size.width - left - right; val h = size.height - top - bottom
        val t0 = items.first().time; val t1 = maxOf(items.last().time, t0 + 60_000)
        val maxV = maxOf(10.0, items.maxOf { maxOf(it.num1 ?: 0.0, it.num2 ?: 0.0) } * 1.15)
        fun x(t: Long) = left + (t - t0).toFloat() / (t1 - t0) * w
        fun y(v: Double) = top + h - (v / maxV).toFloat() * h
        val lbl = TextStyle(fontFamily = Cp.mono, fontSize = 8.sp, color = Cp.muted)
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        for (k in 0..3) {
            val v = maxV * k / 3
            drawLine(Color(0xFF2B0C18), Offset(left, y(v)), Offset(size.width - right, y(v)), pathEffect = dash)
            drawText(tm, "%.0f".format(v), Offset(2f, y(v) - 6.dp.toPx()), lbl)
        }
        val days = (t1 - t0) / 86_400_000.0
        val fmt = SimpleDateFormat(if (days < 1.5) "HH:mm" else "dd.MM", Locale.getDefault())
        for (k in 0..3) {
            val t = t0 + (t1 - t0) * k / 3
            val tl = tm.measure(fmt.format(Date(t)), lbl)
            drawText(tl, topLeft = Offset((x(t) - tl.size.width / 2).coerceIn(left, size.width - tl.size.width), top + h + 3.dp.toPx()))
        }
        fun series(sel: (HItem) -> Double?, c: Color) {
            val pts = items.mapNotNull { i -> sel(i)?.let { Offset(x(i.time), y(it)) } }
            if (pts.isEmpty()) return
            val p = Path().apply { pts.forEachIndexed { k, o -> if (k == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(p, c, style = Stroke(2.5f))
            pts.forEach { drawCircle(c, 4f, it) }
        }
        series({ it.num1 }, Cp.cyan); series({ it.num2 }, Cp.yellow)
        drawText(tm, "■ ↓", Offset(size.width - 70.dp.toPx(), 2f), TextStyle(fontFamily = Cp.mono, fontSize = 9.sp, color = Cp.cyan, fontWeight = FontWeight.Bold))
        drawText(tm, "■ ↑", Offset(size.width - 36.dp.toPx(), 2f), TextStyle(fontFamily = Cp.mono, fontSize = 9.sp, color = Cp.yellow, fontWeight = FontWeight.Bold))
    }
}
