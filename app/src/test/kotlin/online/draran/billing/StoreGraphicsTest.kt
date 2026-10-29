package online.draran.billing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.data.CapitalRepository
import online.draran.billing.core.data.ExpenseRepository
import online.draran.billing.core.data.InvoiceRepository
import online.draran.billing.core.data.ItemRepository
import online.draran.billing.core.data.PartyRepository
import online.draran.billing.core.data.PaymentRepository
import online.draran.billing.core.model.CapitalEntry
import online.draran.billing.core.model.CapitalKind
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.PaymentMode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * Raw screens for the Google Play listing, taken from the real app with a demo shop, at
 * 1080 x 2160 px (360 x 720 dp, xxhdpi). tools/store/make_store_graphics.py adds the captions
 * and writes the final 1080 x 1920 images into fastlane/metadata/android/en-IN/images.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "w360dp-h720dp-xxhdpi")
class StoreScreenshotsTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var business: BusinessRepository
    @Inject lateinit var items: ItemRepository
    @Inject lateinit var parties: PartyRepository
    @Inject lateinit var invoices: InvoiceRepository
    @Inject lateinit var payments: PaymentRepository
    @Inject lateinit var expenses: ExpenseRepository
    @Inject lateinit var capital: CapitalRepository

    @Before fun inject() = hilt.inject()

    private fun capture(name: String) {
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/store_raw_$name.png")
    }

    private fun waitForText(text: String, timeout: Long = 15_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun clickText(text: String) {
        waitForText(text)
        compose.onAllNodesWithText(text, substring = false).onFirst().performClick()
        compose.waitForIdle()
    }

    /** Lets animations, the PDF preview and database work finish. */
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

    @Test fun storeScreens() {
        waitForText("Start billing")
        runBlocking {
            business.save(
                business.get().copy(
                    name = "Sharma Stores", phone = "9840012345", upiId = "sharmastore@okaxis", gstEnabled = true,
                    gstin = "33AAPFU0939F1Z2", address = "12, Gandhi Road, T. Nagar\nChennai 600017", onboarded = true,
                ),
            )
        }
        waitForText("Today's sales")
        runBlocking {
            seedDemoShop(items, parties, invoices, payments, expenses)
            val today = LocalDate.now()
            fun entry(kind: CapitalKind, person: String, rupees: Long, daysAgo: Long, mode: PaymentMode = PaymentMode.CASH) =
                CapitalEntry(kind = kind, person = person, date = today.minusDays(daysAgo), amount = Money.rupees(rupees), mode = mode)
            capital.save(entry(CapitalKind.OWNER_IN, "", 25_000, 120))
            capital.save(entry(CapitalKind.LOAN_RECEIVED, "Father", 50_000, 110))
            capital.save(entry(CapitalKind.LOAN_RECEIVED, "Canara Bank", 2_00_000, 100, PaymentMode.BANK))
            capital.save(entry(CapitalKind.LOAN_REPAID, "Father", 15_000, 40, PaymentMode.UPI))
            capital.save(entry(CapitalKind.LOAN_REPAID, "Canara Bank", 20_000, 30, PaymentMode.BANK))
        }
        waitForText("2 bills today")
        capture("1_home")

        clickText("Sales")
        clickText("Kerala Traders")
        waitForText("Share PDF")
        settle(3_000)
        capture("2_bill")
        back()

        clickText("Items")
        waitForText("Toor Dal")
        capture("3_items")

        clickText("Parties")
        clickText("Ravi Kumar")
        waitForText("Transactions")
        capture("4_party")
        back()

        clickText("More")
        clickText("Capital and loans")
        waitForText("Record repayment")
        capture("5_capital")
        back()

        clickText("Reports")
        clickText("GST summary")
        waitForText("Output tax")
        capture("6_gst")
        back()
        back()

        clickText("Home")
        clickText("Counter")
        waitForText("Charge")
        compose.onAllNodesWithText("Basmati Rice 5 kg").onFirst().performClick()
        compose.onAllNodesWithText("Tea Powder 250 g").onFirst().performClick()
        compose.onAllNodesWithText("Tea Powder 250 g").onFirst().performClick()
        compose.onAllNodesWithText("Sunflower Oil 1 L").onFirst().performClick()
        capture("7_counter")
    }
}

/** The 512 x 512 px Play Store icon: the visible part of the adaptive launcher icon, full bleed (Play adds the rounded mask). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w512dp-h512dp-mdpi")
class StoreIconTest {

    @get:Rule val compose = createComposeRule()

    @Test fun playStoreIcon() {
        compose.setContent {
            Box(Modifier.size(512.dp)) {
                val full = 512.dp * (108f / 72f)
                Box(Modifier.requiredSize(512.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.requiredSize(full))
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(full))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/store_raw_icon.png")
    }
}
