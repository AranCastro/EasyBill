package online.draran.billing.feature.billing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.data.BrandingManager
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.data.InvoiceRepository
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.Invoice
import online.draran.billing.core.print.BluetoothPrinter
import online.draran.billing.core.print.InvoicePdf
import online.draran.billing.core.print.Sharing
import online.draran.billing.core.print.ThermalReceipt
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    private val invoices: InvoiceRepository,
    businessRepository: BusinessRepository,
    private val branding: BrandingManager,
) : ViewModel() {

    private val id = MutableStateFlow(0L)
    val invoice: StateFlow<Invoice?> = id.flatMapLatest { if (it == 0L) flowOf(null) else invoices.invoice(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val business: StateFlow<Business> = businessRepository.business.stateIn(viewModelScope, SharingStarted.Eagerly, Business())

    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var convertedSaleId by mutableStateOf<Long?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    fun load(invoiceId: Long, context: Context) {
        if (id.value == invoiceId) return
        id.value = invoiceId
        viewModelScope.launch { convertedSaleId = invoices.convertedSale(invoiceId) }
        viewModelScope.launch {
            combine(invoice, business, branding.version) { inv, b, _ -> inv to b }.collect { (inv, b) ->
                if (inv != null) preview = withContext(Dispatchers.Default) { render(context, inv, b) }
            }
        }
    }

    private fun render(context: Context, inv: Invoice, b: Business): Bitmap {
        val scale = 1.6f
        val bitmap = Bitmap.createBitmap((595 * scale).toInt(), (842 * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        pdf(context, inv, b).drawPage(canvas, 0)
        return bitmap
    }

    private fun pdf(context: Context, inv: Invoice, b: Business) =
        InvoicePdf(context, inv, b, branding.logo(b.logoFile), branding.signature(b.signatureFile))

    private suspend fun pdfFile(context: Context): File? {
        val inv = invoice.value ?: return null
        val b = business.value
        return withContext(Dispatchers.IO) {
            File(Sharing.sharedDir(context), Sharing.safeName("${inv.type.shortTitle}_${inv.number}") + ".pdf").also { pdf(context, inv, b).writeTo(it) }
        }
    }

    fun shareText(): String {
        val inv = invoice.value ?: return ""
        val b = business.value
        val due = if (inv.balance.paise > 0) "\nBalance due: ${IndianFormat.rupees(inv.balance)}" else ""
        val upi = if (inv.balance.paise > 0 && b.upiId.isNotBlank()) "\nPay by UPI: ${b.upiId}" else ""
        val title = if (inv.type == online.draran.billing.core.model.DocType.SALE) (if (inv.gstEnabled) "Tax Invoice" else b.type.billTitle) else inv.type.title
        return "$title ${inv.number} from ${b.name}\nAmount: ${IndianFormat.rupees(inv.totals.total)}$due$upi\nThank you!"
    }

    fun share(context: Context, whatsApp: Boolean) {
        viewModelScope.launch {
            busy = true
            val file = pdfFile(context)
            busy = false
            if (file != null) Sharing.shareFile(context, file, "application/pdf", shareText(), whatsApp)
        }
    }

    fun printSystem(context: Context) {
        viewModelScope.launch {
            val file = pdfFile(context) ?: return@launch
            Sharing.printPdf(context, file, invoice.value?.number ?: "Bill")
        }
    }

    /** Returns false when no printer is set up. */
    fun printThermal(context: Context): Boolean {
        val inv = invoice.value ?: return true
        val b = business.value
        if (b.printerAddress.isBlank()) return false
        viewModelScope.launch {
            busy = true
            val result = BluetoothPrinter.print(context, b.printerAddress, ThermalReceipt(inv, b, branding.logo(b.logoFile)).escPos())
            busy = false
            message = result.fold({ "Sent to ${b.printerName.ifBlank { "printer" }}" }, { "Printing failed: ${it.message ?: "check the printer is on and paired"}" })
        }
        return true
    }

    fun delete(onDone: () -> Unit) {
        val inv = invoice.value ?: return
        viewModelScope.launch {
            invoices.delete(inv.id)
            onDone()
        }
    }
}
