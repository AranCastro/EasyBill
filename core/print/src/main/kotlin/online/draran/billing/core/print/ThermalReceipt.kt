package online.draran.billing.core.print

import online.draran.billing.core.model.Business
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.Invoice
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Percent
import online.draran.billing.core.model.Qty
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Plain-text receipt for 58 mm (32 columns) or 80 mm (48 columns) thermal
 * printers, plus the ESC/POS byte stream to send over Bluetooth.
 * Printers rarely have the ₹ glyph, so amounts use "Rs.".
 */
class ThermalReceipt(
    private val invoice: Invoice,
    private val business: Business,
    private val logo: android.graphics.Bitmap? = null,
) {

    val width: Int = if (business.thermalWidthMm >= 80) 48 else 32

    private fun amt(m: Money) = BigDecimal.valueOf(m.paise, 2).toPlainString()

    sealed interface Part {
        data class Text(val text: String, val center: Boolean = false, val bold: Boolean = false, val big: Boolean = false) : Part
        data class Qr(val data: String) : Part
        data class Image(val bitmap: android.graphics.Bitmap) : Part

        /** Text the printer's built-in font cannot print (Tamil, Hindi...), sent as a picture of the text. */
        data class TextImage(val text: String, val center: Boolean = false, val bold: Boolean = false) : Part
    }

    fun parts(): List<Part> {
        val out = mutableListOf<Part>()
        val rule = "-".repeat(width)
        fun line(text: String, center: Boolean = false, bold: Boolean = false) = out.add(Part.Text(text, center, bold))
        fun lr(left: String, right: String, bold: Boolean = false) {
            val space = width - left.length - right.length
            if (space >= 1) line(left + " ".repeat(space) + right, bold = bold)
            else {
                line(left.take(width), bold = bold)
                line(right.padStart(width), bold = bold)
            }
        }

        /** Plain text is wrapped to the paper width; text with other scripts becomes a picture. */
        fun emit(text: String, center: Boolean = false, bold: Boolean = false) {
            text.split("\n").filter { it.isNotBlank() }.forEach { piece ->
                if (needsImage(piece)) out += Part.TextImage(piece.trim(), center, bold)
                else wrapText(ascii(piece), width).filter { it.isNotEmpty() }.forEach { line(it, center, bold) }
            }
        }

        if (logo != null && business.printLogoOnReceipt) out += Part.Image(logo)
        // Double size only when the whole name fits at double width; otherwise it is wrapped at normal size
        val shopName = business.name.trim()
        if (needsImage(shopName)) out += Part.TextImage(shopName, center = true, bold = true)
        else {
            val plainName = ascii(shopName).ifBlank { "Receipt" }
            if (plainName.length <= width / 2) out += Part.Text(plainName, center = true, bold = true, big = true)
            else wrapText(plainName, width).forEach { line(it, center = true, bold = true) }
        }
        emit(business.address, center = true)
        if (business.phone.isNotBlank()) line("Ph: ${ascii(business.phone)}", center = true)
        if (business.gstEnabled && business.gstin.isNotBlank()) line("GSTIN: ${ascii(business.gstin)}", center = true)
        business.udyamLine()?.let { u -> wrapText(ascii(u), width).forEach { line(it, center = true) } }
        line(rule)
        val title = when {
            invoice.type == DocType.SALE && invoice.gstEnabled -> "TAX INVOICE"
            invoice.type == DocType.SALE -> business.type.billTitle.uppercase()
            else -> invoice.type.title.uppercase()
        }
        line(title, center = true, bold = true)
        lr("No: ${ascii(invoice.number)}", invoice.date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)))
        if (invoice.partyName.isNotBlank()) emit("${if (business.type.party == "Customer") "To" else business.type.party}: ${invoice.partyName}")
        invoice.customFields.forEach { (k, v) -> emit("$k: $v") }
        line(rule)
        lr("Item", "Amount", bold = true)
        line(rule)
        invoice.lines.forEachIndexed { i, l ->
            emit(l.name.ifBlank { "Item ${i + 1}" })
            val detail = "  ${Qty.format(l.qtyMilli)} x ${amt(l.rate)}" +
                (if (l.discountBp > 0) " -${Percent.format(l.discountBp)}%" else "") +
                (if (invoice.gstEnabled && l.taxRateBp > 0) " @${Percent.format(l.taxRateBp)}%" else "")
            lr(detail, amt(invoice.totals.lines[i].total))
        }
        line(rule)
        val t = invoice.totals
        lr("Items: ${invoice.lines.size}", "Qty: ${Qty.format(invoice.lines.sumOf { it.qtyMilli })}")
        if (!t.discount.isZero) lr("Discount", "-" + amt(t.discount))
        if (invoice.gstEnabled && !t.tax.isZero) {
            lr("Taxable", amt(t.taxable))
            if (invoice.interState) lr("IGST", amt(t.igst)) else {
                lr("CGST", amt(t.cgst))
                lr("SGST", amt(t.sgst))
            }
        }
        if (!t.roundOff.isZero) lr("Round off", amt(t.roundOff))
        out += Part.Text(padLR("TOTAL", "Rs. " + amt(t.total)), bold = true)
        if (invoice.type.tracksPayment) {
            lr(if (invoice.type.paymentDirection == online.draran.billing.core.model.PaymentDirection.IN) "Received" else "Paid", amt(invoice.paid))
            if (!invoice.balance.isZero) lr("Balance", amt(invoice.balance), bold = true)
        }
        line(rule)
        if (business.showUpiQr && business.upiId.isNotBlank() && invoice.type == DocType.SALE && invoice.balance.paise > 0) {
            line("Scan to pay Rs. ${amt(invoice.balance)}", center = true)
            out += Part.Qr(Upi.link(business.upiId, business.name, invoice.balance, invoice.number))
        }
        // Short MSME note only when credit is given; the A4 bill carries the full wording
        if (business.msmeNote() != null && invoice.type == DocType.SALE && invoice.balance.paise > 0) {
            wrapText("MSME supplier: pay within the agreed period, not later than 45 days (MSMED Act 2006, s.15)", width).forEach { line(it, center = true) }
        }
        emit(business.terms, center = true)
        return out
    }

    private fun padLR(l: String, r: String) = l + " ".repeat((width - l.length - r.length).coerceAtLeast(1)) + r

    /** Preview text (what the receipt looks like). */
    fun text(): String = parts().joinToString("\n") { p ->
        when (p) {
            is Part.Text -> if (p.center) p.text.padStart((width + p.text.length) / 2).padEnd(width) else p.text
            is Part.Qr -> "[UPI QR]".padStart((width + 8) / 2)
            is Part.Image -> "[LOGO]".padStart((width + 6) / 2)
            is Part.TextImage -> if (p.center) p.text.padStart((width + p.text.length) / 2) else p.text
        }
    }

    /** ESC/POS commands. */
    fun escPos(): ByteArray {
        val out = ByteArrayOutputStream()
        fun cmd(vararg b: Int) = b.forEach { out.write(it) }
        cmd(0x1B, 0x40) // initialise
        parts().forEach { p ->
            when (p) {
                is Part.Text -> {
                    cmd(0x1B, 0x61, if (p.center) 1 else 0)
                    cmd(0x1B, 0x45, if (p.bold) 1 else 0)
                    cmd(0x1D, 0x21, if (p.big) 0x11 else 0x00)
                    out.write((if (p.big) p.text.take(width / 2) else p.text).toByteArray(Charsets.US_ASCII))
                    out.write(0x0A)
                    cmd(0x1D, 0x21, 0x00)
                }
                is Part.Image -> {
                    cmd(0x1B, 0x61, 1)
                    out.write(raster(p.bitmap, if (width >= 48) 320 else 240, maxHeight = 200))
                    out.write(0x0A)
                }
                is Part.TextImage -> {
                    // Printable width: 48 mm (384 dots) or 72 mm (576 dots)
                    val dots = if (width >= 48) 576 else 384
                    cmd(0x1B, 0x61, 0)
                    out.write(raster(textBitmap(p.text, dots, p.center, p.bold), dots, maxHeight = Int.MAX_VALUE))
                }
                is Part.Qr -> {
                    val data = p.data.toByteArray(Charsets.US_ASCII)
                    cmd(0x1B, 0x61, 1)
                    cmd(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00) // model 2
                    cmd(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, if (width >= 48) 6 else 5) // module size
                    cmd(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30) // error correction L
                    val len = data.size + 3
                    cmd(0x1D, 0x28, 0x6B, len % 256, len / 256, 0x31, 0x50, 0x30)
                    out.write(data)
                    cmd(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30) // print
                    out.write(0x0A)
                }
            }
        }
        cmd(0x1B, 0x61, 0)
        cmd(0x1B, 0x64, 4) // feed
        cmd(0x1D, 0x56, 0x42, 0x00) // partial cut (ignored by printers without cutter)
        return out.toByteArray()
    }

    companion object {
        /**
         * ESC/POS "GS v 0" raster image: the bitmap scaled to [maxDots] wide,
         * flattened onto white and turned into black/white dots.
         */
        fun raster(source: android.graphics.Bitmap, maxDots: Int, maxHeight: Int = 200): ByteArray {
            // A tall logo would otherwise print as a very long strip
            val scale = minOf(1f, maxDots.toFloat() / source.width, maxHeight.toFloat() / source.height)
            val w = (source.width * scale).toInt().coerceAtLeast(8)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            val bmp = android.graphics.Bitmap.createScaledBitmap(source, w, h, true)
            val bytesPerRow = (w + 7) / 8
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, (bytesPerRow % 256).toByte(), (bytesPerRow / 256).toByte(), (h % 256).toByte(), (h / 256).toByte()))
            val row = IntArray(w)
            for (y in 0 until h) {
                bmp.getPixels(row, 0, w, 0, y, w, 1)
                for (b in 0 until bytesPerRow) {
                    var byte = 0
                    for (bit in 0 until 8) {
                        val x = b * 8 + bit
                        if (x < w) {
                            val p = row[x]
                            val a = android.graphics.Color.alpha(p)
                            // Composite on white, then threshold
                            val lum = (android.graphics.Color.red(p) * 299 + android.graphics.Color.green(p) * 587 + android.graphics.Color.blue(p) * 114) / 1000
                            val onWhite = (lum * a + 255 * (255 - a)) / 255
                            if (onWhite < 140) byte = byte or (0x80 shr bit)
                        }
                    }
                    out.write(byte)
                }
            }
            return out.toByteArray()
        }

        /** A short sample receipt to check the printer connection and width. */
        fun testPage(business: Business): ByteArray {
            val width = if (business.thermalWidthMm >= 80) 48 else 32
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0x1B, 0x40, 0x1B, 0x61, 1, 0x1B, 0x45, 1))
            out.write((ascii(business.name).ifBlank { "Printer test" }.take(width) + "\n").toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(0x1B, 0x45, 0))
            out.write(("Printer test OK\n" + "-".repeat(width) + "\n" + "1234567890".repeat(5).take(width) + "\n" + "${business.thermalWidthMm} mm paper\n").toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(0x1B, 0x64, 4, 0x1D, 0x56, 0x42, 0))
            return out.toByteArray()
        }

        /**
         * What a basic printer font can show: accents are removed ("Café" -> "Cafe"),
         * ₹ becomes "Rs.", and characters it has no glyph for are dropped. Real "?" are kept.
         */
        fun ascii(s: String): String {
            // Signs that have a plain equivalent but do not decompose into ASCII
            val plain = s.replace("₹", "Rs.").replace("µ", "u").replace("²", "2").replace("³", "3").replace("°", " deg")
            val decomposed = java.text.Normalizer.normalize(plain, java.text.Normalizer.Form.NFD)
            return decomposed.filter { it.code in 32..126 }.replace(Regex(" {2,}"), " ").trim()
        }

        /** True when [s] has letters outside the Latin script (Tamil, Hindi...), which [ascii] would delete. */
        fun needsImage(s: String): Boolean = s.any {
            // Common/inherited letters (µ, degree signs...) are handled by ascii(); only real other scripts need a picture
            it.isLetter() && Character.UnicodeScript.of(it.code).let { script ->
                script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED
            }
        }

        /** Draws [text] in black on white, wrapped to [dots] wide, using the phone's own fonts. */
        fun textBitmap(text: String, dots: Int, center: Boolean, bold: Boolean): android.graphics.Bitmap {
            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = 26f
                typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            }
            val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, dots)
                .setAlignment(if (center) android.text.Layout.Alignment.ALIGN_CENTER else android.text.Layout.Alignment.ALIGN_NORMAL)
                .build()
            val bitmap = android.graphics.Bitmap.createBitmap(dots, layout.height.coerceAtLeast(1) + 4, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            canvas.translate(0f, 2f)
            layout.draw(canvas)
            return bitmap
        }

        fun wrapText(text: String, width: Int): List<String> {
            if (text.length <= width) return listOf(text)
            val lines = mutableListOf<String>()
            var current = ""
            text.split(" ").forEach { word ->
                val w = if (word.length > width) word.chunked(width) else listOf(word)
                w.forEach { part ->
                    current = when {
                        current.isEmpty() -> part
                        current.length + 1 + part.length <= width -> "$current $part"
                        else -> { lines += current; part }
                    }
                }
            }
            if (current.isNotEmpty()) lines += current
            return lines
        }
    }
}
