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
) {
    private val fonts = PdfFonts(context)
    private val pageW = 595f
    private val pageH = 842f
    private val margin = 36f
    private val contentW = pageW - 2 * margin
    private val rowH = 18f
    private val headerH = 20f
    private val firstTop = 132f
    private val nextTop = margin + 30f
    private val bottom = pageH - margin - 28f

    private val pages: List<IntRange> = run {
        val list = mutableListOf<IntRange>()
        var start = 0
        var top = firstTop
        do {
            val capacity = ((bottom - top - headerH) / rowH).toInt().coerceAtLeast(1)
            val end = minOf(rows.size, start + capacity)
            list += start until end
            start = end
            top = nextTop
        } while (start < rows.size)
        // Summary needs room after the last rows
        val lastRows = list.last().count()
        val lastTop = if (list.size == 1) firstTop else nextTop
        if (summary.isNotEmpty() && lastTop + headerH + lastRows * rowH + 20f + summary.size * 18f > bottom) list += IntRange.EMPTY
        list
    }
    val pageCount get() = pages.size

    fun drawPage(canvas: Canvas, index: Int) {
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawRect(0f, 0f, pageW, 6f, Paint().apply { color = PdfFonts.BRAND })
        val top = if (index == 0) {
            canvas.drawText(businessName, margin, margin + 18f, fonts.paint(16f, fonts.bold))
            var y = margin + 34f
            wrap(businessInfo, fonts.paint(8f, color = PdfFonts.MUTED), contentW * 0.6f).take(3).forEach {
                canvas.drawText(it, margin, y, fonts.paint(8f, color = PdfFonts.MUTED)); y += 11f
            }
            canvas.drawText(title.uppercase(), pageW - margin, margin + 18f, fonts.paint(13f, fonts.bold, PdfFonts.BRAND, Paint.Align.RIGHT))
            canvas.drawText(subtitle, pageW - margin, margin + 34f, fonts.paint(9f, color = PdfFonts.MUTED, align = Paint.Align.RIGHT))
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
            canvas.drawRoundRect(RectF(margin, y, pageW - margin, y + headerH), 4f, 4f, Paint().apply { color = PdfFonts.BRAND_TINT })
            var x = margin
            columns.forEachIndexed { i, c ->
                val p = fonts.paint(8f, fonts.bold, PdfFonts.BRAND, if (c.alignRight) Paint.Align.RIGHT else Paint.Align.LEFT)
                canvas.drawText(c.title, if (c.alignRight) x + widths[i] - 6f else x + 6f, y + 13.5f, p)
                x += widths[i]
            }
            y += headerH
            val line = Paint().apply { color = PdfFonts.LINE; strokeWidth = 0.6f }
            for (r in range) {
                x = margin
                rows[r].forEachIndexed { i, cell ->
                    if (i >= columns.size) return@forEachIndexed
                    val c = columns[i]
                    val p = fonts.paint(8.5f, align = if (c.alignRight) Paint.Align.RIGHT else Paint.Align.LEFT)
                    val text = ellipsize(cell, p, widths[i] - 12f)
                    canvas.drawText(text, if (c.alignRight) x + widths[i] - 6f else x + 6f, y + 12.5f, p)
                    x += widths[i]
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

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > width) end--
        return text.substring(0, end) + "…"
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
