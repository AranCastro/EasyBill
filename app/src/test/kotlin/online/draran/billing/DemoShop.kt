package online.draran.billing

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
import java.time.LocalDate

/** A grocery shop with a week of bills, used by the journey test and the Play Store screenshots. */
internal suspend fun seedDemoShop(
    items: ItemRepository,
    parties: PartyRepository,
    invoices: InvoiceRepository,
    payments: PaymentRepository,
    expenses: ExpenseRepository,
) {
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
