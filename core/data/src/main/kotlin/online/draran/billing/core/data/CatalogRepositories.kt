package online.draran.billing.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import online.draran.billing.core.database.BusinessDao
import online.draran.billing.core.database.ItemDao
import online.draran.billing.core.database.PartyDao
import online.draran.billing.core.database.StockAdjustmentEntity
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.Item
import online.draran.billing.core.model.ItemWithStock
import online.draran.billing.core.model.LedgerEntry
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.Party
import online.draran.billing.core.model.PartyWithBalance
import online.draran.billing.core.model.StockMove
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessRepository @Inject constructor(private val dao: BusinessDao) {
    /** Never null: before onboarding this is an empty profile with onboarded = false. */
    val business: Flow<Business> = dao.observe().map { it?.toModel() ?: Business() }

    suspend fun get(): Business = dao.get()?.toModel() ?: Business()

    suspend fun save(business: Business) = dao.upsert(business.toEntity())
}

@Singleton
class ItemRepository @Inject constructor(private val dao: ItemDao) {

    fun items(query: String = ""): Flow<List<ItemWithStock>> {
        val match = ftsQuery(query)
        val source = if (match == null) dao.observeAll() else dao.search(match)
        return source.map { rows -> rows.map { it.toModel() } }
    }

    fun item(id: Long): Flow<ItemWithStock?> = dao.observe(id).map { it?.toModel() }

    suspend fun get(id: Long): Item? = dao.get(id)?.toModel()

    suspend fun byBarcode(code: String): Item? = code.trim().takeIf { it.isNotEmpty() }?.let { dao.byBarcode(it)?.toModel() }

    suspend fun nameTaken(name: String, exceptId: Long) = dao.countByName(name.trim(), exceptId) > 0

    /** Inserts or updates; returns the id. */
    suspend fun save(item: Item): Long {
        require(item.name.isNotBlank()) { "Item name is required" }
        return if (item.id == 0L) {
            dao.insert(item.toEntity(System.currentTimeMillis()))
        } else {
            val existing = dao.get(item.id)
            dao.update(item.toEntity(existing?.createdAt ?: System.currentTimeMillis()))
            item.id
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun adjustStock(itemId: Long, qtyMilli: Long, note: String, date: LocalDate = LocalDate.now()) {
        if (qtyMilli == 0L) return
        dao.insertAdjustment(StockAdjustmentEntity(itemId = itemId, date = date.toDay(), qty = qtyMilli, note = note, createdAt = System.currentTimeMillis()))
    }

    fun moves(itemId: Long): Flow<List<StockMove>> = dao.observeMoves(itemId).map { rows ->
        rows.map { StockMove(it.date.toDate(), it.qty, it.reason, it.reference) }
    }

    suspend fun setFavourite(item: Item, favourite: Boolean) = save(item.copy(favourite = favourite))

    /** Adds the starter services of a business type, skipping names that already exist. Returns how many were added. */
    suspend fun addPresets(type: online.draran.billing.core.model.BusinessType, gstEnabled: Boolean): Int {
        var added = 0
        type.presets.forEach { p ->
            if (dao.countByName(p.name, 0) > 0) return@forEach
            save(
                Item(
                    name = p.name,
                    type = if (p.goods) online.draran.billing.core.model.ItemType.GOODS else online.draran.billing.core.model.ItemType.SERVICE,
                    unit = p.unit,
                    hsn = p.sac,
                    salePrice = online.draran.billing.core.model.Money.rupees(p.rupees),
                    taxRateBp = if (gstEnabled) p.taxRateBp else 0,
                    favourite = true,
                ),
            )
            added++
        }
        return added
    }

    companion object {
        /** "rice bas" -> "rice* bas*" so typing a prefix finds matches. */
        fun ftsQuery(query: String): String? {
            val tokens = query.trim().split(Regex("\\s+"))
                .map { token -> token.filter { it.isLetterOrDigit() } }
                .filter { it.isNotEmpty() }
            return if (tokens.isEmpty()) null else tokens.joinToString(" ") { "$it*" }
        }
    }
}

@Singleton
class PartyRepository @Inject constructor(private val dao: PartyDao) {

    val parties: Flow<List<PartyWithBalance>> = dao.observeWithBalance().map { rows -> rows.map { it.toModel() } }

    fun party(id: Long): Flow<PartyWithBalance?> = dao.observeWithBalance(id).map { it?.toModel() }

    suspend fun get(id: Long): Party? = dao.get(id)?.toModel()

    suspend fun save(party: Party): Long {
        require(party.name.isNotBlank()) { "Party name is required" }
        return if (party.id == 0L) {
            dao.insert(party.toEntity(System.currentTimeMillis()))
        } else {
            val existing = dao.get(party.id)
            dao.update(party.toEntity(existing?.createdAt ?: System.currentTimeMillis()))
            party.id
        }
    }

    /** Parties with bills or payments cannot be deleted, so their history stays intact. */
    suspend fun canDelete(id: Long) = dao.transactionCount(id) == 0

    suspend fun delete(id: Long): Boolean {
        if (!canDelete(id)) return false
        dao.delete(id)
        return true
    }

    /** Statement with running balance, oldest first. Opening balance comes first. */
    fun ledger(id: Long): Flow<List<LedgerEntry>> = dao.observeLedger(id).map { rows ->
        val party = dao.get(id)
        var running = party?.openingBalance ?: 0L
        val opening = if (running != 0L) {
            listOf(LedgerEntry(LocalDate.ofEpochDay(party!!.createdAt / 86_400_000), "OPENING", "Opening balance", Money(running), Money(running), 0, false))
        } else {
            emptyList()
        }
        opening + rows.map { row ->
            running += row.amount
            LedgerEntry(row.date.toDate(), row.kind, row.number, Money(row.amount), Money(running), row.refId, row.isPayment)
        }
    }
}
