package online.draran.billing.core.database

import androidx.room.Embedded
import online.draran.billing.core.model.DocType

data class ItemStockRow(@Embedded val item: ItemEntity, val stock: Long)

data class PartyBalanceRow(@Embedded val party: PartyEntity, val balance: Long, val lastActivity: Long?)

data class InvoiceSummaryRow(
    val id: Long,
    val type: DocType,
    val number: String,
    val date: Long,
    val partyName: String,
    val total: Long,
    val paid: Long,
    val dueDate: Long? = null,
)

/** Sale bills past their due date that still have money to collect. */
data class OverdueRow(val count: Int, val amount: Long)

data class TopItemRow(val name: String, val qty: Long, val unit: String, val total: Long)

data class OpenDocRow(val id: Long, val date: Long, val total: Long, val paid: Long)

data class DayTotalRow(val date: Long, val total: Long)

/** A dashboard / day-book row from invoices, payments or expenses. */
data class ActivityRow(
    val id: Long,
    /** DocType name, "PAYMENT_IN", "PAYMENT_OUT" or "EXPENSE". */
    val kind: String,
    val number: String,
    val partyName: String,
    val date: Long,
    val amount: Long,
    val balance: Long,
    val mode: String?,
    val createdAt: Long,
)

data class LedgerRow(
    val date: Long,
    val kind: String,
    val number: String,
    val amount: Long,
    val refId: Long,
    val isPayment: Boolean,
    val createdAt: Long,
)

data class StockMoveRow(val date: Long, val qty: Long, val reason: String, val reference: String, val createdAt: Long)

data class TypeTotalsRow(
    val type: DocType,
    val count: Int,
    val taxable: Long,
    val cgst: Long,
    val sgst: Long,
    val igst: Long,
    val total: Long,
)

data class GstRateRow(
    val type: DocType,
    val b2b: Boolean,
    val interState: Boolean,
    val taxRateBp: Int,
    val docs: Int,
    val taxable: Long,
    val cgst: Long,
    val sgst: Long,
    val igst: Long,
)

data class HsnRow(
    val hsn: String,
    val unit: String,
    val taxRateBp: Int,
    val qty: Long,
    val taxable: Long,
    val cgst: Long,
    val sgst: Long,
    val igst: Long,
    val total: Long,
)

data class ItemSalesRow(val name: String, val qty: Long, val unit: String, val total: Long, val cost: Long)

data class CategoryTotalRow(val category: String, val total: Long)

data class ModeTotalRow(val direction: String, val mode: String, val total: Long)

/** A bill that a credit or debit note has been set against. */
data class AgainstRow(val number: String, val date: Long)
