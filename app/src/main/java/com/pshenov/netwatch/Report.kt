package com.pshenov.netwatch

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Report {
    private fun stamp() = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())

    private fun dir(ctx: Context) = File(ctx.cacheDir, "reports").apply { mkdirs() }

    private fun share(ctx: Context, f: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Текст → PDF (A4, моноширинный шрифт, автоматический перенос и разбивка на страницы). */
    fun pdf(ctx: Context, title: String, body: String): File {
        val doc = PdfDocument()
        val w = 595; val h = 842; val margin = 36f
        val head = Paint().apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 13f; color = 0xFFFF003C.toInt() }
        val text = Paint().apply { typeface = Typeface.MONOSPACE; textSize = 8.2f; color = 0xFF111111.toInt() }
        val foot = Paint().apply { typeface = Typeface.MONOSPACE; textSize = 7f; color = 0xFF888888.toInt() }
        val maxChars = ((w - 2 * margin) / text.measureText("M")).toInt()
        val lines = body.lines().flatMap { l -> if (l.isEmpty()) listOf("") else l.chunked(maxChars) }
        val lh = 10.6f
        val perPage = ((h - 2 * margin - 40) / lh).toInt()
        val pages = lines.chunked(perPage).ifEmpty { listOf(emptyList()) }
        pages.forEachIndexed { i, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            val c = page.canvas
            c.drawText(title, margin, margin + 6, head)
            var y = margin + 28
            for (l in chunk) {
                text.isFakeBoldText = l.startsWith("■") || l.startsWith("==")
                c.drawText(l, margin, y, text); y += lh
            }
            c.drawText(tr(R.string.netwatch_network_scanner_created_by_l1ttlehe11, i + 1, pages.size), margin, h - margin / 2, foot)
            doc.finishPage(page)
        }
        val f = File(dir(ctx), tr(R.string.netwatch_report_pdf, stamp()))
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun sharePdf(ctx: Context, title: String, body: String) = share(ctx, pdf(ctx, title, body), "application/pdf", title)

    fun shareText(ctx: Context, title: String, body: String) {
        val f = File(dir(ctx), tr(R.string.netwatch_report_txt, stamp())).apply { writeText(title + "\n\n" + body) }
        share(ctx, f, "text/plain", title)
    }

    fun shareCsv(ctx: Context, name: String, csv: String) {
        // BOM — чтобы Excel сразу открыл кириллицу правильно
        val f = File(dir(ctx), "${name}_${stamp()}.csv").apply { writeText("﻿" + csv) }
        share(ctx, f, "text/csv", name)
    }

    fun csvCell(s: Any?): String {
        val v = s?.toString() ?: ""
        return if (v.any { it == ';' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}
