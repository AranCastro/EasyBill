package online.draran.billing.feature.reports

import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.print.PdfColumn
import online.draran.billing.core.print.TablePdf

/** Turns a [ReportContent] into the rows of a PDF table or the text of a CSV file. */
internal object ReportExport {

    class PdfTable(val columns: List<PdfColumn>, val rows: List<List<String>>)

    /**
     * One table for the whole report. A report with a single section and no
     * summary figures uses the column band as its header; anything else gives
     * every section its own title and column titles, so rows are never printed
     * under another section's headings.
     */
    fun pdfTable(c: ReportContent): PdfTable {
        val width = maxOf(2, c.sections.maxOfOrNull { it.columns.size } ?: 2)
        val single = c.kpis.isEmpty() && c.sections.size == 1
        val first = c.sections.firstOrNull()
        val columns = (0 until width).map { i ->
            PdfColumn(if (single) first?.columns?.getOrNull(i).orEmpty() else "", columnWeight(c, i))
        }
        val rows = mutableListOf<List<String>>()
        fun row(vararg cells: String) = rows.add(cells.toList().let { it + List((width - it.size).coerceAtLeast(0)) { "" } })

        if (c.kpis.isNotEmpty()) {
            row(TablePdf.HEADING + "Summary")
            c.kpis.forEach { k -> rows += listOf(k.label) + List(width - 2) { "" } + listOf(IndianFormat.rupees(k.value)) }
        }
        c.sections.forEachIndexed { index, s ->
            if (!single) {
                if (rows.isNotEmpty()) row("")
                s.title?.let { row(TablePdf.HEADING + it) }
                // Right-align a title when the figures below it are amounts
                val sample = s.rows.firstOrNull().orEmpty()
                row(
                    *s.columns.mapIndexed { i, t ->
                        (if (i == 0) TablePdf.SUBHEAD else "") + (if (i > 0 && TablePdf.looksNumeric(sample.getOrNull(i).orEmpty())) TablePdf.RIGHT else "") + t
                    }.let { cells -> if (cells.isEmpty()) listOf(TablePdf.SUBHEAD) else cells }.toTypedArray(),
                )
            }
            if (s.rows.isEmpty()) row("  No entries")
            s.rows.forEach { r -> rows += r + List((width - r.size).coerceAtLeast(0)) { "" } }
            s.totals.forEach { (k, v) -> rows += listOf("  $k") + List(width - 2) { "" } + listOf(v) }
        }
        c.note?.let { note ->
            row("")
            wrapNote(note).forEach { row(TablePdf.NOTE + it) }
        }
        return PdfTable(columns, rows)
    }

    /**
     * Wide for text columns (names, notes) and narrow for dates and amounts, judged from the
     * rows themselves, so a party name is not squeezed beside a short date.
     */
    private fun columnWeight(c: ReportContent, index: Int): Float {
        val cells = c.sections.flatMap { sec -> sec.rows.take(200).mapNotNull { it.getOrNull(index) } }.filter { it.isNotBlank() }
        if (cells.isEmpty()) return if (index == 0) 2.4f else 1.2f
        val sample = cells.take(20)
        if (sample.count { TablePdf.looksNumeric(it) } * 2 > sample.size) return 1.2f
        return (cells.maxOf { it.length } / 9f).coerceIn(1.2f, 3.0f)
    }

    /** Splits a note into lines that fit across the page, so none is cut off. */
    fun wrapNote(text: String, width: Int = 100): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        text.split(" ").filter { it.isNotEmpty() }.forEach { word ->
            if (current.isEmpty()) current = word
            else if (current.length + 1 + word.length <= width) current += " $word"
            else { lines += current; current = word }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private val MONEY = Regex("^\\(?-?₹?-?[\\d,]+(\\.\\d+)?%?\\)?$")

    /** Cells that spreadsheet programs would run as formulas. */
    private val FORMULA_START = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /**
     * Amounts become plain numbers ("₹1,23,456.00" -> 123456.00) so Excel can sum
     * them; any other text is kept as typed, with a leading quote when it starts
     * with a character Excel treats as a formula (phone numbers like +91 98765 43210).
     */
    fun csvCell(raw: String): String {
        val t = raw.trim()
        // Codes such as HSN 0713 or a 12-digit number must stay text: Excel would drop the zero or show 1.2E+11
        val pureDigits = t.isNotEmpty() && t.all { it.isDigit() }
        val code = pureDigits && ((t.length > 1 && t[0] == '0') || t.length >= 10)
        val value = if (code) {
            "=\"$t\""
        } else if (MONEY.matches(t) && (t.any { it.isDigit() })) {
            t.replace("₹", "").replace(",", "").replace("(", "-").replace(")", "")
        } else if (t.isNotEmpty() && t[0] in FORMULA_START) {
            "'$t"
        } else {
            t
        }
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    fun csv(title: String, subtitle: String, c: ReportContent): String = buildString {
        appendLine(csvCell(title)); appendLine(csvCell(subtitle)); appendLine()
        c.kpis.forEach { appendLine(csvCell(it.label) + "," + csvCell(IndianFormat.rupees(it.value))) }
        c.sections.forEach { s ->
            appendLine()
            s.title?.let { appendLine(csvCell(it)) }
            appendLine(s.columns.joinToString(",") { csvCell(it) })
            s.rows.forEach { r -> appendLine(r.joinToString(",") { csvCell(it) }) }
            s.totals.forEach { (k, v) -> appendLine(csvCell(k) + "," + csvCell(v)) }
        }
        c.note?.let { appendLine(); appendLine(csvCell(it)) }
    }
}
