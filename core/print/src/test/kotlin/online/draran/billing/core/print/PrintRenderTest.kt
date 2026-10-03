package online.draran.billing.core.print

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import online.draran.billing.core.model.Business
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

    @Test fun upiLinkIsWellFormed() {
        val link = Upi.link("shop@okaxis", "Sharma Store", Money(123456), "INV-1")
        assertEquals("upi://pay?pa=shop%40okaxis&pn=Sharma%20Store&am=1234.56&cu=INR&tn=INV-1", link)
        assertTrue(Upi.isValidId("shop.name@okhdfcbank"))
        assertTrue(!Upi.isValidId("not an id"))
    }
}
