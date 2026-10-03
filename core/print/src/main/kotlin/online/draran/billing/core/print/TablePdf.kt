package online.draran.billing.core.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File

data class PdfColumn(val title: String, val weight: Float, val alignRight: Boolean = false)

/**
 * Generic paginated table document for party statements and reports:
 * header (business, title, subtitle), table, then summary lines.
 */
class TablePdf(
    context: Context,
    private val businessName: String,
    private val businessInfo: String,
    private val title: String,
    private val subtitle: String,
    private val columns: List<PdfColumn>,
    private val rows: List<List<String>>,
    private val summary: List<Pair<String, String>> = emptyList(),
    private val logo: android.graphics.Bitmap? = null,
    /** Accent colour; pass Business.accent(). */
    private val brand: Int = PdfFonts.BRAND,
    /** Right-align cells that look like amounts, whatever the column setting (reports mix several tables). */
    private val autoAlign: Boolean = false,
) {
    private val brandTint = online.draran.billing.core.model.BillColors.tint(brand)
    private val fonts = PdfFonts(context)
    // Wide tables (GST reports have eight columns) are printed in landscape so amounts fit
    private val landscape = columns.size >= 6
    private val pageW = if (landscape) 842f else 595f
    private val pageH = if (landscape) 595f else 842f
    private val hasHeader = columns.any { it.title.isNotBlank() }
    private val margin = 36f
    private val contentW = pageW - 2 * margin
    private val rowH = 18f
    private val headerH = 20f
    private val firstTop = 132f
    private val nextTop = margin + 30f
    private val bottom = pageH - margin - 28f

    private fun firstCell(i: Int) = rows[i].firstOrNull().orEmpty()
    private fun isHeading(i: Int) = firstCell(i).startsWith(HEADING)
    private fun isSubhead(i: Int) = firstCell(i).startsWith(SUBHEAD)

    /**
     * The section title and column titles that apply to row [start], so a section that runs onto
     * another page repeats them (empty when the page begins with them or the table has none).
     */
    private fun contextBefore(start: Int): List<Int> {
        if (start <= 0 || start >= rows.size || isHeading(start) || isSubhead(start)) return emptyList()
        val sub = (start - 1 downTo 0).firstOrNull { isSubhead(it) } ?: return emptyList()
        return if (sub > 0 && isHeading(sub - 1)) listOf(sub - 1, sub) else listOf(sub)
    }

    private val pages: List<IntRange> = run {
        val list = mutableListOf<IntRange>()
        var start = 0
        var top = firstTop
        do {
            val repeated = contextBefore(start).size
            val capacity = ((bottom - top - (if (hasHeader) headerH else 0f)) / rowH).toInt() - repeated
            var end = minOf(rows.size, start + capacity.coerceAtLeast(1))
            // Never leave a section title or column titles alone at the bottom of a page
            while (end > start + 1 && end < rows.size && (isHeading(end - 1) || isSubhead(end - 1))) end--
            list += start until end
            start = end
            top = nextTop
        } while (start < rows.size)
        // Summary needs room after the last rows
        val lastRows = list.last().count() + (if (list.last().isEmpty()) 0 else contextBefore(list.last().first).size)
        val lastTop = if (list.size == 1) firstTop else nextTop
        if (summary.isNotEmpty() && lastTop + (if (hasHeader) headerH else 0f) + lastRows * rowH + 20f + summary.size * 18f > bottom) list += IntRange.EMPTY
        list
    }
    val pageCount get() = pages.size

    fun drawPage(canvas: Canvas, index: Int) {
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawRect(0f, 0f, pageW, 6f, Paint().apply { color = brand })
        val top = if (index == 0) {
            val left = if (logo != null) margin + 56f else margin
            logo?.let {
                val sc = minOf(46f / it.width, 46f / it.height)
                canvas.drawBitmap(it, null, RectF(margin, margin, margin + it.width * sc, margin + it.height * sc), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            val titlePaint = fonts.paint(13f, fonts.bold, brand, Paint.Align.RIGHT)
            val titleText = title.uppercase()
            // Name on the left, title on the right; neither may reach the other
            val nameRoom = contentW - (left - margin) - titlePaint.measureText(titleText) - 16f
            canvas.drawText(ellipsize(businessName, fonts.paint(16f, fonts.bold), nameRoom), left, margin + 18f, fonts.paint(16f, fonts.bold))
            var y = margin + 34f
            val infoWidth = contentW * 0.5f - (left - margin)
            wrap(businessInfo, fonts.paint(8f, color = PdfFonts.MUTED), infoWidth).take(3).forEach {
                canvas.drawText(it, left, y, fonts.paint(8f, color = PdfFonts.MUTED)); y += 11f
            }
            canvas.drawText(titleText, pageW - margin, margin + 18f, titlePaint)
            val subPaint = fonts.paint(9f, color = PdfFonts.MUTED, align = Paint.Align.RIGHT)
            canvas.drawText(ellipsize(subtitle, subPaint, contentW * 0.45f), pageW - margin, margin + 34f, subPaint)
            firstTop
        } else {
            canvas.drawText("$title (continued)", margin, margin + 14f, fonts.paint(10f, fonts.bold))
            nextTop
        }
        val range = pages[index]
        val totalWeight = columns.sumOf { it.weight.toDouble() }.toFloat()
        val widths = columns.map { contentW * it.weight / totalWeight }
        var y = top
        if (!range.isEmpty() || index == 0) {
            var x = margin
            if (hasHeader) {
                canvas.drawRoundRect(RectF(margin, y, pageW - margin, y + headerH), 4f, 4f, Paint().apply { color = brandTint })
                columns.forEachIndexed { i, c ->
                    val p = fonts.paint(8f, fonts.bold, brand, if (c.alignRight) Paint.Align.RIGHT else Paint.Align.LEFT)
                    canvas.drawText(c.title, if (c.alignRight) x + widths[i] - 6f else x + 6f, y + 13.5f, p)
                    x += widths[i]
                }
                y += headerH
            }
            val line = Paint().apply { color = PdfFonts.LINE; strokeWidth = 0.6f }
            val shown = (if (range.isEmpty()) emptyList() else contextBefore(range.first)) + range.toList()
            for (r in shown) {
                x = margin
                val cells = rows[r]
                val first = cells.firstOrNull().orEmpty()
                when {
                    first.startsWith(NOTE) -> {
                        val np = fonts.paint(8f, color = PdfFonts.MUTED)
                        canvas.drawText(ellipsize(first.removePrefix(NOTE), np, contentW - 12f), margin + 6f, y + 12.5f, np)
                    }
                    first.startsWith(HEADING) -> {
                        // Section title spanning the row
                        canvas.drawRect(RectF(margin, y, pageW - margin, y + rowH), Paint().apply { color = brandTint })
                        canvas.drawText(ellipsize(first.removePrefix(HEADING), fonts.paint(9f, fonts.bold, brand), contentW - 12f), margin + 6f, y + 12.5f, fonts.paint(9f, fonts.bold, brand))
                    }
                    first.startsWith(SUBHEAD) -> {
                        cells.forEachIndexed { i, raw ->
                            if (i >= columns.size) return@forEachIndexed
                            val right = raw.removePrefix(SUBHEAD).startsWith(RIGHT)
                            val text = raw.removePrefix(SUBHEAD).removePrefix(RIGHT)
                            val p = fonts.paint(7.5f, fonts.bold, PdfFonts.MUTED, if (right) Paint.Align.RIGHT else Paint.Align.LEFT)
                            canvas.drawText(ellipsize(text, p, widths[i] - 12f), if (right) x + widths[i] - 6f else x + 6f, y + 12.5f, p)
                            x += widths[i]
                        }
                    }
                    else -> cells.forEachIndexed { i, cell ->
                        if (i >= columns.size) return@forEachIndexed
                        // The first column holds labels and codes, so it stays left-aligned
                        val right = columns[i].alignRight || (autoAlign && i > 0 && Companion.looksNumeric(cell))
                        val p = fonts.paint(8.5f, align = if (right) Paint.Align.RIGHT else Paint.Align.LEFT)
                        val text = fit(cell, p, widths[i] - 12f, shrinkFirst = right)
                        canvas.drawText(text, if (right) x + widths[i] - 6f else x + 6f, y + 12.5f, p)
                        x += widths[i]
                    }
                }
                y += rowH
                canvas.drawLine(margin, y, pageW - margin, y, line)
            }
            if (rows.isEmpty()) {
                canvas.drawText("No entries in this period", margin + 6f, y + 16f, fonts.paint(9f, color = PdfFonts.MUTED)); y += 24f
            }
        }
        if (index == pageCount - 1 && summary.isNotEmpty()) {
            y += 14f
            summary.forEach { (k, v) ->
                canvas.drawText(k, pageW - margin - 230f, y + 12f, fonts.paint(9.5f, color = PdfFonts.MUTED))
                canvas.drawText(v, pageW - margin - 6f, y + 12f, fonts.paint(9.5f, fonts.bold, align = Paint.Align.RIGHT))
                y += 18f
            }
        }
        val fy = pageH - margin + 6f
        canvas.drawLine(margin, fy - 14f, pageW - margin, fy - 14f, Paint().apply { color = PdfFonts.LINE })
        canvas.drawText("Made with Modern Kallaa Petti", margin, fy, fonts.paint(7.5f, color = PdfFonts.MUTED))
        canvas.drawText("Page ${index + 1} of $pageCount", pageW - margin, fy, fonts.paint(7.5f, color = PdfFonts.MUTED, align = Paint.Align.RIGHT))
    }

    /**
     * Fits [text] into [width]. Amounts are never cut ("₹1,23,45…" reads as a wrong
     * number): the font is made smaller first, and only text is shortened.
     */
    private fun fit(text: String, paint: Paint, width: Float, shrinkFirst: Boolean): String {
        if (shrinkFirst) {
            var size = paint.textSize
            while (paint.measureText(text) > width && size > 5.5f) {
                size -= 0.25f
                paint.textSize = size
            }
        }
        return ellipsize(text, paint, width)
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > width) end--
        return text.substring(0, end) + "…"
    }

    companion object {
        /** First-cell prefix: a line of explanatory text spanning the row. */
        const val NOTE = "\u0003"

        /** First-cell prefix: a section title spanning the row. */
        const val HEADING = "\u0001"

        /** First-cell prefix: column titles of a section; prefix a cell with [RIGHT] to right-align it. */
        const val SUBHEAD = "\u0002"
        const val RIGHT = "\u0004"

        /** Amounts, short counts and percentages (a 10-digit phone number is not numeric). */
        fun looksNumeric(cell: String): Boolean {
            val t = cell.trim()
            return t.startsWith("₹") || t.startsWith("-₹") || t.startsWith("(₹") || NUMBER.matches(t)
        }

        // Short integers, decimals and percentages (a 10-digit phone number stays left-aligned)
        private val NUMBER = Regex("^-?[\\d,]{1,9}(\\.\\d+)?%?$")
    }

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
