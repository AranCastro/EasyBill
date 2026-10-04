package online.draran.billing.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import online.draran.billing.core.database.AllocationEntity
import online.draran.billing.core.database.NoteAllocationEntity
import online.draran.billing.core.database.BillingDatabase
import online.draran.billing.core.database.ExpenseEntity
import online.draran.billing.core.database.InvoiceEntity
import online.draran.billing.core.database.InvoiceLineEntity
import online.draran.billing.core.database.PaymentEntity
import online.draran.billing.core.model.Gstin
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
    /** Industry fields (label to value), e.g. Roll no. or Stylist. */
    val customFields: List<Pair<String, String>> = emptyList(),
    /**
     * GST and round-off settings the bill was made under. Null means "the business's current
     * settings" for a new bill; an edited bill keeps its own, so changing the shop's GST
     * setting later does not rewrite old bills.
     */
    val gstEnabled: Boolean? = null,
    val roundOff: Boolean? = null,
    /** Accent colour for this bill alone (ARGB); 0 = the business colour. */
    val billColor: Int = 0,
)

const val CASH_CUSTOMER = "Cash Customer"

/**
 * Settles standalone payments of a party against its bills, oldest first.
 * Payments taken while saving a bill always stay on that bill.
 */
@Singleton
class Allocator @Inject constructor(private val db: BillingDatabase) {
    /**
     * Applies every party's credit and debit notes again. Run at start-up: notes made before
     * version 1.4 were never set against bills, and this brings them in. Safe to repeat.
     */
    suspend fun reallocateAll() {
        db.withTransaction {
            db.invoiceDao().partiesWithNotes().forEach { reallocate(it) }
        }
    }

    suspend fun reallocate(partyId: Long?) {
        if (partyId == null) return
        val invoices = db.invoiceDao()
        val payments = db.paymentDao()

        // 1. Credit notes against the party's sales, debit notes against its purchases, oldest first.
        //    What a note does not use stays open (a refund to make or receive).
        payments.deleteNoteAllocations(partyId)
        for ((billType, noteType) in listOf(DocType.SALE to DocType.SALE_RETURN, DocType.PURCHASE to DocType.PURCHASE_RETURN)) {
            val notes = invoices.docsForAllocation(partyId, listOf(noteType))
            if (notes.isEmpty()) continue
            val bills = invoices.docsForAllocation(partyId, listOf(billType))
            val open = bills.map { (it.total - it.paid).coerceAtLeast(0) }.toLongArray()
            val links = mutableListOf<NoteAllocationEntity>()
            for (note in notes) {
                var left = (note.total - note.paid).coerceAtLeast(0)
                for (i in bills.indices) {
                    if (left <= 0) break
                    if (open[i] <= 0) continue
                    val take = minOf(left, open[i])
                    links += NoteAllocationEntity(noteId = note.id, invoiceId = bills[i].id, amount = take)
                    open[i] -= take
                    left -= take
                }
            }
            if (links.isNotEmpty()) payments.insertNoteAllocations(links)
        }

        // 2. Standalone payments against what is still open
        for (direction in PaymentDirection.entries) {
            val types = DocType.entries.filter { it.tracksPayment && it.paymentDirection == direction }
            val docs = invoices.docsForAllocation(partyId, types)
            val standalone = payments.standalone(partyId, direction)
            if (standalone.isEmpty()) continue
            payments.deleteStandaloneAllocations(partyId, direction)
            val remaining = docs.map { (it.total - it.paid).coerceAtLeast(0) }.toLongArray()
            val allocations = mutableListOf<AllocationEntity>()
            // The amount brought forward (opening balance) is the oldest due: payments clear it before any bill
            val opening = db.partyDao().get(partyId)?.openingBalance ?: 0L
            var openingDue = (if (direction == PaymentDirection.IN) opening else -opening).coerceAtLeast(0)
            for (payment in standalone) {
                var left = payment.amount
                val toOpening = minOf(left, openingDue)
                openingDue -= toOpening
                left -= toOpening
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

    /** The next free number: after the highest sequence, skipping any number typed by hand that is already used. */
    suspend fun nextNumber(type: DocType): String {
        val prefix = businessRepository.get().prefix(type)
        var seq = highestSeq(type) + 1
        var candidate = prefix + seq.toString().padStart(4, '0')
        while (dao.countNumber(type, candidate, 0) > 0) {
            seq++
            candidate = prefix + seq.toString().padStart(4, '0')
        }
        return candidate
    }

    /** The highest sequence ever used for this kind: a deleted bill's number is not handed out again. */
    private suspend fun highestSeq(type: DocType): Long = maxOf(dao.maxSeq(type), db.counterDao().last(type.name))

    suspend fun numberTaken(type: DocType, number: String, exceptId: Long) = dao.countNumber(type, number.trim(), exceptId) > 0

    /** Computes totals exactly as [save] will, for live display in the editor. */
    suspend fun preview(draft: InvoiceDraft) = totalsFor(draft, businessRepository.get())

    private fun totalsFor(draft: InvoiceDraft, business: online.draran.billing.core.model.Business): online.draran.billing.core.model.BillTotals {
        val gstOn = draft.gstEnabled ?: business.gstEnabled
        return TaxEngine.bill(draft.lines.map { it.toInput() }, isInterState(draft, business, gstOn), gstOn, draft.roundOff ?: business.roundOff)
    }

    /** The party's state: the one chosen, else the one in a valid GSTIN, else blank (same state as the business). */
    private fun partyState(draft: InvoiceDraft): String =
        draft.partyStateCode.ifBlank { draft.partyGstin.trim().uppercase().takeIf { Gstin.isValid(it) }?.let { Gstin.stateCode(it) }.orEmpty() }

    fun isInterState(draft: InvoiceDraft, business: online.draran.billing.core.model.Business, gstOn: Boolean = draft.gstEnabled ?: business.gstEnabled): Boolean =
        gstOn && partyState(draft).let { it.isNotBlank() && it != business.stateCode }

    /** Saves the bill, its lines and any payment taken now; returns the bill id. */
    suspend fun save(draft: InvoiceDraft): Long {
        require(draft.lines.isNotEmpty()) { "Add at least one item" }
        draft.lines.firstNotNullOfOrNull { lineProblem(it) }?.let { throw IllegalArgumentException(it) }
        val business = businessRepository.get()
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val existing = if (draft.id != 0L) dao.get(draft.id) else null
            // An edited bill keeps the GST and round-off settings it was made under
            val gstOn = draft.gstEnabled ?: existing?.gstEnabled ?: business.gstEnabled
            val roundOn = draft.roundOff ?: existing?.roundOffEnabled ?: business.roundOff
            val interState = isInterState(draft, business, gstOn)
            val totals = TaxEngine.bill(draft.lines.map { it.toInput() }, interState, gstOn, roundOn)
            var seq = existing?.seq ?: (highestSeq(draft.type) + 1)
            val typed = draft.number.trim()
            val number = if (typed.isNotEmpty()) {
                // Supplier bill numbers may repeat across suppliers; our own documents must be unique
                // An unchanged number is always allowed (older data may already hold two bills with one number)
                val unchanged = existing != null && existing.number == typed
                if (!unchanged && draft.type != DocType.PURCHASE && dao.countNumber(draft.type, typed, draft.id) > 0) error("Bill number $typed is already in use. Enter a different number.")
                typed
            } else {
                var s = seq
                var candidate = business.prefix(draft.type) + s.toString().padStart(4, '0')
                while (dao.countNumber(draft.type, candidate, draft.id) > 0) {
                    s++
                    candidate = business.prefix(draft.type) + s.toString().padStart(4, '0')
                }
                if (existing == null) seq = s
                candidate
            }
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
                placeOfSupply = partyState(draft).ifBlank { business.stateCode },
                interState = interState,
                gstEnabled = gstOn,
                roundOffEnabled = roundOn,
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
                customFields = online.draran.billing.core.model.CustomFields.encode(draft.customFields),
                billColor = draft.billColor,
            )
            val id = if (existing == null) dao.insert(entity) else entity.id.also { dao.update(entity) }
            db.counterDao().apply { ensure(draft.type.name); raise(draft.type.name, seq) }
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
                        // Only the newest purchase sets the cost, and a free line (cost 0) never does
                        if (unitCost > 0 && draft.date.toDay() >= dao.latestPurchaseDay(itemId, id)) {
                            db.itemDao().update(item.copy(purchasePrice = unitCost))
                        }
                    }
                }
            }
            // Payment taken with the bill. An edit updates the same receipt (same number) instead of making a new one.
            val payments = db.paymentDao()
            val earlier = payments.forInvoice(id).firstOrNull()
            val paid = minOf(draft.paidNow.paise, totals.total.paise)
            if (draft.type.tracksPayment && paid > 0) {
                val paymentId = if (earlier != null) {
                    payments.deleteAllocations(listOf(earlier.id))
                    payments.update(
                        earlier.copy(
                            partyId = draft.partyId, partyName = entity.partyName, date = draft.date.toDay(), amount = paid,
                            mode = draft.paymentMode, note = "Against $number",
                        ),
                    )
                    earlier.id
                } else {
                    val direction = draft.type.paymentDirection
                    val pSeq = payments.maxSeq(direction) + 1
                    payments.insert(
                        PaymentEntity(
                            direction = direction, number = direction.prefix + pSeq.toString().padStart(4, '0'), seq = pSeq,
                            partyId = draft.partyId, partyName = entity.partyName, date = draft.date.toDay(), amount = paid,
                            mode = draft.paymentMode, reference = "", note = "Against $number", invoiceId = id, createdAt = now,
                        ),
                    )
                }
                payments.insertAllocations(listOf(AllocationEntity(paymentId = paymentId, invoiceId = id, amount = paid)))
            } else if (earlier != null) {
                payments.deleteForInvoice(id)
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

    companion object {
        /** Largest amount for one line (₹1,000 crore): keeps quantity x rate far from overflowing a Long. */
        private const val MAX_LINE_PAISE = 1_000_000_000_000L

        /** Largest quantity on one line: a billion units. Keeps stock sums inside the range of a Long. */
        const val MAX_QTY_MILLI = 1_000_000_000_000L

        /** A message when the line cannot be saved, else null. */
        fun lineProblem(line: online.draran.billing.core.model.InvoiceLine): String? {
            if (line.qtyMilli <= 0) return "Enter a quantity greater than zero."
            if (line.qtyMilli > MAX_QTY_MILLI) return "The quantity for \"${line.name}\" is too large."
            val gross = try {
                Math.multiplyExact(line.qtyMilli, Math.abs(line.rate.paise))
            } catch (e: ArithmeticException) {
                return "The amount for \"${line.name}\" is too large."
            }
            return if (gross / 1000 > MAX_LINE_PAISE) "The amount for \"${line.name}\" is too large (up to ₹1,000 crore per line)." else null
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
            // Only turning an estimate into a sale links them; a copy of an estimate is a new estimate
            convertedFromId = if (source.type == DocType.ESTIMATE && newType == DocType.SALE) source.id else null,
            // Keep the payment period, not the old calendar date
            dueDate = source.dueDate?.let { LocalDate.now().plusDays(java.time.temporal.ChronoUnit.DAYS.between(source.date, it)) },
            customFields = source.customFields,
            billColor = source.billColor,
        )
    }

    suspend fun draftForEdit(id: Long): InvoiceDraft? {
        val inv = get(id) ?: return null
        val party = inv.partyId?.let { db.partyDao().get(it) }
        val linked = db.paymentDao().forInvoice(id).firstOrNull()
        return InvoiceDraft(
            id = inv.id, type = inv.type, number = inv.number, date = inv.date, dueDate = inv.dueDate,
            partyId = inv.partyId, partyName = inv.partyName, partyPhone = inv.partyPhone, partyGstin = inv.partyGstin,
            partyAddress = inv.partyAddress, // The state the bill was made with, not the party's current one, so a typo fix cannot flip IGST to CGST and SGST
            partyStateCode = inv.placeOfSupply.takeIf { inv.interState }.orEmpty(),
            lines = inv.lines, notes = inv.notes, paidNow = Money(linked?.amount ?: 0),
            paymentMode = linked?.mode ?: PaymentMode.CASH, convertedFromId = inv.convertedFromId,
            customFields = inv.customFields,
            gstEnabled = inv.gstEnabled, roundOff = inv.roundOff, billColor = inv.billColor,
        )
    }

    /** Changes the accent colour of one bill (0 = back to the business colour). Money is not touched. */
    suspend fun setBillColor(id: Long, color: Int) = dao.setBillColor(id, color)

    suspend fun convertedSale(estimateId: Long): Long? = dao.convertedFrom(estimateId)?.id

    /** The sale made from an estimate, updating as soon as it is created or deleted. */
    fun observeConvertedSale(estimateId: Long): Flow<Long?> = dao.observeConvertedSaleId(estimateId)
}

@Singleton
class PaymentRepository @Inject constructor(
    private val db: BillingDatabase,
    private val allocator: Allocator,
) {
    private val dao = db.paymentDao()

    val payments: Flow<List<Payment>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: Long): Payment? = dao.get(id)?.toModel()

    private suspend fun highestSeq(direction: PaymentDirection): Long = maxOf(dao.maxSeq(direction), db.counterDao().last(direction.name))

    suspend fun nextNumber(direction: PaymentDirection) = direction.prefix + (highestSeq(direction) + 1).toString().padStart(4, '0')

    /** Saves a standalone payment and settles it against the party's open bills. */
    suspend fun save(payment: Payment): Long {
        require(payment.amount.paise > 0) { "Enter an amount" }
        return db.withTransaction {
            val existing = if (payment.id != 0L) dao.get(payment.id) else null
            // A payment taken with a bill is changed on the bill, not here
            require(existing?.invoiceId == null) { "This payment belongs to a bill. Change it from the bill." }
            val seq = existing?.seq ?: (highestSeq(payment.direction) + 1)
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
            db.counterDao().apply { ensure(payment.direction.name); raise(payment.direction.name, seq) }
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
