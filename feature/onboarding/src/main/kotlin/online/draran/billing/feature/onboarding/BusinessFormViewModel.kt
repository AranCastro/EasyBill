package online.draran.billing.feature.onboarding

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.draran.billing.core.data.BrandingManager
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.data.ItemRepository
import online.draran.billing.core.designsystem.component.SignatureStrokes
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.BusinessType
import online.draran.billing.core.model.Gstin
import online.draran.billing.core.model.IndianStates
import online.draran.billing.core.model.MsmeCategory
import online.draran.billing.core.model.Udyam
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
    /** Optional MSME (Udyam) registration. */
    val udyamNumber: String = "",
    val msmeCategory: MsmeCategory? = null,
    val ownerName: String = "",
    val type: BusinessType = BusinessType.RETAIL,
    val signatoryName: String = "",
    val signatoryDesignation: String = "",
    /** Bill accent colour (ARGB), 0 = default. */
    val billColor: Int = 0,
    /** Load the type's starter services into the catalogue on save. */
    val addStarter: Boolean = true,
)

@HiltViewModel
class BusinessFormViewModel @Inject constructor(
    private val repository: BusinessRepository,
    private val items: ItemRepository,
    private val branding: BrandingManager,
) : ViewModel() {

    var form by mutableStateOf(BusinessForm())
        private set
    var loaded by mutableStateOf(false)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var logo by mutableStateOf<ImageBitmap?>(null)
        private set
    var signature by mutableStateOf<ImageBitmap?>(null)
        private set
    /** Colours found in the logo, offered first in the bill colour picker. */
    var logoColours by mutableStateOf<List<Int>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
    var working by mutableStateOf(false)
        private set
    private var original: Business = Business()

    init {
        viewModelScope.launch {
            original = repository.get()
            form = BusinessForm(
                name = original.name, phone = original.phone, email = original.email, address = original.address,
                stateCode = original.stateCode, gstEnabled = original.gstEnabled, gstin = original.gstin,
                upiId = original.upiId, ownerName = original.ownerName, type = original.type,
                udyamNumber = original.udyamNumber, msmeCategory = original.msmeCategory,
                billColor = original.billColor,
                signatoryName = original.signatoryName, signatoryDesignation = original.signatoryDesignation,
                addStarter = !original.onboarded,
            )
            loaded = true
        }
        // Reload the previews whenever the logo or signature changes
        viewModelScope.launch { branding.version.collect { reloadImages() } }
    }

    /** True when saving will (re)apply the type's defaults: first setup, or the type was changed. */
    val typeChanged: Boolean get() = !original.onboarded || form.type != original.type

    fun update(transform: (BusinessForm) -> BusinessForm) {
        val next = transform(form)
        // A valid GSTIN tells us the state
        form = if (next.gstin != form.gstin && Gstin.isValid(next.gstin)) {
            next.copy(stateCode = Gstin.stateCode(next.gstin) ?: next.stateCode)
        } else {
            next
        }
    }

    fun selectType(type: BusinessType) = update {
        it.copy(type = type, addStarter = type.presets.isNotEmpty() && (type != original.type || !original.onboarded))
    }

    private suspend fun reloadImages() {
        val b = repository.get()
        logo = withContext(Dispatchers.IO) { branding.logo(b.logoFile)?.asImageBitmap() }
        logoColours = withContext(Dispatchers.Default) { branding.logoColours(b.logoFile) }
        // A new logo may have set the bill colour; show it unless the user already picked one here
        if (loaded && form.billColor == 0 && b.billColor != 0) update { it.copy(billColor = b.billColor) }
        signature = withContext(Dispatchers.IO) { branding.signature(b.signatureFile)?.asImageBitmap() }
    }

    private fun branding(action: suspend () -> Unit) {
        viewModelScope.launch {
            working = true
            runCatching { action() }.onFailure { message = it.message ?: "Could not use this image" }
            working = false
        }
    }

    fun setLogo(uri: Uri) = branding { branding.setLogo(uri) }
    fun removeLogo() = branding { branding.removeLogo() }
    fun setSignaturePhoto(uri: Uri) = branding { branding.setSignaturePhoto(uri) }
    fun removeSignature() = branding { branding.removeSignature() }
    fun setSignatureDrawn(drawn: SignatureStrokes) = branding {
        val bitmap = withContext(Dispatchers.Default) {
            BrandingManager.drawStrokes(drawn.strokes.map { s -> s.map { it.x to it.y } }, drawn.size.width, drawn.size.height, drawn.strokeWidthPx)
        }
        branding.setSignature(bitmap)
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

    val udyamError: String? get() = when {
        form.udyamNumber.isBlank() -> null
        !Udyam.isValid(form.udyamNumber) -> "Udyam number looks like UDYAM-TN-02-0012345"
        form.msmeCategory == null -> "Choose Micro, Small or Medium (as on your Udyam certificate)"
        else -> null
    }

    var saving by mutableStateOf(false)
        private set

    fun save(onDone: () -> Unit) {
        showErrors = true
        if (nameError != null || phoneError != null || gstinError != null || upiError != null || udyamError != null) return
        if (saving || working) return // a double tap would start the app twice; wait for an image still being processed
        saving = true
        viewModelScope.launch {
            try {
            val applyType = typeChanged
            // Starter services first: saving onboarded = true swaps the screen and ends this scope
            if (applyType && form.addStarter && form.type.presets.isNotEmpty()) {
                withContext(NonCancellable) { items.addPresets(form.type, form.gstEnabled) }
            }
            // Re-read: the logo and signature are saved straight away by BrandingManager
            val current = repository.get()
            repository.save(
                current.copy(
                    name = form.name.trim(), phone = form.phone, email = form.email, address = form.address,
                    stateCode = form.stateCode, gstEnabled = form.gstEnabled, gstin = if (form.gstEnabled) form.gstin else "",
                    upiId = form.upiId, ownerName = form.ownerName, onboarded = true,
                    udyamNumber = Udyam.normalise(form.udyamNumber),
                    msmeCategory = form.msmeCategory.takeIf { form.udyamNumber.isNotBlank() },
                    type = form.type,
                    signatoryName = form.signatoryName.trim(), signatoryDesignation = form.signatoryDesignation.trim(),
                    billColor = form.billColor,
                    customFieldLabels = if (applyType) form.type.customFields else current.customFieldLabels,
                ),
            )
            onDone()
            } catch (e: Exception) {
                message = e.message ?: "Could not save"
                saving = false
            }
        }
    }
}
