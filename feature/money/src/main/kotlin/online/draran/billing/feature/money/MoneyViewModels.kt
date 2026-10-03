package online.draran.billing.feature.money

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.draran.billing.core.data.ExpenseRepository
import online.draran.billing.core.data.PartyRepository
import online.draran.billing.core.data.PaymentRepository
import online.draran.billing.core.model.EXPENSE_CATEGORIES
import online.draran.billing.core.model.Expense
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.MoneyParse
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.PartyWithBalance
import online.draran.billing.core.model.Payment
import online.draran.billing.core.model.PaymentDirection
import online.draran.billing.core.model.PaymentMode
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PaymentsViewModel @Inject constructor(repository: PaymentRepository) : ViewModel() {
    val direction = MutableStateFlow(PaymentDirection.IN)
    val payments: StateFlow<List<Payment>> = combine(repository.payments, direction) { all, d -> all.filter { it.direction == d } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class PaymentEditorViewModel @Inject constructor(
    private val repository: PaymentRepository,
    private val parties: PartyRepository,
) : ViewModel() {
    val allParties: StateFlow<List<PartyWithBalance>> = parties.parties.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var direction by mutableStateOf(PaymentDirection.IN)
        private set
    var paymentId by mutableStateOf(0L)
        private set
    var number by mutableStateOf("")
        private set
    var partyId by mutableStateOf<Long?>(null)
    var amount by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var mode by mutableStateOf(PaymentMode.CASH)
    var reference by mutableStateOf("")
    var note by mutableStateOf("")
    var showErrors by mutableStateOf(false)
        private set
    var linkedToBill by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(dir: PaymentDirection, id: Long, party: Long) {
        if (loaded) return
        loaded = true
        direction = dir
        viewModelScope.launch {
            if (id != 0L) {
                val p = repository.get(id) ?: return@launch
                paymentId = p.id; direction = p.direction; number = p.number; partyId = p.partyId
                amount = MoneyParse.toInput(p.amount); date = p.date; mode = p.mode; reference = p.reference; note = p.note
                linkedToBill = p.invoiceId != null
            } else {
                number = repository.nextNumber(dir)
                if (party != 0L) {
                    partyId = party
                    // Suggest the outstanding amount
                    val bal = parties.parties.first().firstOrNull { it.party.id == party }?.balance
                    if (bal != null) {
                        val due = if (dir == PaymentDirection.IN) bal.paise else -bal.paise
                        if (due > 0) amount = MoneyParse.toInput(Money(due))
                    }
                }
            }
        }
    }

    val partyType: PartyType get() = if (direction == PaymentDirection.IN) PartyType.CUSTOMER else PartyType.SUPPLIER
    val amountError: String? get() = if ((MoneyParse.parse(amount)?.paise ?: 0) <= 0) "Enter the amount" else null
    val partyError: String? get() = if (partyId == null) "Choose a ${partyType.label.lowercase()}" else null

    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    fun save(onDone: () -> Unit) {
        showErrors = true
        if (amountError != null || partyError != null) return
        if (saving) return // a double tap must not record the payment twice
        saving = true
        viewModelScope.launch {
            try {
                val party = allParties.value.firstOrNull { it.party.id == partyId }?.party
                repository.save(
                    Payment(
                        id = paymentId, direction = direction, number = number, partyId = partyId, partyName = party?.name.orEmpty(),
                        date = date, amount = MoneyParse.parse(amount) ?: Money.ZERO, mode = mode, reference = reference, note = note,
                    ),
                )
                onDone() // stays "saving": the screen is closing
            } catch (e: Exception) {
                saveError = e.message ?: "Could not save"
                saving = false
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { repository.delete(paymentId); onDone() }
    }
}

@HiltViewModel
class ExpensesViewModel @Inject constructor(repository: ExpenseRepository) : ViewModel() {
    val expenses: StateFlow<List<Expense>> = repository.expenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class ExpenseEditorViewModel @Inject constructor(private val repository: ExpenseRepository) : ViewModel() {
    var expenseId by mutableStateOf(0L)
        private set
    var category by mutableStateOf(EXPENSE_CATEGORIES.first())
    var amount by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var mode by mutableStateOf(PaymentMode.CASH)
    var note by mutableStateOf("")
    var showErrors by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(id: Long) {
        if (loaded) return
        loaded = true
        if (id == 0L) return
        viewModelScope.launch {
            val e = repository.get(id) ?: return@launch
            expenseId = e.id; category = e.category; amount = MoneyParse.toInput(e.amount); date = e.date; mode = e.mode; note = e.note
        }
    }

    val amountError: String? get() = if ((MoneyParse.parse(amount)?.paise ?: 0) <= 0) "Enter the amount" else null

    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    fun save(onDone: () -> Unit) {
        showErrors = true
        if (amountError != null) return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                repository.save(Expense(expenseId, category, date, MoneyParse.parse(amount) ?: Money.ZERO, mode, note))
                onDone()
            } catch (e: Exception) {
                saveError = e.message ?: "Could not save"
                saving = false
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { repository.delete(expenseId); onDone() }
    }
}
