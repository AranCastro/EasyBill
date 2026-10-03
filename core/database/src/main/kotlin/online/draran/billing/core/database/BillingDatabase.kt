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
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v1.1: business type, logo, signature, custom bill fields
        AutoMigration(from = 1, to = 2),
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
    }
}
