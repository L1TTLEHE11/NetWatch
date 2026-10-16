package com.pshenov.netwatch

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HistoryTest {
    private fun fresh(): File = Files.createTempDirectory("nwhist").toFile()

    private fun item(t: Long, k: HKind = HKind.SPEED, id: Long = t) =
        HItem(id, t, k, "BeregMorya", "↓ 50 ↑ 20", "пинг 12", "детали\nстрока 2 с ; и \"кавычками\"", 50.0, 20.0, 12.0)

    @Test fun persistAndReload() {
        val d = fresh()
        History.init(d, 0)
        val now = System.currentTimeMillis()
        History.add(item(now - 1000)); History.add(item(now))
        History.upsert(item(now, id = now).copy(title = "обновлено"))   // замена по id, не дубль
        assertEquals(2, History.items.value.size)
        // «перезапуск приложения»: читаем тот же файл заново
        val f = File(d, "history.jsonl")
        val reloaded = f.readLines().filter { it.isNotBlank() }.map { HItem.fromJson(org.json.JSONObject(it)) }
        assertEquals(2, reloaded.size)
        assertEquals("обновлено", reloaded.first { it.id == now }.title)
        assertEquals("детали\nстрока 2 с ; и \"кавычками\"", reloaded.first().details)
        assertEquals(50.0, reloaded.first().num1!!, 0.0)
    }

    @Test fun retention() {
        val d = fresh()
        History.init(d, 0)
        val now = System.currentTimeMillis()
        History.add(item(now - 40L * 86_400_000)); History.add(item(now - 10L * 86_400_000)); History.add(item(now))
        History.clear(HKind.MONITOR)
        assertEquals(3, History.items.value.size)
        // срок 30 дней
        val f = History::class.java.getDeclaredField("retentionDays"); f.isAccessible = true; f.setInt(History, 30)
        History.prune(now)
        assertEquals(2, History.items.value.size)
    }

    @Test fun knownDevices() {
        val d = fresh()
        History.init(d, 0)
        fun h(ip: String, mac: String?) = LanHost(ip, 1.0, emptyList(), null, null, mac, "Устройство", false, false)
        val first = History.rememberDevices("net1", listOf(h("192.168.1.10", "AA:00:00:00:00:01"), h("192.168.1.20", null)))
        assertTrue("первый скан не помечает всё новым", first.isEmpty())
        val second = History.rememberDevices("net1", listOf(h("192.168.1.10", "aa:00:00:00:00:01"), h("192.168.1.30", "AA:00:00:00:00:03")))
        assertEquals(setOf("AA:00:00:00:00:03"), second)
        assertEquals(3, History.knownDevices("net1").size)
        assertTrue(History.knownDevices("net2").isEmpty())
    }

    @Test fun hostsJson() {
        val hs = listOf(LanHost("192.168.1.1", 2.5, listOf(80, 8291), "router", "RouterOS", "AA:BB:CC:00:11:22", "Роутер MikroTik (шлюз)", true, false))
        assertEquals(hs, hostsFromJson(hostsToJson(hs)))
    }

    @Test fun speedCsv() {
        val now = System.currentTimeMillis()
        val csv = History.speedCsv(listOf(item(now), item(now - 1, HKind.SCAN)))
        assertEquals(2, csv.lines().size)
        assertTrue(csv.lines()[1].contains("BeregMorya;12;50,0;20,0") || csv.lines()[1].contains("BeregMorya;12;50.0;20.0"))
    }
}
