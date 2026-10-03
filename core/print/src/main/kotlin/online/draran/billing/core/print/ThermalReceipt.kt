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
class ThermalReceipt(private val invoice: Invoice, private val business: Business) {

    val width: Int = if (business.thermalWidthMm >= 80) 48 else 32

    private fun amt(m: Money) = BigDecimal.valueOf(m.paise, 2).toPlainString()

    sealed interface Part {
        data class Text(val text: String, val center: Boolean = false, val bold: Boolean = false, val big: Boolean = false) : Part
        data class Qr(val data: String) : Part
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

        out += Part.Text(ascii(business.name).take(width), center = true, bold = true, big = width >= 48 || business.name.length <= 16)
        business.address.split("\n").filter { it.isNotBlank() }.forEach { addr -> wrapText(ascii(addr), width).forEach { line(it, center = true) } }
        if (business.phone.isNotBlank()) line("Ph: ${business.phone}", center = true)
        if (business.gstEnabled && business.gstin.isNotBlank()) line("GSTIN: ${business.gstin}", center = true)
        line(rule)
        val title = when {
            invoice.type == DocType.SALE && invoice.gstEnabled -> "TAX INVOICE"
            invoice.type == DocType.SALE -> "BILL OF SUPPLY"
            else -> invoice.type.title.uppercase()
        }
        line(title, center = true, bold = true)
        lr("No: ${invoice.number}", invoice.date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)))
        line("To: ${ascii(invoice.partyName)}".take(width))
        line(rule)
        lr("Item", "Amount", bold = true)
        line(rule)
        invoice.lines.forEachIndexed { i, l ->
            wrapText(ascii(l.name), width).forEach { line(it) }
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
            lr(if (invoice.type == DocType.SALE) "Received" else "Paid", amt(invoice.paid))
            if (!invoice.balance.isZero) lr("Balance", amt(invoice.balance), bold = true)
        }
        line(rule)
        if (business.showUpiQr && business.upiId.isNotBlank() && invoice.type == DocType.SALE && invoice.balance.paise > 0) {
            line("Scan to pay Rs. ${amt(invoice.balance)}", center = true)
            out += Part.Qr(Upi.link(business.upiId, business.name, invoice.balance, invoice.number))
        }
        if (business.terms.isNotBlank()) wrapText(ascii(business.terms), width).forEach { line(it, center = true) }
        return out
    }

    private fun padLR(l: String, r: String) = l + " ".repeat((width - l.length - r.length).coerceAtLeast(1)) + r

    /** Preview text (what the receipt looks like). */
    fun text(): String = parts().joinToString("\n") { p ->
        when (p) {
            is Part.Text -> if (p.center) p.text.padStart((width + p.text.length) / 2).padEnd(width) else p.text
            is Part.Qr -> "[UPI QR]".padStart((width + 8) / 2)
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
        /** A short sample receipt to check the printer connection and width. */
        fun testPage(business: Business): ByteArray {
            val width = if (business.thermalWidthMm >= 80) 48 else 32
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0x1B, 0x40, 0x1B, 0x61, 1, 0x1B, 0x45, 1))
            out.write((ascii(business.name.ifBlank { "Modern Kallaa Petti" }).take(width) + "\n").toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(0x1B, 0x45, 0))
            out.write(("Printer test OK\n" + "-".repeat(width) + "\n" + "1234567890".repeat(5).take(width) + "\n" + "${business.thermalWidthMm} mm paper\n").toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(0x1B, 0x64, 4, 0x1D, 0x56, 0x42, 0))
            return out.toByteArray()
        }

        fun ascii(s: String): String = s.replace("₹", "Rs.").map { if (it.code in 32..126) it else '?' }.joinToString("")
            .replace("?", "").trim()

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
