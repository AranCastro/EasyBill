package online.draran.billing.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import online.draran.billing.core.database.BillingDatabase
import online.draran.billing.core.model.DateRange
import online.draran.billing.core.model.DaySales
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.InvoiceSummary
import online.draran.billing.core.model.ItemWithStock
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.PartyWithBalance
import online.draran.billing.core.model.sum
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class DocTotals(
    val count: Int = 0,
    val taxable: Money = Money.ZERO,
    val cgst: Money = Money.ZERO,
    val sgst: Money = Money.ZERO,
    val igst: Money = Money.ZERO,
    val total: Money = Money.ZERO,
) {
    val tax: Money get() = cgst + sgst + igst
}

data class ProfitLoss(
    val range: DateRange,
    val sales: Money,
    val saleReturns: Money,
    val costOfGoods: Money,
    val expenses: List<Pair<String, Money>>,
) {
    val netSales: Money get() = sales - saleReturns
    val grossProfit: Money get() = netSales - costOfGoods
    val totalExpenses: Money get() = expenses.map { it.second }.sum()
    val netProfit: Money get() = grossProfit - totalExpenses
}

data class GstLine(val label: String, val rateBp: Int, val docs: Int, val taxable: Money, val cgst: Money, val sgst: Money, val igst: Money) {
    val tax: Money get() = cgst + sgst + igst
}

data class HsnLine(val hsn: String, val unit: String, val rateBp: Int, val qtyMilli: Long, val taxable: Money, val tax: Money, val total: Money)

data class GstReport(
    val range: DateRange,
    val b2b: List<GstLine>,
    val b2c: List<GstLine>,
    val creditNotes: List<GstLine>,
    val purchases: List<GstLine>,
    val debitNotes: List<GstLine>,
    val hsn: List<HsnLine>,
) {
    private fun List<GstLine>.cgst() = map { it.cgst }.sum()
    private fun List<GstLine>.sgst() = map { it.sgst }.sum()
    private fun List<GstLine>.igst() = map { it.igst }.sum()
    private fun List<GstLine>.taxable() = map { it.taxable }.sum()

    // GSTR-3B 3.1(a) is for taxed supplies only; 0% (nil-rated or exempt) sales belong in 3.1(c)
    private fun List<GstLine>.taxed() = filter { it.rateBp > 0 }
    private fun List<GstLine>.nil() = filter { it.rateBp == 0 }
    val outwardTaxable: Money get() = (b2b + b2c).taxed().taxable() - creditNotes.taxed().taxable()
    val outwardNilExempt: Money get() = (b2b + b2c).nil().taxable() - creditNotes.nil().taxable()
    val outputCgst: Money get() = (b2b + b2c).cgst() - creditNotes.cgst()
    val outputSgst: Money get() = (b2b + b2c).sgst() - creditNotes.sgst()
    val outputIgst: Money get() = (b2b + b2c).igst() - creditNotes.igst()
    val inwardTaxable: Money get() = purchases.taxable() - debitNotes.taxable()
    val itcCgst: Money get() = purchases.cgst() - debitNotes.cgst()
    val itcSgst: Money get() = purchases.sgst() - debitNotes.sgst()
    val itcIgst: Money get() = purchases.igst() - debitNotes.igst()
    val outputTax: Money get() = outputCgst + outputSgst + outputIgst
    val itc: Money get() = itcCgst + itcSgst + itcIgst
    val netPayable: Money get() = outputTax - itc
}

data class DayBookEntry(
    val id: Long,
    val kind: String,
    val number: String,
    val partyName: String,
    val date: LocalDate,
    val amount: Money,
    val balance: Money,
    val mode: String?,
) {
    /** +1 money/value coming in, -1 going out, 0 neutral (estimates). */
    val flow: Int get() = when (kind) {
        "SALE", "PAYMENT_IN", "PURCHASE_RETURN" -> 1
        "PURCHASE", "PAYMENT_OUT", "EXPENSE", "SALE_RETURN" -> -1
        else -> 0
    }
}

data class ItemSales(val name: String, val qtyMilli: Long, val unit: String, val total: Money, val cost: Money)

data class CashFlow(val range: DateRange, val received: List<Pair<String, Money>>, val paid: List<Pair<String, Money>>, val expenses: Money) {
    val totalIn: Money get() = received.map { it.second }.sum()
    val totalOut: Money get() = paid.map { it.second }.sum() + expenses
}

data class DashboardData(
    val businessName: String,
    val todaySales: Money,
    val todayCount: Int,
    val yesterdaySales: Money,
    val toCollect: Money,
    val toCollectCount: Int,
    val toPay: Money,
    val toPayCount: Int,
    val lowStock: Int,
    val week: List<DaySales>,
    val recent: List<DayBookEntry>,
    val overdueCount: Int = 0,
    val overdue: Money = Money.ZERO,
    /** Best sellers this month, by value. */
    val topItems: List<TopItem> = emptyList(),
)

data class TopItem(val name: String, val qtyMilli: Long, val unit: String, val amount: Money)

@Singleton
class ReportsRepository @Inject constructor(
    private val db: BillingDatabase,
    private val businessRepository: BusinessRepository,
    private val partyRepository: PartyRepository,
    private val itemRepository: ItemRepository,
) {
    private val invoices = db.invoiceDao()

    suspend fun typeTotals(range: DateRange): Map<DocType, DocTotals> =
        invoices.typeTotals(range.start.toDay(), range.end.toDay()).associate {
            it.type to DocTotals(it.count, Money(it.taxable), Money(it.cgst), Money(it.sgst), Money(it.igst), Money(it.total))
        }

    suspend fun register(type: DocType, range: DateRange): List<InvoiceSummary> =
        invoices.register(type, range.start.toDay(), range.end.toDay()).map { it.toModel() }

    suspend fun profitLoss(range: DateRange): ProfitLoss {
        val totals = typeTotals(range)
        val from = range.start.toDay()
        val to = range.end.toDay()
        return ProfitLoss(
            range = range,
            sales = totals[DocType.SALE]?.taxable ?: Money.ZERO,
            saleReturns = totals[DocType.SALE_RETURN]?.taxable ?: Money.ZERO,
            costOfGoods = Money(invoices.costOfGoodsSold(from, to)),
            expenses = db.expenseDao().byCategory(from, to).map { it.category to Money(it.total) },
        )
    }

    suspend fun gst(range: DateRange): GstReport {
        val rows = invoices.gstRates(range.start.toDay(), range.end.toDay())
        fun lines(filter: (online.draran.billing.core.database.GstRateRow) -> Boolean, label: (online.draran.billing.core.database.GstRateRow) -> String) =
            rows.filter(filter).groupBy { label(it) to it.taxRateBp }.map { (key, group) ->
                GstLine(
                    label = key.first, rateBp = key.second, docs = group.sumOf { it.docs },
                    taxable = Money(group.sumOf { it.taxable }), cgst = Money(group.sumOf { it.cgst }),
                    sgst = Money(group.sumOf { it.sgst }), igst = Money(group.sumOf { it.igst }),
                )
            }.sortedBy { it.rateBp }
        val scope = { r: online.draran.billing.core.database.GstRateRow -> if (r.interState) "Inter-state" else "Intra-state" }
        return GstReport(
            range = range,
            b2b = lines({ it.type == DocType.SALE && it.b2b }, scope),
            b2c = lines({ it.type == DocType.SALE && !it.b2b }, scope),
            creditNotes = lines({ it.type == DocType.SALE_RETURN }, scope),
            purchases = lines({ it.type == DocType.PURCHASE }, scope),
            debitNotes = lines({ it.type == DocType.PURCHASE_RETURN }, scope),
            hsn = invoices.hsnSummary(range.start.toDay(), range.end.toDay()).map {
                HsnLine(it.hsn.ifBlank { "—" }, it.unit, it.taxRateBp, it.qty, Money(it.taxable), Money(it.cgst + it.sgst + it.igst), Money(it.total))
            },
        )
    }

    fun dayBook(range: DateRange, limit: Int = 500): Flow<List<DayBookEntry>> =
        db.activityDao().observe(range.start.toDay(), range.end.toDay(), limit).map { rows ->
            rows.map { DayBookEntry(it.id, it.kind, it.number, it.partyName, it.date.toDate(), Money(it.amount), Money(it.balance), it.mode) }
        }

    suspend fun itemSales(range: DateRange): List<ItemSales> =
        invoices.itemSales(range.start.toDay(), range.end.toDay()).map { ItemSales(it.name, it.qty, it.unit, Money(it.total), Money(it.cost)) }

    suspend fun expensesByCategory(range: DateRange): List<Pair<String, Money>> =
        db.expenseDao().byCategory(range.start.toDay(), range.end.toDay()).map { it.category to Money(it.total) }

    suspend fun cashFlow(range: DateRange): CashFlow {
        val rows = db.paymentDao().modeTotals(range.start.toDay(), range.end.toDay())
        val expenses = expensesByCategory(range).map { it.second }.sum()
        fun modeLabel(m: String) = online.draran.billing.core.model.PaymentMode.entries.firstOrNull { it.name == m }?.label ?: m
        return CashFlow(
            range = range,
            received = rows.filter { it.direction == "IN" }.map { modeLabel(it.mode) to Money(it.total) },
            paid = rows.filter { it.direction == "OUT" }.map { modeLabel(it.mode) to Money(it.total) },
            expenses = expenses,
        )
    }

    suspend fun stock(): List<ItemWithStock> = itemRepository.items().first()

    suspend fun partyBalances(): List<PartyWithBalance> = partyRepository.parties.first()

    fun dashboard(today: LocalDate = LocalDate.now()): Flow<DashboardData> {
        val weekStart = today.minusDays(6)
        return combine(
            businessRepository.business,
            invoices.observeDayTotals(DocType.SALE, today.minusDays(7).toDay(), today.toDay()),
            partyRepository.parties,
            itemRepository.items(),
            dayBook(DateRange(today.minusYears(5), today.plusDays(1), "all"), limit = 6),
        ) { business, dayTotals, parties, items, recent ->
            val byDay = dayTotals.associate { it.date to it.total }
            val receivable = parties.filter { it.balance.paise > 0 }
            val payable = parties.filter { it.balance.paise < 0 }
            DashboardData(
                businessName = business.name.ifBlank { "My Business" },
                todaySales = Money(byDay[today.toDay()] ?: 0),
                todayCount = 0,
                yesterdaySales = Money(byDay[today.minusDays(1).toDay()] ?: 0),
                toCollect = Money(receivable.sumOf { it.balance.paise }),
                toCollectCount = receivable.size,
                toPay = Money(-payable.sumOf { it.balance.paise }),
                toPayCount = payable.size,
                lowStock = items.count { it.isLow },
                week = (0..6).map { offset ->
                    val d = weekStart.plusDays(offset.toLong())
                    DaySales(d, Money(byDay[d.toDay()] ?: 0))
                },
                recent = recent,
            )
        }.combine(invoices.observeCount(DocType.SALE, today.toDay())) { data, count ->
            data.copy(todayCount = count)
        }.combine(invoices.observeOverdue(today.toDay())) { data, overdue ->
            data.copy(overdueCount = overdue.count, overdue = Money(overdue.amount))
        }.combine(invoices.observeTopItems(today.withDayOfMonth(1).toDay(), today.toDay(), 3)) { data, top ->
            data.copy(topItems = top.map { TopItem(it.name, it.qty, it.unit, Money(it.total)) })
        }
    }
}
