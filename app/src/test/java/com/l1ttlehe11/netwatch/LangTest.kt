package com.l1ttlehe11.netwatch

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LangTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun strings(path: String): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""").findAll(File(path).readText()).associate { it.groupValues[1] to it.groupValues[2] }

    /** У каждой строки есть перевод, и подстановки совпадают — иначе на одном из языков приложение упадёт. */
    @Test fun resourcesConsistent() {
        val en = strings("src/main/res/values/strings.xml"); val ru = strings("src/main/res/values-ru/strings.xml")
        assertEquals("ключи en/ru", en.keys, ru.keys)
        val tok = Regex("""%%|%\d+\$[sd]|%[-+ 0#]*\d*(?:\.\d+)?[sdf]""")
        for ((k, v) in en) assertEquals("подстановки в $k", tok.findAll(v).map { it.value }.toList(), tok.findAll(ru[k]!!).map { it.value }.toList())
        assertTrue(en.size > 500)
    }

    @Test fun switchingLanguageChangesText() {
        Lang.pin(ctx, "en")
        assertEquals("Home", Section.HOME.title); assertEquals("Speed", HKind.SPEED.label); assertEquals("Printer", PortInfo.guessKind(listOf(9100), false, false))
        assertEquals("Router 192.168.1.1 is online", tr(R.string.is_online, "Router", "192.168.1.1"))
        Lang.pin(ctx, "ru")
        assertEquals("Главная", Section.HOME.title); assertEquals("Скорость", HKind.SPEED.label); assertEquals("Принтер", PortInfo.guessKind(listOf(9100), false, false))
        assertEquals("Today", run { Lang.pin(ctx, "en"); History.dayLabel(System.currentTimeMillis()) })
    }

    @Test fun updateNotesFollowLanguage() {
        val j = """{"versionCode":1,"versionName":"1","apk":"a","sha256":"0","notes":["Русский (старый формат)"],"notes_ru":["Русский"],"notes_en":["English"]}"""
        assertEquals(listOf("English"), UpdateInfo.parse(j, "en").notes)
        assertEquals(listOf("Русский"), UpdateInfo.parse(j, "ru").notes)
        assertEquals("неизвестный язык → английский", listOf("English"), UpdateInfo.parse(j, "de").notes)
        assertEquals("старый update.json", listOf("Только notes"), UpdateInfo.parse("""{"versionCode":1,"versionName":"1","apk":"a","sha256":"0","notes":["Только notes"]}""", "en").notes)
    }
}
