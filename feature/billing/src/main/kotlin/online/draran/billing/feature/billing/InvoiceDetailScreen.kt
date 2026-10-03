package online.draran.billing.feature.billing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.ConfirmDialog
import online.draran.billing.core.designsystem.component.StatusBadge
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.component.payStatus
import online.draran.billing.core.designsystem.component.pretty
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.DocType
import online.draran.billing.core.print.BluetoothPrinter

@Composable
fun InvoiceDetailRoute(
    invoiceId: Long,
    justSaved: Boolean = false,
    onBack: () -> Unit,
    onEdit: (DocType, Long) -> Unit,
    onCreateFrom: (DocType, Long) -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onRecordPayment: (DocType, Long?) -> Unit,
    onPrinterSettings: () -> Unit,
    viewModel: InvoiceDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(invoiceId) { viewModel.load(invoiceId, context) }
    // A short "saved" confirmation when arriving straight from the editor; shown once per screen
    val haptics = online.draran.billing.core.designsystem.component.rememberHaptics()
    var showSaved by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(justSaved) }
    LaunchedEffect(showSaved) {
        if (showSaved) {
            haptics.success()
            kotlinx.coroutines.delay(2_600)
            showSaved = false
        }
    }
    val invoice by viewModel.invoice.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ext = BillingTheme.extendedColors

    fun thermal() {
        if (!viewModel.printThermal(context)) {
            scope.launch {
                val r = snackbar.showSnackbar("Choose your Bluetooth printer first", actionLabel = "Set up")
                if (r == SnackbarResult.ActionPerformed) onPrinterSettings()
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) thermal() else scope.launch { snackbar.showSnackbar("Allow Nearby devices to print over Bluetooth") }
    }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let { snackbar.showSnackbar(it); viewModel.message = null }
    }

    val inv = invoice
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(
                title = inv?.type?.let { viewModel.business.collectAsStateWithLifecycle().value.docTitle(it) } ?: "Bill",
                subtitle = inv?.number,
                onBack = onBack,
                actions = {
                    if (inv != null) {
                        IconButton(onClick = { onEdit(inv.type, inv.id) }) { Icon(AppIcons.Edit, contentDescription = "Edit") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(AppIcons.MoreVertical, contentDescription = "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(AppIcons.Copy, null) }, onClick = { menu = false; onCreateFrom(inv.type, inv.id) })
                                if (inv.type == DocType.SALE) {
                                    DropdownMenuItem(text = { Text("Sale return (credit note)") }, leadingIcon = { Icon(AppIcons.Return, null) }, onClick = { menu = false; onCreateFrom(DocType.SALE_RETURN, inv.id) })
                                }
                                if (inv.type == DocType.PURCHASE) {
                                    DropdownMenuItem(text = { Text("Purchase return (debit note)") }, leadingIcon = { Icon(AppIcons.Return, null) }, onClick = { menu = false; onCreateFrom(DocType.PURCHASE_RETURN, inv.id) })
                                }
                                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(AppIcons.Trash, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (inv == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (showSaved) {
                item(key = "saved") {
                    online.draran.billing.core.designsystem.component.SuccessBanner(
                        visible = true,
                        text = "${inv.type.shortTitle} saved",
                        detail = "${inv.number} · ${online.draran.billing.core.common.IndianFormat.rupees(inv.totals.total)}",
                    )
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(ext.heroBrush).padding(Spacing.xl)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(inv.partyName, style = MaterialTheme.typography.titleMedium, color = ext.onHero, modifier = Modifier.weight(1f))
                        if (inv.type.tracksPayment) StatusBadge(payStatus(inv.totals.total, inv.paid))
                    }
                    Text(inv.date.pretty(), style = MaterialTheme.typography.bodySmall, color = ext.onHero.copy(alpha = 0.8f))
                    Spacer(Modifier.height(Spacing.md))
                    AmountText(inv.totals.total, style = MaterialTheme.typography.displaySmall, color = ext.onHero)
                    if (inv.type.tracksPayment) {
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                            Column {
                                Text(if (inv.type.paymentDirection == online.draran.billing.core.model.PaymentDirection.IN) "Received" else "Paid", style = MaterialTheme.typography.labelMedium, color = ext.onHero.copy(alpha = 0.8f))
                                AmountText(inv.paid, style = MaterialTheme.typography.titleMedium, color = ext.onHero)
                            }
                            Column {
                                Text("Balance", style = MaterialTheme.typography.labelMedium, color = ext.onHero.copy(alpha = 0.8f))
                                AmountText(inv.balance, style = MaterialTheme.typography.titleMedium, color = ext.onHero)
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ActionTile("WhatsApp", AppIcons.WhatsApp, Modifier.weight(1f)) { viewModel.share(context, whatsApp = true) }
                    ActionTile("Share PDF", AppIcons.Share, Modifier.weight(1f)) { viewModel.share(context, whatsApp = false) }
                    ActionTile("Print", AppIcons.Printer, Modifier.weight(1f)) { viewModel.printSystem(context) }
                    ActionTile("Thermal", AppIcons.Bluetooth, Modifier.weight(1f)) {
                        if (BluetoothPrinter.hasPermission(context)) thermal() else permission.launch(BluetoothPrinter.permissions)
                    }
                }
            }
            if (inv.type.tracksPayment && inv.balance.paise > 0 && inv.partyId != null) {
                item {
                    FilledTonalButton(onClick = { onRecordPayment(inv.type, inv.partyId) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(AppIcons.HandCoins, null); Spacer(Modifier.width(8.dp))
                        Text(if (inv.type.paymentDirection == online.draran.billing.core.model.PaymentDirection.IN) "Record payment received" else "Record payment made")
                    }
                }
            }
            if (inv.type == DocType.ESTIMATE) {
                item {
                    val convertedState by viewModel.convertedSaleId.collectAsStateWithLifecycle()
                    val converted = convertedState
                    FilledTonalButton(
                        onClick = { if (converted != null) onOpenInvoice(converted) else onCreateFrom(DocType.SALE, inv.id) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(AppIcons.ArrowUUpRight, null); Spacer(Modifier.width(8.dp))
                        Text(if (converted != null) "Already converted · open sale" else "Convert to sale invoice")
                    }
                }
            }
            item {
                SurfaceCard {
                    val bmp = viewModel.preview
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), contentDescription = "Bill preview", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f))
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(595f / 842f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
            item {
                if (viewModel.busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
        }
    }
    if (confirmDelete && inv != null) {
        ConfirmDialog(
            "Delete ${inv.number}?",
            "Stock and the party balance will be updated. Any payment taken with this bill is also removed.",
            "Delete", onConfirm = { viewModel.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

@Composable
private fun ActionTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    SurfaceCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
