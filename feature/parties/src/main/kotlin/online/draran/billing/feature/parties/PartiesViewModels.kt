package online.draran.billing.feature.parties

import online.draran.billing.core.common.userMessage
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.data.BrandingManager
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.data.PartyRepository
import online.draran.billing.core.designsystem.component.pretty
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.Gstin
import online.draran.billing.core.model.LedgerEntry
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.MoneyParse
import online.draran.billing.core.model.Party
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.PartyWithBalance
import online.draran.billing.core.model.abs
import online.draran.billing.core.print.PdfColumn
import online.draran.billing.core.print.Sharing
import online.draran.billing.core.print.TablePdf
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class PartiesUi(
    val parties: List<PartyWithBalance> = emptyList(),
    val toCollect: Money = Money.ZERO,
    val toPay: Money = Money.ZERO,
    val customerCount: Int = 0,
    val supplierCount: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class PartiesViewModel @Inject constructor(
    repository: PartyRepository,
    businessRepository: BusinessRepository,
) : ViewModel() {
    val business: StateFlow<Business> = businessRepository.business.stateIn(viewModelScope, SharingStarted.Eagerly, Business())
    val query = MutableStateFlow("")
    val type = MutableStateFlow(PartyType.CUSTOMER)

    val ui: StateFlow<PartiesUi> = combine(repository.parties, query, type) { all, q, t ->
        val needle = q.trim().lowercase()
        PartiesUi(
            parties = all.filter { it.party.type == t }
                .filter { needle.isEmpty() || it.party.name.lowercase().contains(needle) || it.party.phone.contains(needle) ||
                    // "9876543210" also finds a number saved as "98765 43210" or "+91 98765-43210"
                    needle.filter(Char::isDigit).let { d -> d.length >= 3 && it.party.phone.filter(Char::isDigit).contains(d) } },
            toCollect = Money(all.filter { it.balance.paise > 0 }.sumOf { it.balance.paise }),
            toPay = Money(-all.filter { it.balance.paise < 0 }.sumOf { it.balance.paise }),
            customerCount = all.count { it.party.type == PartyType.CUSTOMER },
            supplierCount = all.count { it.party.type == PartyType.SUPPLIER },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PartiesUi())
}

data class PartyForm(
    val name: String = "",
    val type: PartyType = PartyType.CUSTOMER,
    val phone: String = "",
    val gstin: String = "",
    val stateCode: String = "",
    val address: String = "",
    val opening: String = "",
    /** true = they owe us. */
    val openingReceivable: Boolean = true,
)

@HiltViewModel
class PartyEditorViewModel @Inject constructor(
    private val repository: PartyRepository,
    private val businessRepository: BusinessRepository,
) : ViewModel() {
    var form by mutableStateOf(PartyForm())
        private set
    var showErrors by mutableStateOf(false)
        private set
    var canDelete by mutableStateOf(false)
        private set
    private var partyId = -1L

    val business: StateFlow<Business> = businessRepository.business.stateIn(viewModelScope, SharingStarted.Eagerly, Business())

    fun load(id: Long, type: PartyType, prefillName: String) {
        if (partyId == id) return
        partyId = id
        viewModelScope.launch {
            if (id == 0L) {
                form = PartyForm(type = type, name = prefillName, openingReceivable = type == PartyType.CUSTOMER)
                return@launch
            }
            val p = repository.get(id) ?: return@launch
            canDelete = repository.canDelete(id)
            form = PartyForm(
                name = p.name, type = p.type, phone = p.phone, gstin = p.gstin, stateCode = p.stateCode, address = p.address,
                opening = if (p.openingBalance.isZero) "" else MoneyParse.toInput(p.openingBalance.abs()),
                openingReceivable = p.openingBalance.paise >= 0,
            )
        }
    }

    fun update(transform: (PartyForm) -> PartyForm) {
        val next = transform(form)
        form = if (next.gstin != form.gstin && Gstin.isValid(next.gstin)) next.copy(stateCode = Gstin.stateCode(next.gstin).orEmpty()) else next
    }

    val nameError get() = if (form.name.isBlank()) "Enter a name" else null
    val gstinError get() = if (form.gstin.isNotBlank() && !Gstin.isValid(form.gstin)) "This GSTIN is not valid. Check for typing mistakes." else null
    val phoneError: String? get() = when {
        form.phone.isBlank() -> null
        form.phone.any { !(it.isDigit() || it in " +-()") } -> "Use digits only"
        form.phone.count { it.isDigit() } !in 10..12 -> "Enter a 10-digit mobile number"
        else -> null
    }

    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    fun save(onSaved: (Long) -> Unit) {
        showErrors = true
        if (nameError != null || gstinError != null || phoneError != null) return
        if (saving) return // a double tap must not create the party twice
        saving = true
        viewModelScope.launch {
            try {
                val amount = MoneyParse.parse(form.opening) ?: Money.ZERO
                val id = repository.save(
                    Party(
                        id = partyId.coerceAtLeast(0), name = form.name, type = form.type, phone = form.phone, gstin = form.gstin,
                        stateCode = form.stateCode, address = form.address,
                        openingBalance = if (form.openingReceivable) amount else -amount,
                    ),
                )
                onSaved(id)
            } catch (e: Exception) {
                saveError = e.userMessage("Could not save. Try again.")
                saving = false
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { if (repository.delete(partyId)) onDone() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PartyDetailViewModel @Inject constructor(
    private val repository: PartyRepository,
    businessRepository: BusinessRepository,
    private val branding: BrandingManager,
) : ViewModel() {
    private val id = MutableStateFlow(0L)
    val party: StateFlow<PartyWithBalance?> = id.flatMapLatest { if (it == 0L) flowOf(null) else repository.party(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ledger: StateFlow<List<LedgerEntry>> = id.flatMapLatest { if (it == 0L) emptyFlow() else repository.ledger(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val business: StateFlow<Business> = businessRepository.business.stateIn(viewModelScope, SharingStarted.Eagerly, Business())

    fun load(partyId: Long) {
        id.value = partyId
    }

    /** Shown once as a snackbar when sharing fails. */
    var message by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun reminderText(): String {
        val p = party.value ?: return ""
        val b = business.value
        val amount = IndianFormat.rupees(p.balance.abs())
        val upi = if (b.upiId.isNotBlank()) "\nPay by UPI: ${b.upiId}" else ""
        return "Dear ${p.party.name},\nA payment of $amount is pending with ${b.name}. Please pay at your convenience.$upi\nThank you."
    }

    fun shareStatement(context: Context) {
        val p = party.value ?: return
        val rows = ledger.value
        val b = business.value
        viewModelScope.launch {
            try {
            val file = withContext(Dispatchers.IO) {
                // The id keeps two parties with the same (or non-Latin) name from sharing one file name
                val f = File(Sharing.sharedDir(context), "Statement_${Sharing.safeName(p.party.name)}_${p.party.id}.pdf")
                TablePdf(
                    context = context,
                    businessName = b.name,
                    businessInfo = listOf(b.address.replace("\n", ", "), b.phone, if (b.gstEnabled) "GSTIN ${b.gstin}" else "").filter { it.isNotBlank() }.joinToString(" · "),
                    title = "Statement of Account",
                    subtitle = "As on ${LocalDate.now().pretty()}",
                    columns = listOf(PdfColumn("Date", 1.1f), PdfColumn("Particulars", 2.4f), PdfColumn("Debit", 1.2f, true), PdfColumn("Credit", 1.2f, true), PdfColumn("Balance", 1.3f, true)),
                    // The party's name has a line of its own, so a long name cannot push the date out of the corner
                    rows = listOf(listOf(TablePdf.HEADING + p.party.name)) + rows.map { e ->
                        listOf(
                            e.date.pretty(),
                            kindLabel(e.kind) + if (e.kind != "OPENING") " ${e.number}" else "",
                            if (e.amount.paise > 0) IndianFormat.rupees(e.amount) else "",
                            if (e.amount.paise < 0) IndianFormat.rupees(e.amount.abs()) else "",
                            balanceLabel(e.balance),
                        )
                    },
                    summary = listOf("Closing balance" to balanceLabel(p.balance)),
                    logo = branding.logo(b.logoFile),
                    brand = b.accent(),
                ).writeTo(f)
                f
            }
            Sharing.shareFile(context, file, "application/pdf", "Statement from ${b.name}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "The statement could not be created. Try again."
            }
        }
    }

    companion object {
        fun kindLabel(kind: String): String = when (kind) {
            "OPENING" -> "Opening balance"
            "PAYMENT_IN" -> "Payment in"
            "PAYMENT_OUT" -> "Payment out"
            else -> DocType.entries.firstOrNull { it.name == kind }?.shortTitle ?: kind
        }

        fun balanceLabel(balance: Money) = when {
            balance.paise > 0 -> IndianFormat.rupees(balance) + " Dr"
            balance.paise < 0 -> IndianFormat.rupees(balance.abs()) + " Cr"
            else -> IndianFormat.rupees(balance)
        }
    }
}
