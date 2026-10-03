package online.draran.billing.core.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import online.draran.billing.core.common.AmountInWords
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.IndianStates
import online.draran.billing.core.model.Invoice
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Percent
import online.draran.billing.core.model.Qty
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A4 invoice layout (595 x 842 pt). Pages are laid out first so long bills
 * break cleanly and the header row repeats on every page.
 */
class InvoicePdf(
    context: Context,
    private val invoice: Invoice,
    private val business: Business,
    private val logo: android.graphics.Bitmap? = null,
    private val signature: android.graphics.Bitmap? = null,
) {

    private val fonts = PdfFonts(context)
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    private val pageW = 595f
    private val pageH = 842f
    private val margin = 36f
    private val contentW = pageW - 2 * margin
    private val gst = invoice.gstEnabled
    private val showQr = business.showUpiQr && business.upiId.isNotBlank() && invoice.type == DocType.SALE

    private val body = fonts.paint(9f)
    private val bodyMuted = fonts.paint(8f, color = PdfFonts.MUTED)
    private val bold = fonts.paint(9f, fonts.bold)

    // Table columns: (title, width, right-aligned)
    private val columns: List<Triple<String, Float, Boolean>> = buildList {
        add(Triple("#", 22f, false))
        add(Triple(if (invoice.type == DocType.SALE || invoice.type == DocType.ESTIMATE) business.type.item else "Item", 0f, false)) // flexible
        add(Triple("Qty", 58f, true))
        add(Triple("Rate", 66f, true))
        add(Triple("Disc", 38f, true))
        if (gst) add(Triple("GST", 38f, true))
        add(Triple("Amount", 76f, true))
    }
    private val itemColW = contentW - columns.sumOf { it.second.toDouble() }.toFloat()

    private data class Row(val nameLines: List<String>, val sub: String?, val height: Float)

    private val rows: List<Row> = invoice.lines.map { line ->
        val nameLines = wrap(line.name, body, itemColW - 8)
        val sub = listOfNotNull(
            line.hsn.takeIf { it.isNotBlank() }?.let { "HSN/SAC $it" },
            if (gst && line.taxInclusive) "price incl. tax" else null,
        ).joinToString(" · ").ifEmpty { null }
        Row(nameLines, sub, 8f + nameLines.size * 12f + (if (sub != null) 11f else 0f) + 4f)
    }

    private val tableHeaderH = 22f
    private val footerH = 28f
    private val logoBox = 58f
    private val textLeft: Float get() = if (logo != null) margin + logoBox + 12f else margin
    private val textWidth: Float get() = contentW * 0.6f - (textLeft - margin)
    private val firstTableTop: Float = computeFirstTableTop()
    private val nextTableTop = margin + 40f
    private val totalsH: Float = computeTotalsHeight()

    /** Each page: row range and whether the totals block goes on it. */
    private val pages: List<Pair<IntRange, Boolean>> = paginate()
    val pageCount: Int get() = pages.size

    private fun headerInfo(): List<String> = buildList {
        business.address.split("\n").filter { it.isNotBlank() }.forEach { add(it) }
        IndianStates.byCode(business.stateCode)?.let { add("${it.name} (${it.code})") }
        listOf(
            business.phone.takeIf { it.isNotBlank() }?.let { "Ph: $it" },
            business.email.takeIf { it.isNotBlank() },
        ).filterNotNull().joinToString("  ·  ").takeIf { it.isNotBlank() }?.let { add(it) }
        if (business.gstEnabled && business.gstin.isNotBlank()) add("GSTIN: ${business.gstin}")
    }

    /** Bottom of the business block (name, address, title, logo). */
    private fun headerBottom(): Float {
        var y = margin + 10f
        y += wrap(business.name, fonts.paint(17f, fonts.bold), textWidth).size * 21f
        headerInfo().forEach { line -> y += wrap(line, bodyMuted, textWidth).size * 12f }
        val ry = margin + 40f + 14f * (2 + (if (invoice.dueDate != null) 1 else 0))
        val logoBottom = if (logo != null) margin + logoBox else 0f
        return maxOf(y, ry, logoBottom) + 12f
    }

    private fun detailRows(): Int = (if (gst) 2 else 0) + (if (invoice.type.tracksPayment) 1 else 0) + invoice.customFields.size

    private fun computeFirstTableTop(): Float {
        val boxTop = headerBottom()
        val partyRows = 1 + listOf(invoice.partyAddress, invoice.partyPhone, invoice.partyGstin).count { it.isNotBlank() }
        val boxH = maxOf(86f, 36f + 13f * maxOf(detailRows(), partyRows))
        return boxTop + boxH + 14f
    }

    private fun computeTotalsHeight(): Float {
        val left = 16f + 26f + (if (gst) 18f + 14f * (invoice.totals.slabs.size + 1) else 0f) +
            (if (showQr) 110f else 0f) + (if (business.bankDetails.isNotBlank()) 48f else 0f) +
            (if (business.terms.isNotBlank() || invoice.notes.isNotBlank()) 48f else 0f)
        val right = 18f * 10 + 70f + 40f // signature block
        return maxOf(left, right) + 20f
    }

    private fun paginate(): List<Pair<IntRange, Boolean>> {
        val result = mutableListOf<Pair<IntRange, Boolean>>()
        val bottom = pageH - margin - footerH
        var start = 0
        var top = firstTableTop
        while (true) {
            var y = top + tableHeaderH
            var end = start
            while (end < rows.size && y + rows[end].height <= bottom) {
                y += rows[end].height
                end++
            }
            if (end == rows.size) {
                if (y + totalsH <= bottom) {
                    result += (start until end) to true
                } else {
                    result += (start until end) to false
                    result += IntRange.EMPTY to true
                }
                return result
            }
            if (end == start) end = start + 1 // a single huge row still progresses
            result += (start until end) to false
            start = end
            top = nextTableTop
        }
    }

    /** Draws page [index] onto [canvas] (PDF page or bitmap). */
    fun drawPage(canvas: Canvas, index: Int) {
        val (range, withTotals) = pages[index]
        canvas.drawColor(android.graphics.Color.WHITE)
        // Brand strip
        canvas.drawRect(0f, 0f, pageW, 6f, Paint().apply { color = PdfFonts.BRAND })
        var y = if (index == 0) drawHeader(canvas) else drawContinuationHeader(canvas)
        if (!range.isEmpty() || index == 0) y = drawTable(canvas, y, range)
        if (withTotals) drawTotals(canvas, y + 10f)
        drawFooter(canvas, index)
    }

    private fun title(): String = when {
        invoice.type == DocType.SALE && gst -> "TAX INVOICE"
        invoice.type == DocType.SALE -> business.type.billTitle.uppercase()
        else -> invoice.type.title.uppercase()
    }

    private fun drawHeader(canvas: Canvas): Float {
        var y = margin + 10f
        logo?.let { drawFitted(canvas, it, RectF(margin, margin, margin + logoBox, margin + logoBox), alignRight = false) }
        val nameP = fonts.paint(17f, fonts.bold, PdfFonts.INK)
        wrap(business.name, nameP, textWidth).forEach { canvas.drawText(it, textLeft, y + 8f, nameP); y += 21f }
        headerInfo().forEach { line ->
            wrap(line, bodyMuted, textWidth).forEach { canvas.drawText(it, textLeft, y + 4f, if (line.startsWith("GSTIN")) bold else bodyMuted); y += 12f }
        }

        // Title block on the right
        val right = pageW - margin
        val titleP = fonts.paint(16f, fonts.bold, PdfFonts.BRAND, Paint.Align.RIGHT)
        canvas.drawText(title(), right, margin + 18f, titleP)
        val metaL = fonts.paint(8.5f, color = PdfFonts.MUTED, align = Paint.Align.RIGHT)
        val metaV = fonts.paint(9.5f, fonts.bold, align = Paint.Align.RIGHT)
        var ry = margin + 40f
        fun meta(label: String, value: String) {
            canvas.drawText(value, right, ry, metaV)
            canvas.drawText(label, right - metaV.measureText(value) - 8f, ry, metaL)
            ry += 14f
        }
        meta(if (invoice.type == DocType.PURCHASE) "Bill No." else "No.", invoice.number)
        meta("Date", invoice.date.format(dateFmt))
        invoice.dueDate?.let { meta("Due", it.format(dateFmt)) }

        // Bill-to box
        val boxTop = headerBottom()
        val boxH = firstTableTop - 14f - boxTop
        val half = contentW / 2 - 6f
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfFonts.ROW_TINT }
        canvas.drawRoundRect(RectF(margin, boxTop, margin + half, boxTop + boxH), 6f, 6f, boxPaint)
        canvas.drawRoundRect(RectF(margin + half + 12f, boxTop, pageW - margin, boxTop + boxH), 6f, 6f, boxPaint)
        val label = fonts.paint(7.5f, fonts.medium, PdfFonts.MUTED)
        val partyLabel = when {
            invoice.type == DocType.PURCHASE || invoice.type == DocType.PURCHASE_RETURN -> "SUPPLIER"
            business.type == online.draran.billing.core.model.BusinessType.RETAIL -> "BILL TO"
            else -> business.type.party.uppercase()
        }
        canvas.drawText(partyLabel, margin + 10f, boxTop + 15f, label)
        var by = boxTop + 30f
        canvas.drawText(invoice.partyName, margin + 10f, by, fonts.paint(10.5f, fonts.bold)); by += 13f
        listOf(invoice.partyAddress, invoice.partyPhone.takeIf { it.isNotBlank() }?.let { "Ph: $it" }.orEmpty(),
            invoice.partyGstin.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" }.orEmpty())
            .filter { it.isNotBlank() }
            .flatMap { wrap(it, bodyMuted, half - 20f) }
            .take(4)
            .forEach { canvas.drawText(it, margin + 10f, by, bodyMuted); by += 11f }

        val rx = margin + half + 22f
        canvas.drawText("DETAILS", rx, boxTop + 15f, label)
        var dy = boxTop + 30f
        fun detail(k: String, v: String) {
            canvas.drawText(wrap(k, bodyMuted, 76f).first(), rx, dy, bodyMuted)
            canvas.drawText(wrap(v, body, pageW - margin - rx - 90f).first(), rx + 80f, dy, body)
            dy += 13f
        }
        invoice.customFields.forEach { (k, v) -> detail(k, v) }
        if (gst) detail("Place of supply", IndianStates.byCode(invoice.placeOfSupply)?.let { "${it.name} (${it.code})" } ?: invoice.placeOfSupply)
        if (gst) detail("Supply type", if (invoice.interState) "Inter-state (IGST)" else "Intra-state (CGST + SGST)")
        if (invoice.type.tracksPayment) {
            detail("Status", when {
                invoice.paid.paise >= invoice.totals.total.paise -> "Paid"
                invoice.paid.paise > 0 -> "Partly paid"
                else -> "Unpaid"
            })
        }
        return firstTableTop
    }

    private fun drawContinuationHeader(canvas: Canvas): Float {
        val p = fonts.paint(10f, fonts.bold)
        canvas.drawText("${business.name}  ·  ${title()} ${invoice.number} (continued)", margin, margin + 14f, p)
        return nextTableTop
    }

    private fun colX(): List<Float> {
        val xs = mutableListOf<Float>()
        var x = margin
        columns.forEach { (_, w, _) ->
            xs += x
            x += if (w == 0f) itemColW else w
        }
        return xs
    }

    private fun drawTable(canvas: Canvas, top: Float, range: IntRange): Float {
        val xs = colX()
        val headerFill = Paint().apply { color = PdfFonts.BRAND_TINT }
        canvas.drawRoundRect(RectF(margin, top, pageW - margin, top + tableHeaderH), 4f, 4f, headerFill)
        val hp = fonts.paint(8f, fonts.bold, PdfFonts.BRAND)
        columns.forEachIndexed { i, (title, w, right) ->
            val width = if (w == 0f) itemColW else w
            if (right) {
                hp.textAlign = Paint.Align.RIGHT
                canvas.drawText(title, xs[i] + width - 6f, top + 14.5f, hp)
            } else {
                hp.textAlign = Paint.Align.LEFT
                canvas.drawText(title, xs[i] + 6f, top + 14.5f, hp)
            }
        }
        var y = top + tableHeaderH
        val line = Paint().apply { color = PdfFonts.LINE; strokeWidth = 0.6f }
        val rightP = fonts.paint(9f, align = Paint.Align.RIGHT)
        for (index in range) {
            val row = rows[index]
            val l = invoice.lines[index]
            val amounts = invoice.totals.lines[index]
            var ty = y + 15f
            canvas.drawText("${index + 1}", xs[0] + 6f, ty, bodyMuted)
            row.nameLines.forEach { canvas.drawText(it, xs[1] + 6f, ty, body); ty += 12f }
            row.sub?.let { canvas.drawText(it, xs[1] + 6f, ty - 1f, bodyMuted) }
            var c = 2
            fun cell(text: String) {
                val w = columns[c].second
                canvas.drawText(text, xs[c] + w - 6f, y + 15f, rightP)
                c++
            }
            cell("${Qty.format(l.qtyMilli)} ${l.unit}")
            cell(rs(l.rate).removePrefix("₹"))
            cell(if (l.discountBp > 0) "${Percent.format(l.discountBp)}%" else "-")
            if (gst) cell("${Percent.format(l.taxRateBp)}%")
            cell(rs(amounts.total).removePrefix("₹"))
            y += row.height
            canvas.drawLine(margin, y, pageW - margin, y, line)
        }
        return y
    }

    private fun drawTotals(canvas: Canvas, top: Float) {
        val t = invoice.totals
        val boxL = pageW - margin - 220f
        val right = pageW - margin - 8f
        var y = top + 6f
        val lp = fonts.paint(9f, color = PdfFonts.MUTED)
        val vp = fonts.paint(9f, align = Paint.Align.RIGHT)
        fun row(label: String, value: Money, strong: Boolean = false) {
            canvas.drawText(label, boxL + 8f, y + 12f, if (strong) fonts.paint(10.5f, fonts.bold) else lp)
            canvas.drawText(rs(value), right, y + 12f, if (strong) fonts.paint(10.5f, fonts.bold, align = Paint.Align.RIGHT) else vp)
            y += 18f
        }
        row("Sub total", t.subtotal)
        if (!t.discount.isZero) row("Discount", -t.discount)
        if (gst) {
            row("Taxable value", t.taxable)
            if (invoice.interState) row("IGST", t.igst) else {
                row("CGST", t.cgst)
                row("SGST", t.sgst)
            }
        }
        if (!t.roundOff.isZero) row("Round off", t.roundOff)
        // Grand total band
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PdfFonts.BRAND }
        canvas.drawRoundRect(RectF(boxL, y + 2f, pageW - margin, y + 28f), 5f, 5f, band)
        canvas.drawText("Total", boxL + 8f, y + 19.5f, fonts.paint(11f, fonts.bold, android.graphics.Color.WHITE))
        canvas.drawText(rs(t.total), right, y + 19.5f, fonts.paint(11f, fonts.bold, android.graphics.Color.WHITE, Paint.Align.RIGHT))
        y += 34f
        if (invoice.type.tracksPayment) {
            val paidLabel = if (invoice.type.paymentDirection == online.draran.billing.core.model.PaymentDirection.IN) "Received" else "Paid"
            row(paidLabel, invoice.paid)
            row("Balance due", invoice.balance, strong = true)
        }

        // Left column: words, tax summary, QR, bank, terms
        val leftW = boxL - margin - 18f
        var ly = top + 6f
        canvas.drawText("Amount in words", margin, ly + 10f, fonts.paint(7.5f, fonts.medium, PdfFonts.MUTED))
        ly += 14f
        wrap(AmountInWords.format(t.total), fonts.paint(9f, fonts.medium), leftW).forEach {
            canvas.drawText(it, margin, ly + 10f, fonts.paint(9f, fonts.medium)); ly += 12f
        }
        ly += 8f
        if (gst && t.slabs.isNotEmpty()) {
            val sp = fonts.paint(7.5f, fonts.bold, PdfFonts.MUTED)
            val cols = if (invoice.interState) listOf("Rate", "Taxable", "IGST") else listOf("Rate", "Taxable", "CGST", "SGST")
            val cw = leftW / cols.size
            cols.forEachIndexed { i, c -> canvas.drawText(c, margin + i * cw, ly + 10f, sp) }
            ly += 14f
            val cp = fonts.paint(8f)
            t.slabs.forEach { s ->
                val values = if (invoice.interState) listOf("${Percent.format(s.rateBp)}%", rs(s.taxable), rs(s.igst))
                else listOf("${Percent.format(s.rateBp)}%", rs(s.taxable), rs(s.cgst), rs(s.sgst))
                values.forEachIndexed { i, v -> canvas.drawText(v, margin + i * cw, ly + 10f, cp) }
                ly += 13f
            }
            ly += 8f
        }
        if (showQr) {
            val amount = invoice.balance.takeIf { it.paise > 0 } ?: t.total
            val qr = QrCode.bitmap(Upi.link(business.upiId, business.name, amount, invoice.number), 300)
            canvas.drawBitmap(qr, null, RectF(margin, ly, margin + 92f, ly + 92f), Paint(Paint.FILTER_BITMAP_FLAG))
            val qx = margin + 102f
            canvas.drawText("Scan to pay with any UPI app", qx, ly + 22f, fonts.paint(9f, fonts.bold))
            canvas.drawText(rs(amount), qx, ly + 40f, fonts.paint(12f, fonts.bold, PdfFonts.BRAND))
            canvas.drawText("UPI ID: ${business.upiId}", qx, ly + 56f, bodyMuted)
            canvas.drawText("GPay · PhonePe · Paytm · BHIM", qx, ly + 70f, bodyMuted)
            ly += 104f
        }
        if (business.bankDetails.isNotBlank()) {
            canvas.drawText("Bank details", margin, ly + 10f, fonts.paint(7.5f, fonts.medium, PdfFonts.MUTED)); ly += 14f
            wrap(business.bankDetails, bodyMuted, leftW).take(3).forEach { canvas.drawText(it, margin, ly + 9f, bodyMuted); ly += 11f }
            ly += 6f
        }
        val terms = listOf(invoice.notes, business.terms).filter { it.isNotBlank() }.joinToString("\n")
        if (terms.isNotBlank()) {
            canvas.drawText("Notes & terms", margin, ly + 10f, fonts.paint(7.5f, fonts.medium, PdfFonts.MUTED)); ly += 14f
            wrap(terms, bodyMuted, leftW).take(3).forEach { canvas.drawText(it, margin, ly + 9f, bodyMuted); ly += 11f }
        }

        // Signature block
        val sigY = maxOf(y, ly) + 24f
        val sp = fonts.paint(9f, fonts.bold, align = Paint.Align.RIGHT)
        canvas.drawText("For ${business.name}", pageW - margin, sigY, sp)
        signature?.let { drawFitted(canvas, it, RectF(pageW - margin - 160f, sigY + 6f, pageW - margin, sigY + 48f), alignRight = true) }
        canvas.drawLine(pageW - margin - 160f, sigY + 52f, pageW - margin, sigY + 52f, Paint().apply { color = PdfFonts.LINE })
        canvas.drawText("Authorised signatory", pageW - margin, sigY + 64f, fonts.paint(8f, color = PdfFonts.MUTED, align = Paint.Align.RIGHT))
        val who = listOf(business.signatoryName, business.signatoryDesignation).filter { it.isNotBlank() }.joinToString(", ")
        if (who.isNotBlank()) canvas.drawText(who, pageW - margin, sigY + 76f, fonts.paint(8.5f, fonts.medium, align = Paint.Align.RIGHT))
    }

    /** Draws a bitmap inside [box], keeping its aspect ratio. */
    private fun drawFitted(canvas: Canvas, bitmap: android.graphics.Bitmap, box: RectF, alignRight: Boolean) {
        val scale = minOf(box.width() / bitmap.width, box.height() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = if (alignRight) box.right - w else box.left
        val top = box.top + (box.height() - h) / 2
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawFooter(canvas: Canvas, index: Int) {
        val y = pageH - margin + 6f
        canvas.drawLine(margin, y - 14f, pageW - margin, y - 14f, Paint().apply { color = PdfFonts.LINE })
        canvas.drawText("Made with Modern Kallaa Petti", margin, y, fonts.paint(7.5f, color = PdfFonts.MUTED))
        canvas.drawText("Page ${index + 1} of $pageCount", pageW - margin, y, fonts.paint(7.5f, color = PdfFonts.MUTED, align = Paint.Align.RIGHT))
    }

    /** Writes all pages to [file]. */
    fun writeTo(file: File) {
        val doc = PdfDocument()
        try {
            repeat(pageCount) { i ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), i + 1).create())
                drawPage(page.canvas, i)
                doc.finishPage(page)
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
