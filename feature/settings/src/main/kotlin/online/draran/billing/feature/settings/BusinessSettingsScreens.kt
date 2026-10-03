package online.draran.billing.feature.settings

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import online.draran.billing.core.data.BackupManager
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.ConfirmDialog
import online.draran.billing.core.designsystem.component.FormField
import online.draran.billing.core.designsystem.component.SectionCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.Business
import online.draran.billing.core.model.DocType
import online.draran.billing.core.print.BluetoothPrinter
import online.draran.billing.core.print.PairedPrinter
import online.draran.billing.core.print.ThermalReceipt
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BusinessSettingsViewModel @Inject constructor(
    private val repository: BusinessRepository,
    val backup: BackupManager,
) : ViewModel() {
    var business by mutableStateOf<Business?>(null)
        private set

    init {
        viewModelScope.launch { business = repository.get() }
    }

    fun update(transform: (Business) -> Business) {
        business = business?.let(transform)
    }

    fun save(onDone: () -> Unit = {}) {
        val b = business ?: return
        viewModelScope.launch { repository.save(b); onDone() }
    }
}

@Composable
fun InvoiceSettingsRoute(onBack: () -> Unit, viewModel: BusinessSettingsViewModel = hiltViewModel()) {
    val b = viewModel.business ?: return
    Scaffold(
        topBar = { AppTopBar("Invoice settings", onBack = onBack) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionCard(title = "Bill numbering") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Prefix for each type. Numbers continue automatically, e.g. INV-0001.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(DocType.SALE, DocType.ESTIMATE, DocType.SALE_RETURN, DocType.PURCHASE_RETURN).forEach { type ->
                        FormField(
                            b.prefix(type), { v -> viewModel.update { it.copy(prefixes = it.prefixes + (type to v.uppercase().take(8))) } },
                            type.title, capitalization = KeyboardCapitalization.Characters,
                        )
                    }
                }
            }
            SectionCard(
                title = "Bill fields",
                action = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.update { it.copy(customFieldLabels = it.type.customFields) } }) { Text("Reset") }
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Up to four extra fields filled on each bill and printed in its Details box, e.g. Roll no., Stylist, Vehicle no. Leave blank to hide. Reset uses the defaults for ${b.type.label}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val slots = (b.customFieldLabels + List(MAX_BILL_FIELDS) { "" }).take(MAX_BILL_FIELDS)
                    slots.forEachIndexed { index, label ->
                        FormField(
                            label,
                            { v -> viewModel.update { it.copy(customFieldLabels = slots.toMutableList().also { list -> list[index] = v.take(30) }) } },
                            "Field ${index + 1}",
                            capitalization = KeyboardCapitalization.Sentences,
                        )
                    }
                }
            }
            SectionCard(title = "Calculation") {
                Column {
                    ToggleRow("Round off totals", "Round the bill total to the nearest rupee", b.roundOff) { v -> viewModel.update { it.copy(roundOff = v) } }
                    if (b.gstEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ToggleRow("New items: price includes GST", "Default for items you add from now", b.pricesIncludeTax) { v -> viewModel.update { it.copy(pricesIncludeTax = v) } }
                    }
                }
            }
            SectionCard(title = "Printed on the bill") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ToggleRow("UPI payment QR", if (b.upiId.isBlank()) "Add your UPI ID in Business profile first" else "QR for ${b.upiId} with the amount due", b.showUpiQr && b.upiId.isNotBlank()) { v -> viewModel.update { it.copy(showUpiQr = v) } }
                    FormField(b.bankDetails, { v -> viewModel.update { it.copy(bankDetails = v) } }, "Bank details (optional)", singleLine = false, minLines = 2, placeholder = "Bank name · A/c no. · IFSC")
                    FormField(b.terms, { v -> viewModel.update { it.copy(terms = v) } }, "Terms / thank-you note", singleLine = false, minLines = 2)
                }
            }
        }
    }
}

private const val MAX_BILL_FIELDS = 4

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun PrinterSettingsRoute(onBack: () -> Unit, viewModel: BusinessSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val b = viewModel.business ?: return
    val snackbar = remember { SnackbarHostState() }
    var printers by remember { mutableStateOf(BluetoothPrinter.pairedDevices(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        printers = BluetoothPrinter.pairedDevices(context)
    }
    LaunchedEffect(Unit) { if (!BluetoothPrinter.hasPermission(context)) permission.launch(BluetoothPrinter.permissions) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); message = null } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar("Printer", onBack = onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionCard(title = "Paper width") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(58, 80).forEachIndexed { i, mm ->
                        SegmentedButton(b.thermalWidthMm == mm, { viewModel.update { it.copy(thermalWidthMm = mm) }; viewModel.save() }, SegmentedButtonDefaults.itemShape(i, 2)) { Text("$mm mm") }
                    }
                }
            }
            SectionCard(title = "Receipt") {
                ToggleRow(
                    "Print logo on receipts",
                    if (b.logoFile.isBlank()) "Add a logo in Business profile first" else "Printed in black and white at the top",
                    b.printLogoOnReceipt && b.logoFile.isNotBlank(),
                ) { v -> viewModel.update { it.copy(printLogoOnReceipt = v) }; viewModel.save() }
            }
            SectionCard(title = "Bluetooth thermal printer") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Pair the printer in your phone's Bluetooth settings first (PIN is usually 0000 or 1234). Then choose it here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!BluetoothPrinter.hasPermission(context)) {
                        OutlinedButton(onClick = { permission.launch(BluetoothPrinter.permissions) }) { Text("Allow Nearby devices") }
                    } else if (printers.isEmpty()) {
                        Text("No paired devices found. Turn on Bluetooth and pair your printer.", style = MaterialTheme.typography.bodyMedium)
                    }
                    printers.forEach { p: PairedPrinter ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = b.printerAddress == p.address, onClick = {
                                viewModel.update { it.copy(printerAddress = p.address, printerName = p.name) }
                                viewModel.save()
                            })
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                Text(p.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(onClick = { printers = BluetoothPrinter.pairedDevices(context) }) {
                            Icon(AppIcons.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Refresh")
                        }
                        Button(enabled = b.printerAddress.isNotBlank(), onClick = {
                            scope.launch {
                                val sample = ThermalReceipt.testPage(b)
                                val r = BluetoothPrinter.print(context, b.printerAddress, sample)
                                message = r.fold({ "Test page sent" }, { "Could not print: ${it.message}" })
                            }
                        }) { Text("Print test page") }
                    }
                }
            }
            SectionCard(title = "Other printers") {
                Text("Wi-Fi and office printers work through the Print button on any bill (uses Android printing). You can also choose Save as PDF there.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BackupRoute(onBack: () -> Unit, viewModel: BusinessSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val backup = viewModel.backup
    val last by backup.lastBackup.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<(() -> Unit)?>(null) }
    var autoFiles by remember { mutableStateOf(backup.autoBackups()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ext = BillingTheme.extendedColors

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            message = runCatching { backup.exportTo(uri) }.fold({ "Backup saved" }, { "Backup failed: ${it.message}" })
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = {
            scope.launch {
                runCatching { backup.importFrom(uri) }.fold({ restartApp(context) }, { message = "Restore failed: ${it.message}" })
            }
        }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); message = null } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { AppTopBar("Backup & restore", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (last != null) AppIcons.CloudCheck else AppIcons.CloudSlash, null, tint = if (last != null) ext.received else ext.due)
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text(if (last != null) "Last backup" else "No backup yet", style = MaterialTheme.typography.titleSmall)
                        Text(last?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "Your data lives only on this phone. Back it up regularly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SectionCard(title = "Back up") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Save a backup file anywhere: choose Google Drive in the picker to keep it in the cloud for free, or save to Downloads and copy it to a pen drive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { exportLauncher.launch(backup.suggestedFileName()) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(AppIcons.Upload, null); Spacer(Modifier.width(8.dp)); Text("Back up now")
                    }
                }
            }
            SectionCard(title = "Restore") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Replaces all data on this phone with the backup. Use this on a new phone too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(AppIcons.Download, null); Spacer(Modifier.width(8.dp)); Text("Restore from file")
                    }
                }
            }
            SectionCard(title = "Automatic daily copies") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("A copy is saved inside the app once a day (last ${BackupManager.KEEP} kept). It protects against mistakes, not against losing the phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (autoFiles.isEmpty()) Text("No automatic copies yet.", style = MaterialTheme.typography.bodyMedium)
                    autoFiles.forEach { f: File ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(DateUtils.formatDateTime(context, f.lastModified(), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME), style = MaterialTheme.typography.bodyMedium)
                                Text("${f.length() / 1024} KB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = {
                                pendingRestore = { scope.launch { runCatching { backup.restoreAuto(f) }.fold({ restartApp(context) }, { message = "Restore failed: ${it.message}" }) } }
                            }) { Text("Restore") }
                        }
                    }
                    OutlinedButton(onClick = { scope.launch { backup.saveCopy(); autoFiles = backup.autoBackups(); message = "Copy saved" } }) { Text("Save a copy now") }
                }
            }
        }
    }
    pendingRestore?.let { action ->
        ConfirmDialog(
            "Restore this backup?",
            "All current bills, items and parties on this phone will be replaced. The app will restart.",
            "Restore",
            onConfirm = action,
            onDismiss = { pendingRestore = null },
            destructive = true,
        )
    }
}

/** Relaunches the app so the restored database is opened fresh. */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
