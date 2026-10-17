package com.pshenov.netwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

data class TargetStats(
    val host: String = "", val sent: Int = 0, val lost: Int = 0, val sumMs: Double = 0.0, val maxMs: Double = 0.0,
    val last: Double? = null, val failStreak: Int = 0, val down: Boolean = false, val downSince: Long = 0,
    val outages: Int = 0, val downTotalMs: Long = 0,
) {
    val avg get() = if (sent - lost > 0) sumMs / (sent - lost) else 0.0
    val lossPct get() = if (sent > 0) lost * 100.0 / sent else 0.0
    fun push(v: Double?, now: Long): Pair<TargetStats, String?> {
        val ok = v != null
        var s = copy(sent = sent + 1, lost = lost + if (ok) 0 else 1, sumMs = sumMs + (v ?: 0.0), maxMs = maxOf(maxMs, v ?: 0.0), last = v,
            failStreak = if (ok) 0 else failStreak + 1)
        var event: String? = null
        if (!s.down && s.failStreak >= 3) { s = s.copy(down = true, downSince = now - 2 * Monitor.PERIOD_MS, outages = outages + 1); event = "DOWN" }
        if (s.down && ok) { s = s.copy(down = false, downTotalMs = downTotalMs + (now - downSince)); event = "UP" }
        return s to event
    }
    fun currentDownMs(now: Long) = downTotalMs + if (down) now - downSince else 0
}

data class MonEvent(val time: Long, val text: String, val bad: Boolean)

data class MonitorState(
    val running: Boolean = false, val startedAt: Long = 0,
    val gw: TargetStats = TargetStats(), val inet: TargetStats = TargetStats(),
    val gwHist: List<Float> = emptyList(), val inetHist: List<Float> = emptyList(),
    val events: List<MonEvent> = emptyList(),
)

/** Состояние фонового мониторинга — его читает экран, пишет сервис. */
object Monitor {
    const val PERIOD_MS = 2000L
    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state

    internal fun reset(gw: String, inet: String) { _state.value = MonitorState(true, System.currentTimeMillis(), TargetStats(host = gw), TargetStats(host = inet)) }
    internal fun update(f: (MonitorState) -> MonitorState) = _state.update(f)

    fun start(ctx: Context, gw: String, inet: String) {
        val i = Intent(ctx, MonitorService::class.java).putExtra("gw", gw).putExtra("inet", inet)
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
    }

    fun stop(ctx: Context) { ctx.startService(Intent(ctx, MonitorService::class.java).setAction(MonitorService.ACTION_STOP)) }

    fun summary(s: MonitorState, now: Long = System.currentTimeMillis()): String {
        val dur = fmtDur(now - s.startedAt)
        fun t(name: String, x: TargetStats) =
            tr(R.string.loss_1f_d_d_avg_1f, name, x.host).format(
                x.lossPct, x.lost, x.sent, x.avg, x.maxMs, x.outages, fmtDur(x.currentDownMs(now)))
        return tr(R.string.monitoring_for, dur) + t(tr(R.string.router_2), s.gw) + "\n" + t(tr(R.string.internet_3), s.inet)
    }

    fun fmtDur(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
        return if (h > 0) tr(R.string.dh_02dm).format(h, m) else if (m > 0) tr(R.string.dm_02ds).format(m, sec) else tr(R.string.s, sec)
    }
}

class MonitorService : Service() {
    companion object {
        const val ACTION_STOP = "com.pshenov.netwatch.STOP_MONITOR"
        const val CH_RUN = "monitor_run"; const val CH_ALERT = "monitor_alert"
        const val ID_RUN = 1001
    }

    @Volatile private var running = false
    private var sessionId = 0L
    private var network = ""
    private var worker: Thread? = null
    private var wake: PowerManager.WakeLock? = null
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onBind(intent: Intent?): IBinder? = null
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(Lang.wrap(newBase)) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { shutdown(); return START_NOT_STICKY }
        channels()
        val gw = intent?.getStringExtra("gw")?.ifBlank { null } ?: NetInfo.gateway(this).ifBlank { "192.168.1.1" }
        val inet = intent?.getStringExtra("inet")?.ifBlank { null } ?: "8.8.8.8"
        ServiceCompat.startForeground(this, ID_RUN, runNotification(tr(R.string.starting_monitoring)),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if (!running) {
            running = true
            History.init(this)
            sessionId = History.nextId(); network = NetInfo.networkLabel(this)
            Monitor.reset(gw, inet)
            Monitor.update { it.copy(events = listOf(MonEvent(System.currentTimeMillis(), tr(R.string.monitoring_started_router_internet, gw, inet), false))) }
            wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NetWatch:monitor").apply { acquire(24 * 3600_000L) }
            worker = thread(isDaemon = true, name = "netwatch-monitor") { loop(gw, inet) }
        }
        return START_STICKY
    }

    private fun loop(gw: String, inet: String) {
        var tick = 0
        while (running) {
            val t0 = System.currentTimeMillis()
            var a: Double? = null; var b: Double? = null
            val ta = thread { a = Pinger.ping(gw) }; val tb = thread { b = Pinger.ping(inet) }
            ta.join(2500); tb.join(2500)
            val now = System.currentTimeMillis()
            Monitor.update { s ->
                val (g, ge) = s.gw.push(a, now); val (n, ne) = s.inet.push(b, now)
                val ev = s.events.toMutableList()
                fun add(e: String?, what: String, st: TargetStats) {
                    when (e) {
                        "DOWN" -> { ev.add(0, MonEvent(now, tr(R.string.is_not_responding_2, what), true)); alert(tr(R.string.is_not_responding_2, what), tr(R.string.stopped_responding, fmt.format(Date(now)), st.host)) }
                        "UP" -> { val d = Monitor.fmtDur(now - s.let { if (what == tr(R.string.router_2)) it.gw.downSince else it.inet.downSince })
                            ev.add(0, MonEvent(now, tr(R.string.is_back_online_was_down, what, d), false)); alert(tr(R.string.is_back_online, what), tr(R.string.connection_was_down_for, d)) }
                    }
                }
                add(ge, tr(R.string.router_2), g); add(ne, tr(R.string.internet_3), n)
                if (ge != null || ne != null) pendingSave = true
                s.copy(gw = g, inet = n, gwHist = (s.gwHist + (a?.toFloat() ?: 0f)).takeLast(150),
                    inetHist = (s.inetHist + (b?.toFloat() ?: 0f)).takeLast(150), events = ev.take(200))
            }
            if (pendingSave || tick % 30 == 0) { pendingSave = false; checkpoint(final = false) }
            if (tick++ % 5 == 0) {
                val s = Monitor.state.value
                getSystemService(NotificationManager::class.java).notify(ID_RUN, runNotification(
                    tr(R.string.router_internet_loss_1f, s.gw.last?.let { tr(R.string.s_0f_ms_2).format(it) } ?: tr(R.string.no_reply), s.inet.last?.let { tr(R.string.s_0f_ms_2).format(it) } ?: tr(R.string.no_reply)).format(s.inet.lossPct)))
                NetWatchWidget.push(this, s.gw.last, s.inet.last, null, tr(R.string.monitor_3))
            }
            val sleep = Monitor.PERIOD_MS - (System.currentTimeMillis() - t0)
            if (sleep > 0) try { Thread.sleep(sleep) } catch (_: InterruptedException) { break }
        }
    }

    @Volatile private var pendingSave = false

    /** Сохранить сессию в историю: раз в минуту, при каждом обрыве и при остановке. */
    private fun checkpoint(final: Boolean) {
        val s = Monitor.state.value
        if (s.startedAt == 0L || sessionId == 0L) return
        val now = System.currentTimeMillis()
        val f = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
        val outages = s.gw.outages + s.inet.outages
        val downSec = s.inet.currentDownMs(now) / 1000.0
        History.upsert(HItem(
            id = sessionId, time = s.startedAt, kind = HKind.MONITOR, network = network,
            title = (if (final) "" else "● ") + tr(R.string.monitoring_3, Monitor.fmtDur(now - s.startedAt)) + (if (final) "" else tr(R.string.running)),
            summary = tr(R.string.outages_down_loss_1f, outages, Monitor.fmtDur(s.inet.currentDownMs(now))).format(s.inet.lossPct),
            details = Monitor.summary(s, now) + tr(R.string.events) + s.events.reversed().joinToString("\n") { "${f.format(Date(it.time))}  ${it.text}" },
            num1 = s.inet.lossPct, num2 = outages.toDouble(), num3 = downSec,
            csv = tr(R.string.time_event) + s.events.reversed().joinToString("\n") { "${f.format(Date(it.time))};${Report.csvCell(it.text)}" },
            endTime = now,
        ))
    }

    private fun shutdown() {
        if (running) checkpoint(final = true)
        running = false
        worker?.interrupt()
        try { wake?.release() } catch (_: Exception) { }
        Monitor.update { it.copy(running = false, events = listOf(MonEvent(System.currentTimeMillis(), tr(R.string.monitoring_stopped), false)) + it.events) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { if (running) checkpoint(final = true); running = false; try { wake?.release() } catch (_: Exception) { }; super.onDestroy() }

    private fun channels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RUN, tr(R.string.connection_monitor_2), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, tr(R.string.connection_outages), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun openApp() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)

    private fun runNotification(text: String): Notification = NotificationCompat.Builder(this, CH_RUN)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentTitle(tr(R.string.netwatch_connection_monitor))
        .setContentText(text)
        .setOngoing(true).setOnlyAlertOnce(true)
        .setContentIntent(openApp())
        .addAction(0, tr(R.string.stop_2), PendingIntent.getService(this, 1, Intent(this, MonitorService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun alert(title: String, text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() % 100000).toInt() + 2000,
                NotificationCompat.Builder(this, CH_ALERT).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
                    .setAutoCancel(true).setContentIntent(openApp()).build())
        } catch (_: SecurityException) { }
    }
}
