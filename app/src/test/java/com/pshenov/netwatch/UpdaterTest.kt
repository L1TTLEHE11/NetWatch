package com.pshenov.netwatch

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.nio.file.Files
import java.security.MessageDigest

class UpdaterTest {
    private lateinit var srv: ServerSocket
    private val apk = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private var json = ""
    private var served = apk

    @Before fun up() {
        // минимальный HTTP-сервер: /update.json и /apk/*
        srv = ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            while (!srv.isClosed) {
                val c = try { srv.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) {
                    c.use { sock ->
                        val line = sock.getInputStream().bufferedReader().readLine() ?: return@use
                        val path = line.split(' ')[1]
                        val body = if (path.startsWith("/update.json")) json.toByteArray() else served
                        val o = sock.getOutputStream()
                        o.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); o.write(body); o.flush()
                    }
                }
            }
        }
        json = """{"versionCode":10,"versionName":"2.3","apk":"apk/NetWatch-2.3-universal.apk","sha256":"$sha","size":${apk.size},"date":"2026-10-16","notes":["Обновления из приложения","Исправления"]}"""
    }
    @After fun down() { srv.close() }

    private val dead = "http://127.0.0.1:1/"   // первое «зеркало» недоступно
    private val live get() = "http://127.0.0.1:${srv.localPort}/"

    @Test fun fetchFallsBackToNextMirror() {
        val i = Updater.fetch(listOf(dead, live))
        assertEquals(10L, i.versionCode); assertEquals("2.3", i.versionName); assertEquals(2, i.notes.size)
    }

    @Test fun downloadVerifiesChecksum() {
        val dir = Files.createTempDirectory("upd").toFile()
        val i = Updater.fetch(listOf(live))
        var last = 0f
        val f = Updater.download(i, dir, { last = it }, bases = listOf(dead, live))
        assertEquals(sha, Updater.sha256(f)); assertEquals(1f, last, 0f); assertTrue(f.name.endsWith(".apk"))
    }

    @Test fun tamperedFileRejected() {
        val dir = Files.createTempDirectory("upd").toFile()
        val i = Updater.fetch(listOf(live))
        served = apk.copyOf().also { it[100] = (it[100] + 1).toByte() }   // подмена файла
        try { Updater.download(i, dir, { }, bases = listOf(live)); fail("подменённый файл не должен пройти") }
        catch (e: UpdateException) { assertTrue(e.message!!.contains("Контрольная сумма")) }
        assertTrue("после ошибки не остаётся apk", dir.listFiles()!!.none { it.name.endsWith(".apk") })
    }

    @Test fun allMirrorsDown() {
        try { Updater.fetch(listOf(dead)); fail() } catch (e: UpdateException) { assertTrue(e.message!!.startsWith("Сервер обновлений недоступен")) }
    }
}
