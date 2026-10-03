package online.draran.billing.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import online.draran.billing.core.database.BillingDatabase
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.DateRange
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.InvoiceLine
import online.draran.billing.core.model.Item
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Party
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.Payment
import online.draran.billing.core.model.PaymentDirection
import online.draran.billing.core.model.Qty
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RepositoryTest {

    private lateinit var db: BillingDatabase
    private lateinit var business: BusinessRepository
    private lateinit var items: ItemRepository
    private lateinit var parties: PartyRepository
    private lateinit var invoices: InvoiceRepository
    private lateinit var payments: PaymentRepository
    private lateinit var expenses: ExpenseRepository
    private lateinit var reports: ReportsRepository
    private val today = LocalDate.of(2026, 10, 3)

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BillingDatabase::class.java)
            .allowMainThreadQueries().build()
        business = BusinessRepository(db.businessDao())
        items = ItemRepository(db.itemDao())
        parties = PartyRepository(db.partyDao())
        val allocator = Allocator(db)
        invoices = InvoiceRepository(db, business, allocator)
        payments = PaymentRepository(db, allocator)
        expenses = ExpenseRepository(db)
        reports = ReportsRepository(db, business, parties, items)
        business.save(Business(name = "Sharma Store", stateCode = "33", gstEnabled = true, gstin = "33AAAAA0000A1Z5", roundOff = false, onboarded = true))
    }

    @After fun tearDown() = db.close()

    private fun line(itemId: Long?, qty: Long, rupees: Long, tax: Int = 0, cost: Long = 0) =
        InvoiceLine(itemId = itemId, name = "Item $itemId", qtyMilli = Qty.of(qty), rate = Money.rupees(rupees), taxRateBp = tax, costRate = Money.rupees(cost))

    private suspend fun sale(partyId: Long?, total: Long, paid: Long = 0, date: LocalDate = today, type: DocType = DocType.SALE, stateCode: String = "") =
        invoices.save(
            InvoiceDraft(
                type = type, number = "", date = date, partyId = partyId, partyName = if (partyId == null) "" else "P$partyId",
                partyStateCode = stateCode, lines = listOf(line(null, 1, total)), paidNow = Money.rupees(paid),
            ),
        )

    @Test fun cashSaleIsFullyPaid() = runTest {
        val id = sale(null, 500, paid = 500)
        val inv = invoices.get(id)!!
        assertEquals("INV-0001", inv.number)
        assertEquals("Cash Customer", inv.partyName)
        assertEquals(Money.rupees(500), inv.paid)
        assertTrue(inv.isPaid)
    }

    @Test fun paymentsSettleOldestBillsFirst() = runTest {
        val pid = parties.save(Party(name = "Ravi", type = PartyType.CUSTOMER, openingBalance = Money.rupees(100)))
        val first = sale(pid, 1000, date = today.minusDays(2))
        val second = sale(pid, 500, paid = 100, date = today.minusDays(1))
        assertEquals(Money.rupees(100 + 1000 + 500 - 100), parties.party(pid).first()!!.balance)

        payments.save(Payment(direction = PaymentDirection.IN, number = "", partyId = pid, partyName = "Ravi", date = today, amount = Money.rupees(1200)))
        assertEquals(Money.rupees(1000), invoices.get(first)!!.paid)
        assertEquals(Money.rupees(300), invoices.get(second)!!.paid) // 100 at sale + 200 settled
        assertEquals(Money.rupees(300), parties.party(pid).first()!!.balance)

        // Deleting the first bill frees the payment, which now settles the second bill fully
        invoices.delete(first)
        assertEquals(Money.rupees(500), invoices.get(second)!!.paid)
        assertEquals(Money.rupees(100 + 500 - 100 - 1200), parties.party(pid).first()!!.balance)
    }

    @Test fun editingABillReplacesItsPayment() = runTest {
        val pid = parties.save(Party(name = "Asha"))
        val id = sale(pid, 1000, paid = 1000)
        val draft = invoices.draftForEdit(id)!!
        assertEquals(Money.rupees(1000), draft.paidNow)
        invoices.save(draft.copy(lines = listOf(line(null, 2, 1000)), paidNow = Money.rupees(500)))
        val inv = invoices.get(id)!!
        assertEquals(Money.rupees(2000), inv.totals.total)
        assertEquals(Money.rupees(500), inv.paid)
        assertEquals(Money.rupees(1500), parties.party(pid).first()!!.balance)
        assertEquals("INV-0001", inv.number)
    }

    @Test fun stockFollowsPurchasesSalesReturnsAndAdjustments() = runTest {
        val itemId = items.save(Item(name = "Basmati Rice", unit = "kg", openingStockMilli = Qty.of(10), lowStockMilli = Qty.of(5), salePrice = Money.rupees(90)))
        val supplier = parties.save(Party(name = "Metro", type = PartyType.SUPPLIER))
        invoices.save(InvoiceDraft(type = DocType.PURCHASE, number = "B-77", date = today, partyId = supplier, partyName = "Metro", lines = listOf(line(itemId, 5, 60))))
        invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, lines = listOf(line(itemId, 3, 90)), paidNow = Money.rupees(270)))
        invoices.save(InvoiceDraft(type = DocType.SALE_RETURN, number = "", date = today, lines = listOf(line(itemId, 1, 90))))
        invoices.save(InvoiceDraft(type = DocType.ESTIMATE, number = "", date = today, lines = listOf(line(itemId, 50, 90))))
        items.adjustStock(itemId, -Qty.of(2), "Damaged")
        val stock = items.item(itemId).first()!!
        assertEquals(Qty.of(10 + 5 - 3 + 1 - 2), stock.stockMilli)
        assertFalse(stock.isLow)
        assertEquals(Money.rupees(60), items.get(itemId)!!.purchasePrice) // updated from purchase
        assertEquals(4, items.moves(itemId).first().size) // estimates do not move stock
        // Supplier balance: we owe ₹300
        assertEquals(Money.rupees(-300), parties.party(supplier).first()!!.balance)
    }

    @Test fun searchFindsByPrefixAndBarcode() = runTest {
        items.save(Item(name = "Basmati Rice 5kg", barcode = "8901234567890"))
        items.save(Item(name = "Toor Dal"))
        assertEquals(listOf("Basmati Rice 5kg"), items.items("bas ri").first().map { it.item.name })
        assertEquals(1, items.items("8901234").first().size)
        assertEquals("Basmati Rice 5kg", items.byBarcode("8901234567890")!!.name)
        assertEquals(2, items.items("").first().size)
    }

    @Test fun ledgerHasRunningBalance() = runTest {
        val pid = parties.save(Party(name = "Kumar", openingBalance = Money.rupees(50)))
        sale(pid, 200, date = today.minusDays(1))
        payments.save(Payment(direction = PaymentDirection.IN, number = "", partyId = pid, partyName = "Kumar", date = today, amount = Money.rupees(120)))
        val ledger = parties.ledger(pid).first()
        assertEquals(listOf("OPENING", "SALE", "PAYMENT_IN"), ledger.map { it.kind })
        assertEquals(listOf(5000L, 25000L, 13000L), ledger.map { it.balance.paise })
        assertFalse(parties.canDelete(pid))
    }

    @Test fun interStateSaleUsesIgstAndGstReportSplitsB2b() = runTest {
        val b2b = parties.save(Party(name = "Kerala Traders", gstin = "32AAAAA0000A1Z5", stateCode = "32"))
        invoices.save(
            InvoiceDraft(
                type = DocType.SALE, number = "", date = today, partyId = b2b, partyName = "Kerala Traders",
                partyGstin = "32AAAAA0000A1Z5", partyStateCode = "32", lines = listOf(line(null, 1, 1000, tax = 1800).copy(hsn = "1006")),
            ),
        )
        invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, lines = listOf(line(null, 2, 100, tax = 500).copy(hsn = "1006")), paidNow = Money.rupees(210)))
        val gst = reports.gst(DateRange.thisMonth(today))
        assertEquals(1, gst.b2b.size)
        assertEquals(Money.rupees(180), gst.b2b.first().igst)
        assertEquals(Money.rupees(5), gst.b2c.first().cgst)
        assertEquals(Money.rupees(190), gst.outputTax)
        assertEquals(Money.rupees(1200), gst.outwardTaxable)
        assertEquals(2, gst.hsn.size) // one HSN row per tax rate
    }

    @Test fun profitAndLossUsesCostAndExpenses() = runTest {
        invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, lines = listOf(line(null, 2, 100, cost = 60)), paidNow = Money.rupees(200)))
        expenses.save(online.draran.billing.core.model.Expense(category = "Rent", date = today, amount = Money.rupees(30)))
        val pl = reports.profitLoss(DateRange.thisMonth(today))
        assertEquals(Money.rupees(200), pl.netSales)
        assertEquals(Money.rupees(120), pl.costOfGoods)
        assertEquals(Money.rupees(80), pl.grossProfit)
        assertEquals(Money.rupees(50), pl.netProfit)
    }

    @Test fun dashboardSummarisesToday() = runTest {
        val pid = parties.save(Party(name = "Ravi"))
        val sup = parties.save(Party(name = "Metro", type = PartyType.SUPPLIER))
        sale(pid, 700)
        sale(null, 300, paid = 300)
        sale(null, 100, paid = 100, date = today.minusDays(1))
        invoices.save(InvoiceDraft(type = DocType.PURCHASE, number = "X1", date = today, partyId = sup, partyName = "Metro", lines = listOf(line(null, 1, 400))))
        val d = reports.dashboard(today).first()
        assertEquals(Money.rupees(1000), d.todaySales)
        assertEquals(2, d.todayCount)
        assertEquals(Money.rupees(100), d.yesterdaySales)
        assertEquals(Money.rupees(700), d.toCollect)
        assertEquals(Money.rupees(400), d.toPay)
        assertEquals(7, d.week.size)
        assertEquals(4, d.recent.size)
    }

    @Test fun estimateConvertsToSale() = runTest {
        val est = sale(null, 999, type = DocType.ESTIMATE)
        val draft = invoices.draftFrom(est, DocType.SALE)!!
        assertEquals(DocType.SALE, draft.type)
        assertEquals("INV-0001", draft.number)
        val saleId = invoices.save(draft.copy(paidNow = Money.rupees(999)))
        assertEquals(saleId, invoices.convertedSale(est))
    }

    @Test fun backupRoundTrip() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(BillingDatabase.NAME)
        val fileDb = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        ItemRepository(fileDb.itemDao()).save(Item(name = "Saved Item"))
        val out = ByteArrayOutputStream()
        BackupManager(context, fileDb).write(out)
        ItemRepository(fileDb.itemDao()).save(Item(name = "Added After Backup"))

        BackupManager(context, fileDb).restore(out.toByteArray().inputStream())
        val reopened = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        assertEquals(listOf("Saved Item"), ItemRepository(reopened.itemDao()).items().first().map { it.item.name })
        reopened.close()
    }

    @Test fun customFieldsAndPresets() = runTest {
        val id = invoices.save(
            InvoiceDraft(
                type = DocType.SALE, number = "", date = today, lines = listOf(line(null, 1, 2000)), paidNow = Money.rupees(2000),
                customFields = listOf("Roll / Admission no." to "A-102", "Class / Course" to "Class 8 B", "Batch / Section" to ""),
            ),
        )
        assertEquals(listOf("Roll / Admission no." to "A-102", "Class / Course" to "Class 8 B"), invoices.get(id)!!.customFields)
        assertEquals(listOf("Roll / Admission no." to "A-102", "Class / Course" to "Class 8 B"), invoices.draftForEdit(id)!!.customFields)

        assertEquals(12, items.addPresets(online.draran.billing.core.model.BusinessType.SALON, gstEnabled = true))
        assertEquals(0, items.addPresets(online.draran.billing.core.model.BusinessType.SALON, gstEnabled = true)) // no duplicates
        val haircut = items.items("haircut").first().first { it.item.name == "Haircut" }.item
        assertEquals(1800, haircut.taxRateBp)
        assertEquals("9997", haircut.hsn)
        assertEquals(online.draran.billing.core.model.ItemType.SERVICE, haircut.type)

        val b = business.get().copy(type = online.draran.billing.core.model.BusinessType.EDUCATION, customFieldLabels = online.draran.billing.core.model.BusinessType.EDUCATION.customFields, signatoryName = "R. Lakshmi", signatoryDesignation = "Principal")
        business.save(b)
        val loaded = business.get()
        assertEquals(online.draran.billing.core.model.BusinessType.EDUCATION, loaded.type)
        assertEquals(4, loaded.customFieldLabels.size)
        assertEquals("Principal", loaded.signatoryDesignation)
        assertEquals("Tax Invoice", loaded.saleTitle()) // GST on in this test
        assertEquals("Fee Receipt", loaded.copy(gstEnabled = false).saleTitle())
    }

    @Test fun signaturePhotoBackgroundIsRemoved() {
        val w = 120
        val h = 60
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(235, 232, 225)) // paper
        for (x in 20 until 100) for (y in 28 until 32) bmp.setPixel(x, y, android.graphics.Color.rgb(30, 30, 40)) // ink stroke
        val clean = BrandingManager.cleanSignature(bmp)
        assertTrue(clean.width < w && clean.height < h) // trimmed to the ink
        assertEquals(0, android.graphics.Color.alpha(clean.getPixel(0, 0)))
        assertTrue(android.graphics.Color.alpha(clean.getPixel(clean.width / 2, clean.height / 2)) > 200)
    }

    @Test fun backupIncludesLogoAndSignature() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(BillingDatabase.NAME)
        val fileDb = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        val dir = java.io.File(context.filesDir, BrandingManager.DIR).apply { mkdirs() }
        java.io.File(dir, BrandingManager.LOGO).writeBytes(byteArrayOf(1, 2, 3))
        java.io.File(dir, BrandingManager.SIGNATURE).writeBytes(byteArrayOf(4, 5))
        val out = ByteArrayOutputStream()
        BackupManager(context, fileDb).write(out)
        dir.listFiles()!!.forEach { it.delete() }
        BackupManager(context, fileDb).restore(out.toByteArray().inputStream())
        assertEquals(3, java.io.File(dir, BrandingManager.LOGO).length())
        assertEquals(2, java.io.File(dir, BrandingManager.SIGNATURE).length())
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun logoSetsBillColourOnce() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val branding = BrandingManager(context, business)
        fun logoFile(r: Int, g: Int, b: Int): android.net.Uri {
            val bmp = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).apply {
                drawColor(android.graphics.Color.WHITE)
                drawCircle(60f, 60f, 50f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(r, g, b) })
            }
            val f = java.io.File(context.cacheDir, "logo_$r$g$b.png")
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            return android.net.Uri.fromFile(f)
        }
        branding.setLogo(logoFile(200, 20, 40)) // red logo
        val first = business.get().billColor
        assertTrue("red expected, got ${online.draran.billing.core.model.BillColors.hex(first)}", online.draran.billing.core.model.BillColors.red(first) > online.draran.billing.core.model.BillColors.blue(first) + 60)
        assertTrue(online.draran.billing.core.model.BillColors.contrastWithWhite(business.get().accent()) >= 4.5)
        // A new logo does not override a colour already set
        branding.setLogo(logoFile(20, 60, 200))
        assertEquals(first, business.get().billColor)
        assertEquals(first, branding.logoColours(BrandingManager.LOGO).let { business.get().billColor })
        assertTrue(online.draran.billing.core.model.BillColors.blue(branding.logoColours(BrandingManager.LOGO).first()) > 100)
    }

    // ---- Audit fixes ----

    @Test fun editingAnOldBillKeepsItsOwnGstSetting() = runTest {
        // A bill made while GST was off...
        business.save(business.get().copy(gstEnabled = false))
        val id = invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, lines = listOf(line(null, 1, 1000, tax = 1800)), paidNow = Money.rupees(1000)))
        assertEquals(Money.rupees(1000), invoices.get(id)!!.totals.total)
        // ...then the shop registers for GST; fixing a note on the old bill must not add tax to it
        business.save(business.get().copy(gstEnabled = true, roundOff = true))
        val draft = invoices.draftForEdit(id)!!
        assertEquals(false, draft.gstEnabled)
        invoices.save(draft.copy(notes = "Delivered"))
        val after = invoices.get(id)!!
        assertEquals(Money.rupees(1000), after.totals.total)
        assertFalse(after.gstEnabled)
        assertEquals(Money.rupees(1000), after.paid)
        // A new bill uses the shop's current setting
        val fresh = invoices.get(invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, lines = listOf(line(null, 1, 1000, tax = 1800)))))!!
        assertTrue(fresh.gstEnabled)
        assertEquals(Money.rupees(1180), fresh.totals.total)
    }

    @Test fun editingABillKeepsItsReceiptNumber() = runTest {
        val id = sale(null, 500, paid = 200)
        val first = db.paymentDao().forInvoice(id).single()
        invoices.save(invoices.draftForEdit(id)!!.copy(paidNow = Money.rupees(300)))
        invoices.save(invoices.draftForEdit(id)!!.copy(paidNow = Money.rupees(500)))
        val after = db.paymentDao().forInvoice(id).single()
        assertEquals(first.id, after.id)
        assertEquals(first.number, after.number)
        assertEquals(Money.rupees(500), invoices.get(id)!!.paid)
        // Clearing the payment removes the receipt
        invoices.save(invoices.draftForEdit(id)!!.copy(paidNow = Money.ZERO))
        assertTrue(db.paymentDao().forInvoice(id).isEmpty())
        assertEquals(Money.ZERO, invoices.get(id)!!.paid)
    }

    @Test fun billNumbersStayUnique() = runTest {
        // A number typed by hand that the sequence would reach later
        invoices.save(InvoiceDraft(type = DocType.SALE, number = "INV-0002", date = today, lines = listOf(line(null, 1, 10))))
        val a = sale(null, 10)
        val b = sale(null, 10)
        val numbers = listOf(invoices.get(a)!!.number, invoices.get(b)!!.number, "INV-0002")
        assertEquals(numbers.size, numbers.toSet().size)
        // Typing a number that is taken is refused; supplier bill numbers may repeat
        val taken = runCatching { invoices.save(InvoiceDraft(type = DocType.SALE, number = "INV-0002", date = today, lines = listOf(line(null, 1, 10)))) }
        assertTrue(taken.exceptionOrNull()?.message?.contains("already used") == true)
        invoices.save(InvoiceDraft(type = DocType.PURCHASE, number = "S-1", date = today, lines = listOf(line(null, 1, 10))))
        invoices.save(InvoiceDraft(type = DocType.PURCHASE, number = "S-1", date = today, lines = listOf(line(null, 1, 10))))
        // The suggestion skips used numbers
        val next = invoices.nextNumber(DocType.SALE)
        assertFalse(invoices.numberTaken(DocType.SALE, next, 0))
    }

    @Test fun hugeLinesAreRefusedInsteadOfOverflowing() = runTest {
        val huge = InvoiceLine(itemId = null, name = "Gold", qtyMilli = Qty.of(900_000), rate = Money.rupees(9_999_999_999), taxRateBp = 0)
        val result = runCatching { invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, lines = listOf(huge))) }
        assertTrue(result.exceptionOrNull()?.message?.contains("too large") == true)
        assertEquals(null, InvoiceRepository.lineProblem(line(null, 10, 1_000_000)))
    }

    @Test fun duplicatingAnEstimateDoesNotMarkItConverted() = runTest {
        val est = sale(null, 999, type = DocType.ESTIMATE)
        val copy = invoices.save(invoices.draftFrom(est, DocType.ESTIMATE)!!)
        assertEquals(null, invoices.get(copy)!!.convertedFromId)
        assertEquals(null, invoices.convertedSale(est))
        assertEquals(null, invoices.observeConvertedSale(est).first())
        // The real conversion is still found, and the copy of the estimate is not mistaken for it
        val saleId = invoices.save(invoices.draftFrom(est, DocType.SALE)!!)
        assertEquals(saleId, invoices.observeConvertedSale(est).first())
    }

    @Test fun duplicateKeepsThePaymentPeriodNotTheOldDueDate() = runTest {
        val id = invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today.minusDays(40), dueDate = today.minusDays(25), lines = listOf(line(null, 1, 10))))
        val copy = invoices.draftFrom(id, DocType.SALE)!!
        assertEquals(LocalDate.now().plusDays(15), copy.dueDate)
    }

    @Test fun purchasePriceFollowsTheNewestPurchaseOnly() = runTest {
        val item = items.save(Item(name = "Rice", purchasePrice = Money.rupees(50)))
        fun purchase(date: LocalDate, rate: Long) = InvoiceDraft(type = DocType.PURCHASE, number = "", date = date, lines = listOf(line(item, 1, rate)))
        invoices.save(purchase(today, 60))
        assertEquals(Money.rupees(60), items.get(item)!!.purchasePrice)
        invoices.save(purchase(today.minusDays(30), 40)) // an older bill entered late
        assertEquals(Money.rupees(60), items.get(item)!!.purchasePrice)
        invoices.save(purchase(today.plusDays(1), 0)) // a free line never sets the cost
        assertEquals(Money.rupees(60), items.get(item)!!.purchasePrice)
    }

    @Test fun ledgerShowsABillBeforeThePaymentTakenWithIt() = runTest {
        val pid = parties.save(Party(name = "Ravi", type = PartyType.CUSTOMER))
        sale(pid, 1000, paid = 1000)
        val rows = parties.ledger(pid).first()
        assertEquals(listOf(false, true), rows.map { it.isPayment })
        assertEquals(listOf(Money.rupees(1000), Money.ZERO), rows.map { it.balance })
    }

    @Test fun searchKeepsTamilVowelSigns() {
        assertEquals("கடை* வீதி*", ItemRepository.ftsQuery("கடை வீதி"))
        assertEquals("rice* bas*", ItemRepository.ftsQuery("rice, bas"))
        assertEquals(null, ItemRepository.ftsQuery("   "))
    }

    @Test fun gstReportLeavesOutPurchasesFromUnregisteredSuppliers() = runTest {
        fun purchase(gstin: String) = InvoiceDraft(
            type = DocType.PURCHASE, number = "P", date = today, partyGstin = gstin, partyStateCode = "33",
            lines = listOf(line(null, 1, 1000, tax = 1800)),
        )
        invoices.save(purchase("33AAPFU0939F1ZW"))
        invoices.save(purchase("")) // unregistered supplier: no input credit
        val report = reports.gst(DateRange.today(today))
        assertEquals(Money.rupees(180), report.itc)
    }

    @Test fun restoreRefusesABackupFromANewerApp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(BillingDatabase.NAME)
        val fileDb = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        ItemRepository(fileDb.itemDao()).save(Item(name = "Keep Me"))

        // A well-formed SQLite file stamped with a future schema version
        val fake = java.io.File(context.cacheDir, "future.db").also { it.delete() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(fake, null).use { d ->
            d.execSQL("CREATE TABLE business (id INTEGER PRIMARY KEY)")
            d.version = BillingDatabase.VERSION + 5
        }
        val zip = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zip).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("manifest.txt")); z.write("app=Modern Kallaa Petti\n".toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("database.db")); z.write(fake.readBytes()); z.closeEntry()
        }
        val failure = runCatching { BackupManager(context, fileDb).restore(zip.toByteArray().inputStream()) }.exceptionOrNull()
        assertTrue(failure?.message, failure?.message?.contains("newer version") == true)
        // Nothing was replaced: the current data is still there and the database is still open
        assertEquals(listOf("Keep Me"), ItemRepository(fileDb.itemDao()).items().first().map { it.item.name })
        assertTrue(fileDb.isOpen)
        fileDb.close()
    }

    @Test fun restoreRefusesADamagedFile() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(BillingDatabase.NAME)
        val fileDb = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        ItemRepository(fileDb.itemDao()).save(Item(name = "Keep Me"))
        val zip = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zip).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("manifest.txt")); z.write("app=Modern Kallaa Petti\n".toByteArray()); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("database.db")); z.write("SQLite format 3\u0000 but nothing useful follows".toByteArray()); z.closeEntry()
        }
        assertTrue(runCatching { BackupManager(context, fileDb).restore(zip.toByteArray().inputStream()) }.isFailure)
        assertEquals(listOf("Keep Me"), ItemRepository(fileDb.itemDao()).items().first().map { it.item.name })
        fileDb.close()
    }

    @Test fun creditNoteReducesWhatTheSaleShowsAsDue() = runTest {
        val pid = parties.save(Party(name = "Ravi", type = PartyType.CUSTOMER))
        val saleId = sale(pid, 1000, date = today.minusDays(3))
        val noteId = sale(pid, 300, date = today.minusDays(2), type = DocType.SALE_RETURN)
        payments.save(Payment(direction = PaymentDirection.IN, number = "", partyId = pid, partyName = "Ravi", date = today, amount = Money.rupees(700)))
        assertEquals(Money.rupees(1000), invoices.get(saleId)!!.paid)
        assertTrue(invoices.get(saleId)!!.isPaid)
        assertEquals(Money.rupees(300), invoices.get(noteId)!!.paid)
        assertEquals(Money.ZERO, parties.party(pid).first()!!.balance)
        // The sales list agrees
        val summary = invoices.summaries(listOf(DocType.SALE)).first().single { it.id == saleId }
        assertEquals(Money.ZERO, summary.balance)
        // Deleting the note puts the amount back on the sale
        invoices.delete(noteId)
        assertEquals(Money.rupees(300), invoices.get(saleId)!!.balance)
    }

    @Test fun aLargerCreditNoteIsSettledByARefund() = runTest {
        val pid = parties.save(Party(name = "Anita", type = PartyType.CUSTOMER))
        sale(pid, 200, paid = 200, date = today.minusDays(3))
        val noteId = sale(pid, 300, date = today.minusDays(1), type = DocType.SALE_RETURN)
        assertEquals(Money.rupees(300), invoices.get(noteId)!!.balance) // we owe the customer
        payments.save(Payment(direction = PaymentDirection.OUT, number = "", partyId = pid, partyName = "Anita", date = today, amount = Money.rupees(300)))
        assertEquals(Money.ZERO, invoices.get(noteId)!!.balance)
        assertEquals(Money.ZERO, parties.party(pid).first()!!.balance)
    }

    @Test fun debitNoteReducesWhatWeOweOnAPurchase() = runTest {
        val sid = parties.save(Party(name = "Metro", type = PartyType.SUPPLIER))
        val bill = invoices.save(InvoiceDraft(type = DocType.PURCHASE, number = "M1", date = today.minusDays(5), partyId = sid, partyName = "Metro", lines = listOf(line(null, 1, 1000))))
        val note = invoices.save(InvoiceDraft(type = DocType.PURCHASE_RETURN, number = "", date = today.minusDays(2), partyId = sid, partyName = "Metro", lines = listOf(line(null, 1, 250))))
        assertEquals(Money.rupees(750), invoices.get(bill)!!.balance)
        assertEquals(Money.ZERO, invoices.get(note)!!.balance)
    }

    @Test fun gstr3bSeparatesNilRatedSales() = runTest {
        invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, lines = listOf(line(null, 1, 1000, tax = 1800)), paidNow = Money.rupees(1180)))
        invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, lines = listOf(line(null, 1, 400, tax = 0)), paidNow = Money.rupees(400)))
        val report = reports.gst(DateRange.today(today))
        assertEquals(Money.rupees(1000), report.outwardTaxable)
        assertEquals(Money.rupees(400), report.outwardNilExempt)
        assertEquals(Money.rupees(180), report.outputTax)
    }

    @Test fun restoreKeepsABeforeRestoreCopyThatCanBeRestored() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(BillingDatabase.NAME)
        java.io.File(context.filesDir, "backups").deleteRecursively()
        val fileDb = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        ItemRepository(fileDb.itemDao()).save(Item(name = "First"))
        val out = ByteArrayOutputStream()
        BackupManager(context, fileDb).write(out)
        ItemRepository(fileDb.itemDao()).save(Item(name = "Second"))

        val manager = BackupManager(context, fileDb)
        manager.restore(out.toByteArray().inputStream())
        val safety = manager.autoBackups().single()
        assertTrue(manager.isBeforeRestore(safety))

        // Undo the restore from the copy: both items are back
        val reopened = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        assertEquals(listOf("First"), ItemRepository(reopened.itemDao()).items().first().map { it.item.name })
        BackupManager(context, reopened).restoreAuto(safety)
        val again = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        assertEquals(setOf("First", "Second"), ItemRepository(again.itemDao()).items().first().map { it.item.name }.toSet())
        again.close()
    }

    @Test fun backupHoldsChangesStillInTheLog() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(BillingDatabase.NAME)
        val fileDb = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        ItemRepository(fileDb.itemDao()).save(Item(name = "Logged"))
        // A reader holding an old snapshot stops the checkpoint from folding the log in
        val reader = android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(BillingDatabase.NAME).path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        reader.beginTransactionNonExclusive()
        reader.rawQuery("SELECT count(*) FROM item", null).use { it.moveToFirst() }
        ItemRepository(fileDb.itemDao()).save(Item(name = "After Reader"))
        val out = ByteArrayOutputStream()
        BackupManager(context, fileDb).write(out)
        reader.endTransaction()
        reader.close()

        context.deleteDatabase(BillingDatabase.NAME)
        val other = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        BackupManager(context, other).restore(out.toByteArray().inputStream())
        val reopened = Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).allowMainThreadQueries().build()
        assertEquals(setOf("Logged", "After Reader"), ItemRepository(reopened.itemDao()).items().first().map { it.item.name }.toSet())
        reopened.close()
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun sidewaysPhotoIsTurnedUpright() {
        val bmp = android.graphics.Bitmap.createBitmap(200, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val turned = BrandingManager.upright(bmp, android.media.ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(100, turned.width)
        assertEquals(200, turned.height)
        assertTrue(BrandingManager.upright(bmp, android.media.ExifInterface.ORIENTATION_NORMAL) === bmp)
    }
}
