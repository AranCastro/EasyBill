package online.draran.billing.feature.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.Gstin
import online.draran.billing.core.model.IndianStates
import javax.inject.Inject

data class BusinessForm(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val stateCode: String = "33",
    val gstEnabled: Boolean = false,
    val gstin: String = "",
    val upiId: String = "",
    val ownerName: String = "",
)

@HiltViewModel
class BusinessFormViewModel @Inject constructor(
    private val repository: BusinessRepository,
) : ViewModel() {

    var form by mutableStateOf(BusinessForm())
        private set
    var loaded by mutableStateOf(false)
        private set
    var showErrors by mutableStateOf(false)
        private set
    private var original: Business = Business()

    init {
        viewModelScope.launch {
            original = repository.get()
            form = BusinessForm(
                name = original.name, phone = original.phone, email = original.email, address = original.address,
                stateCode = original.stateCode, gstEnabled = original.gstEnabled, gstin = original.gstin,
                upiId = original.upiId, ownerName = original.ownerName,
            )
            loaded = true
        }
    }

    fun update(transform: (BusinessForm) -> BusinessForm) {
        val next = transform(form)
        // A valid GSTIN tells us the state
        form = if (next.gstin != form.gstin && Gstin.isValid(next.gstin)) {
            next.copy(stateCode = Gstin.stateCode(next.gstin) ?: next.stateCode)
        } else {
            next
        }
    }

    val nameError: String? get() = if (form.name.isBlank()) "Enter your shop or business name" else null
    val phoneError: String? get() = form.phone.filter { it.isDigit() }.let {
        if (form.phone.isNotBlank() && it.length !in 10..12) "Enter a 10-digit mobile number" else null
    }
    val gstinError: String? get() = when {
        !form.gstEnabled -> null
        form.gstin.isBlank() -> "Enter your 15-character GSTIN"
        !Gstin.isValid(form.gstin) -> "This GSTIN is not valid. Check for typing mistakes."
        Gstin.stateCode(form.gstin) != form.stateCode -> "GSTIN belongs to ${IndianStates.nameOf(Gstin.stateCode(form.gstin))}"
        else -> null
    }
    val upiError: String? get() = if (form.upiId.isNotBlank() && !form.upiId.trim().matches(Regex("^[A-Za-z0-9.\\-_]{2,}@[A-Za-z][A-Za-z0-9.\\-]+$"))) "UPI ID looks like name@bank" else null

    fun save(onDone: () -> Unit) {
        showErrors = true
        if (nameError != null || phoneError != null || gstinError != null || upiError != null) return
        viewModelScope.launch {
            repository.save(
                original.copy(
                    name = form.name, phone = form.phone, email = form.email, address = form.address,
                    stateCode = form.stateCode, gstEnabled = form.gstEnabled, gstin = if (form.gstEnabled) form.gstin else "",
                    upiId = form.upiId, ownerName = form.ownerName, onboarded = true,
                ),
            )
            onDone()
        }
    }
}
