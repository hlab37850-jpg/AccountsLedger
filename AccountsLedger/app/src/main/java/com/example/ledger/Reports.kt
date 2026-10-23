package com.example.ledger

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class ReportDoc(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val footer: List<String> = emptyList()
)

fun fmt(d: Double): String =
    if (d == Math.floor(d) && abs(d) < 1e15) String.format(Locale.US, "%,d", d.toLong())
    else String.format(Locale.US, "%,.2f", d)

fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun stamp(prefs: Prefs, date: String): String =
    if (prefs.b("showTime", true)) date + " " + SimpleDateFormat("HH:mm", Locale.US).format(Date()) else date

/** Parses numbers typed with Arabic-Indic digits too. */
fun num(s: String): Double {
    val sb = StringBuilder()
    for (ch in s.trim()) {
        when (ch) {
            in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
            '٫', '،', ',' -> sb.append('.')
            else -> sb.append(ch)
        }
    }
    return sb.toString().toDoubleOrNull() ?: 0.0
}

object ReportBuilder {
    fun build(
        type: Int,
        entries: List<EntryRow>,
        accounts: List<AccountRow>,
        cats: List<Category>,
        curs: List<Currency>,
        catId: Long,
        from: String?,
        to: String?,
        sortByBalance: Boolean,
        dW: String,
        cW: String
    ): ReportDoc {
        val curName = curs.associate { it.id to it.name }
        val catName = cats.associate { it.id to it.name }
        val er = entries.filter { (from == null || it.date.take(10) >= from) && (to == null || it.date.take(10) <= to) }
        val period = if (from != null || to != null) " (${from ?: "..."} - ${to ?: "..."})" else ""

        fun summary(list: List<Triple<String, Double, Double>>): List<String> =
            list.map { (c, d, cr) -> "الإجمالي ($c): $dW ${fmt(d)}  |  $cW ${fmt(cr)}  |  الرصيد ${fmt(cr - d)}" }

        fun grouped(list: List<EntryRow>, key: (EntryRow) -> Pair<String, Long>): List<List<String>> =
            list.groupBy(key).entries
                .sortedWith(compareBy({ it.key.first }, { it.key.second }))
                .map { (k, v) ->
                    val d = v.filter { it.isDebit }.sumOf { it.amount }
                    val c = v.filter { !it.isDebit }.sumOf { it.amount }
                    listOf(k.first, curName[k.second] ?: "", fmt(d), fmt(c), fmt(c - d))
                }

        return when (type) {
            0 -> {
                var list = accounts.filter { it.categoryId == catId }
                list = if (sortByBalance) list.sortedByDescending { abs(it.balance) } else list.sortedBy { it.name }
                val totals = curs.mapNotNull { c ->
                    val rs = list.filter { it.currencyId == c.id }
                    if (rs.isEmpty()) null else Triple(c.name, rs.sumOf { it.debit }, rs.sumOf { it.credit })
                }
                ReportDoc(
                    "إجمالي المبالغ - ${catName[catId] ?: ""}",
                    listOf("الحساب", "العملة", dW, cW, "الرصيد"),
                    list.map { listOf(it.name, curName[it.currencyId] ?: "", fmt(it.debit), fmt(it.credit), fmt(it.balance)) },
                    summary(totals)
                )
            }
            1 -> {
                val totals = curs.mapNotNull { c ->
                    val rs = er.filter { it.currencyId == c.id }
                    if (rs.isEmpty()) null else Triple(c.name, rs.filter { it.isDebit }.sumOf { it.amount }, rs.filter { !it.isDebit }.sumOf { it.amount })
                }
                ReportDoc(
                    "تفصيل جميع المبالغ$period",
                    listOf("التاريخ", "الحساب", "التفاصيل", dW, cW),
                    er.map {
                        listOf(it.date, it.accountName, it.details, if (it.isDebit) fmt(it.amount) else "", if (!it.isDebit) fmt(it.amount) else "")
                    },
                    summary(totals)
                )
            }
            2 -> ReportDoc(
                "إجمالي المبالغ شهرياً$period",
                listOf("الشهر", "العملة", dW, cW, "الرصيد"),
                grouped(er) { it.date.take(7) to it.currencyId }
            )
            3 -> ReportDoc(
                "إجمالي التصنيفات والعملات$period",
                listOf("التصنيف", "العملة", dW, cW, "الرصيد"),
                er.groupBy { it.categoryId to it.currencyId }.entries
                    .sortedWith(compareBy({ catName[it.key.first] ?: "" }, { it.key.second }))
                    .map { (k, v) ->
                        val d = v.filter { it.isDebit }.sumOf { it.amount }
                        val c = v.filter { !it.isDebit }.sumOf { it.amount }
                        listOf(catName[k.first] ?: "", curName[k.second] ?: "", fmt(d), fmt(c), fmt(c - d))
                    }
            )
            else -> ReportDoc(
                "تفصيلي المبالغ شهرياً - ${catName[catId] ?: ""}$period",
                listOf("الشهر", "العملة", dW, cW, "الرصيد"),
                grouped(er.filter { it.categoryId == catId }) { it.date.take(7) to it.currencyId }
            )
        }
    }
}

object Exporter {
    private fun shareDir(ctx: Context) = File(ctx.cacheDir, "share").apply { mkdirs() }

    fun toText(doc: ReportDoc): String {
        val sb = StringBuilder()
        sb.append(doc.title).append('\n').append("─".repeat(18)).append('\n')
        for (r in doc.rows) sb.append(r.filter { it.isNotBlank() }.joinToString(" | ")).append('\n')
        if (doc.footer.isNotEmpty()) sb.append("─".repeat(18)).append('\n')
        for (f in doc.footer) sb.append(f).append('\n')
        return sb.toString()
    }

    fun shareWhatsapp(ctx: Context, doc: ReportDoc) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, toText(doc))
            setPackage("com.whatsapp")
        }
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            val j = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, toText(doc)) }
            ctx.startActivity(Intent.createChooser(j, "مشاركة"))
        }
    }

    fun shareCsv(ctx: Context, doc: ReportDoc) {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("\uFEFF")
        sb.append(q(doc.title)).append('\n')
        sb.append(doc.headers.joinToString(",") { q(it) }).append('\n')
        for (r in doc.rows) sb.append(r.joinToString(",") { q(it) }).append('\n')
        for (f in doc.footer) sb.append(q(f)).append('\n')
        val f = File(shareDir(ctx), "report.csv")
        f.writeText(sb.toString(), Charsets.UTF_8)
        Backup.shareFile(ctx, f, "text/csv")
    }

    fun loadImage(ctx: Context, name: String): Bitmap? {
        val f = File(ctx.filesDir, "$name.png")
        return if (f.exists()) BitmapFactory.decodeFile(f.path) else null
    }

    fun sharePdf(ctx: Context, doc: ReportDoc, prefs: Prefs) {
        val f = File(shareDir(ctx), "report.pdf")
        writePdf(ctx, doc, prefs, f)
        Backup.shareFile(ctx, f, "application/pdf")
    }

    private fun fit(t: String, maxW: Float, p: Paint): String {
        val n = p.breakText(t, true, maxW, null)
        return if (n < t.length) t.substring(0, n) else t
    }

    private fun writePdf(ctx: Context, doc: ReportDoc, prefs: Prefs, out: File) {
        val pdf = PdfDocument()
        val w = 595
        val h = 842
        val m = 30f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val fill = Paint().apply { color = Color.parseColor("#0F9D7A") }
        val grid = Paint().apply { color = Color.parseColor("#CCCCCC"); strokeWidth = 0.6f }

        var pageNo = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        var c = page.canvas
        var y = 40f

        // header: logo + business names
        val logo = loadImage(ctx, "logo")
        if (logo != null) c.drawBitmap(Bitmap.createScaledBitmap(logo, 56, 56, true), m, 24f, null)
        p.textSize = 16f; p.isFakeBoldText = true
        c.drawText(prefs.s("nameAr").ifBlank { "دفتر الحسابات" }, w - m, y, p)
        p.textSize = 10f; p.isFakeBoldText = false
        y += 15f
        val en = prefs.s("nameEn"); if (en.isNotBlank()) { c.drawText(en, w - m, y, p); y += 13f }
        val addr = prefs.s("addrAr"); if (addr.isNotBlank()) { c.drawText(addr, w - m, y, p); y += 13f }
        val ph = prefs.s("phone"); if (ph.isNotBlank()) { c.drawText(ph, w - m, y, p); y += 13f }
        y = maxOf(y, 90f) + 14f

        p.textAlign = Paint.Align.CENTER; p.textSize = 14f; p.isFakeBoldText = true
        c.drawText(doc.title, w / 2f, y, p)
        y += 14f
        p.textSize = 9f; p.isFakeBoldText = false
        c.drawText(today(), w / 2f, y, p)
        y += 16f
        p.textAlign = Paint.Align.RIGHT; p.textSize = 10f

        val n = doc.headers.size
        val colW = (w - 2 * m) / n
        val rowH = 22f

        fun drawRow(cells: List<String>, header: Boolean) {
            if (header) {
                c.drawRect(m, y, w - m, y + rowH, fill)
                p.color = Color.WHITE; p.isFakeBoldText = true
            } else {
                p.color = Color.BLACK; p.isFakeBoldText = false
            }
            for (i in 0 until n) {
                val right = w - m - i * colW
                c.drawText(fit(cells.getOrElse(i) { "" }, colW - 8f, p), right - 4f, y + 15f, p)
            }
            p.color = Color.BLACK; p.isFakeBoldText = false
            c.drawLine(m, y + rowH, w - m, y + rowH, grid)
            y += rowH
        }

        fun newPageIfNeeded(need: Float) {
            if (y + need > h - 40f) {
                pdf.finishPage(page)
                pageNo++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
                c = page.canvas
                y = 40f
            }
        }

        drawRow(doc.headers, true)
        for (r in doc.rows) {
            newPageIfNeeded(rowH)
            if (y == 40f) drawRow(doc.headers, true)
            drawRow(r, false)
        }
        y += 14f
        p.textAlign = Paint.Align.RIGHT; p.textSize = 11f; p.isFakeBoldText = true
        for (f in doc.footer) {
            newPageIfNeeded(20f)
            c.drawText(fit(f, w - 2 * m, p), w - m, y, p)
            y += 18f
        }
        p.isFakeBoldText = false
        if (prefs.b("signature")) {
            newPageIfNeeded(60f)
            y += 20f
            c.drawText("التوقيع: ..............................", w - m, y, p)
        }
        if (prefs.b("stamp")) {
            val st = loadImage(ctx, "stamp")
            if (st != null) {
                newPageIfNeeded(90f)
                c.drawBitmap(Bitmap.createScaledBitmap(st, 80, 80, true), m, y + 10f, null)
            }
        }
        pdf.finishPage(page)
        out.outputStream().use { pdf.writeTo(it) }
        pdf.close()
    }
}
