package online.draran.billing

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.data.ExpenseRepository
import online.draran.billing.core.data.InvoiceDraft
import online.draran.billing.core.data.InvoiceRepository
import online.draran.billing.core.data.ItemRepository
import online.draran.billing.core.data.PartyRepository
import online.draran.billing.core.data.PaymentRepository
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.Expense
import online.draran.billing.core.model.InvoiceLine
import online.draran.billing.core.model.Item
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Party
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.Payment
import online.draran.billing.core.model.PaymentDirection
import online.draran.billing.core.model.PaymentMode
import online.draran.billing.core.model.Qty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the real app (Hilt + Room + Compose) on the JVM: onboarding, data entry,
 * a full sale through the UI, then every main screen. Screenshots go to
 * app/build/outputs/roborazzi/journey_*.png.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppJourneyTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var business: BusinessRepository
    @Inject lateinit var items: ItemRepository
    @Inject lateinit var parties: PartyRepository
    @Inject lateinit var invoices: InvoiceRepository
    @Inject lateinit var payments: PaymentRepository
    @Inject lateinit var expenses: ExpenseRepository

    @Before fun inject() = hilt.inject()

    private var shot = 0
    private var prefix = "journey"
    private fun capture(name: String) {
        compose.waitForIdle()
        shot++
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${prefix}_${shot.toString().padStart(2, '0')}_$name.png")
    }

    private fun waitForText(text: String, timeout: Long = 10_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun clickFab(label: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(label).performClick()
        compose.waitForIdle()
    }

    private fun clickText(text: String) {
        waitForText(text)
        compose.onAllNodesWithText(text, substring = false).onFirst().performClick()
        compose.waitForIdle()
    }

    /** Lets background work (PDF preview, database) finish before a screenshot. */
    private fun settle(ms: Long = 2_500) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            compose.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
        }
        compose.waitForIdle()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun skipOnboarding() {
        waitForText("Start billing")
        runBlocking {
            business.save(
                business.get().copy(
                    name = "Sharma General Store", phone = "9840012345", upiId = "sharmastore@okaxis", gstEnabled = true,
                    gstin = "33AAPFU0939F1Z2", address = "12, Gandhi Road, T. Nagar\nChennai 600017", onboarded = true,
                ),
            )
        }
        waitForText("Today's sales")
    }

    private fun seed() = runBlocking {
        val today = LocalDate.now()
        val rice = items.save(Item(name = "Basmati Rice 5 kg", unit = "bag", hsn = "1006", salePrice = Money.rupees(645), purchasePrice = Money.rupees(560), taxRateBp = 500, openingStockMilli = Qty.of(40), lowStockMilli = Qty.of(5), favourite = true, barcode = "8901234567890"))
        val oil = items.save(Item(name = "Sunflower Oil 1 L", unit = "pcs", hsn = "1512", salePrice = Money(18500), purchasePrice = Money(16000), taxRateBp = 500, openingStockMilli = Qty.of(24), lowStockMilli = Qty.of(6), favourite = true))
        val dal = items.save(Item(name = "Toor Dal", unit = "kg", hsn = "0713", salePrice = Money(14200), purchasePrice = Money(12500), openingStockMilli = Qty.of(3), lowStockMilli = Qty.of(5), favourite = true))
        val soap = items.save(Item(name = "Dettol Soap 4-pack", unit = "pcs", hsn = "3401", salePrice = Money.rupees(220), purchasePrice = Money.rupees(180), taxRateBp = 1800, taxInclusive = true, openingStockMilli = Qty.of(30), favourite = true))
        items.save(Item(name = "Tea Powder 250 g", unit = "pcs", salePrice = Money.rupees(140), purchasePrice = Money.rupees(118), taxRateBp = 500, openingStockMilli = Qty.of(18), favourite = true))
        items.save(Item(name = "Home Delivery", type = online.draran.billing.core.model.ItemType.SERVICE, salePrice = Money.rupees(30), taxRateBp = 1800))
        val ravi = parties.save(Party(name = "Ravi Kumar", phone = "9876543210", openingBalance = Money.rupees(500)))
        val anita = parties.save(Party(name = "Anita Verma", phone = "9840012345"))
        val kerala = parties.save(Party(name = "Kerala Traders", phone = "9447001122", gstin = "32AABCK1234L1ZV", stateCode = "32"))
        val metro = parties.save(Party(name = "Metro Wholesale", type = PartyType.SUPPLIER, phone = "9000011111"))

        fun l(id: Long, name: String, qty: Long, paise: Long, tax: Int, cost: Long) = InvoiceLine(itemId = id, name = name, qtyMilli = Qty.of(qty), rate = Money(paise), taxRateBp = tax, costRate = Money(cost))
        invoices.save(InvoiceDraft(type = DocType.PURCHASE, number = "MW-7781", date = today.minusDays(6), partyId = metro, partyName = "Metro Wholesale", lines = listOf(l(rice, "Basmati Rice 5 kg", 10, 56000, 500, 56000), l(oil, "Sunflower Oil 1 L", 12, 16000, 500, 16000)), paidNow = Money.rupees(4000), paymentMode = PaymentMode.BANK))
        val days = listOf(6, 5, 4, 3, 2, 1, 1, 0)
        days.forEachIndexed { i, d ->
            invoices.save(
                InvoiceDraft(
                    type = DocType.SALE, number = "", date = today.minusDays(d.toLong()),
                    partyId = if (i % 3 == 0) ravi else null, partyName = if (i % 3 == 0) "Ravi Kumar" else "",
                    lines = listOf(l(rice, "Basmati Rice 5 kg", 1 + i % 2L, 64500, 500, 56000), l(oil, "Sunflower Oil 1 L", 2, 18500, 500, 16000)),
                    paidNow = if (i % 3 == 0) Money.rupees(500) else Money.rupees(5000), paymentMode = if (i % 2 == 0) PaymentMode.UPI else PaymentMode.CASH,
                ),
            )
        }
        invoices.save(InvoiceDraft(type = DocType.SALE, number = "", date = today, partyId = kerala, partyName = "Kerala Traders", partyGstin = "32AABCK1234L1ZV", partyStateCode = "32", lines = listOf(l(soap, "Dettol Soap 4-pack", 20, 18644, 1800, 18000)), paidNow = Money.ZERO))
        invoices.save(InvoiceDraft(type = DocType.ESTIMATE, number = "", date = today, partyId = anita, partyName = "Anita Verma", lines = listOf(l(dal, "Toor Dal", 5, 14200, 0, 12500))))
        payments.save(Payment(direction = PaymentDirection.IN, number = "", partyId = ravi, partyName = "Ravi Kumar", date = today, amount = Money.rupees(800), mode = PaymentMode.UPI))
        expenses.save(Expense(category = "Electricity", date = today.minusDays(2), amount = Money.rupees(1850)))
        expenses.save(Expense(category = "Tea & Snacks", date = today, amount = Money.rupees(120)))
    }

    @Test fun fullJourney() {
        // 1. First run: onboarding
        waitForText("Start billing")
        capture("onboarding")
        compose.onNode(hasSetTextAction() and hasText("Business name *")).performTextInput("Sharma General Store")
        compose.onNode(hasSetTextAction() and hasText("Mobile number")).performTextInput("9840012345")
        compose.onNode(hasSetTextAction() and hasText("UPI ID (optional)")).performTextInput("sharmastore@okaxis")
        clickText("Start billing")
        waitForText("Today's sales")
        runBlocking {
            assertTrue(business.get().onboarded)
            business.save(business.get().copy(gstEnabled = true, gstin = "33AAPFU0939F1Z2", address = "12, Gandhi Road, T. Nagar\nChennai 600017"))
        }
        capture("dashboard_empty")

        // 2. Seed realistic data and look at every tab
        seed()
        waitForText("2 bills today")
        capture("dashboard")
        clickText("Sales")
        waitForText("INV-0009")
        capture("sales")
        clickText("Items")
        waitForText("Basmati Rice 5 kg")
        capture("items")
        clickText("Parties")
        waitForText("Ravi Kumar")
        capture("parties")
        clickText("Ravi Kumar")
        waitForText("Transactions")
        capture("party_detail")
        back()
        clickText("More")
        waitForText("Counter billing")
        capture("more")

        // 3. A full sale through the UI
        clickText("Home")
        clickFab("New Sale")
        waitForText("Tap to add items")
        capture("editor_empty")
        clickText("Tap to add items")
        waitForText("Add items")
        compose.onNodeWithContentDescription("Add Basmati Rice 5 kg").performClick()
        compose.onNodeWithContentDescription("Add Sunflower Oil 1 L").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("More")[1].performClick() // second unit of oil
        capture("item_picker")
        clickText("Done")
        waitForText("Fully received")
        capture("editor_filled")
        val before = runBlocking { invoices.summaries(listOf(DocType.SALE)).first().size }
        clickText("Save")
        waitForText("Share PDF", timeout = 15_000)
        settle()
        capture("invoice_detail")
        runBlocking {
            val sales = invoices.summaries(listOf(DocType.SALE)).first()
            assertEquals(before + 1, sales.size)
            val newest = invoices.get(sales.first().id)!!
            assertEquals(2, newest.lines.size)
            // 1 x 645 + 5% = 677.25; 2 x 185 + 5% = 388.50; 1065.75 rounds to 1066
            assertEquals(Money(106600), newest.totals.total)
        }
    }

    @Test fun screensTour() {
        prefix = "tour"
        skipOnboarding()
        seed()
        waitForText("2 bills today")

        // Counter billing with UPI QR
        clickText("Counter")
        waitForText("Charge")
        compose.onAllNodesWithText("Basmati Rice 5 kg").onFirst().performClick()
        compose.onAllNodesWithText("Tea Powder 250 g").onFirst().performClick()
        compose.onAllNodesWithText("Tea Powder 250 g").onFirst().performClick()
        capture("counter")
        compose.onAllNodesWithText("Charge", substring = true).onFirst().performClick()
        clickText("UPI")
        settle(800)
        capture("counter_charge_upi")
        clickText("Paid · Save bill")
        waitForText("Saved INV-", timeout = 15_000)
        capture("counter_saved")
        back()

        // Reports
        clickText("Reports")
        waitForText("Profit & loss")
        capture("reports_hub")
        clickText("Profit & loss")
        waitForText("Net profit")
        settle(500)
        capture("report_profit_loss")
        back()
        clickText("GST summary")
        waitForText("Output tax")
        settle(500)
        capture("report_gst")
        back()
        back()

        // More: purchases, payments, expenses, settings
        clickText("More")
        clickText("Purchases")
        waitForText("MW-7781")
        capture("purchases")
        back()
        clickText("Payments")
        waitForText("RCPT-")
        capture("payments")
        back()
        clickText("Expenses")
        waitForText("Electricity")
        capture("expenses")
        clickFab("Add expense")
        waitForText("Save expense")
        capture("expense_editor")
        back()
        back()
        clickText("Settings")
        waitForText("Wallpaper colours")
        capture("settings")
        clickText("Invoice settings")
        waitForText("Bill numbering")
        capture("invoice_settings")
        back()
        clickText("Backup and restore")
        waitForText("Back up now")
        capture("backup")
        back()
        back()

        // Item editor and party editor
        clickText("Items")
        clickText("Basmati Rice 5 kg")
        waitForText("Current stock")
        settle(500)
        capture("item_editor")
        back()
        clickText("Parties")
        clickFab("Add party")
        waitForText("Opening balance")
        capture("party_editor")
    }
}
