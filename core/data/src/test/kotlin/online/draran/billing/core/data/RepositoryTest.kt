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
}
