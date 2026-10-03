package online.draran.billing.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.ItemType
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.PaymentDirection
import online.draran.billing.core.model.PaymentMode

// Conventions: money in paise (Long), quantity in thousandths (Long),
// percentages in basis points (Int), dates as epoch days (Long).

@Entity(tableName = "business")
data class BusinessEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val ownerName: String,
    val phone: String,
    val email: String,
    val address: String,
    val stateCode: String,
    val gstEnabled: Boolean,
    val gstin: String,
    val upiId: String,
    val bankDetails: String,
    val terms: String,
    val roundOff: Boolean,
    val showUpiQr: Boolean,
    val pricesIncludeTax: Boolean,
    val thermalWidthMm: Int,
    val printerAddress: String,
    val printerName: String,
    /** "SALE=INV-;PURCHASE=PUR-;..." */
    val prefixes: String,
    val onboarded: Boolean,
    // Added in version 2
    @ColumnInfo(defaultValue = "'RETAIL'") val businessType: String = "RETAIL",
    @ColumnInfo(defaultValue = "''") val logoFile: String = "",
    @ColumnInfo(defaultValue = "''") val signatureFile: String = "",
    @ColumnInfo(defaultValue = "''") val signatoryName: String = "",
    @ColumnInfo(defaultValue = "''") val signatoryDesignation: String = "",
    @ColumnInfo(defaultValue = "''") val customFields: String = "",
    @ColumnInfo(defaultValue = "1") val printLogoOnReceipt: Boolean = true,
    // Added in version 3
    @ColumnInfo(defaultValue = "''") val udyamNumber: String = "",
    @ColumnInfo(defaultValue = "''") val msmeCategory: String = "",
    @ColumnInfo(defaultValue = "1") val printMsmeNote: Boolean = true,
    // Added in version 4
    @ColumnInfo(defaultValue = "0") val billColor: Int = 0,
)

@Entity(tableName = "party", indices = [Index("name")])
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PartyType,
    val phone: String,
    val gstin: String,
    val stateCode: String,
    val address: String,
    val openingBalance: Long,
    val createdAt: Long,
)

@Entity(tableName = "item", indices = [Index("barcode"), Index("name")])
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ItemType,
    val code: String,
    val barcode: String,
    val unit: String,
    val hsn: String,
    val salePrice: Long,
    val purchasePrice: Long,
    val taxRateBp: Int,
    val taxInclusive: Boolean,
    val openingStock: Long,
    val lowStock: Long,
    val favourite: Boolean,
    val category: String,
    val createdAt: Long,
)

/** Full-text index for instant item search by name, code or barcode. */
@Fts4(contentEntity = ItemEntity::class)
@Entity(tableName = "item_fts")
data class ItemFts(val name: String, val code: String, val barcode: String)

@Entity(
    tableName = "invoice",
    indices = [Index("type", "date"), Index("partyId")],
    foreignKeys = [
        ForeignKey(entity = PartyEntity::class, parentColumns = ["id"], childColumns = ["partyId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: DocType,
    val number: String,
    val seq: Long,
    val date: Long,
    val dueDate: Long?,
    val partyId: Long?,
    val partyName: String,
    val partyPhone: String,
    val partyGstin: String,
    val partyAddress: String,
    val placeOfSupply: String,
    val interState: Boolean,
    val gstEnabled: Boolean,
    val roundOffEnabled: Boolean,
    val subtotal: Long,
    val discount: Long,
    val taxable: Long,
    val cgst: Long,
    val sgst: Long,
    val igst: Long,
    val roundOff: Long,
    val total: Long,
    val notes: String,
    val convertedFromId: Long?,
    val createdAt: Long,
    /** Industry fields (label/value pairs). Added in version 2. */
    @ColumnInfo(defaultValue = "''") val customFields: String = "",
)

@Entity(
    tableName = "invoice_line",
    indices = [Index("invoiceId"), Index("itemId")],
    foreignKeys = [
        ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val position: Int,
    val itemId: Long?,
    val name: String,
    val hsn: String,
    val unit: String,
    val qty: Long,
    val rate: Long,
    val discountBp: Int,
    val taxRateBp: Int,
    val taxInclusive: Boolean,
    val costRate: Long,
    val taxable: Long,
    val tax: Long,
    val cgst: Long,
    val sgst: Long,
    val igst: Long,
    val total: Long,
)

@Entity(
    tableName = "payment",
    indices = [Index("partyId"), Index("invoiceId"), Index("date")],
    foreignKeys = [
        ForeignKey(entity = PartyEntity::class, parentColumns = ["id"], childColumns = ["partyId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: PaymentDirection,
    val number: String,
    val seq: Long,
    val partyId: Long?,
    val partyName: String,
    val date: Long,
    val amount: Long,
    val mode: PaymentMode,
    val reference: String,
    val note: String,
    /** Payment taken while saving this bill; deleted together with the bill. */
    val invoiceId: Long?,
    val createdAt: Long,
)

/** How much of a payment settles which bill. */
@Entity(
    tableName = "allocation",
    indices = [Index("paymentId"), Index("invoiceId")],
    foreignKeys = [
        ForeignKey(entity = PaymentEntity::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class AllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val invoiceId: Long,
    val amount: Long,
)

@Entity(tableName = "expense", indices = [Index("date")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val date: Long,
    val amount: Long,
    val mode: PaymentMode,
    val note: String,
    val createdAt: Long,
)

@Entity(
    tableName = "stock_adjustment",
    indices = [Index("itemId")],
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class StockAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val date: Long,
    val qty: Long,
    val note: String,
    val createdAt: Long,
)
