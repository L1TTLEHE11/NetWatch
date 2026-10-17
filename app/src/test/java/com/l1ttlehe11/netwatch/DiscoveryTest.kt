package com.l1ttlehe11.netwatch

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

// Robolectric — чтобы были ресурсы строк; язык фиксируем русский, на нём написаны проверки
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class DiscoveryTest {
    @org.junit.Before fun pinLang() { Lang.pin(androidx.test.core.app.ApplicationProvider.getApplicationContext(), "ru") }

    /** Ответ mDNS, как его шлёт Android TV: PTR со сжатием имени + TXT (fn, md) + A. */
    private fun tvResponse(): ByteArray {
        val o = ByteArrayOutputStream()
        fun u16(v: Int) { o.write(v shr 8 and 0xff); o.write(v and 0xff) }
        fun name(vararg l: String) { l.forEach { o.write(it.toByteArray().size); o.write(it.toByteArray()) }; o.write(0) }
        u16(0); u16(0x8400); u16(0); u16(1); u16(0); u16(2)
        val svcPos = o.size()
        name("_androidtvremote2", "_tcp", "local"); u16(12); u16(1); u16(0); u16(120)
        val inst = "Living Room TV".toByteArray()
        u16(inst.size + 1 + 2); o.write(inst.size); o.write(inst); u16(0xC000 or svcPos)      // экземпляр + указатель на имя сервиса
        // TXT для экземпляра (имя целиком через указатель на rdata PTR не делаем — пишем полностью)
        name("Living Room TV", "_androidtvremote2", "_tcp", "local"); u16(16); u16(1); u16(0); u16(120)
        val t1 = "fn=Гостиная TV".toByteArray(); val t2 = "md=BRAVIA 4K".toByteArray()
        u16(2 + t1.size + t2.size); o.write(t1.size); o.write(t1); o.write(t2.size); o.write(t2)
        name("Android", "local"); u16(1); u16(1); u16(0); u16(120); u16(4); listOf(192, 168, 1, 77).forEach { o.write(it) }
        return o.toByteArray()
    }

    @Test fun parseAndroidTvAnswer() {
        val b = tvResponse()
        val r = Discovery.parseMdns(b, b.size)
        assertEquals("_androidtvremote2._tcp.local" to "Living Room TV._androidtvremote2._tcp.local", r.ptr.single())
        assertEquals("Гостиная TV", r.txt["Living Room TV._androidtvremote2._tcp.local"]!!["fn"])
        assertEquals("192.168.1.77", r.a["Android.local"])
    }

    @Test fun queryIsWellFormed() {
        val q = Discovery.mdnsQuery(0, listOf("_googlecast._tcp.local"))
        assertEquals(1, q[5].toInt())
        assertEquals("_googlecast._tcp.local", Discovery.readName(q, 12, q.size).first)
    }

    @Test fun garbageDoesNotCrash() {
        val junk = ByteArray(200) { (it * 37).toByte() }
        Discovery.parseMdns(junk, junk.size)
        Discovery.parseMdns(ByteArray(5), 5)
    }

    /** Полный обмен: поддельный «телевизор» отвечает на наш запрос по UDP. */
    @Test fun mdnsRoundTrip() {
        val tv = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            val buf = ByteArray(1500)
            val q = DatagramPacket(buf, buf.size); tv.receive(q)
            val resp = tvResponse()
            tv.send(DatagramPacket(resp, resp.size, q.address, q.port))
        }
        val found = Discovery.mdns(1200, "127.0.0.1", tv.localPort)
        tv.close()
        val d = found.single()
        assertEquals("127.0.0.1", d.ip); assertEquals("Android TV", d.kind); assertEquals("Гостиная TV", d.name); assertEquals("BRAVIA 4K", d.model)
    }

    @Test fun ssdpHeadersAndKinds() {
        val h = Discovery.parseSsdp("HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.77:8008/ssdp/device-desc.xml\r\nST: urn:dial-multiscreen-org:service:dial:1\r\nSERVER: Linux/3.10 UPnP/1.0 Android/11\r\n\r\n")
        assertEquals("http://192.168.1.77:8008/ssdp/device-desc.xml", h["LOCATION"])
        assertEquals("Smart TV / медиаплеер", Discovery.kindFromUpnp(h["ST"], h["SERVER"], null))
        assertEquals("Роутер / шлюз", Discovery.kindFromUpnp("urn:schemas-upnp-org:device:InternetGatewayDevice:1", null, null))
        assertEquals("Принтер", Discovery.kindFromUpnp("urn:schemas-upnp-org:device:Printer:1", null, null))
        assertNull(Discovery.kindFromUpnp("urn:schemas-upnp-org:device:Basic:1", "Linux", "box"))
    }

    @Test fun tvKindBeatsGenericPorts() {
        val d = Discovered("192.168.1.77", "Гостиная TV", "Android TV", null, "mDNS")
        assertEquals("Android TV", LanScanner.kindOf(listOf(8443), false, false, d))
        assertEquals("Android TV", LanScanner.kindOf(listOf(8008, 8009), false, false, d))
        assertEquals("Роутер / шлюз", LanScanner.kindOf(emptyList(), true, false, d))
        assertEquals("Android TV", PortInfo.guessKind(listOf(6466), false, false))
    }

    @Test fun nioProbeFindsOpenPortsAndRefusals() {
        val a = ServerSocket(0); val b = ServerSocket(0)
        val closed = ServerSocket(0).let { val p = it.localPort; it.close(); p }
        val lo = InetAddress.getByName("127.0.0.1")
        val all = LanScanner.tcpProbe(lo, intArrayOf(a.localPort, closed, b.localPort), 700, stopOnFirst = false)
        assertEquals(listOf(a.localPort, b.localPort).sorted(), all.open); assertFalse(all.refusedOnly); assertNotNull(all.ms)
        val ref = LanScanner.tcpProbe(lo, intArrayOf(closed), 700, stopOnFirst = true)
        assertTrue("закрытый порт = отказ, хост живой", ref.refusedOnly); assertNotNull(ref.ms)
        a.close(); b.close()
    }
}
