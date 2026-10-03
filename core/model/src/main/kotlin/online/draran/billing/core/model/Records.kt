package online.draran.billing.core.model

import java.time.LocalDate

/** The shop's own details, printed on every bill. Single row. */
data class Business(
    val name: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val stateCode: String = "33",
    val gstEnabled: Boolean = false,
    val gstin: String = "",
    val upiId: String = "",
    val bankDetails: String = "",
    val terms: String = "Thank you for your business!",
    val roundOff: Boolean = true,
    val showUpiQr: Boolean = true,
    val pricesIncludeTax: Boolean = false,
    val thermalWidthMm: Int = 58,
    val printerAddress: String = "",
    val printerName: String = "",
    val prefixes: Map<DocType, String> = DocType.entries.associateWith { it.defaultPrefix },
    val onboarded: Boolean = false,
) {
    fun prefix(type: DocType) = prefixes[type] ?: type.defaultPrefix
}

data class Party(
    val id: Long = 0,
    val name: String,
    val type: PartyType = PartyType.CUSTOMER,
    val phone: String = "",
    val gstin: String = "",
    val stateCode: String = "",
    val address: String = "",
    /** Positive: they owe us. Negative: we owe them. */
    val openingBalance: Money = Money.ZERO,
)

data class PartyWithBalance(val party: Party, val balance: Money, val lastActivity: LocalDate?)

data class Item(
    val id: Long = 0,
    val name: String,
    val type: ItemType = ItemType.GOODS,
    val code: String = "",
    val barcode: String = "",
    val unit: String = "pcs",
    val hsn: String = "",
    val salePrice: Money = Money.ZERO,
    val purchasePrice: Money = Money.ZERO,
    val taxRateBp: Int = 0,
    val taxInclusive: Boolean = false,
    val openingStockMilli: Long = 0,
    val lowStockMilli: Long = 0,
    val favourite: Boolean = false,
    val category: String = "",
) {
    val tracksStock: Boolean get() = type == ItemType.GOODS
}

data class ItemWithStock(val item: Item, val stockMilli: Long) {
    val isLow: Boolean get() = item.tracksStock && item.lowStockMilli > 0 && stockMilli <= item.lowStockMilli
}

data class InvoiceLine(
    val id: Long = 0,
    val itemId: Long?,
    val name: String,
    val hsn: String = "",
    val unit: String = "pcs",
    val qtyMilli: Long,
    val rate: Money,
    val discountBp: Int = 0,
    val taxRateBp: Int = 0,
    val taxInclusive: Boolean = false,
    /** Cost per unit at the time of sale, for profit reports. */
    val costRate: Money = Money.ZERO,
) {
    fun toInput() = LineInput(qtyMilli, rate, discountBp, taxRateBp, taxInclusive)
}

data class Invoice(
    val id: Long = 0,
    val type: DocType,
    val number: String,
    val seq: Long = 0,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val partyId: Long?,
    val partyName: String,
    val partyPhone: String = "",
    val partyGstin: String = "",
    val partyAddress: String = "",
    val placeOfSupply: String,
    val interState: Boolean,
    val gstEnabled: Boolean,
    val lines: List<InvoiceLine>,
    val totals: BillTotals,
    val notes: String = "",
    /** Sum of payments applied to this document. */
    val paid: Money = Money.ZERO,
    val convertedFromId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val balance: Money get() = if (type.tracksPayment) totals.total - paid else Money.ZERO
    val isPaid: Boolean get() = type.tracksPayment && !balance.isNegative && balance.isZero
}

/** Lightweight row for lists. */
data class InvoiceSummary(
    val id: Long,
    val type: DocType,
    val number: String,
    val date: LocalDate,
    val partyName: String,
    val total: Money,
    val paid: Money,
) {
    val balance: Money get() = if (type.tracksPayment) total - paid else Money.ZERO
}

data class Payment(
    val id: Long = 0,
    val direction: PaymentDirection,
    val number: String,
    val partyId: Long?,
    val partyName: String,
    val date: LocalDate,
    val amount: Money,
    val mode: PaymentMode = PaymentMode.CASH,
    val reference: String = "",
    val note: String = "",
    /** Set when the payment was taken while saving that bill. */
    val invoiceId: Long? = null,
)

data class Expense(
    val id: Long = 0,
    val category: String,
    val date: LocalDate,
    val amount: Money,
    val mode: PaymentMode = PaymentMode.CASH,
    val note: String = "",
)

data class StockMove(
    val date: LocalDate,
    val qtyMilli: Long,
    val reason: String,
    val reference: String,
)

/** One row of a party statement. */
data class LedgerEntry(
    val date: LocalDate,
    val kind: String,
    val number: String,
    /** Positive increases receivable. */
    val amount: Money,
    val balance: Money,
    val refId: Long,
    val isPayment: Boolean,
)
