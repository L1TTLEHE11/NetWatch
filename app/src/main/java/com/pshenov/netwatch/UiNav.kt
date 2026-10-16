package com.pshenov.netwatch

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ============================ иконки (контуры 24×24, как на макете) ============================ */

object Ic {
    const val home = "M3 11l9-7 9 7M5 10v10h14V10"
    const val speed = "M4 16a8 8 0 1 1 16 0M12 16l4-5"
    const val net = "M14.5 5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0M7.5 19a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0M21.5 19a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0M12 7.5v4M12 11.5L6.5 17M12 11.5l5.5 5.5"
    const val wifi = "M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01"
    const val more = "M5 12h.01M12 12h.01M19 12h.01"
    const val pulse = "M3 12h4l2-6 4 12 2-6h6"
    const val history = "M3 12a9 9 0 1 0 3-6.7M3 4v5h5M12 7v5l3 2"
    const val share = "M12 3v12M7 8l5-5 5 5M5 21h14"
    const val search = "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0M20 20l-4-4"
    const val router = "M3 13h18v7H3zM7 16.5h.01M11 16.5h.01M8 13V9M16 13V9M5 6a10 10 0 0 1 14 0"
    const val doc = "M6 3h9l4 4v14H6zM14 3v5h5M9 13h7M9 17h5"
    const val gear = "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"
    const val chevron = "M9 6l6 6-6 6"
    const val back = "M15 6l-6 6 6 6"
}

private val pathCache = mutableMapOf<String, Path>()
private fun pathOf(d: String) = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

@Composable
fun NwIcon(d: String, color: Color, size: Dp = 22.dp, stroke: Float = 2f) {
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(pathOf(d), color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Рубленые углы: срез слева сверху и справа снизу. */
fun cutShape(cut: Float) = GenericShape { s, _ ->
    moveTo(cut, 0f); lineTo(s.width, 0f); lineTo(s.width, s.height - cut); lineTo(s.width - cut, s.height)
    lineTo(0f, s.height); lineTo(0f, cut); close()
}

/* ============================ нижнее HUD-меню ============================ */

private fun icFor(s: Section) = when (s) { Section.HOME -> Ic.home; Section.SPEED -> Ic.speed; Section.NET -> Ic.net; Section.WIFI -> Ic.wifi; Section.MORE -> Ic.more }

@Composable
fun BottomNav(current: Section, onSelect: (Section) -> Unit) {
    Box(Modifier.fillMaxWidth().height(76.dp).background(Cp.bg)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val c = 12.dp.toPx(); val s = 16.dp.toPx(); val nd = 5.dp.toPx(); val ns = 8.dp.toPx()
            val nl = w * 0.31f; val nr = w * 0.69f
            fun edge(p: Path) = p.apply {
                moveTo(0f, c); lineTo(s, 0f); lineTo(nl, 0f); lineTo(nl + ns, nd); lineTo(nr - ns, nd); lineTo(nr, 0f); lineTo(w - s, 0f); lineTo(w, c)
            }
            drawPath(edge(Path()).apply { lineTo(w, h); lineTo(0f, h); close() }, Color(0xFF0B0508))
            drawPath(edge(Path()), Cp.red, style = Stroke(1.5.dp.toPx()))
            drawRect(Cp.yellow, Offset(w / 2 - 9.dp.toPx(), 3.dp.toPx()), Size(18.dp.toPx(), 2.dp.toPx()))
            for (x in listOf(22f, 32f)) drawRect(Cp.dim, Offset(x.dp.toPx(), 3.dp.toPx()), Size(6.dp.toPx(), 2.dp.toPx()))
            for (x in listOf(28f, 38f)) drawRect(Cp.dim, Offset(w - x.dp.toPx(), 3.dp.toPx()), Size(6.dp.toPx(), 2.dp.toPx()))
        }
        Row(Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Section.entries.forEach { s -> NavItem(s, s == current, Modifier.weight(1f).fillMaxHeight()) { onSelect(s) } }
        }
    }
}

@Composable
private fun NavItem(s: Section, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .semantics { role = Role.Tab; selected = active; contentDescription = s.title }
            .clickable(onClick = onClick)
            .drawBehind {
                val cut = 10.dp.toPx()
                if (active) {
                    // неоновое свечение: несколько полупрозрачных слоёв вокруг плашки
                    for (k in 3 downTo 1) {
                        val g = k * 2.5.dp.toPx()
                        val p = Path().apply {
                            moveTo(cut - g, -g); lineTo(size.width + g, -g); lineTo(size.width + g, size.height - cut + g)
                            lineTo(size.width - cut + g, size.height + g); lineTo(-g, size.height + g); lineTo(-g, cut - g); close()
                        }
                        drawPath(p, Cp.red.copy(alpha = 0.10f))
                    }
                    val p = Path().apply {
                        moveTo(cut, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - cut); lineTo(size.width - cut, size.height)
                        lineTo(0f, size.height); lineTo(0f, cut); close()
                    }
                    drawPath(p, Cp.red)
                    drawRect(Cp.yellow, Offset(size.width - 11.dp.toPx(), 5.dp.toPx()), Size(6.dp.toPx(), 6.dp.toPx()))
                } else {
                    val b = 8.dp.toPx(); val sw = 1.dp.toPx()
                    drawLine(Cp.dim, Offset(0f, 0f), Offset(b, 0f), sw); drawLine(Cp.dim, Offset(0f, 0f), Offset(0f, b), sw)
                    drawLine(Cp.dim, Offset(size.width, size.height), Offset(size.width - b, size.height), sw)
                    drawLine(Cp.dim, Offset(size.width, size.height), Offset(size.width, size.height - b), sw)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (active) Cp.dark else Color(0xFFC09AA8)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            T(s.idx, if (active) Cp.dark else Color(0xFF8A5A6A), 9.sp, bold = true, fixed = true)
            NwIcon(icFor(s), fg, 22.dp, if (s == Section.MORE) 3f else 2f)
            T(s.label, fg, 10.sp, bold = true, fixed = true, maxLines = 1)
        }
    }
}

/* ============================ шапка ============================ */

@Composable
fun HeaderIconButton(d: String, tint: Color, label: String, border: Color = Cp.dim, bg: Color = Color(0xFF1F0810), led: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).background(bg).border(1.dp, border)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NwIcon(d, tint)
        if (led != null) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(6.dp).drawBehind {
            drawCircle(led.copy(alpha = 0.35f), size.minDimension * 1.1f); drawRect(led)
        })
    }
}

/** Кнопка фонового мониторинга: зелёная со «светодиодом», когда работает. */
@Composable
fun MonitorToggle(vm: NetVm) {
    val ctx = LocalContext.current
    val m by Monitor.state.collectAsState()
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { Monitor.start(ctx, vm.gw.host.trim(), vm.inet.host.trim()) }
    Box(Modifier.semantics { stateDescription = if (m.running) "включён" else "выключен" }) {
        HeaderIconButton(
            Ic.pulse, if (m.running) Cp.green else Color(0xFFC09AA8),
            if (m.running) "Фоновый мониторинг включён — выключить" else "Фоновый мониторинг выключен — включить",
            border = if (m.running) Cp.green else Cp.dim, bg = if (m.running) Color(0xFF062410) else Color(0xFF1F0810),
            led = if (m.running) Cp.green else null,
        ) {
            if (m.running) Monitor.stop(ctx)
            else if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
            else Monitor.start(ctx, vm.gw.host.trim(), vm.inet.host.trim())
        }
    }
}

@Composable
fun Header(vm: NetVm, onReport: () -> Unit) {
    val sec = vm.section
    val inner = sec == Section.MORE && vm.tab != TAB_MORE
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            sec == Section.HOME -> Box(Modifier.background(Cp.red).padding(horizontal = 9.dp, vertical = 4.dp)) { T("NETWATCH // TOOL", Cp.dark, 16.sp, bold = true, fixed = true, maxLines = 1) }
            inner -> {
                HeaderIconButton(Ic.back, Cp.text, "Назад в «Ещё»") { vm.selectTab(TAB_MORE) }
                T(innerTitle(vm.tab), Cp.text, 20.sp, bold = true, maxLines = 1)
            }
            else -> T(sec.title, Cp.text, 22.sp, bold = true, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (sec != Section.MORE || inner) {
            MonitorToggle(vm)
            HeaderIconButton(Ic.history, Cp.yellow, "История", border = if (vm.tab == TAB_HISTORY) Cp.yellow else Cp.dim) { vm.selectTab(TAB_HISTORY) }
            HeaderIconButton(Ic.share, Cp.cyan, "Отчёт") { onReport() }
        }
    }
}

private fun innerTitle(tab: Int) = when (tab) { TAB_MONITOR -> "Мониторинг"; TAB_MIKROTIK -> "MikroTik"; TAB_HISTORY -> "История"; TAB_SETTINGS -> "Настройки"; else -> "Ещё" }

/* ============================ переключатель внутри раздела ============================ */

@Composable
fun SubTabs(items: List<Pair<Int, String>>, current: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().border(1.dp, Cp.red)) {
        items.forEachIndexed { i, (tab, label) ->
            val sel = tab == current
            if (i > 0) Box(Modifier.width(1.dp).height(44.dp).background(Cp.red))
            Box(
                Modifier.weight(1f).height(44.dp).background(if (sel) Cp.red else Color(0xFF1A070E))
                    .semantics { role = Role.Tab; selected = sel }.clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) { T(label, if (sel) Cp.dark else Color(0xFFFF6B8A), 13.sp, bold = sel, fixed = true, maxLines = 1) }
        }
    }
}

/* ============================ общие кирпичики новых экранов ============================ */

@Composable
fun Tile(modifier: Modifier = Modifier.fillMaxWidth(), border: Color = Cp.dim, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.background(Cp.card).border(1.dp, border)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp), content = content,
    )
}

@Composable
fun SectionLabel(s: String) = T("/// $s", Cp.yellow, 12.sp, bold = true, modifier = Modifier.padding(top = 2.dp))

@Composable
fun MenuRow(icon: String, tint: Color, title: String, sub: String, subColor: Color = Cp.muted2, border: Color = Cp.dim, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Cp.card).border(1.dp, border).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NwIcon(icon, tint, 26.dp)
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(title, Cp.text, 15.sp, bold = true)
            T(sub, subColor, 12.sp, bold = subColor != Cp.muted2, maxLines = 2)
        }
        NwIcon(Ic.chevron, Cp.muted2, 18.dp)
    }
}
