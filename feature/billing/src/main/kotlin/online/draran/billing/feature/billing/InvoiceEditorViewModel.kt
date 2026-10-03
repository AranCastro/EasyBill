package online.draran.billing.feature.billing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.draran.billing.core.data.BrandingManager
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.data.CASH_CUSTOMER
import online.draran.billing.core.data.InvoiceDraft
import online.draran.billing.core.data.InvoiceRepository
import online.draran.billing.core.data.ItemRepository
import online.draran.billing.core.data.PartyRepository
import online.draran.billing.core.model.BillTotals
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.InvoiceLine
import online.draran.billing.core.model.Item
import online.draran.billing.core.model.ItemWithStock
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.MoneyParse
import online.draran.billing.core.model.Party
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.PartyWithBalance
import online.draran.billing.core.model.PaymentMode
import online.draran.billing.core.model.Qty
import online.draran.billing.core.model.TaxEngine
import java.time.LocalDate
import javax.inject.Inject

data class PartyChoice(
    val id: Long? = null,
    val name: String = CASH_CUSTOMER,
    val phone: String = "",
    val gstin: String = "",
    val address: String = "",
    val stateCode: String = "",
    val balance: Money = Money.ZERO,
) {
    val isCash: Boolean get() = id == null
}

@HiltViewModel
class InvoiceEditorViewModel @Inject constructor(
    private val invoices: InvoiceRepository,
    private val items: ItemRepository,
    private val parties: PartyRepository,
    private val businessRepository: BusinessRepository,
    private val branding: BrandingManager,
) : ViewModel() {

    val business: StateFlow<Business> = businessRepository.business.stateIn(viewModelScope, SharingStarted.Eagerly, Business())
    val allItems: StateFlow<List<ItemWithStock>> = items.items().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allParties: StateFlow<List<PartyWithBalance>> = parties.parties.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var type by mutableStateOf(DocType.SALE)
        private set
    var invoiceId by mutableStateOf(0L)
        private set
    var number by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var party by mutableStateOf(PartyChoice())
        private set
    var lines by mutableStateOf<List<InvoiceLine>>(emptyList())
        private set
    var notes by mutableStateOf("")
    var dueDate by mutableStateOf<LocalDate?>(null)
    /** Industry bill fields typed for this bill, by label. */
    var customValues by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var draftLabels: List<String> = emptyList()
    var fullyPaid by mutableStateOf(true)
    var received by mutableStateOf("")
    var mode by mutableStateOf(PaymentMode.CASH)
    var error by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    private var convertedFromId: Long? = null

    /** The number suggested for a new bill, so it can be replaced if another bill takes it first. */
    private var suggestedNumber = ""

    /** GST and round-off a bill was made under, kept when it is edited (null = the shop's current settings). */
    private var editGst: Boolean? = null
    private var editRound: Boolean? = null

    /** Whether tax applies to this bill: its own setting when editing an old bill, else the shop's. */
    val gstOn: Boolean get() = editGst ?: business.value.gstEnabled
    private var originalNumber = ""

    fun init(docType: DocType, id: Long, sourceId: Long, partyId: Long) {
        if (loaded) return
        loaded = true
        type = docType
        viewModelScope.launch {
            // The shop settings load asynchronously; totals below must not run on the empty defaults
            business.first { it.onboarded }
            val draft: InvoiceDraft? = when {
                id != 0L -> invoices.draftForEdit(id)
                sourceId != 0L -> invoices.draftFrom(sourceId, docType)
                else -> null
            }
            if (draft != null) {
                type = draft.type
                invoiceId = draft.id
                number = draft.number
                originalNumber = draft.number
                date = draft.date
                party = if (draft.partyId == null) PartyChoice(name = draft.partyName.ifBlank { CASH_CUSTOMER }) else PartyChoice(
                    draft.partyId, draft.partyName, draft.partyPhone, draft.partyGstin, draft.partyAddress, draft.partyStateCode,
                )
                lines = draft.lines
                notes = draft.notes
                dueDate = draft.dueDate
                customValues = draft.customFields.toMap()
                draftLabels = draft.customFields.map { it.first }
                convertedFromId = draft.convertedFromId
                editGst = draft.gstEnabled
                editRound = draft.roundOff
                val total = totals().total
                fullyPaid = draft.paidNow.paise >= total.paise && total.paise > 0 || (draft.id == 0L && draft.partyId == null)
                received = if (fullyPaid || draft.paidNow.isZero) "" else MoneyParse.toInput(draft.paidNow)
                mode = draft.paymentMode
            } else {
                number = if (docType == DocType.PURCHASE) "" else invoices.nextNumber(docType)
                suggestedNumber = number
                if (partyId != 0L) parties.get(partyId)?.let { selectParty(it) }
                fullyPaid = party.isCash
            }
        }
    }

    /** Bill fields for sale-side documents: the business's labels plus any already on this bill. */
    val customLabels: List<String>
        get() = if (type.partyType != PartyType.CUSTOMER || type == DocType.SALE_RETURN) emptyList()
        else (business.value.customFieldLabels + draftLabels).distinct()

    val showsDueDate: Boolean get() = type == DocType.SALE && (business.value.type.showsDueDate || dueDate != null)

    fun setCustomValue(label: String, value: String) {
        customValues = customValues + (label to value)
    }

    val title: String get() = (if (invoiceId == 0L) "New " else "Edit ") + business.value.docShortTitle(type).let { if (it == type.shortTitle) it.lowercase().replaceFirstChar { c -> c.uppercase() } else it }

    fun interState(): Boolean {
        val b = business.value
        return gstOn && party.stateCode.isNotBlank() && party.stateCode != b.stateCode
    }

    fun totals(): BillTotals = TaxEngine.bill(lines.map { it.toInput() }, interState(), gstOn, editRound ?: business.value.roundOff)

    fun selectParty(p: Party, balance: Money = Money.ZERO) {
        party = PartyChoice(p.id, p.name, p.phone, p.gstin, p.address, p.stateCode, balance)
        if (invoiceId == 0L) fullyPaid = false
    }

    fun selectCash() {
        party = PartyChoice()
        fullyPaid = true
    }

    fun quickAddParty(name: String, phone: String) {
        viewModelScope.launch {
            val partyType = type.partyType
            val id = parties.save(Party(name = name, phone = phone, type = partyType))
            parties.get(id)?.let { selectParty(it) }
        }
    }

    private fun rateFor(item: Item) = if (type == DocType.PURCHASE || type == DocType.PURCHASE_RETURN) item.purchasePrice else item.salePrice

    private fun lineFor(item: Item, qty: Long) = InvoiceLine(
        itemId = item.id, name = item.name, hsn = item.hsn, unit = item.unit, qtyMilli = qty, rate = rateFor(item),
        taxRateBp = item.taxRateBp,
        // Purchase prices are kept before tax
        taxInclusive = if (type == DocType.PURCHASE || type == DocType.PURCHASE_RETURN) false else item.taxInclusive,
        costRate = item.purchasePrice,
    )

    private var lastAddedNewItem = 0L

    /** Adds an item that was just created from this screen. */
    fun addNewItem(id: Long) {
        if (id == 0L || id == lastAddedNewItem) return
        lastAddedNewItem = id
        viewModelScope.launch { items.get(id)?.let { addItem(it) } }
    }

    fun quantityOf(itemId: Long): Long = lines.filter { it.itemId == itemId }.sumOf { it.qtyMilli }

    /** Adds one unit, or [qty] units, of an item; repeats increase the existing line. */
    fun addItem(item: Item, qty: Long = Qty.ONE) {
        val index = lines.indexOfFirst { it.itemId == item.id }
        lines = if (index >= 0) {
            lines.toMutableList().also { it[index] = it[index].copy(qtyMilli = it[index].qtyMilli + qty) }
        } else {
            lines + lineFor(item, qty)
        }
        error = null
    }

    /** Sets the quantity for an item from the picker; zero removes it. */
    fun setItemQuantity(item: Item, qty: Long) {
        val index = lines.indexOfFirst { it.itemId == item.id }
        lines = when {
            qty <= 0 && index >= 0 -> lines.filterIndexed { i, _ -> i != index }
            qty <= 0 -> lines
            index >= 0 -> lines.toMutableList().also { it[index] = it[index].copy(qtyMilli = qty) }
            else -> lines + lineFor(item, qty)
        }
    }

    fun addCustomLine(name: String, rate: Money, qty: Long, taxBp: Int) {
        lines = lines + InvoiceLine(itemId = null, name = name, qtyMilli = qty, rate = rate, taxRateBp = taxBp, taxInclusive = business.value.pricesIncludeTax)
    }

    fun updateLine(index: Int, line: InvoiceLine) {
        lines = lines.toMutableList().also { it[index] = line }
    }

    fun changeQty(index: Int, delta: Long) {
        val line = lines[index]
        val next = line.qtyMilli + delta
        lines = if (next <= 0) lines.filterIndexed { i, _ -> i != index } else lines.toMutableList().also { it[index] = line.copy(qtyMilli = next) }
    }

    fun removeLine(index: Int) {
        lines = lines.filterIndexed { i, _ -> i != index }
    }

    /** Returns false when no item has this barcode. */
    suspend fun addByBarcode(code: String): Boolean {
        val item = items.byBarcode(code) ?: return false
        addItem(item)
        return true
    }

    fun paidAmount(): Money {
        val total = totals().total
        if (!type.tracksPayment) return Money.ZERO
        return if (fullyPaid) total else (MoneyParse.parse(received) ?: Money.ZERO).let { if (it.paise > total.paise) total else it }
    }

    fun save(onSaved: (Long) -> Unit) {
        if (saving) return
        val total = totals().total
        error = when {
            lines.isEmpty() -> "Add at least one item"
            dueDate?.isBefore(date) == true && type == DocType.SALE -> "The due date cannot be before the bill date"
            lines.firstNotNullOfOrNull { InvoiceRepository.lineProblem(it) } != null -> lines.firstNotNullOfOrNull { InvoiceRepository.lineProblem(it) }
            type.tracksPayment && party.isCash && paidAmount().paise < total.paise ->
                "Select a ${type.partyType.label.lowercase()} to keep a balance, or mark it fully paid"
            type == DocType.PURCHASE && number.isBlank() -> null
            else -> null
        }
        if (error != null) return
        saving = true
        viewModelScope.launch {
            try {
                // The Counter cannot edit its number: if another bill used the suggestion meanwhile, take the next free one
                if (invoiceId == 0L && type != DocType.PURCHASE && number == suggestedNumber && invoices.numberTaken(type, number, 0)) {
                    number = invoices.nextNumber(type)
                    suggestedNumber = number
                }
                if (number.isNotBlank() && number != originalNumber && type != DocType.PURCHASE && invoices.numberTaken(type, number, invoiceId)) {
                    error = "Number $number is already used"
                    saving = false
                    return@launch
                }
                val id = invoices.save(
                    InvoiceDraft(
                        id = invoiceId, type = type, number = number, date = date,
                        partyId = party.id, partyName = if (party.isCash) "" else party.name, partyPhone = party.phone,
                        partyGstin = party.gstin, partyAddress = party.address, partyStateCode = party.stateCode,
                        lines = lines, notes = notes, paidNow = paidAmount(), paymentMode = mode, convertedFromId = convertedFromId,
                        dueDate = dueDate?.takeIf { type == DocType.SALE },
                        customFields = customLabels.map { it to customValues[it].orEmpty().trim() }.filter { it.second.isNotEmpty() },
                        gstEnabled = editGst, roundOff = editRound,
                    ),
                )
                // Stays "saving" after success, so a second tap cannot save a copy; reset() or a new editor clears it
                onSaved(id)
            } catch (e: Exception) {
                error = e.message ?: "Could not save"
                saving = false
            }
        }
    }

    /** Resets for the next bill after "Save & new". */
    fun reset() {
        saving = false
        error = null
        date = LocalDate.now()
        editGst = null
        editRound = null
        lines = emptyList()
        notes = ""
        dueDate = null
        customValues = emptyMap()
        draftLabels = emptyList()
        received = ""
        party = PartyChoice()
        fullyPaid = true
        invoiceId = 0
        convertedFromId = null
        viewModelScope.launch { number = invoices.nextNumber(type); suggestedNumber = number; originalNumber = "" }
    }

    val partyTypeForDoc: PartyType get() = type.partyType

    /** Prints a saved bill on the configured Bluetooth printer (counter mode). */
    fun printReceipt(context: android.content.Context, id: Long, onResult: (String) -> Unit) {
        val b = business.value
        if (b.printerAddress.isBlank()) return
        viewModelScope.launch {
            val inv = invoices.get(id) ?: return@launch
            val result = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    online.draran.billing.core.print.ThermalReceipt(inv, b, branding.logo(b.logoFile)).escPos()
                }
            }.fold({ bytes -> online.draran.billing.core.print.BluetoothPrinter.print(context, b.printerAddress, bytes) }, { Result.failure(it) })
            onResult(result.fold({ "Receipt printed" }, { "Print failed: ${it.message}" }))
        }
    }

    suspend fun numberOf(id: Long): String = invoices.get(id)?.number.orEmpty()
}
