package online.draran.billing.core.model

import java.time.LocalDate

/** Total sales for one calendar day, used by the weekly chart. */
data class DaySales(val date: LocalDate, val total: Money)

enum class TransactionType { SALE, PURCHASE, PAYMENT_IN, PAYMENT_OUT, EXPENSE }

/** A row in the "Recent activity" list on the dashboard. */
data class RecentTransaction(
    val id: Long,
    val type: TransactionType,
    val number: String,
    val partyName: String,
    val date: LocalDate,
    val total: Money,
    val balanceDue: Money,
)
