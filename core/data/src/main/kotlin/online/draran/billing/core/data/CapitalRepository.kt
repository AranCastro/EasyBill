package online.draran.billing.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.database.BillingDatabase
import online.draran.billing.core.database.CapitalEntryEntity
import online.draran.billing.core.model.Capital
import online.draran.billing.core.model.CapitalEntry
import online.draran.billing.core.model.CapitalKind
import online.draran.billing.core.model.CapitalSummary
import javax.inject.Inject
import javax.inject.Singleton

/** Own money put in or taken out, and loans with their repayments. Kept apart from sales and profit. */
@Singleton
class CapitalRepository @Inject constructor(private val db: BillingDatabase) {
    private val dao = db.capitalDao()

    val entries: Flow<List<CapitalEntry>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    val summary: Flow<CapitalSummary> = entries.map { Capital.summarise(it) }

    suspend fun get(id: Long): CapitalEntry? = dao.get(id)?.toModel()

    suspend fun summaryNow(): CapitalSummary = Capital.summarise(dao.all().map { it.toModel() })

    /** Saves the entry. A repayment can never be more than is still owed to that lender. */
    suspend fun save(entry: CapitalEntry): Long = db.withTransaction {
        require(entry.amount.paise > 0) { "Enter the amount" }
        val person = Capital.cleanName(entry.person)
        if (entry.kind.isLoan) {
            require(person.isNotEmpty()) {
                if (entry.kind == CapitalKind.LOAN_RECEIVED) "Enter who lent the money" else "Enter whom the loan was repaid to"
            }
        }
        val existing = if (entry.id != 0L) dao.get(entry.id) ?: error("This entry was deleted. Go back and try again.") else null
        val clean = entry.copy(person = person, note = entry.note.trim())
        val others = dao.all().filter { it.id != entry.id }.map { it.toModel() }
        checkLoans(Capital.summarise(others + clean), setOfNotNull(Capital.key(person), existing?.let { Capital.key(it.person) }), clean)
        val row = CapitalEntryEntity(
            id = entry.id, kind = clean.kind, person = person, date = clean.date.toDay(), amount = clean.amount.paise,
            mode = clean.mode, note = clean.note, createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        if (entry.id == 0L) dao.insert(row) else entry.id.also { dao.update(row) }
    }

    /** Deletes the entry, unless that would leave a lender repaid more than was lent. */
    suspend fun delete(id: Long) = db.withTransaction {
        val existing = dao.get(id) ?: return@withTransaction
        val others = dao.all().filter { it.id != id }.map { it.toModel() }
        checkLoans(Capital.summarise(others), setOf(Capital.key(existing.person)), null)
        dao.delete(id)
    }

    private fun checkLoans(after: CapitalSummary, keys: Set<String>, change: CapitalEntry?) {
        val bad = after.loans.firstOrNull { it.key in keys && it.outstanding.isNegative } ?: return
        if (change != null && change.kind == CapitalKind.LOAN_REPAID && Capital.key(change.person) == bad.key) {
            val owed = bad.outstanding + change.amount
            throw IllegalArgumentException(
                if (owed.paise <= 0) "Nothing is owed to ${bad.lender} now" else "Only ${IndianFormat.rupees(owed)} is still owed to ${bad.lender}",
            )
        }
        throw IllegalArgumentException("${bad.lender} would be repaid more than was lent. Change or delete those repayments first.")
    }
}
