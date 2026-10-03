package online.draran.billing.feature.reports

import online.draran.billing.core.model.Money
import online.draran.billing.core.print.TablePdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExportTest {

    private val twoColumn = ReportContent(
        kpis = emptyList(),
        sections = listOf(ReportSection(null, listOf("Particulars", "Amount"), listOf(listOf("Net sales", "₹1,000.00"), listOf("Net profit", "₹400.00")))),
    )

    @Test fun twoColumnReportsDoNotCrash() {
        // Profit & loss and Cash flow: every section has two columns
        val withKpis = ReportContent(
            kpis = listOf(Kpi("Net profit", Money(40000))),
            sections = twoColumn.sections + ReportSection("Paid", listOf("Mode", "Amount"), listOf(listOf("Cash", "₹10.00")), listOf("Total" to "₹10.00")),
        )
        val t = ReportExport.pdfTable(withKpis)
        assertEquals(2, t.columns.size)
        t.rows.forEach { assertEquals(2, it.size) }
        assertTrue(t.rows.any { it.first() == TablePdf.HEADING + "Summary" })
        assertTrue(t.rows.any { it == listOf("Net profit", "₹400.00") })
    }

    @Test fun singleSectionUsesTheColumnBand() {
        val t = ReportExport.pdfTable(twoColumn)
        assertEquals(listOf("Particulars", "Amount"), t.columns.map { it.title })
        assertFalse(t.rows.any { it.first().startsWith(TablePdf.SUBHEAD) })
    }

    @Test fun everySectionGetsItsOwnHeadings() {
        val gst = ReportContent(
            kpis = emptyList(),
            sections = listOf(
                ReportSection("Sales", listOf("Supply", "Rate", "Taxable", "Tax"), listOf(listOf("B2C", "18%", "₹100.00", "₹18.00"))),
                ReportSection("HSN", listOf("HSN", "Rate", "Qty", "Taxable", "Tax", "Total"), listOf(listOf("1006", "5%", "2", "₹100.00", "₹5.00", "₹105.00"))),
            ),
        )
        val t = ReportExport.pdfTable(gst)
        assertEquals(6, t.columns.size)
        assertTrue(t.columns.all { it.title.isBlank() })
        val subheads = t.rows.filter { it.first().startsWith(TablePdf.SUBHEAD) }
        assertEquals(2, subheads.size)
        assertTrue(subheads[1].first().contains("HSN"))
        // The amount titles are right-aligned, the text title is not
        assertTrue(subheads[1][3].startsWith(TablePdf.RIGHT))
        // First column titles stay left-aligned even when the codes below are digits
        assertFalse(subheads[1][0].removePrefix(TablePdf.SUBHEAD).startsWith(TablePdf.RIGHT))
        t.rows.forEach { assertEquals(6, it.size) }
    }

    @Test fun emptySectionSaysSo() {
        val t = ReportExport.pdfTable(ReportContent(emptyList(), listOf(ReportSection(null, listOf("A", "B"), emptyList()))))
        assertTrue(t.rows.any { it.first().contains("No entries") })
    }

    @Test fun csvAmountsBecomeNumbersAndTextIsProtected() {
        assertEquals("\"1234567.50\"", ReportExport.csvCell("₹12,34,567.50"))
        assertEquals("\"-500.00\"", ReportExport.csvCell("-₹500.00"))
        assertEquals("\"18%\"", ReportExport.csvCell("18%"))
        // Phone numbers and formulas must not be run by a spreadsheet
        assertEquals("\"'+91 98765 43210\"", ReportExport.csvCell("+91 98765 43210"))
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", ReportExport.csvCell("=HYPERLINK(\"http://x\")"))
        assertEquals("\"'@SUM(A1)\"", ReportExport.csvCell("@SUM(A1)"))
        // Text keeps its commas and any rupee sign
        assertEquals("\"Cable 2,5mm\"", ReportExport.csvCell("Cable 2,5mm"))
        assertEquals("\"Room ₹1,2\"", ReportExport.csvCell("Room ₹1,2"))
        assertEquals("\"\"", ReportExport.csvCell(""))
    }

    @Test fun csvHasAllSections() {
        val text = ReportExport.csv("Profit & loss", "1 Apr to 3 Oct", twoColumn)
        val lines = text.lines()
        assertEquals("\"Profit & loss\"", lines[0])
        assertTrue(text.contains("\"Net profit\",\"400.00\""))
    }

    @Test fun notesAreWrappedIntoFullLines() {
        val note = "A summary to help you or your accountant file returns. Verify with the GST portal before filing. " +
            "Purchases from suppliers without a GSTIN are left out of input tax credit."
        val lines = ReportExport.wrapNote(note)
        assertTrue(lines.size >= 2)
        lines.forEach { assertTrue(it.length <= 100) }
        assertEquals(note, lines.joinToString(" "))
        val t = ReportExport.pdfTable(ReportContent(emptyList(), twoColumn.sections, note))
        assertEquals(lines.size, t.rows.count { it.first().startsWith(TablePdf.NOTE) })
    }
}
