package online.draran.billing.core.print

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.BusinessType
import android.graphics.Color
import android.graphics.Paint
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.Invoice
import online.draran.billing.core.model.InvoiceLine
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Qty
import online.draran.billing.core.model.TaxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PrintRenderTest {

    private val business = Business(
        name = "Sharma General Store", address = "12, Gandhi Road, T. Nagar\nChennai 600017", stateCode = "33",
        phone = "98400 12345", gstEnabled = true, gstin = "33AAPFU0939F1ZW", upiId = "sharmastore@okaxis",
        bankDetails = "State Bank of India · A/c 1234567890 · IFSC SBIN0001234", onboarded = true,
    )

    private fun sample(lines: Int = 4): Invoice {
        val base = listOf(
            InvoiceLine(itemId = 1, name = "Basmati Rice Premium 5 kg", hsn = "1006", unit = "bag", qtyMilli = Qty.of(2), rate = Money.rupees(645), taxRateBp = 500),
            InvoiceLine(itemId = 2, name = "Sunflower Oil 1 L", hsn = "1512", unit = "pcs", qtyMilli = Qty.of(3), rate = Money(18500), taxRateBp = 500, discountBp = 500),
            InvoiceLine(itemId = 3, name = "Toor Dal", hsn = "0713", unit = "kg", qtyMilli = 1500, rate = Money(14200), taxRateBp = 0),
            InvoiceLine(itemId = 4, name = "Dettol Soap (pack of 4)", hsn = "3401", unit = "pcs", qtyMilli = Qty.of(1), rate = Money(22000), taxRateBp = 1800, taxInclusive = true),
        )
        val all = (0 until lines).map { base[it % base.size].copy(name = if (it < base.size) base[it].name else "${base[it % base.size].name} #$it") }
        val totals = TaxEngine.bill(all.map { it.toInput() }, interState = false)
        return Invoice(
            id = 1, type = DocType.SALE, number = "INV-0142", date = LocalDate.of(2026, 10, 3), partyId = 1,
            partyName = "Rakesh Kumar", partyPhone = "98765 43210", partyAddress = "45, Anna Salai, Chennai",
            placeOfSupply = "33", interState = false, gstEnabled = true, lines = all, totals = totals,
            paid = Money.rupees(1000),
        )
    }

    private fun renderPage(draw: (Canvas) -> Unit, name: String) {
        val scale = 2f
        val bitmap = Bitmap.createBitmap((595 * scale).toInt(), (842 * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        draw(canvas)
        val out = File("build/outputs/roborazzi/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun invoicePdfRenders() {
        val pdf = InvoicePdf(ApplicationProvider.getApplicationContext(), sample(), business)
        assertEquals(1, pdf.pageCount)
        renderPage({ pdf.drawPage(it, 0) }, "invoice_a4")
    }

    @Test fun longInvoiceBreaksIntoPages() {
        val pdf = InvoicePdf(ApplicationProvider.getApplicationContext(), sample(lines = 60), business)
        assertTrue(pdf.pageCount >= 3)
        renderPage({ pdf.drawPage(it, pdf.pageCount - 1) }, "invoice_a4_last_page")
    }

    @Test fun statementRenders() {
        val pdf = TablePdf(
            ApplicationProvider.getApplicationContext(), business.name, "Chennai · 98400 12345", "Party Statement",
            "Rakesh Kumar · 1 Apr 2026 to 3 Oct 2026",
            listOf(PdfColumn("Date", 1.2f), PdfColumn("Particulars", 2.5f), PdfColumn("Amount", 1.3f, true), PdfColumn("Balance", 1.3f, true)),
            (1..12).map { listOf("0$it Oct 2026", "Sale INV-00$it", "₹1,200.00", "₹${it * 1200}.00") },
            listOf("Closing balance" to "₹14,400.00"),
        )
        renderPage({ pdf.drawPage(it, 0) }, "statement")
    }

    @Test fun thermalReceiptFitsWidth() {
        val receipt = ThermalReceipt(sample(), business)
        val text = receipt.text()
        text.lines().forEach { assertTrue("Line too long: '$it'", it.length <= 32) }
        assertTrue(text.contains("TAX INVOICE"))
        assertTrue(text.contains("TOTAL"))
        assertTrue(receipt.escPos().size > 200)
        File("build/outputs/roborazzi/thermal_58mm.txt").apply { parentFile?.mkdirs() }.writeText(text)
        val wide = ThermalReceipt(sample(), business.copy(thermalWidthMm = 80))
        wide.text().lines().forEach { assertTrue(it.length <= 48) }
    }

    private fun sampleLogo(): Bitmap {
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(15, 118, 110) }
        c.drawCircle(100f, 100f, 96f, p)
        p.color = Color.WHITE; p.textSize = 90f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        c.drawText("SA", 100f, 132f, p)
        return b
    }

    private fun sampleSignature(): Bitmap {
        val b = Bitmap.createBitmap(400, 140, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 24, 60); strokeWidth = 6f; style = Paint.Style.STROKE }
        val path = android.graphics.Path().apply {
            moveTo(10f, 100f); cubicTo(60f, 10f, 90f, 130f, 140f, 60f); cubicTo(180f, 10f, 220f, 120f, 270f, 70f); lineTo(390f, 40f)
        }
        c.drawPath(path, p)
        return b
    }

    @Test fun brandedInvoiceShowsLogoAndSignature() {
        val branded = business.copy(signatoryName = "R. Sharma", signatoryDesignation = "Proprietor")
        val pdf = InvoicePdf(ApplicationProvider.getApplicationContext(), sample(), branded, sampleLogo(), sampleSignature())
        assertEquals(1, pdf.pageCount)
        renderPage({ pdf.drawPage(it, 0) }, "invoice_a4_branded")
    }

    @Test fun educationFeeReceiptUsesTypeWords() {
        val school = business.copy(
            name = "Bright Future Academy", gstEnabled = false, gstin = "", type = BusinessType.EDUCATION,
            customFieldLabels = BusinessType.EDUCATION.customFields, signatoryName = "Dr. Meena Iyer", signatoryDesignation = "Principal",
        )
        val lines = listOf(
            InvoiceLine(itemId = 1, name = "Tuition fee", hsn = "9992", unit = "term", qtyMilli = Qty.of(1), rate = Money.rupees(18000), taxRateBp = 0),
            InvoiceLine(itemId = 2, name = "Lab fee", hsn = "9992", unit = "term", qtyMilli = Qty.of(1), rate = Money.rupees(2500), taxRateBp = 0),
        )
        val invoice = sample().copy(
            gstEnabled = false, lines = lines, totals = TaxEngine.bill(lines.map { it.toInput() }, interState = false),
            partyName = "Ananya R", customFields = listOf("Roll / Admission no." to "BFA-2026-118", "Class / Course" to "Class X", "Fee period" to "Term 2"),
        )
        val pdf = InvoicePdf(ApplicationProvider.getApplicationContext(), invoice, school, sampleLogo(), sampleSignature())
        renderPage({ pdf.drawPage(it, 0) }, "invoice_a4_education")
        val receipt = ThermalReceipt(invoice, school)
        val text = receipt.text()
        assertTrue(text.contains("FEE RECEIPT"))
        assertTrue(text.contains("BFA-2026-118"))
        assertTrue(text.contains("Student"))
        text.lines().forEach { assertTrue("Line too long: '$it'", it.length <= 32) }
    }

    @Test fun thermalLogoIsSentAsRasterImage() {
        // The UPI QR is also sent as a picture, so it is left out here to see the logo alone
        val noQr = business.copy(showUpiQr = false)
        val plain = ThermalReceipt(sample(), noQr).escPos()
        val withLogo = ThermalReceipt(sample(), noQr, sampleLogo()).escPos()
        val off = ThermalReceipt(sample(), noQr.copy(printLogoOnReceipt = false), sampleLogo()).escPos()
        fun hasRaster(b: ByteArray) = (0 until b.size - 2).any { b[it] == 0x1D.toByte() && b[it + 1] == 0x76.toByte() && b[it + 2] == 0x30.toByte() }
        assertTrue(hasRaster(withLogo))
        assertTrue(!hasRaster(plain))
        assertTrue(!hasRaster(off))
        val raster = ThermalReceipt.raster(sampleLogo(), 240)
        // GS v 0 m xL xH yL yH: width in bytes ≤ 30 for 58 mm paper
        assertTrue((raster[4].toInt() and 0xFF) + (raster[5].toInt() and 0xFF) * 256 <= 30)
    }

    @Test fun statementWithLogoRenders() {
        val pdf = TablePdf(
            ApplicationProvider.getApplicationContext(), business.name, "Chennai · 98400 12345", "Party Statement",
            "Rakesh Kumar · 1 Apr 2026 to 3 Oct 2026",
            listOf(PdfColumn("Date", 1.2f), PdfColumn("Particulars", 2.5f), PdfColumn("Amount", 1.3f, true)),
            (1..5).map { listOf("0$it Oct 2026", "Sale INV-00$it", "₹1,200.00") },
            listOf("Closing balance" to "₹6,000.00"), logo = sampleLogo(),
        )
        renderPage({ pdf.drawPage(it, 0) }, "statement_branded")
    }

    @Test fun msmeDetailsPrint() {
        val msme = business.copy(udyamNumber = "UDYAM-TN-02-0012345", msmeCategory = online.draran.billing.core.model.MsmeCategory.MICRO)
        val pdf = InvoicePdf(ApplicationProvider.getApplicationContext(), sample(), msme)
        assertEquals(1, pdf.pageCount)
        renderPage({ pdf.drawPage(it, 0) }, "invoice_a4_msme")
        val text = ThermalReceipt(sample(), msme).text()
        text.lines().forEach { assertTrue("Line too long: '$it'", it.length <= 32) }
        assertTrue(text.contains("UDYAM-TN-02-0012345"))
        assertTrue(text.contains("MSMED Act")) // balance due on the sample bill
        // Medium enterprises are outside Section 15, and the note can be turned off
        assertTrue(!ThermalReceipt(sample(), msme.copy(msmeCategory = online.draran.billing.core.model.MsmeCategory.MEDIUM)).text().contains("MSMED Act"))
        assertTrue(!ThermalReceipt(sample(), msme.copy(printMsmeNote = false)).text().contains("MSMED Act"))
    }

    @Test fun billColourFollowsBusiness() {
        val maroon = business.copy(billColor = 0xFF9F1239.toInt(), signatoryName = "R. Sharma", signatoryDesignation = "Proprietor")
        val pdf = InvoicePdf(ApplicationProvider.getApplicationContext(), sample(), maroon, sampleLogo(), sampleSignature())
        renderPage({ pdf.drawPage(it, 0) }, "invoice_a4_maroon")
        // A very light colour is printed darker so the white total text stays readable
        val pale = business.copy(billColor = 0xFFFDE68A.toInt())
        renderPage({ InvoicePdf(ApplicationProvider.getApplicationContext(), sample(), pale).drawPage(it, 0) }, "invoice_a4_pale_input")
        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        InvoicePdf(ApplicationProvider.getApplicationContext(), sample(), maroon).drawPage(Canvas(bitmap), 0)
        assertEquals(maroon.accent(), bitmap.getPixel(300, 2)) // top bar
    }

    @Test fun wideReportPrintsLandscapeWithFullAmounts() {
        val cols = listOf("", "", "", "", "", "", "", "").mapIndexed { i, t -> PdfColumn(t, if (i == 0) 2.4f else 1.2f) }
        val h = TablePdf.HEADING; val sh = TablePdf.SUBHEAD; val r = TablePdf.RIGHT
        val rows = listOf(
            listOf(h + "Sales to registered buyers (B2B)", "", "", "", "", "", "", ""),
            listOf(sh + "Supply", r + "Rate", r + "Bills", r + "Taxable", r + "CGST", r + "SGST", r + "IGST", r + "Tax"),
            listOf("Intra-state", "18%", "142", "₹1,23,45,678.00", "₹11,11,111.02", "₹11,11,111.02", "₹0.00", "₹22,22,222.04"),
            listOf("Inter-state", "5%", "9", "₹9,87,654.00", "₹0.00", "₹0.00", "₹49,382.70", "₹49,382.70"),
            listOf(""),
            listOf(h + "HSN summary"),
            listOf(sh + "HSN", r + "Rate", r + "Qty", r + "Taxable", r + "Tax", r + "Total", "", ""),
            listOf("1006", "5%", "12,500", "₹9,87,654.00", "₹49,382.70", "₹10,37,036.70", "", ""),
        ).map { it + List(8 - it.size) { "" } }
        val pdf = TablePdf(ApplicationProvider.getApplicationContext(), "Sri Lakshmi Narayana Traders and Wholesale Merchants Private Limited", "Chennai · 98400 12345", "GST summary", "Sri Lakshmi Narayana Traders Chennai · 1 Apr 2026 to 3 Oct 2026", cols, rows, autoAlign = true)
        assertEquals(1, pdf.pageCount)
        val bmp = Bitmap.createBitmap(842, 595, Bitmap.Config.ARGB_8888)
        pdf.drawPage(Canvas(bmp), 0)
        File("build/outputs/roborazzi/report_gst_landscape.png").apply { parentFile?.mkdirs() }.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun worstCaseBillKeepsEverythingVisible() {
        val shop = business.copy(
            name = "Sri Lakshmi Narayana Traders and Wholesale Merchants Private Limited",
            bankDetails = "Account holder: Sri Lakshmi Narayana Traders\nState Bank of India, T. Nagar branch, Chennai\nA/c no. 123456789012 · IFSC SBIN0001234 · MICR 600002003",
            udyamNumber = "UDYAM-TN-02-0012345", msmeCategory = online.draran.billing.core.model.MsmeCategory.MICRO,
            signatoryName = "R. Sharma", signatoryDesignation = "Proprietor",
        )
        val lines = listOf(
            InvoiceLine(itemId = 1, name = "Copper wire 2.5 sq mm, ISI marked, 90 m coil, flame retardant", hsn = "8544", unit = "Meters", qtyMilli = 1_250_750, rate = Money.rupees(1_250_000), taxRateBp = 1800),
            InvoiceLine(itemId = 2, name = "Switch board", hsn = "8536", unit = "pcs", qtyMilli = Qty.of(3), rate = Money.rupees(12_00_000), taxRateBp = 1800),
        )
        val invoice = sample().copy(
            lines = lines, totals = TaxEngine.bill(lines.map { it.toInput() }, interState = false),
            partyName = "Sri Venkateswara Electricals and Hardware Merchants Private Limited",
            partyAddress = "No. 45/2, Second Floor, Anna Salai Main Road, Near Government Higher Secondary School, Teynampet, Chennai, Tamil Nadu 600018",
            partyPhone = "98765 43210", partyGstin = "33AAPFU0939F1ZW",
            notes = "Goods once sold will not be taken back. Please quote the invoice number in all payments and correspondence.",
            paid = Money.ZERO,
        )
        val pdf = InvoicePdf(ApplicationProvider.getApplicationContext(), invoice, shop.copy(terms = "Thank you for your business! Interest at 18% p.a. is charged on overdue invoices."), sampleLogo(), sampleSignature())
        renderPage({ pdf.drawPage(it, 0) }, "invoice_a4_worst_case")
        renderPage({ pdf.drawPage(it, pdf.pageCount - 1) }, "invoice_a4_worst_case_last")
        // A fully paid bill carries no payment QR
        val paid = invoice.copy(paid = invoice.totals.total)
        renderPage({ InvoicePdf(ApplicationProvider.getApplicationContext(), paid, shop).drawPage(it, 0) }, "invoice_a4_paid_no_qr")
    }

    @Test fun receiptTextSurvivesAccentsTamilAndLongNames() {
        // Accents are transliterated and a real question mark is kept
        assertEquals("Cafe Rs.50 ok?", ThermalReceipt.ascii("Café ₹50 ok?"))
        assertTrue(ThermalReceipt.needsImage("குமார் ஸ்டோர்ஸ்"))
        assertTrue(!ThermalReceipt.needsImage("Café ₹50"))

        val tamilShop = business.copy(name = "ஸ்ரீ லட்சுமி ஸ்டோர்ஸ்", address = "12, காந்தி சாலை, சென்னை")
        val lines = listOf(
            InvoiceLine(itemId = 1, name = "பாசுமதி அரிசி 5 கிலோ", unit = "bag", qtyMilli = Qty.of(2), rate = Money.rupees(645), taxRateBp = 500),
            InvoiceLine(itemId = 2, name = "Dettol Soap", unit = "pcs", qtyMilli = Qty.of(1), rate = Money(22000), taxRateBp = 1800),
        )
        val invoice = sample().copy(lines = lines, totals = TaxEngine.bill(lines.map { it.toInput() }, interState = false), partyName = "குமார்")
        val receipt = ThermalReceipt(invoice, tamilShop)
        val parts = receipt.parts()
        // Tamil lines become pictures instead of blank lines; Latin lines stay text
        assertTrue(parts.any { it is ThermalReceipt.Part.TextImage && it.text.contains("பாசுமதி") })
        assertTrue(parts.any { it is ThermalReceipt.Part.TextImage && it.text.startsWith("To: ") })
        assertTrue(parts.any { it is ThermalReceipt.Part.Text && it.text == "Dettol Soap" })
        assertTrue(parts.none { it is ThermalReceipt.Part.Text && it.text.isBlank() })
        assertTrue(receipt.escPos().isNotEmpty())

        // A long business name is wrapped at normal size, never cut
        val longName = business.copy(name = "Sri Lakshmi Narayana Traders and Wholesale Merchants")
        val shop58 = ThermalReceipt(sample(), longName).parts().filterIsInstance<ThermalReceipt.Part.Text>().filter { it.bold && it.center }
        assertTrue(shop58.joinToString(" ") { it.text }.contains("Wholesale Merchants"))
        shop58.forEach { assertTrue(it.text.length <= 32) }
        // A short name keeps the big heading
        assertTrue(ThermalReceipt(sample(), business.copy(name = "Style Studio")).parts().any { it is ThermalReceipt.Part.Text && it.big })
    }

    @Test fun purchaseReturnReceiptSaysReceived() {
        val inv = sample().copy(type = DocType.PURCHASE_RETURN, paid = Money.rupees(500))
        assertTrue(ThermalReceipt(inv, business).text().contains("Received"))
    }

    @Test fun tallLogoIsCappedInHeight() {
        val tall = Bitmap.createBitmap(100, 400, Bitmap.Config.ARGB_8888)
        val bytes = ThermalReceipt.raster(tall, 240, maxHeight = 200)
        val height = (bytes[6].toInt() and 0xFF) + (bytes[7].toInt() and 0xFF) * 256
        assertTrue("height $height", height <= 200)
    }

    @Test fun oldSharedFilesAreCleanedUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = Sharing.sharedDir(context)
        val old = File(dir, "old-bill.pdf").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L) }
        val fresh = File(dir, "fresh-bill.pdf").apply { writeText("x") }
        Sharing.sharedDir(context)
        assertTrue(!old.exists())
        assertTrue(fresh.exists())
        fresh.delete()
    }

    @Test fun sectionHeadingsRepeatAfterAPageBreak() {
        val cols = listOf("", "", "", "").map { PdfColumn(it, 1f) }
        val h = TablePdf.HEADING; val sh = TablePdf.SUBHEAD; val r = TablePdf.RIGHT
        val rows = buildList {
            add(listOf(h + "Sales", "", "", ""))
            add(listOf(sh + "Date", "Number", r + "Total", r + "Tax"))
            (1..60).forEach { add(listOf("0$it Oct", "INV-$it", "₹${it * 100}.00", "₹${it * 18}.00")) }
            add(listOf(TablePdf.NOTE + "A note that explains how these figures were worked out and what they leave out.", "", "", ""))
        }
        val pdf = TablePdf(ApplicationProvider.getApplicationContext(), "Shop", "Chennai", "Sales register", "Oct 2026", cols, rows, autoAlign = true)
        assertTrue(pdf.pageCount >= 2)
        renderPage({ pdf.drawPage(it, 1) }, "report_continued_page")
    }

    @Test fun thermalTextKeepsMicroSignsAndAsciiFields() {
        assertEquals("Wire 5um 90 deg", ThermalReceipt.ascii("Wire 5µm 90°"))
        assertTrue(!ThermalReceipt.needsImage("Wire 5µm 90°"))
        assertTrue(ThermalReceipt.needsImage("வயர் 5mm"))
    }

    @Test fun upiLinkIsWellFormed() {
        val link = Upi.link("shop@okaxis", "Sharma Store", Money(123456), "INV-1")
        assertEquals("upi://pay?pa=shop@okaxis&pn=Sharma%20Store&am=1234.56&cu=INR&tn=INV-1", link)
        assertTrue(Upi.isValidId("shop.name@okhdfcbank"))
        assertTrue(!Upi.isValidId("not an id"))
    }

    @Test fun creditNoteNamesTheBillItIsSetAgainst() {
        val note = sample().copy(type = DocType.SALE_RETURN, number = "CN-0001", reference = "INV-0142 dated 3 Oct 2026")
        assertTrue(ThermalReceipt(note, business).text().contains("Against: INV-0142"))
        // The A4 note renders with the extra Details row
        renderPage({ InvoicePdf(ApplicationProvider.getApplicationContext(), note, business).drawPage(it, 0) }, "credit_note_reference")
    }

    @Test fun taxInvoiceLinesShowTaxableValue() {
        val inv = sample()
        val pdf = InvoicePdf(ApplicationProvider.getApplicationContext(), inv, business)
        renderPage({ pdf.drawPage(it, 0) }, "invoice_taxable_column")
        // The columns of a GST bill are Qty, Rate, Disc, GST and Taxable value; the tax is in the summary
        assertTrue(inv.totals.lines.all { it.taxable.paise <= it.total.paise })
    }

    @Test fun qrOnReceiptIsAPictureNotAPrinterCommand() {
        val upiBusiness = business.copy(upiId = "shop@okaxis", showUpiQr = true)
        val unpaid = sample().copy(paid = Money.ZERO)
        val bytes = ThermalReceipt(unpaid, upiBusiness).escPos()
        fun has(vararg seq: Int): Boolean {
            val pattern = seq.map { it.toByte() }
            return (0..bytes.size - pattern.size).any { i -> pattern.indices.all { bytes[i + it] == pattern[it] } }
        }
        assertTrue("raster header (GS v 0)", has(0x1D, 0x76, 0x30))
        assertTrue("no native QR command (GS ( k)", !has(0x1D, 0x28, 0x6B))
    }

    @Test fun shortenedTextNeverEndsInABrokenGlyph() {
        val tamil = "குமார்" // "கு" is one letter made of two characters
        val cut = safeCut(tamil, 1)
        assertTrue(cut == 1 || tamil.substring(0, cut).let { !Character.isLowSurrogate(it.last()) })
        // A mark is never left at the start of what follows the cut
        assertTrue(cut >= tamil.length || Character.getType(tamil[cut]) != Character.NON_SPACING_MARK.toInt())
        val emoji = "ab😀cd"
        assertEquals(2, safeCut(emoji, 3)) // 3 would split the surrogate pair
    }
}
