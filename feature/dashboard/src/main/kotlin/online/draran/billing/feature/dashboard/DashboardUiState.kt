package online.draran.billing.feature.dashboard

import online.draran.billing.core.model.DaySales
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.RecentTransaction
import java.time.LocalDate
import java.time.LocalTime

enum class Greeting {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        /** 5 am to 11:59 am morning, then afternoon, evening from 5 pm, night from 9 pm to 4:59 am. */
        fun at(time: LocalTime): Greeting = when (time.hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
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
    /** Sale bills past their due date with money still to collect. */
    val overdueCount: Int = 0,
    val overdue: Money = Money.ZERO,
    /** Best sellers this month. */
    val topItems: List<TopItemUi> = emptyList(),
) {
    /** A short message to share the day's figures on WhatsApp. */
    fun summaryText(today: LocalDate): String = buildString {
        appendLine("$businessName · ${today.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))}")
        appendLine("Sales today: ${online.draran.billing.core.common.IndianFormat.rupees(todaySales)} ($todayBillCount ${if (todayBillCount == 1) "bill" else "bills"})")
        if (!toCollect.isZero) appendLine("To collect: ${online.draran.billing.core.common.IndianFormat.rupees(toCollect)}")
        if (!toPay.isZero) appendLine("To pay: ${online.draran.billing.core.common.IndianFormat.rupees(toPay)}")
        if (overdueCount > 0) appendLine("Overdue: $overdueCount ${if (overdueCount == 1) "bill" else "bills"}, ${online.draran.billing.core.common.IndianFormat.rupees(overdue)}")
        if (topItems.isNotEmpty()) appendLine("Top this month: " + topItems.joinToString(", ") { it.name })
    }.trim()

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

/** One best seller: name, quantity sold (e.g. "12 kg") and sales value. */
data class TopItemUi(val name: String, val quantity: String, val amount: Money)

/** Where the dashboard can send the user. The app module maps these to routes. */
enum class DashboardDestination {
    NEW_SALE, COUNTER, PAYMENT_IN, ADD_ITEM, PURCHASE, EXPENSE, ESTIMATE, REPORTS,
    TO_COLLECT, TO_PAY, LOW_STOCK, ALL_TRANSACTIONS, BACKUP, SETTINGS, OVERDUE, ITEM_SALES,
}
