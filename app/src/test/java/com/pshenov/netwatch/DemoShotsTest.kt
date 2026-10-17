package com.pshenov.netwatch

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin

/**
 * Скриншоты для README, сайта и каталогов приложений — с примерными данными
 * (на JVM нет реальной сети). Запуск: ./gradlew testDebugUnitTest --tests DemoShotsTest
 * Результат: PNG-файлы в docs/screenshots
 */
@RunWith(org.robolectric.ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class DemoShotsTest(private val lang: String) {
    companion object {
        @JvmStatic @org.robolectric.ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun langs() = listOf(arrayOf<Any>("en"), arrayOf<Any>("ru"))
    }
    @get:Rule val rule = createComposeRule()
    /** Примерные тексты на языке скриншота. */
    private fun L(en: String, ru: String) = if (lang == "ru") ru else en
    private val now = System.currentTimeMillis()
    private val min = 60_000L

    private fun wave(n: Int, base: Float, amp: Float, phase: Float = 0f) = List(n) { i -> base + amp * sin(i * 0.45f + phase) + (i * 7 % 5) * amp * 0.12f }

    private fun demoVm(): NetVm {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Lang.pin(ctx, lang)
        History.init(ctx); History.clear()
        listOf(
            HItem(11, now - 3 * 86_400_000L, HKind.SPEED, "Office-5G", L("↓ 87.4  ↑ 39.2 Mbit/s", "↓ 87.4  ↑ 39.2 Мбит/с"), L("ping 12 ms", "пинг 12 мс"), "", 87.4, 39.2, 12.0),
            HItem(12, now - 2 * 86_400_000L, HKind.SPEED, "Office-5G", L("↓ 91.0  ↑ 40.5 Mbit/s", "↓ 91.0  ↑ 40.5 Мбит/с"), L("ping 11 ms", "пинг 11 мс"), "", 91.0, 40.5, 11.0),
            HItem(13, now - 26 * 3_600_000L, HKind.SPEED, "Office-5G", L("↓ 62.3  ↑ 35.8 Mbit/s", "↓ 62.3  ↑ 35.8 Мбит/с"), L("ping 19 ms", "пинг 19 мс"), "", 62.3, 35.8, 19.0),
            HItem(14, now - 20 * 3_600_000L, HKind.SPEED, "Office-5G", L("↓ 95.1  ↑ 41.7 Mbit/s", "↓ 95.1  ↑ 41.7 Мбит/с"), L("ping 10 ms", "пинг 10 мс"), "", 95.1, 41.7, 10.0),
            HItem(15, now - 8 * 3_600_000L, HKind.TRACE, "Office-5G", L("to ya.ru · 9 hops", "до ya.ru · 9 прыжков"), L("target reached, 14 ms", "цель достигнута, 14 мс"), ""),
            HItem(16, now - 3 * 3_600_000L, HKind.MONITOR, "Office-5G", L("Monitoring 2h 14m", "Мониторинг 2ч 14м"), L("outages 1 · down 4m 02s · loss 0.6%", "обрывов 1 · без интернета 4м 02с · потери 0.6%"), "", 0.6, 1.0, 242.0),
            HItem(17, now - 40 * min, HKind.DNS, "Office-5G", L("best: Yandex · 9 ms", "лучший: Яндекс · 9 мс"), L("Yandex 9 · Google 14 · Cloudflare 15", "Яндекс 9 · Google 14 · Cloudflare 15"), "", num3 = 9.0),
            HItem(18, now - 12 * min, HKind.SCAN, "Office-5G", L("7 devices · new 1", "7 устройств · новых 1"), "192.168.88.57/24", "", 7.0, 1.0),
            HItem(19, now - 3 * min, HKind.SPEED, "Office-5G", L("↓ 94.2  ↑ 41.8 Mbit/s", "↓ 94.2  ↑ 41.8 Мбит/с"), L("ping 11 ms · mirror.yandex.ru", "пинг 11 мс · mirror.yandex.ru"), "", 94.2, 41.8, 11.0),
        ).forEach { History.add(it) }

        // разрешения и системные состояния, как у настроенного телефона
        org.robolectric.Shadows.shadowOf(ctx).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        org.robolectric.Shadows.shadowOf(ctx.getSystemService(android.location.LocationManager::class.java)).setLocationEnabled(true)
        org.robolectric.Shadows.shadowOf(ctx.getSystemService(android.os.PowerManager::class.java)).setIgnoringBatteryOptimizations(ctx.packageName, true)

        val vm = NetVm(ctx)
        vm.onAppVisible(false)   // фоновые опросы не перетирают демо-данные
        Thread.sleep(2500)       // дождаться, пока уже начатые опросы закончатся
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        vm.radio = RadioState(RadioStatus.WIFI_OK, "Office-5G", "02:5e:0c:aa:10:44", -54, 92, 866, 5220)
        vm.radioHist = wave(40, -56f, 4f)
        fun ping(c: PingCard, host: String, base: Float, amp: Float, last: Double) {
            c.host = host; c.history = wave(40, base, amp, 1f).map { maxOf(0.3f, it) }; c.rtt = last; c.started = true; c.sent = 240; c.lost = 1
        }
        ping(vm.gw, "192.168.88.1", 2.5f, 1.2f, 2.8)
        ping(vm.inet, "8.8.8.8", 17f, 5f, 18.4)
        ping(vm.custom, "192.168.88.24", 5f, 2f, 6.1)
        vm.results = History.items.value.filter { it.kind == HKind.SPEED }.map { SpeedResult(it.time, it.num3, it.num1!!, it.num2!!) }
        vm.pingMs = 11.0; vm.jitterMs = 1.4; vm.dl = 94.2; vm.ul = 41.8; vm.server = "mirror.yandex.ru"; vm.phase = Phase.DONE
        vm.dlCurve = List(70) { i -> (94f * (1 - kotlin.math.exp(-i / 9f)) + 6f * sin(i * 0.7f)).coerceAtLeast(0f) }
        vm.ulCurve = List(60) { i -> (42f * (1 - kotlin.math.exp(-i / 8f)) + 3f * sin(i * 0.9f)).coerceAtLeast(0f) }

        fun host(ip: String, ms: Double, ports: List<Int>, name: String?, title: String?, mac: String?, kind: String, gw: Boolean = false, self: Boolean = false) =
            LanHost(ip, ms, ports, name, title, mac, kind, gw, self)
        val cam = host("192.168.88.24", 6.0, listOf(80, 554), null, "IPC Web Viewer", "02:1c:27:5a:00:24", tr(R.string.ip_camera_nvr))
        vm.scan.hosts = listOf(
            host("192.168.88.1", 1.0, listOf(22, 80, 8291, 8728), "router", "RouterOS", "02:4c:5e:0c:00:01", tr(R.string.mikrotik_router_gateway), gw = true),
            host("192.168.88.10", 3.0, listOf(80, 631, 9100), "Printer-2F", "Printer Web", null, tr(R.string.printer)),
            cam,
            host("192.168.88.31", 4.0, listOf(8008, 8009, 8443), L("Living Room TV", "Гостиная TV"), "Android TV", null, "Android TV"),
            host("192.168.88.40", 3.0, listOf(139, 445, 3389), "RECEPTION-PC", null, "02:1a:2b:3c:4d:5e", tr(R.string.windows_computer)),
            host("192.168.88.57", 0.0, emptyList(), null, null, null, tr(R.string.this_phone), self = true),
            host("192.168.88.63", 7.0, listOf(62078), "iPhone", null, null, "iPhone / iPad"),
        )
        vm.scan.newKeys = setOf(History.deviceKey(cam))
        vm.scan.subnet = "192.168.88.57/24"; vm.scan.finishedAt = now - 2 * min; vm.scan.total = 254; vm.scan.done = 254

        fun ap(ssid: String, ch: Int, dbm: Int, sec: String, conn: Boolean = false, w: Int = 20, band: Int = 24) =
            Ap(ssid, "02:00:00:00:%02x:%02x".format(ch, -dbm), dbm, if (band == 24) 2407 + ch * 5 else 5000 + ch * 5,
                if (band == 24) 2407 + ch * 5 else 5000 + ch * 5, ch, band, w, sec, conn)
        vm.wifi.band = 24
        vm.wifi.aps = listOf(
            ap("Office", 1, -41, "WPA2", conn = true), ap("Neighbor_2G", 3, -63, "WPA2"), ap("Cafe-Guest", 6, -68, tr(R.string.open_3)),
            ap("TP-Link_7A", 6, -74, "WPA2"), ap("HomeNet", 11, -82, "WPA3"), ap("Office-5G", 44, -54, "WPA2", w = 80, band = 5),
        ).sortedByDescending { it.dbm }
        vm.wifi.lastScan = now - min

        Monitor.update {
            MonitorState(true, now - (2 * 60 + 14) * min,
                TargetStats("192.168.88.1", 4020, 3, 4020 * 2.6, 31.0, 2.8, 0, false, 0, 0, 0),
                TargetStats("8.8.8.8", 4020, 24, 3996 * 18.0, 212.0, 18.4, 0, false, 0, 1, 242_000),
                wave(80, 2.6f, 1.1f).map { maxOf(0.4f, it) }, wave(80, 18f, 6f, 2f).map { maxOf(1f, it) },
                listOf(
                    MonEvent(now - 47 * min, L("✔ Internet is back online (was down 4m 02s)", "✔ Интернет снова на связи (не было 4м 02с)"), false),
                    MonEvent(now - 51 * min, L("✖ Internet is not responding", "✖ Интернет не отвечает"), true),
                    MonEvent(now - (2 * 60 + 14) * min, L("Monitoring started: router 192.168.88.1, internet 8.8.8.8", "Мониторинг запущен: роутер 192.168.88.1, интернет 8.8.8.8"), false),
                ))
        }

        vm.mt.host = "192.168.88.1"; vm.mt.connected = true
        vm.mt.info = MtInfo("Core-Router", "RB1100AHx4", "7.16.1", "21d04h13m", 7, 31, "41")
        vm.mt.ifaces = listOf(
            MtIface("ether1-WAN", "ether", true, false, 84.2e6, 12.1e6, 0, 0),
            MtIface("bridge-LAN", "bridge", true, false, 11.6e6, 79.4e6, 0, 0),
            MtIface("ether2-AP-1F", "ether", true, false, 6.3e6, 41.0e6, 0, 0),
            MtIface("ether3-AP-2F", "ether", true, false, 4.1e6, 28.7e6, 0, 0),
            MtIface("ether5", "ether", false, false, 0.0, 0.0, 0, 0),
        )
        vm.mt.caps = listOf(MtCap("AP-1F", "192.168.88.11", "cAP ax", "Run"), MtCap("AP-2F", "192.168.88.12", "cAP ax", "Run"))
        vm.mt.wifiSrc = "CAPsMAN"
        vm.mt.wifi = listOf(
            MtWifiClient("02:AA:00:00:00:01", "AP-1F-5G", "-52", "3h12m", "866Mbps", "Laptop-Reception", "192.168.88.70"),
            MtWifiClient("02:AA:00:00:00:02", "AP-2F-5G", "-61", "1h40m", "433Mbps", "Phone-Staff", "192.168.88.71"),
            MtWifiClient("02:AA:00:00:00:03", "AP-2F-2G", "-77", "22m", "65Mbps", "Tablet", "192.168.88.72"),
        )
        vm.mt.leases = listOf(
            MtLease("192.168.88.40", "02:1A:2B:3C:4D:5E", "RECEPTION-PC", "bound", ""),
            MtLease("192.168.88.70", "02:AA:00:00:00:01", "Laptop-Reception", "bound", ""),
            MtLease("192.168.88.24", "02:1C:27:5A:00:24", "", "bound", L("entrance camera", "камера вход")),
        )
        return vm
    }

    private fun shot(name: String, tab: Int) {
        val vm = demoVm()
        vm.selectTab(tab)
        rule.setContent { NetWatchScreen(vm) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../docs/screenshots/$lang/$name.png")
    }

    @Test fun s1home() = shot("1_home", TAB_DASH)
    @Test fun s2speed() = shot("2_speed", TAB_SPEED)
    @Test fun s3scan() = shot("3_scan", TAB_SCAN)
    @Test fun s4wifi() = shot("4_wifi", TAB_WIFI)
    @Test fun s5monitor() = shot("5_monitor", TAB_MONITOR)
    @Test fun s6mikrotik() = shot("6_mikrotik", TAB_MIKROTIK)
    @Test fun s7history() = shot("7_history", TAB_HISTORY)
    @Test fun s8more() = shot("8_more", TAB_MORE)
}
