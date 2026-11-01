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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import online.draran.billing.core.common.userMessage
import online.draran.billing.core.data.CashBankRepository
import online.draran.billing.core.model.CashBalances
import online.draran.billing.core.model.CashBook
import online.draran.billing.core.model.CashBookLine
import online.draran.billing.core.model.CashMove
import online.draran.billing.core.model.CashMoveKind
import online.draran.billing.core.model.CashMovement
import online.draran.billing.core.model.CashSetup
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.MoneyAccount
import online.draran.billing.core.model.MoneyParse
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CashBankViewModel @Inject constructor(repository: CashBankRepository) : ViewModel() {
    val account = MutableStateFlow(MoneyAccount.CASH)

    val balances: StateFlow<CashBalances> = repository.balances
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CashBalances(null, Money.ZERO, Money.ZERO))

    /** The cash book of the chosen account, newest first. */
    val lines: StateFlow<List<CashBookLine>> = combine(repository.setup, repository.movements, account) { s, m, a ->
        CashBook.lines(s, m, a).asReversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class CashSetupViewModel @Inject constructor(private val repository: CashBankRepository) : ViewModel() {
    var date by mutableStateOf(LocalDate.now())
    var cash by mutableStateOf("")
    var bank by mutableStateOf("")
    var editing by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val s = repository.setupNow() ?: return@launch
            editing = true
            date = s.startDate
            cash = MoneyParse.toInput(s.cash)
            bank = MoneyParse.toInput(s.bank)
        }
    }

    val isDirty: Boolean get() = !editing && !saving && (cash.isNotBlank() || bank.isNotBlank())

    fun save(onDone: () -> Unit) {
        if (saving) return
        saving = true
        saveError = null
        viewModelScope.launch {
            try {
                repository.saveSetup(CashSetup(date, MoneyParse.parse(cash) ?: Money.ZERO, MoneyParse.parse(bank) ?: Money.ZERO))
                onDone()
            } catch (e: Exception) {
                saveError = e.userMessage("Could not save. Try again.")
                saving = false
            }
        }
    }
}

@HiltViewModel
class CashMoveViewModel @Inject constructor(private val repository: CashBankRepository) : ViewModel() {
    var moveId by mutableStateOf(0L)
        private set
    var kind by mutableStateOf(CashMoveKind.DEPOSIT)
    var amount by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var note by mutableStateOf("")

    /** A saved cash count: shown, and can be deleted, but not edited. */
    var count by mutableStateOf<CashMove?>(null)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    private var loaded = false

    fun load(id: Long, startKind: CashMoveKind?) {
        if (loaded) return
        loaded = true
        startKind?.takeIf { it != CashMoveKind.COUNT }?.let { kind = it }
        if (id == 0L) return
        viewModelScope.launch {
            val m = repository.getMove(id) ?: return@launch
            moveId = m.id; date = m.date; note = m.note
            if (m.kind == CashMoveKind.COUNT) count = m else { kind = m.kind; amount = MoneyParse.toInput(m.amount) }
        }
    }

    val amountError: String? get() = if ((MoneyParse.parse(amount)?.paise ?: 0) <= 0) "Enter the amount" else null
    val isDirty: Boolean get() = moveId == 0L && !saving && (amount.isNotBlank() || note.isNotBlank())

    fun save(onDone: () -> Unit) {
        showErrors = true
        if (amountError != null || saving) return
        saving = true
        saveError = null
        viewModelScope.launch {
            try {
                repository.saveMove(CashMove(moveId, kind, date, MoneyParse.parse(amount) ?: Money.ZERO, note = note))
                onDone()
            } catch (e: Exception) {
                saveError = e.userMessage("Could not save. Try again.")
                saving = false
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteMove(moveId); onDone()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = e.userMessage("The entry could not be deleted. Try again.")
            }
        }
    }
}

@HiltViewModel
class CashCountViewModel @Inject constructor(private val repository: CashBankRepository) : ViewModel() {
    var counted by mutableStateOf("")
    var note by mutableStateOf("")
    val date: LocalDate = LocalDate.now()

    /** What the entries say should be in the drawer today. */
    val expected: StateFlow<Money?> = combine(repository.setup, repository.movements) { s, m ->
        CashBook.balanceAsOf(s, m, MoneyAccount.CASH, date)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    val countedMoney: Money? get() = MoneyParse.parse(counted)
    val countedError: String? get() = if (counted.isBlank()) "Enter the cash you counted" else null
    val isDirty: Boolean get() = !saving && counted.isNotBlank()

    fun save(onDone: () -> Unit) {
        showErrors = true
        if (countedError != null || saving) return
        saving = true
        saveError = null
        viewModelScope.launch {
            try {
                repository.countCash(date, countedMoney ?: Money.ZERO, note)
                onDone()
            } catch (e: Exception) {
                saveError = e.userMessage("Could not save. Try again.")
                saving = false
            }
        }
    }
}

/** Where a cash book line came from, so the screen can open it. */
fun CashMovement.describe(): String = listOfNotNull(detail.takeIf { it.isNotBlank() }, mode?.label).joinToString(" · ")
