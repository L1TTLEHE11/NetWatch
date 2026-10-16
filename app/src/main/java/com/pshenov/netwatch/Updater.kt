package com.pshenov.netwatch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Описание версии из update.json в репозитории:
 * {"versionCode":10,"versionName":"2.3","apk":"apk/NetWatch-2.3-universal.apk","sha256":"…","size":1100000,
 *  "date":"2026-10-16","notes":["…","…"]}
 */
data class UpdateInfo(
    val versionCode: Long, val versionName: String, val apkPath: String, val sha256: String,
    val size: Long, val date: String, val notes: List<String>,
) {
    companion object {
        fun parse(json: String): UpdateInfo {
            val o = JSONObject(json)
            val notes = o.optJSONArray("notes")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            return UpdateInfo(o.getLong("versionCode"), o.getString("versionName"), o.getString("apk"),
                o.getString("sha256").lowercase(), o.optLong("size", 0), o.optString("date"), notes)
        }
    }
}

class UpdateException(msg: String) : Exception(msg)

object Updater {
    /**
     * Откуда берём обновления. Несколько зеркал: если GitHub недоступен или тормозит,
     * пробуем jsDelivr (CDN, кэширует репозиторий GitHub).
     */
    val mirrors = listOf(
        "https://raw.githubusercontent.com/L1TTLEHE11/NetWatch/main/",
        "https://cdn.jsdelivr.net/gh/L1TTLEHE11/NetWatch@main/",
        "https://github.com/L1TTLEHE11/NetWatch/raw/main/",
    )

    private fun open(url: String, timeout: Int) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeout; readTimeout = timeout; instanceFollowRedirects = true
        useCaches = false
        setRequestProperty("User-Agent", "NetWatch-Updater")
        setRequestProperty("Cache-Control", "no-cache")
    }

    /** Прочитать update.json с первого ответившего зеркала. */
    fun fetch(bases: List<String> = mirrors): UpdateInfo {
        var last: Exception? = null
        for (b in bases) {
            try {
                val c = open(b + "update.json?t=" + System.currentTimeMillis() / 60_000, 6000)
                try {
                    if (c.responseCode != 200) throw UpdateException("HTTP ${c.responseCode}")
                    return UpdateInfo.parse(c.inputStream.bufferedReader().readText())
                } finally { c.disconnect() }
            } catch (e: Exception) { last = e }
        }
        throw UpdateException("Сервер обновлений недоступен" + (last?.message?.let { ": $it" } ?: ""))
    }

    fun currentVersionCode(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(65536); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Скачать APK (пробуя зеркала по очереди) и проверить SHA-256 — битый или подменённый
     * файл до установки не дойдёт. onProgress(доля 0..1).
     */
    fun download(info: UpdateInfo, dir: File, onProgress: (Float) -> Unit, running: () -> Boolean = { true }, bases: List<String> = mirrors): File {
        dir.mkdirs()
        dir.listFiles()?.forEach { if (it.name.endsWith(".apk") || it.name.endsWith(".part")) it.delete() }
        val out = File(dir, "NetWatch-${info.versionName}.apk")
        val part = File(dir, out.name + ".part")
        var last: Exception? = null
        for (b in bases) {
            try {
                val c = open(b + info.apkPath, 15000)
                try {
                    if (c.responseCode != 200) throw UpdateException("HTTP ${c.responseCode}")
                    val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
                    var got = 0L
                    c.inputStream.use { inp ->
                        part.outputStream().use { o ->
                            val buf = ByteArray(65536)
                            while (true) {
                                if (!running()) throw UpdateException("Загрузка отменена")
                                val n = inp.read(buf); if (n < 0) break
                                o.write(buf, 0, n); got += n
                                if (total > 0) onProgress((got.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                } finally { c.disconnect() }
                val sum = sha256(part)
                if (sum != info.sha256) { part.delete(); throw UpdateException("Контрольная сумма не совпала — файл повреждён") }
                part.renameTo(out)
                onProgress(1f)
                return out
            } catch (e: UpdateException) {
                if (e.message == "Загрузка отменена") throw e
                last = e
            } catch (e: Exception) { last = e }
        }
        throw UpdateException("Не удалось скачать обновление" + (last?.message?.let { ": $it" } ?: ""))
    }

    /** Может ли приложение предложить установку (Android 8+: «Установка неизвестных приложений»). */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Открыть системный установщик. Приложение закроется, данные и настройки сохранятся. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
