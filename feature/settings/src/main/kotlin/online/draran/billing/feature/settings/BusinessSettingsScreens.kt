package online.draran.billing.feature.settings

import androidx.compose.foundation.layout.heightIn
import online.draran.billing.core.common.userMessage
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val app: Context,
) : ViewModel() {
    /** True while a backup or restore runs: the buttons are off and Back is blocked. */
    var backupBusy by mutableStateOf(false)
        private set

    /** Shown once as a snackbar. */
    var backupMessage by mutableStateOf<String?>(null)

    fun exportBackup(uri: android.net.Uri) = backupTask { backup.exportTo(uri); "Backup saved" }
    fun saveCopy() = backupTask { backup.saveCopy(); "Copy saved" }
    fun importBackup(uri: android.net.Uri) = restoreTask { backup.importFrom(uri) }
    fun restoreCopy(file: File) = restoreTask { backup.restoreAuto(file) }

    // Run in the view model and shielded from cancellation: leaving the screen, turning the phone or pressing Back
    // must never cut a backup or a restore in half.
    private fun backupTask(task: suspend () -> String) {
        if (backupBusy) return
        backupBusy = true
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runCatching { task() } }
            backupMessage = result.fold({ it }, { "The backup could not be saved. ${it.userMessage("Check the free space on your phone and try again.")}" })
            backupBusy = false
        }
    }

    private fun restoreTask(task: suspend () -> Unit) {
        if (backupBusy) return
        backupBusy = true
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runCatching { task() } }
            val failure = result.exceptionOrNull()
            if (failure == null || (failure as? online.draran.billing.core.data.RestoreFailedException)?.needsRestart == true) {
                // The running database was replaced (or closed): the app starts again on the new data
                restartApp(app)
            } else {
                backupMessage = "The backup could not be restored. " + (failure.cause ?: failure).userMessage("Check the free space on your phone and try again.")
                backupBusy = false
            }
        }
    }

    var business by mutableStateOf<Business?>(null)
        private set

    init {
        viewModelScope.launch { business = repository.get() }
    }

    fun update(transform: (Business) -> Business) {
        business = business?.let(transform)
    }

    private var leaving = false

    /** [onDone] runs once: a double tap on Save must not pop two screens. */
    fun save(onDone: (() -> Unit)? = null) {
        val b = business ?: return
        if (onDone != null) {
            if (leaving) return
            leaving = true
        }
        viewModelScope.launch {
            try {
                repository.save(b)
                onDone?.invoke()
            } catch (e: Exception) {
                leaving = false
            }
        }
    }
}

private val numberedTypes = listOf(DocType.SALE, DocType.ESTIMATE, DocType.SALE_RETURN, DocType.PURCHASE_RETURN)

/** Blank prefixes, and one prefix used for two kinds of document (two "INV-0001"s), are refused. */
internal fun prefixProblems(b: Business): Map<DocType, String> = buildMap {
    numberedTypes.forEach { type ->
        val p = b.prefix(type).trim()
        val clash = numberedTypes.firstOrNull { it != type && b.prefix(it).trim().equals(p, ignoreCase = true) }
        when {
            p.isEmpty() -> put(type, "Enter a prefix")
            clash != null -> put(type, "Already used for ${clash.title}. Use a different prefix.")
        }
    }
}

@Composable
fun InvoiceSettingsRoute(onBack: () -> Unit, viewModel: BusinessSettingsViewModel = hiltViewModel()) {
    val b = viewModel.business ?: return
    val prefixErrors = prefixProblems(b)
    Scaffold(
        topBar = { AppTopBar("Bill settings", onBack = onBack) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onBack) }, enabled = prefixErrors.isEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionCard(title = "Bill numbering") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Prefix for each type. Numbers continue automatically, e.g. INV-0001.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    numberedTypes.forEach { type ->
                        FormField(
                            // ";" and "=" would break how prefixes are stored
                            b.prefix(type), { v -> viewModel.update { it.copy(prefixes = it.prefixes + (type to v.uppercase().filter { c -> c != ';' && c != '=' }.take(8))) } },
                            type.title, capitalization = KeyboardCapitalization.Characters,
                            error = prefixErrors[type],
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
                        "Add up to four extra fields to each bill. They print in the Details box, for example Roll no., Stylist or Vehicle no. Leave a field blank to hide it. Reset restores the defaults for ${b.type.label}.",
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
                    if (b.udyamNumber.isNotBlank()) {
                        ToggleRow(
                            "MSME payment note",
                            if (b.msmeCategory?.hasPaymentProtection == true) "45-day payment term under the MSMED Act, 2006, printed on sale bills"
                            else "Applies to micro and small enterprises only",
                            b.printMsmeNote && b.msmeCategory?.hasPaymentProtection == true,
                        ) { v -> viewModel.update { it.copy(printMsmeNote = v) } }
                    }
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
    var refused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        printers = BluetoothPrinter.pairedDevices(context)
        refused = !granted.values.all { it }
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
                        if (refused) {
                            Text("Nearby devices is switched off for this app. Open the app settings, choose Permissions, and allow it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { BluetoothPrinter.openAppSettings(context) }) { Text("Open app settings") }
                        } else {
                            OutlinedButton(onClick = { permission.launch(BluetoothPrinter.permissions) }) { Text("Allow Nearby devices") }
                        }
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
                                message = r.fold({ "Test page sent" }, { "The test page could not be printed. Check that the printer is on and paired." })
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
    var pendingRestore by remember { mutableStateOf<(() -> Unit)?>(null) }
    var autoFiles by remember { mutableStateOf(backup.autoBackups()) }
    val busy = viewModel.backupBusy
    val ext = BillingTheme.extendedColors
    // The list of copies changes after every backup or restore
    LaunchedEffect(busy) { if (!busy) autoFiles = backup.autoBackups() }
    // No leaving the screen halfway through
    androidx.activity.compose.BackHandler(enabled = busy) {}

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = { viewModel.importBackup(uri) }
    }
    LaunchedEffect(viewModel.backupMessage) { viewModel.backupMessage?.let { snackbar.showSnackbar(it); viewModel.backupMessage = null } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = { AppTopBar("Backup and restore", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (busy) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Please wait. Do not close the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                    Text("Save the backup file to Google Drive (free) or to Downloads, then copy it to a pen drive. The file is not password protected, so keep it somewhere private.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { exportLauncher.launch(backup.suggestedFileName()) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(AppIcons.Upload, null); Spacer(Modifier.width(8.dp)); Text("Back up now")
                    }
                }
            }
            SectionCard(title = "Restore") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Replaces all data on this phone with the backup. Use this on a new phone too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Icon(AppIcons.Download, null); Spacer(Modifier.width(8.dp)); Text("Restore from file")
                    }
                }
            }
            SectionCard(title = "Automatic daily copies") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("A copy is saved inside the app once a day (last ${BackupManager.KEEP} kept), and another just before any restore. It protects against mistakes, not against losing the phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (autoFiles.isEmpty()) Text("No automatic copies yet.", style = MaterialTheme.typography.bodyMedium)
                    autoFiles.forEach { f: File ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(DateUtils.formatDateTime(context, f.lastModified(), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    (if (backup.isBeforeRestore(f)) "Before restore · " else "") + "${f.length() / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = { pendingRestore = { viewModel.restoreCopy(f) } }, enabled = !busy) { Text("Restore") }
                        }
                    }
                    OutlinedButton(onClick = { viewModel.saveCopy() }, enabled = !busy) { Text("Save a copy now") }
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
