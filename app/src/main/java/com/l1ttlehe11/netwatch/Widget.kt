package com.l1ttlehe11.netwatch

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Виджет рабочего стола: пинг до роутера и интернета + сигнал. Обновляется по нажатию и раз в 30 минут. */
class NetWatchWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_REFRESH = "com.l1ttlehe11.netwatch.WIDGET_REFRESH"
        private const val GREEN = 0xFF00FF66.toInt(); private const val YELLOW = 0xFFFCEE0A.toInt(); private const val RED = 0xFFFF003C.toInt()

        private fun color(ms: Double?, good: Double, mid: Double) = when { ms == null -> RED; ms < good -> GREEN; ms < mid -> YELLOW; else -> RED }
        private fun fmt(ms: Double?) = ms?.let { if (it < 10) "%.1f".format(it) else "%.0f".format(it) } ?: "✖"

        fun push(ctx: Context, gw: Double?, inet: Double?, radio: RadioState?, source: String) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NetWatchWidget::class.java))
            if (ids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            v.setTextViewText(R.id.w_gw, fmt(gw)); v.setTextColor(R.id.w_gw, color(gw, 15.0, 50.0))
            v.setTextViewText(R.id.w_inet, fmt(inet)); v.setTextColor(R.id.w_inet, color(inet, 60.0, 150.0))
            if (radio != null) {
                val d = radio.dbm
                v.setTextViewText(R.id.w_sig, d?.toString() ?: "—")
                v.setTextColor(R.id.w_sig, when { d == null -> 0xFF7A5260.toInt(); d >= -60 -> GREEN; d >= -75 -> YELLOW; else -> RED })
                v.setTextViewText(R.id.w_ssid, when (radio.status) {
                    RadioStatus.CELLULAR -> radio.operator ?: tr(R.string.mobile_network)
                    RadioStatus.ETHERNET -> "Ethernet"; RadioStatus.OFFLINE -> tr(R.string.no_network)
                    else -> radio.ssid ?: "Wi-Fi"
                })
            }
            v.setTextViewText(R.id.w_time, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) + " · " + source)
            v.setOnClickPendingIntent(R.id.w_refresh, PendingIntent.getBroadcast(ctx, 0,
                Intent(ctx, NetWatchWidget::class.java).setAction(ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE))
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 0,
                Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            if (radio == null) mgr.partiallyUpdateAppWidget(ids, v) else mgr.updateAppWidget(ids, v)
        }

        fun refresh(ctx: Context, done: () -> Unit = {}) = thread(isDaemon = true) {
            try {
                val gwHost = NetInfo.gateway(ctx)
                var g: Double? = null; var i: Double? = null
                val a = thread { g = if (gwHost.isNotEmpty()) Pinger.ping(gwHost) else null }
                val b = thread { i = Pinger.ping("8.8.8.8") }
                a.join(3000); b.join(3000)
                push(ctx, g, i, NetInfo.readRadio(ctx), tr(R.string.updated_2))
            } catch (_: Exception) { } finally { done() }
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val p = goAsync(); refresh(ctx) { p.finish() }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) { val p = goAsync(); refresh(ctx) { p.finish() } }
    }
}
