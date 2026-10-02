package online.draran.billing.feature.dashboard

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.model.DaySales
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.RecentTransaction
import online.draran.billing.core.model.TransactionType
import java.time.LocalDate
import java.time.LocalTime

/** Realistic sample data for previews and screenshot tests. */
object DashboardSampleData {
    private val today: LocalDate = LocalDate.of(2026, 10, 2)

    val filled = DashboardUiState(
        greeting = Greeting.MORNING,
        businessName = "Sharma General Store",
        todaySales = Money(24_850_00),
        todayBillCount = 18,
        changeVsYesterdayPercent = 12,
        toCollect = Money(1_42_300_00),
        toCollectPartyCount = 9,
        toPay = Money(38_640_00),
        toPayPartyCount = 3,
        lowStockCount = 4,
        week = listOf(18_200, 22_450, 15_900, 27_300, 19_750, 31_200, 24_850).mapIndexed { i, rupees ->
            DaySales(today.minusDays((6 - i).toLong()), Money.rupees(rupees.toLong()))
        },
        recent = listOf(
            RecentTransaction(1, TransactionType.SALE, "INV-0142", "Rakesh Kumar", today, Money(3_450_00), Money.ZERO),
            RecentTransaction(2, TransactionType.PAYMENT_IN, "RCPT-0058", "Gupta Traders", today, Money(12_000_00), Money.ZERO),
            RecentTransaction(3, TransactionType.SALE, "INV-0141", "Cash Customer", today, Money(860_00), Money.ZERO),
            RecentTransaction(4, TransactionType.PURCHASE, "PB-0031", "Metro Wholesale", today.minusDays(1), Money(18_640_00), Money(8_640_00)),
            RecentTransaction(5, TransactionType.SALE, "INV-0140", "Anita Verma", today.minusDays(1), Money(5_200_00), Money(2_200_00)),
        ),
        lastBackupDaysAgo = 0,
    )

    val empty = DashboardUiState.empty(today, LocalTime.of(9, 30), "Sharma General Store")
}

@Preview(name = "Dashboard - light", showBackground = true, heightDp = 1500)
@Composable
private fun DashboardLightPreview() {
    BillingTheme { DashboardScreen(DashboardSampleData.filled, onNavigate = {}) }
}

@Preview(name = "Dashboard - dark", showBackground = true, heightDp = 1500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DashboardDarkPreview() {
    BillingTheme { DashboardScreen(DashboardSampleData.filled, onNavigate = {}) }
}

@Preview(name = "Dashboard - empty", showBackground = true, heightDp = 1300)
@Composable
private fun DashboardEmptyPreview() {
    BillingTheme { DashboardScreen(DashboardSampleData.empty, onNavigate = {}) }
}
