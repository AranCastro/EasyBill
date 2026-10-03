package online.draran.billing.feature.billing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.ChipRow
import online.draran.billing.core.designsystem.component.MoneyField
import online.draran.billing.core.designsystem.component.SearchField
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.designsystem.theme.TABULAR_NUMBERS
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.MoneyParse
import online.draran.billing.core.model.PaymentMode
import online.draran.billing.core.model.Qty
import online.draran.billing.core.print.QrCode
import online.draran.billing.core.print.Upi

/** Counter billing: tap tiles, charge, next customer. Cash sale, fully paid. */
@Composable
fun CounterRoute(
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onCreateItem: (String) -> Unit,
    newItemId: Long = 0,
    viewModel: InvoiceEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(newItemId) { viewModel.addNewItem(newItemId) }
    LaunchedEffect(Unit) { viewModel.init(DocType.SALE, 0, 0, 0) }
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val business by viewModel.business.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var showCart by remember { mutableStateOf(false) }
    var charging by remember { mutableStateOf(false) }
    val totals = viewModel.totals()
    val count = viewModel.lines.sumOf { it.qtyMilli }

    val needle = query.trim().lowercase()
    val shown = items.filter { needle.isEmpty() || it.item.name.lowercase().contains(needle) || it.item.code.lowercase() == needle || it.item.barcode == needle }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(if (business.type == online.draran.billing.core.model.BusinessType.RETAIL) "Counter" else business.type.counterLabel, onBack = onBack, subtitle = viewModel.number, actions = {
                IconButton(onClick = {
                    scanBarcode(context) { code -> scope.launch { if (!viewModel.addByBarcode(code)) onCreateItem(code) } }
                }) { Icon(AppIcons.Barcode, contentDescription = "Scan barcode") }
            })
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(enabled = viewModel.lines.isNotEmpty()) { showCart = true }.padding(Spacing.sm)) {
                        Text(if (viewModel.lines.isEmpty()) "Tap items to add" else "${Qty.format(count)} items · View cart", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AmountText(totals.total, style = MaterialTheme.typography.titleLarge)
                    }
                    Button(onClick = { charging = true }, enabled = viewModel.lines.isNotEmpty(), modifier = Modifier.height(52.dp)) {
                        Text("Charge ${IndianFormat.rupees(totals.total, showPaise = false)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, "Search ${business.type.items.lowercase()}", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            if (items.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("No ${business.type.items.lowercase()} yet", style = MaterialTheme.typography.titleMedium)
                    Text("Add ${business.type.items.lowercase()} and mark the ones you bill most as favourites to see them here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.md))
                    Button(onClick = { onCreateItem("") }) { Text("Add ${business.type.item.lowercase()}") }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(shown, key = { it.item.id }) { row ->
                    val qty = viewModel.quantityOf(row.item.id)
                    val selected = qty > 0
                    Box(
                        Modifier
                            .heightIn(min = 96.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                            .clickable { viewModel.addItem(row.item) }
                            .padding(Spacing.md),
                    ) {
                        Column {
                            // Leave room for the quantity badge in the corner
                            Text(row.item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = if (selected) 22.dp else 0.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(IndianFormat.rupees(row.item.salePrice), style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_NUMBERS), color = MaterialTheme.colorScheme.primary)
                            if (row.item.tracksStock) {
                                Text("${Qty.format(row.stockMilli)} ${row.item.unit} left", style = MaterialTheme.typography.labelSmall, color = if (row.isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (selected) {
                            Box(
                                Modifier.align(Alignment.TopEnd).size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(Qty.format(qty), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCart) CartSheet(viewModel, onDismiss = { showCart = false })
    if (charging) {
        ChargeSheet(
            viewModel = viewModel,
            upiId = business.upiId,
            businessName = business.name,
            canPrint = business.printerAddress.isNotBlank(),
            onDismiss = { charging = false },
            onSaved = { id, print ->
                charging = false
                if (print) viewModel.printReceipt(context, id) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                scope.launch {
                    val number = viewModel.numberOf(id)
                    viewModel.reset()
                    val r = snackbar.showSnackbar("Saved $number", actionLabel = "View bill")
                    if (r == SnackbarResult.ActionPerformed) onOpenInvoice(id)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartSheet(viewModel: InvoiceEditorViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.lg).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cart", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { viewModel.reset(); onDismiss() }) { Text("Clear") }
            }
            val totals = viewModel.totals()
            viewModel.lines.forEachIndexed { index, line ->
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, style = MaterialTheme.typography.titleSmall)
                        AmountText(totals.lines.getOrNull(index)?.total ?: line.rate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    QtyStepper(line.qtyMilli, { viewModel.changeQty(index, -Qty.ONE) }, { viewModel.changeQty(index, Qty.ONE) })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (viewModel.lines.isEmpty()) LaunchedEffect(Unit) { onDismiss() }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChargeSheet(
    viewModel: InvoiceEditorViewModel,
    upiId: String,
    businessName: String,
    canPrint: Boolean,
    onDismiss: () -> Unit,
    onSaved: (Long, Boolean) -> Unit,
) {
    val total = viewModel.totals().total
    var given by remember { mutableStateOf("") }
    var print by remember { mutableStateOf(canPrint) }
    val ext = BillingTheme.extendedColors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Amount to collect", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(total, style = MaterialTheme.typography.displaySmall)
            }
            ChipRow(listOf(PaymentMode.CASH, PaymentMode.UPI, PaymentMode.CARD), viewModel.mode, { it.label }, { viewModel.mode = it })
            when (viewModel.mode) {
                PaymentMode.CASH -> {
                    MoneyField(given, { given = it }, "Cash given by customer (optional)")
                    val change = (MoneyParse.parse(given)?.paise ?: 0) - total.paise
                    if (change > 0) {
                        Text("Return change: ${IndianFormat.rupees(online.draran.billing.core.model.Money(change))}", style = MaterialTheme.typography.titleMedium, color = ext.received)
                    }
                }
                PaymentMode.UPI -> {
                    if (upiId.isNotBlank()) {
                        val qr = remember(total, upiId) { QrCode.bitmap(Upi.link(upiId, businessName, total, viewModel.number), 512) }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(qr.asImageBitmap(), contentDescription = "UPI QR code", modifier = Modifier.size(220.dp))
                            Text("Ask the customer to scan with any UPI app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("Add your UPI ID in Settings › Business profile to show a payment QR here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> Unit
            }
            if (canPrint) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Print receipt", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = print, onCheckedChange = { print = it })
                }
            }
            Button(
                onClick = { viewModel.fullyPaid = true; viewModel.save { id -> onSaved(id, print && canPrint) } },
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text("Paid · Save bill", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
