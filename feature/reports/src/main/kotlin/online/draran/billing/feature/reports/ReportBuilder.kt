package online.draran.billing.feature.reports

import kotlinx.coroutines.flow.first
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.data.ReportsRepository
import online.draran.billing.core.designsystem.component.pretty
import online.draran.billing.core.model.DateRange
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Percent
import online.draran.billing.core.model.Qty
import online.draran.billing.core.model.abs
import online.draran.billing.core.model.sum
import javax.inject.Inject

enum class ReportKind(val title: String, val description: String, val usesRange: Boolean = true) {
    SALES("Sales register", "Every sale bill with amount received and due"),
    PURCHASES("Purchase register", "Every purchase bill with amount paid and due"),
    PROFIT_LOSS("Profit & loss", "Sales minus cost of goods and expenses"),
    DAY_BOOK("Day book", "All bills, payments and expenses in order"),
    ITEM_SALES("Item-wise sales", "Best sellers, quantity and profit by item"),
    STOCK("Stock summary", "Current stock and its value", usesRange = false),
    GST("GST summary", "GSTR-1 (B2B, B2C, HSN) and GSTR-3B totals"),
    PARTY_BALANCES("Party balances", "Who owes you and whom you owe", usesRange = false),
    EXPENSES("Expense report", "Spending by category"),
    CASH_FLOW("Cash flow", "Money in and out by payment mode"),
}

enum class Tone { NEUTRAL, GOOD, BAD, BRAND }

data class Kpi(val label: String, val value: Money, val tone: Tone = Tone.NEUTRAL)

data class ReportSection(
    val title: String?,
    val columns: List<String>,
    val rows: List<List<String>>,
    val totals: List<Pair<String, String>> = emptyList(),
)

data class ReportContent(val kpis: List<Kpi>, val sections: List<ReportSection>, val note: String? = null)

private fun rs(m: Money) = IndianFormat.rupees(m)
private fun pct(bp: Int) = "${Percent.format(bp)}%"

/** Turns repository data into display/export tables. */
class ReportBuilder @Inject constructor(private val repo: ReportsRepository) {

    suspend fun build(kind: ReportKind, range: DateRange): ReportContent = when (kind) {
        ReportKind.SALES -> register(DocType.SALE, range, "Received")
        ReportKind.PURCHASES -> register(DocType.PURCHASE, range, "Paid")
        ReportKind.PROFIT_LOSS -> profitLoss(range)
        ReportKind.DAY_BOOK -> dayBook(range)
        ReportKind.ITEM_SALES -> itemSales(range)
        ReportKind.STOCK -> stock()
        ReportKind.GST -> gst(range)
        ReportKind.PARTY_BALANCES -> partyBalances()
        ReportKind.EXPENSES -> expenses(range)
        ReportKind.CASH_FLOW -> cashFlow(range)
    }

    private suspend fun register(type: DocType, range: DateRange, paidWord: String): ReportContent {
        val docs = repo.register(type, range)
        val returnType = if (type == DocType.SALE) DocType.SALE_RETURN else DocType.PURCHASE_RETURN
        val returns = repo.register(returnType, range)
        val total = docs.map { it.total }.sum()
        val paid = docs.map { it.paid }.sum()
        return ReportContent(
            kpis = listOf(
                Kpi("Total (${docs.size} bills)", total, Tone.BRAND),
                Kpi(paidWord, paid, Tone.GOOD),
                Kpi("Due", total - paid, Tone.BAD),
                Kpi("Returns", returns.map { it.total }.sum()),
            ),
            sections = listOf(
                ReportSection(
                    null,
                    listOf("Date", "Number", "Party", "Total", paidWord, "Due"),
                    docs.map { listOf(it.date.pretty(), it.number, it.partyName, rs(it.total), rs(it.paid), rs(it.balance)) },
                    listOf("Total" to rs(total), paidWord to rs(paid), "Due" to rs(total - paid)),
                ),
            ) + if (returns.isEmpty()) emptyList() else listOf(
                ReportSection(
                    returnType.title + "s",
                    listOf("Date", "Number", "Party", "Total"),
                    returns.map { listOf(it.date.pretty(), it.number, it.partyName, rs(it.total)) },
                ),
            ),
        )
    }

    private suspend fun profitLoss(range: DateRange): ReportContent {
        val pl = repo.profitLoss(range)
        val rows = mutableListOf(
            listOf("Sales (before tax)", rs(pl.sales)),
            listOf("Less: sale returns", rs(-pl.saleReturns)),
            listOf("Net sales", rs(pl.netSales)),
            listOf("Less: cost of goods sold", rs(-pl.costOfGoods)),
            listOf("Gross profit", rs(pl.grossProfit)),
        )
        pl.expenses.forEach { (c, m) -> rows += listOf("Expense: $c", rs(-m)) }
        rows += listOf("Net profit", rs(pl.netProfit))
        return ReportContent(
            kpis = listOf(
                Kpi("Net sales", pl.netSales, Tone.BRAND),
                Kpi("Gross profit", pl.grossProfit, if (pl.grossProfit.isNegative) Tone.BAD else Tone.GOOD),
                Kpi("Expenses", pl.totalExpenses, Tone.BAD),
                Kpi("Net profit", pl.netProfit, if (pl.netProfit.isNegative) Tone.BAD else Tone.GOOD),
            ),
            sections = listOf(ReportSection(null, listOf("Particulars", "Amount"), rows)),
            note = "Cost of goods uses each item's purchase price at the time of sale. Keep purchase prices updated for accurate profit.",
        )
    }

    private suspend fun dayBook(range: DateRange): ReportContent {
        val entries = repo.dayBook(range, limit = Int.MAX_VALUE).first()
        fun label(kind: String) = when (kind) {
            "PAYMENT_IN" -> "Payment in"
            "PAYMENT_OUT" -> "Payment out"
            "EXPENSE" -> "Expense"
            else -> DocType.entries.firstOrNull { it.name == kind }?.shortTitle ?: kind
        }
        // Separate kinds only: a credit sale and the receipt that later settles it must not both count as money in
        val sales = entries.filter { it.kind == "SALE" }.map { it.amount }.sum()
        val paymentsIn = entries.filter { it.kind == "PAYMENT_IN" }.map { it.amount }.sum()
        val paymentsOut = entries.filter { it.kind == "PAYMENT_OUT" || it.kind == "EXPENSE" }.map { it.amount }.sum()
        return ReportContent(
            kpis = listOf(Kpi("Sales billed", sales, Tone.BRAND), Kpi("Payments in", paymentsIn, Tone.GOOD), Kpi("Payments out & expenses", paymentsOut, Tone.BAD)),
            sections = listOf(
                ReportSection(
                    null,
                    listOf("Date", "Type", "Number", "Party / note", "Amount"),
                    entries.map { listOf(it.date.pretty(), label(it.kind), it.number, it.partyName, rs(it.amount)) },
                ),
            ),
        )
    }

    private suspend fun itemSales(range: DateRange): ReportContent {
        val rows = repo.itemSales(range)
        val total = rows.map { it.total }.sum()
        val cost = rows.map { it.cost }.sum()
        return ReportContent(
            kpis = listOf(Kpi("Sales", total, Tone.BRAND), Kpi("Cost", cost), Kpi("Margin", total - cost, Tone.GOOD)),
            sections = listOf(
                ReportSection(
                    null,
                    listOf("Item", "Qty", "Sales", "Cost", "Margin"),
                    rows.map { listOf(it.name, "${Qty.format(it.qtyMilli)} ${it.unit}", rs(it.total), rs(it.cost), rs(it.total - it.cost)) },
                ),
            ),
            note = "Sales are before GST and net of returns, like the profit and loss report. Cost is the purchase price. Margin is indicative.",
        )
    }

    private suspend fun stock(): ReportContent {
        val items = repo.stock().filter { it.item.tracksStock }
        val value = Money(items.filter { it.stockMilli > 0 }.sumOf { it.stockMilli * it.item.purchasePrice.paise / 1000 })
        val low = items.filter { it.isLow }
        return ReportContent(
            kpis = listOf(Kpi("Stock value (cost)", value, Tone.BRAND)),
            sections = listOf(
                ReportSection(
                    if (low.isEmpty()) null else "Running low",
                    listOf("Item", "Stock", "Alert at"),
                    low.map { listOf(it.item.name, "${Qty.format(it.stockMilli)} ${it.item.unit}", "${Qty.format(it.item.lowStockMilli)} ${it.item.unit}") },
                ).takeIf { low.isNotEmpty() },
                ReportSection(
                    "All items",
                    listOf("Item", "Stock", "Cost price", "Value"),
                    items.map { listOf(it.item.name, "${Qty.format(it.stockMilli)} ${it.item.unit}", rs(it.item.purchasePrice), rs(Money(it.stockMilli.coerceAtLeast(0) * it.item.purchasePrice.paise / 1000))) },
                    listOf("Total value" to rs(value)),
                ),
            ).filterNotNull(),
            note = if (low.isEmpty()) null else "${low.size} item(s) are at or below their alert level.",
        )
    }

    private suspend fun gst(range: DateRange): ReportContent {
        val g = repo.gst(range)
        fun section(title: String, lines: List<online.draran.billing.core.data.GstLine>) = ReportSection(
            title,
            listOf("Supply", "Rate", "Bills", "Taxable", "CGST", "SGST", "IGST", "Tax"),
            lines.map { listOf(it.label, pct(it.rateBp), it.docs.toString(), rs(it.taxable), rs(it.cgst), rs(it.sgst), rs(it.igst), rs(it.tax)) },
            if (lines.isEmpty()) emptyList() else listOf("Taxable" to rs(lines.map { it.taxable }.sum()), "Tax" to rs(lines.map { it.tax }.sum())),
        )
        return ReportContent(
            kpis = listOf(
                Kpi("Output tax", g.outputTax, Tone.BAD),
                Kpi("Input tax credit", g.itc, Tone.GOOD),
                Kpi(if (g.netPayable.isNegative) "Carry forward" else "Net GST payable", g.netPayable.abs(), Tone.BRAND),
            ),
            sections = listOf(
                section("GSTR-1 · B2B (to GST-registered buyers)", g.b2b),
                section("GSTR-1 · B2C (consumers)", g.b2c),
                section("Credit notes (sale returns)", g.creditNotes),
                ReportSection(
                    "GSTR-1 · HSN summary",
                    listOf("HSN", "Rate", "Qty", "Taxable", "Tax", "Total"),
                    g.hsn.map { listOf(it.hsn, pct(it.rateBp), "${Qty.format(it.qtyMilli)} ${it.unit}", rs(it.taxable), rs(it.tax), rs(it.total)) },
                ),
                section("Purchases (input tax credit)", g.purchases),
                section("Debit notes (purchase returns)", g.debitNotes),
                ReportSection(
                    "GSTR-3B summary",
                    listOf("Particulars", "Taxable", "CGST", "SGST", "IGST"),
                    listOf(
                        listOf("3.1(a) Outward taxable supplies", rs(g.outwardTaxable), rs(g.outputCgst), rs(g.outputSgst), rs(g.outputIgst)),
                        listOf("4(A) Input tax credit", rs(g.inwardTaxable), rs(g.itcCgst), rs(g.itcSgst), rs(g.itcIgst)),
                        listOf("Net payable", "", rs(g.outputCgst - g.itcCgst), rs(g.outputSgst - g.itcSgst), rs(g.outputIgst - g.itcIgst)),
                    ),
                ),
            ),
            note = "A summary to help you or your accountant file returns. Verify with the GST portal before filing. Purchases from suppliers without a GSTIN are left out of input tax credit.",
        )
    }

    private suspend fun partyBalances(): ReportContent {
        val all = repo.partyBalances()
        val receivable = all.filter { it.balance.paise > 0 }.sortedByDescending { it.balance.paise }
        val payable = all.filter { it.balance.paise < 0 }.sortedBy { it.balance.paise }
        val toGet = receivable.map { it.balance }.sum()
        val toGive = payable.map { it.balance.abs() }.sum()
        return ReportContent(
            kpis = listOf(Kpi("You'll get", toGet, Tone.GOOD), Kpi("You'll give", toGive, Tone.BAD)),
            sections = listOf(
                ReportSection("Receivable (they owe you)", listOf("Party", "Phone", "Amount"), receivable.map { listOf(it.party.name, it.party.phone, rs(it.balance)) }, listOf("Total" to rs(toGet))),
                ReportSection("Payable (you owe them)", listOf("Party", "Phone", "Amount"), payable.map { listOf(it.party.name, it.party.phone, rs(it.balance.abs())) }, listOf("Total" to rs(toGive))),
            ),
        )
    }

    private suspend fun expenses(range: DateRange): ReportContent {
        val rows = repo.expensesByCategory(range)
        val total = rows.map { it.second }.sum()
        return ReportContent(
            kpis = listOf(Kpi("Total expenses", total, Tone.BAD)),
            sections = listOf(
                ReportSection(
                    null, listOf("Category", "Amount", "Share"),
                    rows.map { (c, m) -> listOf(c, rs(m), if (total.paise > 0) "${m.paise * 100 / total.paise}%" else "-") },
                    listOf("Total" to rs(total)),
                ),
            ),
        )
    }

    private suspend fun cashFlow(range: DateRange): ReportContent {
        val cf = repo.cashFlow(range)
        return ReportContent(
            kpis = listOf(Kpi("Money in", cf.totalIn, Tone.GOOD), Kpi("Money out", cf.totalOut, Tone.BAD), Kpi("Net", cf.totalIn - cf.totalOut, Tone.BRAND)),
            sections = listOf(
                ReportSection("Received", listOf("Mode", "Amount"), cf.received.map { listOf(it.first, rs(it.second)) }, listOf("Total" to rs(cf.totalIn))),
                ReportSection("Paid", listOf("Mode", "Amount"), cf.paid.map { listOf(it.first, rs(it.second)) } + listOf(listOf("Expenses", rs(cf.expenses))), listOf("Total" to rs(cf.totalOut))),
            ),
        )
    }
}
