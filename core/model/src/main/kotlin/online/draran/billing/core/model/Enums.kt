package online.draran.billing.core.model

/**
 * Kinds of bill the app can create. Each type knows how it moves stock,
 * how it changes the party balance and which way money flows when paid.
 */
enum class DocType(
    val title: String,
    val shortTitle: String,
    val defaultPrefix: String,
    /** +1 adds to stock, -1 removes, 0 no effect. */
    val stockSign: Int,
    /** +1 increases what the party owes us (receivable), -1 decreases it. */
    val ledgerSign: Int,
    /** Direction of a payment made against this document. */
    val paymentDirection: PaymentDirection,
    /** Party role expected for this document. */
    val partyType: PartyType,
) {
    SALE("Sale bill", "Sale", "INV-", -1, +1, PaymentDirection.IN, PartyType.CUSTOMER),
    PURCHASE("Purchase bill", "Purchase", "PUR-", +1, -1, PaymentDirection.OUT, PartyType.SUPPLIER),
    ESTIMATE("Estimate", "Estimate", "EST-", 0, 0, PaymentDirection.IN, PartyType.CUSTOMER),
    SALE_RETURN("Credit note", "Sale return", "CN-", +1, -1, PaymentDirection.OUT, PartyType.CUSTOMER),
    PURCHASE_RETURN("Debit note", "Purchase return", "DN-", -1, +1, PaymentDirection.IN, PartyType.SUPPLIER),
    ;

    val tracksPayment: Boolean get() = this != ESTIMATE
}

enum class PaymentDirection {
    /** Money received from a party. */
    IN,

    /** Money paid to a party. */
    OUT,
    ;

    /** Effect on the receivable balance: receiving money reduces what they owe. */
    val ledgerSign: Int get() = if (this == IN) -1 else +1
    val prefix: String get() = if (this == IN) "RCPT-" else "PAY-"
}

enum class PaymentMode(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    CARD("Card"),
    BANK("Bank transfer"),
    CHEQUE("Cheque"),
}

enum class PartyType(val label: String) { CUSTOMER("Customer"), SUPPLIER("Supplier") }

enum class ItemType { GOODS, SERVICE }

/** Common units of measure; free text is also allowed. */
val COMMON_UNITS = listOf("pcs", "nos", "kg", "g", "ltr", "ml", "box", "pkt", "dozen", "mtr", "bag", "set", "hr")

/** GST slabs in basis points (5 % = 500). Editable per item. */
val GST_RATES_BP = listOf(0, 25, 300, 500, 1200, 1800, 2800, 4000)

val EXPENSE_CATEGORIES = listOf(
    "Rent", "Salary", "Electricity", "Transport", "Tea and snacks",
    "Maintenance", "Internet and phone", "Packaging", "Advertising", "Other",
)
