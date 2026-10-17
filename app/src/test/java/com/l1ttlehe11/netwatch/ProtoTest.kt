package com.l1ttlehe11.netwatch

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

// Robolectric — чтобы были ресурсы строк; язык фиксируем русский, на нём написаны проверки
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ProtoTest {
    @org.junit.Before fun pinLang() { Lang.pin(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "ru") }

    @Test fun lengthEncoding() {
        for (n in listOf(0, 1, 0x7f, 0x80, 0x3fff, 0x4000, 0x1fffff, 0x200000, 0xfffffff, 0x10000000)) {
            val enc = RouterOs.encodeLen(n)
            assertEquals("len $n", n, RouterOs.decodeLen(ByteArrayInputStream(enc)))
        }
    }

    /** Поддельный RouterOS: принимает /login и отвечает на /system/identity/print и ошибочную команду. */
    @Test fun fakeRouter() {
        val srv = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        println("srv ${srv.inetAddress} ${srv.localPort}")
        thread {
            srv.accept().use { s ->
                val i = s.getInputStream().buffered(); val o = s.getOutputStream()
                fun read(): List<String> { val w = mutableListOf<String>(); while (true) { val n = RouterOs.decodeLen(i); if (n == 0) return w; val b = ByteArray(n); var off = 0; while (off < n) off += i.read(b, off, n - off); w += String(b) } }
                fun send(vararg w: String) { val bo = ByteArrayOutputStream(); w.forEach { bo.write(RouterOs.encodeLen(it.toByteArray().size)); bo.write(it.toByteArray()) }; bo.write(0); o.write(bo.toByteArray()); o.flush() }
                val login = read(); assertEquals("/login", login[0]); assertTrue(login.contains("=name=admin")); assertTrue(login.contains("=password=secret"))
                send("!done")
                assertEquals("/system/identity/print", read()[0]); send("!re", "=name=Core-Router"); send("!done")
                read(); send("!trap", "=message=no such command prefix"); send("!done")
                val lp = read(); assertEquals("/ip/dhcp-server/lease/print", lp[0])
                send("!re", "=address=192.168.88.10", "=mac-address=AA:BB:CC:DD:EE:01", "=host-name=office-pc", "=status=bound")
                send("!re", "=address=192.168.88.11", "=mac-address=AA:BB:CC:DD:EE:02", "=host-name=", "=status=waiting")
                send("!done")
            }
        }
        RouterOs("127.0.0.1", srv.localPort, false).connect().use { api ->
            api.login("admin", "secret")
            assertEquals("Core-Router", api.talk("/system/identity/print").first()["name"])
            assertNull(api.tryTalk("/interface/wifi/registration-table/print"))
            val l = MtFetch.leases(api)
            assertEquals(2, l.size); assertEquals("office-pc", l[0].host); assertEquals("AA:BB:CC:DD:EE:02", l[1].mac)
        }
        println("RouterOS API: login, print, trap, leases — OK")
    }

    @Test fun dnsQueryFormat() {
        val q = DnsBench.buildQuery(0x1234, "ya.ru")
        assertEquals(0x12, q[0].toInt()); assertEquals(0x34, q[1].toInt())
        assertEquals(12 + 1 + 2 + 1 + 2 + 1 + 4, q.size)
        assertEquals(2, q[12].toInt()); assertEquals('y'.code, q[13].toInt())
    }

    @Test fun dnsLive() {
        val r = DnsBench.bench("8.8.8.8", "Google") { true }
        println("DNS 8.8.8.8: avg=${r.avgMs} ok=${r.ok}/${r.total}  (в песочнице UDP может быть закрыт)")
    }

    @Test fun netbiosParse() {
        val b = ByteArray(57 + 2 * 18 + 6)
        b[56] = 2
        "OFFICE-PC      ".toByteArray().copyInto(b, 57); b[57 + 15] = 0; b[57 + 16] = 0x04
        "WORKGROUP      ".toByteArray().copyInto(b, 75); b[75 + 15] = 0; b[75 + 16] = 0x84.toByte()
        byteArrayOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E).copyInto(b, 93)
        val (name, mac) = NetBios.parse(b, b.size)!!
        assertEquals("OFFICE-PC", name); assertEquals("00:1A:2B:3C:4D:5E", mac)
    }

    @Test fun wol() {
        assertEquals("AA:BB:CC:DD:EE:FF", WakeOnLan.normalizeMac("aa-bb-cc-dd-ee-ff"))
        assertNull(WakeOnLan.normalizeMac("zz:bb"))
        val p = WakeOnLan.packet("AA:BB:CC:DD:EE:FF")
        assertEquals(102, p.size); assertEquals(0xFF.toByte(), p[0]); assertEquals(0xAA.toByte(), p[6]); assertEquals(0xFF.toByte(), p[101])
    }

    @Test fun subnet() {
        val (h, bc) = Ip.hostsOf("192.168.88.37", 24)
        assertEquals(254, h.size); assertEquals("192.168.88.1", h.first()); assertEquals("192.168.88.255", bc)
        val (h2, bc2) = Ip.hostsOf("10.20.30.40", 16)
        assertEquals(1022, h2.size); assertEquals("10.20.255.255", bc2); assertTrue("10.20.30.40" in h2)
    }

    @Test fun ports() {
        assertEquals(listOf(22, 80, 443), PortInfo.parse("80, 22,443"))
        assertEquals(1024, PortInfo.parse("1-1024").size)
        assertTrue(8291 in PortInfo.parse("популярные"))
        assertEquals("IP-камера / регистратор", PortInfo.guessKind(listOf(80, 554), false, false))
        assertEquals("Роутер MikroTik (шлюз)", PortInfo.guessKind(listOf(80, 8291), true, false))
        val s1 = ServerSocket(0); val s2 = ServerSocket(0)
        val open = mutableListOf<Int>()
        PortScanner.scan("127.0.0.1", listOf(s1.localPort, s2.localPort, 1), { true }, { p, _ -> synchronized(open) { open += p } }, { })
        assertEquals(setOf(s1.localPort, s2.localPort), open.toSet())
    }

    @Test fun wifiLoad() {
        val ap = { ch: Int, dbm: Int -> Ap("x", "b", dbm, 2407 + ch * 5, 2407 + ch * 5, ch, 24, 20, "WPA2", false) }
        val aps = listOf(ap(1, -40), ap(1, -50), ap(6, -85))
        val a = WifiScan.advice(aps, 24, 1)!!
        assertEquals(11, a.best)
        println("Совет по каналу: ${a.best}, ${a.note}")
    }

    @Test fun monitorStats() {
        var s = TargetStats(host = "x")
        var ev: String? = null
        for (v in listOf(1.0, null, null, null, 2.0)) { val r = s.push(v, System.currentTimeMillis()); s = r.first; if (r.second != null) ev = (ev ?: "") + r.second }
        assertEquals("DOWNUP", ev); assertEquals(1, s.outages); assertEquals(3, s.lost); assertFalse(s.down)
    }

    @Test fun refusedVsUnreachable() {
        // так Android (libcore) формулирует ошибки connect()
        assertTrue(isRefused(java.net.ConnectException("failed to connect to /192.168.1.5 (port 80) from /192.168.1.20 (port 41234) after 280ms: isConnected failed: ECONNREFUSED (Connection refused)")))
        assertFalse(isRefused(java.net.ConnectException("failed to connect to /192.168.1.77 (port 80) from /192.168.1.20 (port 40112) after 12ms: isConnected failed: EHOSTUNREACH (No route to host)")))
        assertFalse(isRefused(java.net.ConnectException("failed to connect to /10.0.0.1 (port 443): connect failed: ENETUNREACH (Network is unreachable)")))
        assertTrue(isRefused(java.net.ConnectException("Connection refused")))
    }
}
