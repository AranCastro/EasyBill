package online.draran.billing.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import online.draran.billing.core.database.BillingDatabase
import online.draran.billing.core.database.CashMoveEntity
import online.draran.billing.core.database.CashMovementRow
import online.draran.billing.core.database.CashSetupEntity
import online.draran.billing.core.model.CapitalKind
import online.draran.billing.core.model.CashBalances
import online.draran.billing.core.model.CashBook
import online.draran.billing.core.model.CashMove
import online.draran.billing.core.model.CashMoveKind
import online.draran.billing.core.model.CashMovement
import online.draran.billing.core.model.CashSetup
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.MoneyAccount
import online.draran.billing.core.model.MovementSource
import online.draran.billing.core.model.PaymentMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cash in hand and the bank balance, always worked out again from the entries themselves
 * (bills paid, payments, expenses, capital and loans, deposits and withdrawals), so editing
 * or deleting an old entry corrects the balance by itself. Nothing here touches profit.
 */
@Singleton
class CashBankRepository @Inject constructor(private val db: BillingDatabase) {
    private val dao = db.cashDao()

    val setup: Flow<CashSetup?> = dao.observeSetup().map { it?.toModel() }

    val movements: Flow<List<CashMovement>> = dao.observeMovements().map { rows -> rows.map { it.toModel() } }

    val balances: Flow<CashBalances> = combine(setup, movements) { s, m -> CashBook.balances(s, m) }

    suspend fun setupNow(): CashSetup? = dao.setup()?.toModel()

    suspend fun movementsNow(): List<CashMovement> = dao.movements().map { it.toModel() }

    suspend fun saveSetup(setup: CashSetup) {
        require(!setup.cash.isNegative && !setup.bank.isNegative) { "Opening balances cannot be below zero" }
        dao.saveSetup(CashSetupEntity(startDate = setup.startDate.toDay(), cash = setup.cash.paise, bank = setup.bank.paise))
    }

    suspend fun getMove(id: Long): CashMove? = dao.get(id)?.toModel()

    /** Saves a deposit or withdrawal. */
    suspend fun saveMove(move: CashMove): Long {
        require(move.kind != CashMoveKind.COUNT) { "Use Count cash to record a count" }
        require(move.amount.paise > 0) { "Enter the amount" }
        checkDate(move.date)
        val existing = if (move.id != 0L) dao.get(move.id) ?: error("This entry was deleted. Go back and try again.") else null
        val row = CashMoveEntity(
            id = move.id, kind = move.kind, date = move.date.toDay(), amount = move.amount.paise, counted = null,
            note = move.note.trim(), createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        return if (move.id == 0L) dao.insert(row) else move.id.also { dao.update(row) }
    }

    /**
     * Records the cash actually found in the drawer on [date]. The difference from the expected
     * balance is kept as a shortage or excess, so the balance then matches the drawer.
     * Returns the difference (negative when cash is short).
     */
    suspend fun countCash(date: LocalDate, counted: Money, note: String = ""): Money = db.withTransaction {
        require(!counted.isNegative) { "Cash counted cannot be below zero" }
        checkDate(date)
        val expected = CashBook.balanceAsOf(setupNow(), movementsNow(), MoneyAccount.CASH, date)
        val difference = counted - expected
        dao.insert(
            CashMoveEntity(
                kind = CashMoveKind.COUNT, date = date.toDay(), amount = difference.paise, counted = counted.paise,
                note = note.trim(), createdAt = System.currentTimeMillis(),
            ),
        )
        difference
    }

    suspend fun deleteMove(id: Long) = dao.delete(id)

    private suspend fun checkDate(date: LocalDate) {
        val start = setupNow()?.startDate ?: return
        require(!date.isBefore(start)) { "Choose a date on or after the opening date, ${start.format(DATE)}" }
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun CashSetupEntity.toModel() = CashSetup(startDate.toDate(), Money(cash), Money(bank))

internal fun CashMoveEntity.toModel() = CashMove(id, kind, date.toDate(), Money(amount), counted?.let { Money(it) }, note)

internal fun CashMovementRow.toModel(): CashMovement {
    val src = MovementSource.valueOf(source)
    val payMode = mode?.let { m -> PaymentMode.entries.firstOrNull { it.name == m } }
    val (title, detail) = when (src) {
        MovementSource.PAYMENT -> {
            val doc = docType?.let { t -> DocType.entries.firstOrNull { it.name == t } }
            if (doc != null && docNumber != null) "${doc.shortTitle} $docNumber" to detail.ifBlank { "Cash customer" }
            else (if (amount >= 0) "Payment in $title" else "Payment out $title") to detail
        }
        MovementSource.EXPENSE -> "Expense: $title" to detail
        MovementSource.CAPITAL -> (CapitalKind.entries.firstOrNull { it.name == title }?.label ?: title) to detail
        MovementSource.MOVE -> {
            val kind = CashMoveKind.entries.firstOrNull { it.name == title }
            val label = when {
                kind != CashMoveKind.COUNT -> kind?.label ?: title
                amount < 0 -> "Cash count: short"
                amount > 0 -> "Cash count: excess"
                else -> "Cash count: matched"
            }
            label to detail
        }
    }
    return CashMovement(
        source = src, id = id, billId = refId, date = date.toDate(),
        account = if (account == "CASH") MoneyAccount.CASH else MoneyAccount.BANK,
        amount = Money(amount), title = title, detail = detail, mode = payMode, createdAt = createdAt,
    )
}
