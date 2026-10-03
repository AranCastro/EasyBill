package online.draran.billing.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import online.draran.billing.core.database.AllocationEntity
import online.draran.billing.core.database.BillingDatabase
import online.draran.billing.core.database.ExpenseEntity
import online.draran.billing.core.database.InvoiceEntity
import online.draran.billing.core.database.InvoiceLineEntity
import online.draran.billing.core.database.PaymentEntity
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.Expense
import online.draran.billing.core.model.Invoice
import online.draran.billing.core.model.InvoiceLine
import online.draran.billing.core.model.InvoiceSummary
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Payment
import online.draran.billing.core.model.PaymentDirection
import online.draran.billing.core.model.PaymentMode
import online.draran.billing.core.model.TaxEngine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the bill editor collects before saving. */
data class InvoiceDraft(
    val id: Long = 0,
    val type: DocType,
    val number: String,
    val date: LocalDate,
    val dueDate: LocalDate? = null,
    val partyId: Long? = null,
    val partyName: String = "",
    val partyPhone: String = "",
    val partyGstin: String = "",
    val partyAddress: String = "",
    /** Party's state; blank means same state as the business. */
    val partyStateCode: String = "",
    val lines: List<InvoiceLine> = emptyList(),
    val notes: String = "",
    /** Amount received/paid right now while saving. */
    val paidNow: Money = Money.ZERO,
    val paymentMode: PaymentMode = PaymentMode.CASH,
    val convertedFromId: Long? = null,
)

const val CASH_CUSTOMER = "Cash Customer"

/**
 * Settles standalone payments of a party against its bills, oldest first.
 * Payments taken while saving a bill always stay on that bill.
 */
@Singleton
class Allocator @Inject constructor(private val db: BillingDatabase) {
    suspend fun reallocate(partyId: Long?) {
        if (partyId == null) return
        val invoices = db.invoiceDao()
        val payments = db.paymentDao()
        for (direction in PaymentDirection.entries) {
            val types = DocType.entries.filter { it.tracksPayment && it.paymentDirection == direction }
            val docs = invoices.docsForAllocation(partyId, types)
            val standalone = payments.standalone(partyId, direction)
            if (standalone.isEmpty()) continue
            payments.deleteAllocations(standalone.map { it.id })
            val remaining = docs.map { (it.total - it.paid).coerceAtLeast(0) }.toLongArray()
            val allocations = mutableListOf<AllocationEntity>()
            for (payment in standalone) {
                var left = payment.amount
                for (i in docs.indices) {
                    if (left <= 0) break
                    if (remaining[i] <= 0) continue
                    val take = minOf(left, remaining[i])
                    allocations += AllocationEntity(paymentId = payment.id, invoiceId = docs[i].id, amount = take)
                    remaining[i] -= take
                    left -= take
                }
            }
            if (allocations.isNotEmpty()) payments.insertAllocations(allocations)
        }
    }
}

@Singleton
class InvoiceRepository @Inject constructor(
    private val db: BillingDatabase,
    private val businessRepository: BusinessRepository,
    private val allocator: Allocator,
) {
    private val dao = db.invoiceDao()

    fun summaries(types: List<DocType>): Flow<List<InvoiceSummary>> =
        dao.observeSummaries(types).map { rows -> rows.map { it.toModel() } }

    fun forParty(partyId: Long): Flow<List<InvoiceSummary>> = dao.observeForParty(partyId).map { rows -> rows.map { it.toModel() } }

    fun invoice(id: Long): Flow<Invoice?> = combine(dao.observe(id), dao.observeLines(id), dao.observePaid(id)) { entity, lines, paid ->
        entity?.toModel(lines, paid)
    }

    suspend fun get(id: Long): Invoice? {
        val entity = dao.get(id) ?: return null
        return entity.toModel(dao.lines(id), dao.paid(id))
    }

    suspend fun nextNumber(type: DocType): String {
        val prefix = businessRepository.get().prefix(type)
        return prefix + (dao.maxSeq(type) + 1).toString().padStart(4, '0')
    }

    suspend fun numberTaken(type: DocType, number: String, exceptId: Long) = dao.countNumber(type, number.trim(), exceptId) > 0

    /** Computes totals exactly as [save] will, for live display in the editor. */
    suspend fun preview(draft: InvoiceDraft) = totalsFor(draft, businessRepository.get())

    private fun totalsFor(draft: InvoiceDraft, business: online.draran.billing.core.model.Business) =
        TaxEngine.bill(draft.lines.map { it.toInput() }, isInterState(draft, business), business.gstEnabled, business.roundOff)

    fun isInterState(draft: InvoiceDraft, business: online.draran.billing.core.model.Business): Boolean =
        business.gstEnabled && draft.partyStateCode.isNotBlank() && draft.partyStateCode != business.stateCode

    /** Saves the bill, its lines and any payment taken now; returns the bill id. */
    suspend fun save(draft: InvoiceDraft): Long {
        require(draft.lines.isNotEmpty()) { "Add at least one item" }
        val business = businessRepository.get()
        val interState = isInterState(draft, business)
        val totals = TaxEngine.bill(draft.lines.map { it.toInput() }, interState, business.gstEnabled, business.roundOff)
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val existing = if (draft.id != 0L) dao.get(draft.id) else null
            val seq = existing?.seq ?: (dao.maxSeq(draft.type) + 1)
            val number = draft.number.trim().ifEmpty { business.prefix(draft.type) + seq.toString().padStart(4, '0') }
            val entity = InvoiceEntity(
                id = draft.id,
                type = draft.type,
                number = number,
                seq = seq,
                date = draft.date.toDay(),
                dueDate = draft.dueDate?.toDay(),
                partyId = draft.partyId,
                partyName = draft.partyName.trim().ifEmpty { CASH_CUSTOMER },
                partyPhone = draft.partyPhone,
                partyGstin = draft.partyGstin.trim().uppercase(),
                partyAddress = draft.partyAddress,
                placeOfSupply = draft.partyStateCode.ifBlank { business.stateCode },
                interState = interState,
                gstEnabled = business.gstEnabled,
                roundOffEnabled = business.roundOff,
                subtotal = totals.subtotal.paise,
                discount = totals.discount.paise,
                taxable = totals.taxable.paise,
                cgst = totals.cgst.paise,
                sgst = totals.sgst.paise,
                igst = totals.igst.paise,
                roundOff = totals.roundOff.paise,
                total = totals.total.paise,
                notes = draft.notes.trim(),
                convertedFromId = draft.convertedFromId ?: existing?.convertedFromId,
                createdAt = existing?.createdAt ?: now,
            )
            val id = if (existing == null) dao.insert(entity) else entity.id.also { dao.update(entity) }
            dao.deleteLines(id)
            dao.insertLines(
                draft.lines.zip(totals.lines).mapIndexed { index, (line, amounts) ->
                    InvoiceLineEntity(
                        invoiceId = id, position = index, itemId = line.itemId, name = line.name.trim(),
                        hsn = line.hsn.trim(), unit = line.unit, qty = line.qtyMilli, rate = line.rate.paise,
                        discountBp = line.discountBp, taxRateBp = line.taxRateBp, taxInclusive = line.taxInclusive,
                        costRate = line.costRate.paise, taxable = amounts.taxable.paise, tax = amounts.tax.paise,
                        cgst = amounts.cgst.paise, sgst = amounts.sgst.paise, igst = amounts.igst.paise,
                        total = amounts.total.paise,
                    )
                },
            )
            // Remember the latest purchase cost on each item (per unit, before tax)
            if (draft.type == DocType.PURCHASE) {
                draft.lines.zip(totals.lines).forEach { (line, amounts) ->
                    val itemId = line.itemId ?: return@forEach
                    val item = db.itemDao().get(itemId) ?: return@forEach
                    if (line.qtyMilli > 0) {
                        val unitCost = TaxEngine.divRound(amounts.taxable.paise * 1000, line.qtyMilli)
                        db.itemDao().update(item.copy(purchasePrice = unitCost))
                    }
                }
            }
            // Payment taken with the bill: replace any earlier one
            val payments = db.paymentDao()
            payments.deleteForInvoice(id)
            val paid = minOf(draft.paidNow.paise, totals.total.paise)
            if (draft.type.tracksPayment && paid > 0) {
                val direction = draft.type.paymentDirection
                val pSeq = payments.maxSeq(direction) + 1
                val paymentId = payments.insert(
                    PaymentEntity(
                        direction = direction, number = direction.prefix + pSeq.toString().padStart(4, '0'), seq = pSeq,
                        partyId = draft.partyId, partyName = entity.partyName, date = draft.date.toDay(), amount = paid,
                        mode = draft.paymentMode, reference = "", note = "Against $number", invoiceId = id, createdAt = now,
                    ),
                )
                payments.insertAllocations(listOf(AllocationEntity(paymentId = paymentId, invoiceId = id, amount = paid)))
            }
            allocator.reallocate(draft.partyId)
            if (existing?.partyId != null && existing.partyId != draft.partyId) allocator.reallocate(existing.partyId)
            id
        }
    }

    suspend fun delete(id: Long) {
        db.withTransaction {
            val existing = dao.get(id) ?: return@withTransaction
            dao.delete(id) // lines, linked payments and allocations cascade
            allocator.reallocate(existing.partyId)
        }
    }

    /** Builds a sale draft from an estimate (or any document) for the editor. */
    suspend fun draftFrom(sourceId: Long, newType: DocType): InvoiceDraft? {
        val source = get(sourceId) ?: return null
        val party = source.partyId?.let { db.partyDao().get(it) }
        return InvoiceDraft(
            type = newType,
            number = nextNumber(newType),
            date = LocalDate.now(),
            partyId = source.partyId,
            partyName = source.partyName,
            partyPhone = source.partyPhone,
            partyGstin = source.partyGstin,
            partyAddress = source.partyAddress,
            partyStateCode = party?.stateCode.orEmpty(),
            lines = source.lines.map { it.copy(id = 0) },
            notes = source.notes,
            convertedFromId = if (source.type == DocType.ESTIMATE) source.id else null,
        )
    }

    suspend fun draftForEdit(id: Long): InvoiceDraft? {
        val inv = get(id) ?: return null
        val party = inv.partyId?.let { db.partyDao().get(it) }
        val linked = db.paymentDao().forInvoice(id).firstOrNull()
        return InvoiceDraft(
            id = inv.id, type = inv.type, number = inv.number, date = inv.date, dueDate = inv.dueDate,
            partyId = inv.partyId, partyName = inv.partyName, partyPhone = inv.partyPhone, partyGstin = inv.partyGstin,
            partyAddress = inv.partyAddress, partyStateCode = party?.stateCode ?: inv.placeOfSupply.takeIf { inv.interState }.orEmpty(),
            lines = inv.lines, notes = inv.notes, paidNow = Money(linked?.amount ?: 0),
            paymentMode = linked?.mode ?: PaymentMode.CASH, convertedFromId = inv.convertedFromId,
        )
    }

    suspend fun convertedSale(estimateId: Long): Long? = dao.convertedFrom(estimateId)?.id
}

@Singleton
class PaymentRepository @Inject constructor(
    private val db: BillingDatabase,
    private val allocator: Allocator,
) {
    private val dao = db.paymentDao()

    val payments: Flow<List<Payment>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: Long): Payment? = dao.get(id)?.toModel()

    suspend fun nextNumber(direction: PaymentDirection) = direction.prefix + (dao.maxSeq(direction) + 1).toString().padStart(4, '0')

    /** Saves a standalone payment and settles it against the party's open bills. */
    suspend fun save(payment: Payment): Long {
        require(payment.amount.paise > 0) { "Enter an amount" }
        return db.withTransaction {
            val existing = if (payment.id != 0L) dao.get(payment.id) else null
            val seq = existing?.seq ?: (dao.maxSeq(payment.direction) + 1)
            val entity = PaymentEntity(
                id = payment.id,
                direction = payment.direction,
                number = payment.number.ifBlank { payment.direction.prefix + seq.toString().padStart(4, '0') },
                seq = seq,
                partyId = payment.partyId,
                partyName = payment.partyName.trim(),
                date = payment.date.toDay(),
                amount = payment.amount.paise,
                mode = payment.mode,
                reference = payment.reference.trim(),
                note = payment.note.trim(),
                invoiceId = existing?.invoiceId,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            val id = if (existing == null) dao.insert(entity) else entity.id.also { dao.update(entity) }
            allocator.reallocate(payment.partyId)
            if (existing?.partyId != null && existing.partyId != payment.partyId) allocator.reallocate(existing.partyId)
            id
        }
    }

    suspend fun delete(id: Long) {
        db.withTransaction {
            val existing = dao.get(id) ?: return@withTransaction
            dao.delete(id)
            allocator.reallocate(existing.partyId)
        }
    }
}

@Singleton
class ExpenseRepository @Inject constructor(private val db: BillingDatabase) {
    private val dao = db.expenseDao()

    val expenses: Flow<List<Expense>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: Long) = dao.get(id)?.toModel()

    suspend fun save(expense: Expense): Long {
        require(expense.amount.paise > 0) { "Enter an amount" }
        val entity = ExpenseEntity(
            id = expense.id, category = expense.category.trim().ifEmpty { "Other" }, date = expense.date.toDay(),
            amount = expense.amount.paise, mode = expense.mode, note = expense.note.trim(),
            createdAt = dao.get(expense.id)?.createdAt ?: System.currentTimeMillis(),
        )
        return if (expense.id == 0L) dao.insert(entity) else expense.id.also { dao.update(entity) }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
