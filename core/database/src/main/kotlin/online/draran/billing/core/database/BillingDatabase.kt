package online.draran.billing.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BusinessEntity::class,
        PartyEntity::class,
        ItemEntity::class,
        ItemFts::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        PaymentEntity::class,
        AllocationEntity::class,
        ExpenseEntity::class,
        StockAdjustmentEntity::class,
    ],
    version = BillingDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // v1.1: business type, logo, signature, custom bill fields
        AutoMigration(from = 1, to = 2),
        // v1.2: optional MSME (Udyam) registration
        AutoMigration(from = 2, to = 3),
        // v1.3: bill colour
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class BillingDatabase : RoomDatabase() {
    abstract fun businessDao(): BusinessDao
    abstract fun itemDao(): ItemDao
    abstract fun partyDao(): PartyDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun activityDao(): ActivityDao

    companion object {
        const val NAME = "kallaa_petti.db"

        /** Schema version; the newest file in schemas/. Backups from a higher version are refused. */
        const val VERSION = 4
    }
}
