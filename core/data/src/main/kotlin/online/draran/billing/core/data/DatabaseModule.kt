package online.draran.billing.core.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import online.draran.billing.core.database.BillingDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BillingDatabase =
        Room.databaseBuilder(context, BillingDatabase::class.java, BillingDatabase.NAME).build()

    @Provides fun businessDao(db: BillingDatabase) = db.businessDao()
    @Provides fun itemDao(db: BillingDatabase) = db.itemDao()
    @Provides fun partyDao(db: BillingDatabase) = db.partyDao()
    @Provides fun invoiceDao(db: BillingDatabase) = db.invoiceDao()
    @Provides fun paymentDao(db: BillingDatabase) = db.paymentDao()
    @Provides fun expenseDao(db: BillingDatabase) = db.expenseDao()
    @Provides fun activityDao(db: BillingDatabase) = db.activityDao()
}
