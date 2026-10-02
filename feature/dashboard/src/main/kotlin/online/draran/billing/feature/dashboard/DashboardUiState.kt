package online.draran.billing.feature.dashboard

import online.draran.billing.core.model.DaySales
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.RecentTransaction
import java.time.LocalDate
import java.time.LocalTime

enum class Greeting {
    MORNING, AFTERNOON, EVENING;

    companion object {
        fun at(time: LocalTime): Greeting = when (time.hour) {
            in 0..11 -> MORNING
            in 12..16 -> AFTERNOON
            else -> EVENING
        }
    }
}

/** Everything the dashboard shows, in one immutable snapshot. */
data class DashboardUiState(
    val greeting: Greeting,
    val businessName: String,
    val todaySales: Money,
    val todayBillCount: Int,
    /** Percentage change against yesterday; null when yesterday had no sales. */
    val changeVsYesterdayPercent: Int?,
    val toCollect: Money,
    val toCollectPartyCount: Int,
    val toPay: Money,
    val toPayPartyCount: Int,
    val lowStockCount: Int,
    val week: List<DaySales>,
    val recent: List<RecentTransaction>,
    /** Days since the last backup; null if never backed up. */
    val lastBackupDaysAgo: Int?,
) {
    companion object {
        fun empty(today: LocalDate, now: LocalTime, businessName: String) = DashboardUiState(
            greeting = Greeting.at(now),
            businessName = businessName,
            todaySales = Money.ZERO,
            todayBillCount = 0,
            changeVsYesterdayPercent = null,
            toCollect = Money.ZERO,
            toCollectPartyCount = 0,
            toPay = Money.ZERO,
            toPayPartyCount = 0,
            lowStockCount = 0,
            week = (6 downTo 0).map { DaySales(today.minusDays(it.toLong()), Money.ZERO) },
            recent = emptyList(),
            lastBackupDaysAgo = null,
        )
    }
}

/** Where the dashboard can send the user. The app module maps these to routes. */
enum class DashboardDestination {
    NEW_SALE, COUNTER, PAYMENT_IN, ADD_ITEM, PURCHASE, EXPENSE, ESTIMATE, REPORTS,
    TO_COLLECT, TO_PAY, LOW_STOCK, ALL_TRANSACTIONS, BACKUP, SETTINGS,
}
