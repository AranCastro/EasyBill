package online.draran.billing.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.PaymentDirection

@Dao
interface BusinessDao {
    @Query("SELECT * FROM business WHERE id = 1")
    fun observe(): Flow<BusinessEntity?>

    @Query("SELECT * FROM business WHERE id = 1")
    suspend fun get(): BusinessEntity?

    @Upsert
    suspend fun upsert(entity: BusinessEntity)
}

@Dao
interface ItemDao {
    @Query("SELECT item.*, " + Sql.STOCK_OF_ITEM + " AS stock FROM item ORDER BY favourite DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ItemStockRow>>

    @Query(
        "SELECT item.*, " + Sql.STOCK_OF_ITEM + " AS stock FROM item " +
            "WHERE item.rowid IN (SELECT rowid FROM item_fts WHERE item_fts MATCH :match) " +
            "ORDER BY favourite DESC, name COLLATE NOCASE",
    )
    fun search(match: String): Flow<List<ItemStockRow>>

    @Query("SELECT item.*, " + Sql.STOCK_OF_ITEM + " AS stock FROM item WHERE id = :id")
    fun observe(id: Long): Flow<ItemStockRow?>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Query("SELECT * FROM item WHERE barcode = :barcode OR code = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): ItemEntity?

    @Query("SELECT COUNT(*) FROM item WHERE name = :name COLLATE NOCASE AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long): Int

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM item")
    suspend fun count(): Int

    @Insert
    suspend fun insertAdjustment(adjustment: StockAdjustmentEntity): Long

    @Query(
        "SELECT i.date AS date, l.qty * " + Sql.STOCK_SIGN + " AS qty, i.type AS reason, i.number AS reference, i.createdAt AS createdAt " +
            "FROM invoice_line l JOIN invoice i ON i.id = l.invoiceId WHERE l.itemId = :itemId AND i.type != 'ESTIMATE' " +
            "UNION ALL SELECT a.date, a.qty, 'ADJUSTMENT', a.note, a.createdAt FROM stock_adjustment a WHERE a.itemId = :itemId " +
            "ORDER BY date DESC, createdAt DESC",
    )
    fun observeMoves(itemId: Long): Flow<List<StockMoveRow>>
}

@Dao
interface PartyDao {
    @Query(
        "SELECT p.*, " + Sql.BALANCE_OF_PARTY + " AS balance, " +
            "(SELECT MAX(d) FROM (SELECT MAX(date) AS d FROM invoice WHERE partyId = p.id UNION ALL SELECT MAX(date) FROM payment WHERE partyId = p.id)) AS lastActivity " +
            "FROM party p ORDER BY p.name COLLATE NOCASE",
    )
    fun observeWithBalance(): Flow<List<PartyBalanceRow>>

    @Query(
        "SELECT p.*, " + Sql.BALANCE_OF_PARTY + " AS balance, NULL AS lastActivity FROM party p WHERE p.id = :id",
    )
    fun observeWithBalance(id: Long): Flow<PartyBalanceRow?>

    @Query("SELECT * FROM party WHERE id = :id")
    suspend fun get(id: Long): PartyEntity?

    @Query("SELECT * FROM party WHERE type = :type ORDER BY name COLLATE NOCASE")
    fun observeByType(type: PartyType): Flow<List<PartyEntity>>

    @Insert
    suspend fun insert(party: PartyEntity): Long

    @Update
    suspend fun update(party: PartyEntity)

    @Query("DELETE FROM party WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT (SELECT COUNT(*) FROM invoice WHERE partyId = :id) + (SELECT COUNT(*) FROM payment WHERE partyId = :id)")
    suspend fun transactionCount(id: Long): Int

    @Query(
        "SELECT i.date AS date, i.type AS kind, i.number AS number, i.total * " + Sql.LEDGER_SIGN + " AS amount, " +
            "i.id AS refId, 0 AS isPayment, i.createdAt AS createdAt FROM invoice i WHERE i.partyId = :id AND i.type != 'ESTIMATE' " +
            "UNION ALL SELECT date, CASE direction WHEN 'IN' THEN 'PAYMENT_IN' ELSE 'PAYMENT_OUT' END, number, " +
            "CASE direction WHEN 'IN' THEN -amount ELSE amount END, id, 1, createdAt FROM payment WHERE partyId = :id " +
            // A bill and the payment taken with it share a time: the bill comes first so the balance never dips
            "ORDER BY date, createdAt, isPayment, refId",
    )
    fun observeLedger(id: Long): Flow<List<LedgerRow>>
}

@Dao
interface InvoiceDao {
    @Insert
    suspend fun insert(invoice: InvoiceEntity): Long

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("DELETE FROM invoice WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT i.number AS number, i.date AS date FROM note_allocation n JOIN invoice i ON i.id = n.invoiceId WHERE n.noteId = :noteId ORDER BY i.date, i.id")
    fun observeAgainst(noteId: Long): Flow<List<AgainstRow>>

    @Query("SELECT i.number AS number, i.date AS date FROM note_allocation n JOIN invoice i ON i.id = n.invoiceId WHERE n.noteId = :noteId ORDER BY i.date, i.id")
    suspend fun against(noteId: Long): List<AgainstRow>

    @Query("SELECT DISTINCT partyId FROM invoice WHERE partyId IS NOT NULL AND type IN ('SALE_RETURN', 'PURCHASE_RETURN')")
    suspend fun partiesWithNotes(): List<Long>

    @Query("UPDATE invoice SET billColor = :color WHERE id = :id")
    suspend fun setBillColor(id: Long, color: Int)

    @Insert
    suspend fun insertLines(lines: List<InvoiceLineEntity>)

    @Query("DELETE FROM invoice_line WHERE invoiceId = :invoiceId")
    suspend fun deleteLines(invoiceId: Long)

    @Query("SELECT * FROM invoice WHERE id = :id")
    suspend fun get(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoice WHERE id = :id")
    fun observe(id: Long): Flow<InvoiceEntity?>

    @Query("SELECT * FROM invoice_line WHERE invoiceId = :invoiceId ORDER BY position")
    suspend fun lines(invoiceId: Long): List<InvoiceLineEntity>

    @Query("SELECT * FROM invoice_line WHERE invoiceId = :invoiceId ORDER BY position")
    fun observeLines(invoiceId: Long): Flow<List<InvoiceLineEntity>>

    /** Payments applied plus credit/debit notes set against it (or, for a note, used). */
    @Query(
        "SELECT COALESCE((SELECT SUM(amount) FROM allocation WHERE invoiceId = :invoiceId), 0) + " +
            "COALESCE((SELECT SUM(amount) FROM note_allocation WHERE invoiceId = :invoiceId OR noteId = :invoiceId), 0)",
    )
    fun observePaid(invoiceId: Long): Flow<Long>

    @Query(
        "SELECT COALESCE((SELECT SUM(amount) FROM allocation WHERE invoiceId = :invoiceId), 0) + " +
            "COALESCE((SELECT SUM(amount) FROM note_allocation WHERE invoiceId = :invoiceId OR noteId = :invoiceId), 0)",
    )
    suspend fun paid(invoiceId: Long): Long

    @Query(
        "SELECT i.id, i.type, i.number, i.date, i.partyName, i.total, " + Sql.PAID_OF_INVOICE + " AS paid, i.dueDate AS dueDate " +
            "FROM invoice i WHERE i.type IN (:types) ORDER BY i.date DESC, i.id DESC",
    )
    fun observeSummaries(types: List<DocType>): Flow<List<InvoiceSummaryRow>>

    @Query(
        "SELECT i.id, i.type, i.number, i.date, i.partyName, i.total, " + Sql.PAID_OF_INVOICE + " AS paid, i.dueDate AS dueDate " +
            "FROM invoice i WHERE i.partyId = :partyId ORDER BY i.date DESC, i.id DESC",
    )
    fun observeForParty(partyId: Long): Flow<List<InvoiceSummaryRow>>

    @Query("SELECT COALESCE(MAX(seq), 0) FROM invoice WHERE type = :type")
    suspend fun maxSeq(type: DocType): Long

    @Query("SELECT COUNT(*) FROM invoice WHERE type = :type AND number = :number AND id != :exceptId")
    suspend fun countNumber(type: DocType, number: String, exceptId: Long): Int

    /** Documents of a party that still have something to settle, oldest first. */
    @Query(
        // Settled by payments taken with the bill and by notes; standalone payments are spread afterwards
        "SELECT i.id, i.date, i.total, " +
            "COALESCE((SELECT SUM(al.amount) FROM allocation al JOIN payment p ON p.id = al.paymentId " +
            "WHERE al.invoiceId = i.id AND p.invoiceId IS NOT NULL), 0) + " + Sql.NOTES_OF_INVOICE + " AS paid " +
            "FROM invoice i WHERE i.partyId = :partyId AND i.type IN (:types) ORDER BY i.date, i.id",
    )
    suspend fun docsForAllocation(partyId: Long, types: List<DocType>): List<OpenDocRow>

    /** Date of the newest other purchase bill that includes this item (0 if none). */
    @Query(
        "SELECT COALESCE(MAX(i.date), 0) FROM invoice i JOIN invoice_line l ON l.invoiceId = i.id " +
            "WHERE i.type = 'PURCHASE' AND l.itemId = :itemId AND i.id != :exceptId",
    )
    suspend fun latestPurchaseDay(itemId: Long, exceptId: Long): Long

    @Query(
        "SELECT COUNT(*) AS count, COALESCE(SUM(i.total - " + Sql.PAID_OF_INVOICE + "), 0) AS amount FROM invoice i " +
            "WHERE i.type = 'SALE' AND i.dueDate IS NOT NULL AND i.dueDate < :today AND i.total > " + Sql.PAID_OF_INVOICE,
    )
    fun observeOverdue(today: Long): Flow<OverdueRow>

    /** Best sellers by value (before tax, net of returns) between two days. */
    @Query(
        "SELECT l.name AS name, SUM(CASE i.type WHEN 'SALE' THEN l.qty ELSE -l.qty END) AS qty, l.unit AS unit, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.taxable ELSE -l.taxable END) AS total " +
            "FROM invoice_line l JOIN invoice i ON i.id = l.invoiceId " +
            "WHERE i.type IN ('SALE', 'SALE_RETURN') AND i.date BETWEEN :from AND :to " +
            "GROUP BY COALESCE(l.itemId, l.name), l.unit " +
            "HAVING SUM(CASE i.type WHEN 'SALE' THEN l.taxable ELSE -l.taxable END) > 0 " +
            "ORDER BY SUM(CASE i.type WHEN 'SALE' THEN l.taxable ELSE -l.taxable END) DESC LIMIT :limit",
    )
    fun observeTopItems(from: Long, to: Long, limit: Int): Flow<List<TopItemRow>>

    @Query("SELECT COUNT(*) FROM invoice WHERE type = :type AND date = :day")
    fun observeCount(type: DocType, day: Long): Flow<Int>

    @Query("SELECT date, SUM(total) AS total FROM invoice WHERE type = :type AND date BETWEEN :from AND :to GROUP BY date")
    fun observeDayTotals(type: DocType, from: Long, to: Long): Flow<List<DayTotalRow>>

    @Query(
        "SELECT type, COUNT(*) AS count, SUM(taxable) AS taxable, SUM(cgst) AS cgst, SUM(sgst) AS sgst, " +
            "SUM(igst) AS igst, SUM(total) AS total FROM invoice WHERE date BETWEEN :from AND :to GROUP BY type",
    )
    suspend fun typeTotals(from: Long, to: Long): List<TypeTotalsRow>

    @Query(
        "SELECT i.type AS type, (i.partyGstin != '') AS b2b, i.interState AS interState, l.taxRateBp AS taxRateBp, " +
            "COUNT(DISTINCT i.id) AS docs, SUM(l.taxable) AS taxable, SUM(l.cgst) AS cgst, SUM(l.sgst) AS sgst, SUM(l.igst) AS igst " +
            "FROM invoice_line l JOIN invoice i ON i.id = l.invoiceId " +
            "WHERE i.gstEnabled = 1 AND i.type != 'ESTIMATE' AND i.date BETWEEN :from AND :to " +
            // Input tax credit needs the supplier's GSTIN: purchases from unregistered suppliers are left out
            "AND (i.type NOT IN ('PURCHASE', 'PURCHASE_RETURN') OR i.partyGstin != '') " +
            "GROUP BY i.type, b2b, i.interState, l.taxRateBp ORDER BY i.type, l.taxRateBp",
    )
    suspend fun gstRates(from: Long, to: Long): List<GstRateRow>

    @Query(
        "SELECT l.hsn AS hsn, l.unit AS unit, l.taxRateBp AS taxRateBp, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.qty ELSE -l.qty END) AS qty, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.taxable ELSE -l.taxable END) AS taxable, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.cgst ELSE -l.cgst END) AS cgst, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.sgst ELSE -l.sgst END) AS sgst, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.igst ELSE -l.igst END) AS igst, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.total ELSE -l.total END) AS total " +
            "FROM invoice_line l JOIN invoice i ON i.id = l.invoiceId " +
            "WHERE i.type IN ('SALE', 'SALE_RETURN') AND i.gstEnabled = 1 AND i.date BETWEEN :from AND :to " +
            "GROUP BY l.hsn, l.unit, l.taxRateBp ORDER BY l.hsn",
    )
    suspend fun hsnSummary(from: Long, to: Long): List<HsnRow>

    /** Cost of goods sold: sales at cost minus returns at cost (paise). */
    @Query(
        "SELECT COALESCE(SUM(CASE i.type WHEN 'SALE' THEN l.qty * l.costRate WHEN 'SALE_RETURN' THEN -l.qty * l.costRate ELSE 0 END), 0) / 1000 " +
            "FROM invoice_line l JOIN invoice i ON i.id = l.invoiceId WHERE i.date BETWEEN :from AND :to",
    )
    suspend fun costOfGoodsSold(from: Long, to: Long): Long

    @Query(
        // Before tax and net of returns, so it agrees with the profit and loss report
        "SELECT l.name AS name, SUM(CASE i.type WHEN 'SALE' THEN l.qty ELSE -l.qty END) AS qty, l.unit AS unit, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.taxable ELSE -l.taxable END) AS total, " +
            "SUM(CASE i.type WHEN 'SALE' THEN l.qty * l.costRate ELSE -l.qty * l.costRate END) / 1000 AS cost " +
            "FROM invoice_line l JOIN invoice i ON i.id = l.invoiceId WHERE i.type IN ('SALE', 'SALE_RETURN') AND i.date BETWEEN :from AND :to " +
            "GROUP BY COALESCE(l.itemId, l.name), l.unit ORDER BY total DESC",
    )
    suspend fun itemSales(from: Long, to: Long): List<ItemSalesRow>

    @Query(
        "SELECT i.id, i.type, i.number, i.date, i.partyName, i.total, " + Sql.PAID_OF_INVOICE + " AS paid " +
            "FROM invoice i WHERE i.type = :type AND i.date BETWEEN :from AND :to ORDER BY i.date, i.id",
    )
    suspend fun register(type: DocType, from: Long, to: Long): List<InvoiceSummaryRow>

    // Only a sale counts as the conversion of an estimate
    @Query("SELECT * FROM invoice WHERE convertedFromId = :id AND type = 'SALE' LIMIT 1")
    suspend fun convertedFrom(id: Long): InvoiceEntity?

    @Query("SELECT id FROM invoice WHERE convertedFromId = :id AND type = 'SALE' LIMIT 1")
    fun observeConvertedSaleId(id: Long): Flow<Long?>
}

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("DELETE FROM payment WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM payment WHERE invoiceId = :invoiceId")
    suspend fun deleteForInvoice(invoiceId: Long)

    @Query("SELECT * FROM payment WHERE id = :id")
    suspend fun get(id: Long): PaymentEntity?

    @Query("SELECT * FROM payment WHERE invoiceId = :invoiceId")
    suspend fun forInvoice(invoiceId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payment ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payment WHERE partyId = :partyId AND direction = :direction AND invoiceId IS NULL ORDER BY date, id")
    suspend fun standalone(partyId: Long, direction: PaymentDirection): List<PaymentEntity>

    @Query("SELECT COALESCE(MAX(seq), 0) FROM payment WHERE direction = :direction")
    suspend fun maxSeq(direction: PaymentDirection): Long

    @Insert
    suspend fun insertAllocations(allocations: List<AllocationEntity>)

    /** Clears how a party's standalone payments were spread (one statement, so no limit on how many). */
    @Query("DELETE FROM allocation WHERE paymentId IN (SELECT id FROM payment WHERE partyId = :partyId AND direction = :direction AND invoiceId IS NULL)")
    suspend fun deleteStandaloneAllocations(partyId: Long, direction: PaymentDirection)

    @Insert
    suspend fun insertNoteAllocations(allocations: List<NoteAllocationEntity>)

    @Query(
        "DELETE FROM note_allocation WHERE noteId IN (SELECT id FROM invoice WHERE partyId = :partyId) " +
            "OR invoiceId IN (SELECT id FROM invoice WHERE partyId = :partyId)",
    )
    suspend fun deleteNoteAllocations(partyId: Long)

    @Query("DELETE FROM allocation WHERE paymentId IN (:paymentIds)")
    suspend fun deleteAllocations(paymentIds: List<Long>)

    @Query("SELECT * FROM allocation WHERE paymentId = :paymentId")
    suspend fun allocations(paymentId: Long): List<AllocationEntity>

    @Query(
        "SELECT direction, mode, SUM(amount) AS total FROM payment WHERE date BETWEEN :from AND :to GROUP BY direction, mode",
    )
    suspend fun modeTotals(from: Long, to: Long): List<ModeTotalRow>
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun get(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expense ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT category, SUM(amount) AS total FROM expense WHERE date BETWEEN :from AND :to GROUP BY category ORDER BY total DESC")
    suspend fun byCategory(from: Long, to: Long): List<CategoryTotalRow>
}

@Dao
interface ActivityDao {
    @Query(
        "SELECT i.id AS id, i.type AS kind, i.number AS number, i.partyName AS partyName, i.date AS date, i.total AS amount, " +
            "(i.total - " + Sql.PAID_OF_INVOICE + ") AS balance, NULL AS mode, i.createdAt AS createdAt " +
            "FROM invoice i WHERE i.date BETWEEN :from AND :to " +
            "UNION ALL SELECT id, CASE direction WHEN 'IN' THEN 'PAYMENT_IN' ELSE 'PAYMENT_OUT' END, number, partyName, date, amount, 0, mode, createdAt " +
            "FROM payment WHERE date BETWEEN :from AND :to AND invoiceId IS NULL " +
            "UNION ALL SELECT id, 'EXPENSE', category, note, date, amount, 0, mode, createdAt FROM expense WHERE date BETWEEN :from AND :to " +
            "ORDER BY date DESC, createdAt DESC LIMIT :limit",
    )
    fun observe(from: Long, to: Long, limit: Int): Flow<List<ActivityRow>>
}

@Dao
interface CounterDao {
    @Query("SELECT COALESCE(MAX(last), 0) FROM doc_counter WHERE `key` = :key")
    suspend fun last(key: String): Long

    @Query("INSERT OR IGNORE INTO doc_counter(`key`, last) VALUES (:key, 0)")
    suspend fun ensure(key: String)

    @Query("UPDATE doc_counter SET last = MAX(last, :value) WHERE `key` = :key")
    suspend fun raise(key: String, value: Long)
}
