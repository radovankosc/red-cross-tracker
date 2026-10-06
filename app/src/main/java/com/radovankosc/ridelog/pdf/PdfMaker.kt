package com.radovankosc.ridelog.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.radovankosc.ridelog.data.AppSettings
import com.radovankosc.ridelog.data.Customer
import com.radovankosc.ridelog.data.Fmt
import com.radovankosc.ridelog.data.RideRow
import com.radovankosc.ridelog.data.round1
import com.radovankosc.ridelog.data.round2
import java.io.File
import java.time.LocalDate

private const val PAGE_W = 595 // A4 in PDF points
private const val PAGE_H = 842
private const val MARGIN = 48f

/** Draws text top to bottom across as many A4 pages as needed. */
private class PdfWriter {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageCount = 0
    lateinit var canvas: Canvas
    var y = MARGIN
    var onNewPage: (() -> Unit)? = null

    fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        this.color = color
    }

    fun newPage() {
        page?.let { doc.finishPage(it) }
        pageCount++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageCount).create())
        page = p
        canvas = p.canvas
        y = MARGIN
        onNewPage?.invoke()
    }

    fun ensureSpace(height: Float) {
        if (page == null || y + height > PAGE_H - MARGIN) newPage()
    }

    /** Writes one line (wrapping long text) and moves down. */
    fun line(text: String, size: Float = 11f, bold: Boolean = false, x: Float = MARGIN, gap: Float = 1.45f) {
        val p = paint(size, bold)
        wrap(text, p, PAGE_W - MARGIN - x).forEach {
            ensureSpace(size * gap)
            y += size * gap
            canvas.drawText(it, x, y, p)
        }
    }

    fun labelValue(label: String, value: String) {
        val size = 11f
        val lp = paint(size, color = Color.DKGRAY)
        val vp = paint(size, bold = true)
        val valueX = MARGIN + 150f
        val lines = wrap(value, vp, PAGE_W - MARGIN - valueX)
        lines.forEachIndexed { i, v ->
            ensureSpace(size * 1.6f)
            y += size * 1.6f
            if (i == 0) canvas.drawText(label, MARGIN, y, lp)
            canvas.drawText(v, valueX, y, vp)
        }
    }

    fun rule(space: Float = 10f) {
        ensureSpace(space * 2)
        y += space
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, paint(1f, color = Color.GRAY))
        y += space / 2
    }

    fun space(h: Float) {
        y += h
    }

    fun writeTo(file: File): File {
        if (page == null) newPage()
        doc.finishPage(page!!)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    companion object {
        fun fit(text: String, p: Paint, maxWidth: Float): String {
            if (p.measureText(text) <= maxWidth) return text
            var t = text
            while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
            return "$t…"
        }

        fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
            val out = mutableListOf<String>()
            var current = ""
            for (word in text.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (p.measureText(candidate) <= maxWidth || current.isEmpty()) {
                    current = candidate
                } else {
                    out += current
                    current = word
                }
            }
            out += current
            return out.map { fit(it, p, maxWidth) }
        }
    }
}

private fun pdfDir(context: Context) = File(context.cacheDir, "pdfs").apply { mkdirs() }

private fun issuer(s: AppSettings): String =
    listOf(s.driverName, s.organization).filter { it.isNotBlank() }.joinToString(", ")

object PdfMaker {

    fun receipt(context: Context, row: RideRow, customer: Customer?, s: AppSettings): File {
        val r = row.ride
        val w = PdfWriter()
        w.newPage()
        w.line("RECEIPT", size = 22f, bold = true)
        w.line("No. ${Fmt.receiptNo(r)}", size = 16f, bold = true)
        w.rule()
        w.labelValue("Date", Fmt.date(r.dateEpochDay))
        if (issuer(s).isNotBlank()) w.labelValue("Issued by", issuer(s))
        w.labelValue("Customer", row.customerName)
        if (customer != null && customer.homeAddress.isNotBlank()) w.labelValue("Customer address", customer.homeAddress)
        w.rule()
        w.labelValue("From", r.fromAddress)
        w.labelValue("To", r.toAddress)
        w.labelValue("Trip", Fmt.trip(r))
        w.labelValue("Distance", if (r.roundTrip) "${Fmt.km(r.totalKm)} (2 × ${Fmt.km(r.oneWayKm)})" else Fmt.km(r.totalKm))
        w.labelValue("Rate", "${Fmt.money(r.startFee)} starting fee + ${Fmt.money(r.ratePerKm)} per km")
        w.rule()
        w.line("Total: ${Fmt.money(r.price)}", size = 18f, bold = true)
        w.space(70f)
        val p = w.paint(10f, color = Color.DKGRAY)
        w.canvas.drawLine(MARGIN, w.y, MARGIN + 200f, w.y, p)
        w.canvas.drawLine(PAGE_W - MARGIN - 200f, w.y, PAGE_W - MARGIN, w.y, p)
        w.space(14f)
        w.canvas.drawText("Driver's signature", MARGIN, w.y, p)
        w.canvas.drawText("Customer's signature", PAGE_W - MARGIN - 200f, w.y, p)
        return w.writeTo(File(pdfDir(context), "receipt-${r.receiptYear}-${r.receiptNumber}.pdf"))
    }

    fun report(context: Context, rows: List<RideRow>, from: LocalDate, to: LocalDate, s: AppSettings): File {
        val w = PdfWriter()
        // Column positions: left edges, except km and amount which are right-aligned at these x values.
        val xNo = MARGIN
        val xDate = MARGIN + 62f
        val xCustomer = MARGIN + 140f
        val xTrip = MARGIN + 330f
        val xKmRight = MARGIN + 445f
        val xAmountRight = PAGE_W - MARGIN
        val head = w.paint(10f, bold = true)
        val body = w.paint(10f)

        fun right(text: String, x: Float, p: Paint) = w.canvas.drawText(text, x - p.measureText(text), w.y, p)

        fun header() {
            w.y += 12f
            w.canvas.drawText("No.", xNo, w.y, head)
            w.canvas.drawText("Date", xDate, w.y, head)
            w.canvas.drawText("Customer", xCustomer, w.y, head)
            w.canvas.drawText("Trip", xTrip, w.y, head)
            right("km", xKmRight, head)
            right("Amount", xAmountRight, head)
            w.y += 5f
            w.canvas.drawLine(MARGIN, w.y, PAGE_W - MARGIN, w.y, w.paint(1f, color = Color.GRAY))
        }

        w.newPage()
        w.line("Ride report", size = 20f, bold = true)
        w.line("${Fmt.date(from)} – ${Fmt.date(to)}", size = 13f)
        if (issuer(s).isNotBlank()) w.line(issuer(s), size = 11f)
        w.space(10f)
        w.onNewPage = { header() }
        header()

        for (row in rows) {
            val r = row.ride
            w.ensureSpace(16f)
            w.y += 16f
            w.canvas.drawText(Fmt.receiptNo(r), xNo, w.y, body)
            w.canvas.drawText(Fmt.date(r.dateEpochDay), xDate, w.y, body)
            w.canvas.drawText(PdfWriter.fit(row.customerName, body, xTrip - xCustomer - 8f), xCustomer, w.y, body)
            w.canvas.drawText(Fmt.trip(r), xTrip, w.y, body)
            right(Fmt.km(r.totalKm).removeSuffix(" km"), xKmRight, body)
            right(Fmt.money(r.price), xAmountRight, body)
        }
        w.onNewPage = null

        w.rule()
        val totalKm = round1(rows.sumOf { it.ride.totalKm })
        val total = round2(rows.sumOf { it.ride.price })
        w.labelValue("Receipts", rows.size.toString())
        w.labelValue("Total distance", Fmt.km(totalKm))
        w.labelValue("Total amount", Fmt.money(total))
        return w.writeTo(File(pdfDir(context), "report-$from-$to.pdf"))
    }
}

object Share {
    /** Opens the share sheet (or an email app, when an address is given) with the file attached. */
    fun file(context: Context, file: File, mime: String, subject: String, text: String, email: String? = null) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            if (!email.isNullOrBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(email.trim()))
            clipData = ClipData.newRawUri(subject, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }
}
