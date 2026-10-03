package online.draran.billing.feature.billing

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.designsystem.component.AmountRow
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.BottomActionBar
import online.draran.billing.core.designsystem.component.ChipRow
import online.draran.billing.core.designsystem.component.DateField
import online.draran.billing.core.designsystem.component.FormField
import online.draran.billing.core.designsystem.component.IconBadge
import online.draran.billing.core.designsystem.component.MoneyField
import online.draran.billing.core.designsystem.component.SectionCard
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.DocType
import androidx.compose.ui.text.input.KeyboardCapitalization
import online.draran.billing.core.model.PaymentDirection
import online.draran.billing.core.model.PaymentMode
import online.draran.billing.core.model.Percent
import online.draran.billing.core.model.Qty
import online.draran.billing.core.model.abs

/**
 * One editor for every bill type. Designed for speed: cash customer by default,
 * items added with a tap or a scan, totals update instantly, one tap to save.
 */
@Composable
fun InvoiceEditorRoute(
    type: DocType,
    invoiceId: Long,
    sourceId: Long,
    partyId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onCreateItem: (String) -> Unit,
    newItemId: Long = 0,
    viewModel: InvoiceEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(newItemId) { viewModel.addNewItem(newItemId) }
    LaunchedEffect(Unit) { viewModel.init(type, invoiceId, sourceId, partyId) }
    val business by viewModel.business.collectAsStateWithLifecycle()
    val items by viewModel.allItems.collectAsStateWithLifecycle()
    val parties by viewModel.allParties.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickParty by remember { mutableStateOf(false) }
    var pickItems by remember { mutableStateOf(false) }
    var editIndex by remember { mutableIntStateOf(-2) } // -2 none, -1 new one-time line
    var editNumber by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(viewModel.notes.isNotBlank()) }
    val ext = BillingTheme.extendedColors
    val docType = viewModel.type
    val totals = viewModel.totals()
    val interState = viewModel.interState()
    val isPurchase = docType == DocType.PURCHASE || docType == DocType.PURCHASE_RETURN
    // Industry words (Student, Fee head, Service…) on the selling side only
    val words = business.type
    val itemWord = if (isPurchase) "Item" else words.item
    val itemsWord = if (isPurchase) "Items" else words.items

    fun scan() = scanBarcode(context) { code ->
        scope.launch {
            if (!viewModel.addByBarcode(code)) {
                Toast.makeText(context, "No item with barcode $code. Create it now.", Toast.LENGTH_SHORT).show()
                onCreateItem(code)
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(viewModel.title, onBack = onBack, actions = {
                IconButton(onClick = { scan() }) { Icon(AppIcons.Barcode, contentDescription = "Scan barcode") }
            })
        },
        bottomBar = {
            BottomActionBar(
                label = if (docType.tracksPayment && !viewModel.fullyPaid && viewModel.paidAmount().paise < totals.total.paise) {
                    "Total · Balance ${IndianFormat.rupees(totals.total - viewModel.paidAmount())}"
                } else {
                    "Total (${viewModel.lines.size} item${if (viewModel.lines.size == 1) "" else "s"})"
                },
                amount = totals.total,
                actionText = if (viewModel.saving) "Saving…" else "Save",
                enabled = !viewModel.saving,
                onAction = { viewModel.save(onSaved) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = padding.calculateTopPadding() + Spacing.xs, bottom = padding.calculateBottomPadding() + Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // Number and date
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    SurfaceCard(Modifier.weight(1f), onClick = { editNumber = true }) {
                        Column(Modifier.padding(horizontal = Spacing.lg, vertical = 10.dp)) {
                            Text(if (docType == DocType.PURCHASE) "Supplier bill no." else "${business.docShortTitle(docType)} no.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(viewModel.number.ifBlank { "Tap to enter" }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        }
                    }
                    DateField("Date", viewModel.date, { viewModel.date = it }, Modifier.weight(1f))
                }
            }
            // Party
            item {
                val p = viewModel.party
                SurfaceCard(onClick = { pickParty = true }) {
                    Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(if (p.isCash) AppIcons.Money else if (isPurchase) AppIcons.Truck else AppIcons.User, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, size = 44)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(if (isPurchase) "Supplier" else words.party, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val hint = listOfNotNull(
                                p.phone.takeIf { it.isNotBlank() },
                                if (!p.balance.isZero) (if (p.balance.paise > 0) "Owes " else "Advance ") + IndianFormat.rupees(p.balance.abs(), showPaise = false) else null,
                                if (business.gstEnabled && interState) "IGST" else null,
                            ).joinToString(" · ")
                            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // Industry bill fields and due date
            val labels = viewModel.customLabels
            if (labels.isNotEmpty() || viewModel.showsDueDate) {
                item {
                    SectionCard(title = "Bill details") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            labels.forEach { label ->
                                FormField(
                                    viewModel.customValues[label].orEmpty(), { viewModel.setCustomValue(label, it) }, label,
                                    capitalization = KeyboardCapitalization.Sentences,
                                )
                            }
                            if (viewModel.showsDueDate) {
                                val due = viewModel.dueDate
                                if (due == null) {
                                    TextButton(onClick = { viewModel.dueDate = viewModel.date.plusDays(15) }) {
                                        Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add due date")
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        DateField("Due date", due, { viewModel.dueDate = it }, Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.dueDate = null }) { Icon(AppIcons.Close, contentDescription = "Remove due date") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Items header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(itemsWord, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { pickItems = true }) {
                        Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add ${itemsWord.lowercase()}")
                    }
                }
            }
            if (viewModel.lines.isEmpty()) {
                item {
                    Surface(
                        onClick = { pickItems = true },
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(AppIcons.PlusCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(Spacing.sm))
                            Text("Tap to add ${itemsWord.lowercase()}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text("or scan a barcode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    SurfaceCard {
                        Column {
                            viewModel.lines.forEachIndexed { index, line ->
                                val amounts = totals.lines.getOrNull(index)
                                Row(
                                    Modifier.fillMaxWidth().clickable { editIndex = index }.padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(line.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val detail = buildString {
                                            append("${Qty.format(line.qtyMilli)} ${line.unit} × ${IndianFormat.rupees(line.rate)}")
                                            if (line.discountBp > 0) append(" · -${Percent.format(line.discountBp)}%")
                                            if (business.gstEnabled && line.taxRateBp > 0) append(" · GST ${Percent.format(line.taxRateBp)}%")
                                        }
                                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (amounts != null) AmountText(amounts.total, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    QtyStepper(line.qtyMilli, onMinus = { viewModel.changeQty(index, -Qty.ONE) }, onPlus = { viewModel.changeQty(index, Qty.ONE) })
                                }
                                if (index < viewModel.lines.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                // Totals
                item {
                    SectionCard {
                        Column {
                            AmountRow("Sub total", totals.subtotal)
                            if (!totals.discount.isZero) AmountRow("Discount", -totals.discount, color = ext.received)
                            if (business.gstEnabled) {
                                AmountRow("Taxable value", totals.taxable)
                                if (interState) AmountRow("IGST", totals.igst) else {
                                    AmountRow("CGST", totals.cgst)
                                    AmountRow("SGST", totals.sgst)
                                }
                            }
                            if (!totals.roundOff.isZero) AmountRow("Round off", totals.roundOff)
                            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            AmountRow("Total", totals.total, emphasise = true)
                        }
                    }
                }
            }
            // Payment
            if (docType.tracksPayment && viewModel.lines.isNotEmpty()) {
                item {
                    val word = if (docType.paymentDirection == PaymentDirection.IN) "received" else "paid"
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Fully $word", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (viewModel.fullyPaid) "${IndianFormat.rupees(totals.total)} $word now" else "Enter what was $word now; the rest is added to the balance",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = viewModel.fullyPaid, onCheckedChange = { viewModel.fullyPaid = it })
                            }
                            if (!viewModel.fullyPaid) {
                                MoneyField(viewModel.received, { viewModel.received = it }, "Amount $word now", supporting = "Balance: ${IndianFormat.rupees(totals.total - viewModel.paidAmount())}")
                            }
                            if (viewModel.paidAmount().paise > 0) {
                                ChipRow(PaymentMode.entries, viewModel.mode, { it.label }, { viewModel.mode = it })
                            }
                        }
                    }
                }
            }
            // Notes
            item {
                if (showNotes) {
                    FormField(viewModel.notes, { viewModel.notes = it }, "Notes (printed on the bill)", singleLine = false, minLines = 2)
                } else {
                    OutlinedButton(onClick = { showNotes = true }) {
                        Icon(AppIcons.Note, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add note")
                    }
                }
            }
            viewModel.error?.let { message ->
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(Spacing.md))
                    }
                }
            }
        }
    }

    if (pickParty) {
        PartyPickerSheet(
            parties = parties,
            type = viewModel.partyTypeForDoc,
            allowCash = true,
            onPick = { p, bal -> viewModel.selectParty(p, bal) },
            onCash = { viewModel.selectCash() },
            onQuickAdd = { name, phone -> viewModel.quickAddParty(name, phone) },
            onDismiss = { pickParty = false },
            partyWord = if (isPurchase) viewModel.partyTypeForDoc.label else words.party,
        )
    }
    if (pickItems) {
        ItemPickerSheet(
            items = items,
            purchase = isPurchase,
            quantityOf = { viewModel.quantityOf(it) },
            onSetQuantity = { item, qty -> viewModel.setItemQuantity(item, qty) },
            onScan = { scan() },
            onNewItem = { name -> pickItems = false; onCreateItem("name:$name") },
            onCustomLine = { pickItems = false; editIndex = -1 },
            onDismiss = { pickItems = false },
            itemWord = itemWord,
            itemsWord = itemsWord,
        )
    }
    if (editIndex >= -1) {
        val line = viewModel.lines.getOrNull(editIndex)
        LineEditSheet(
            line = line,
            gstEnabled = business.gstEnabled,
            interState = interState,
            onSave = { updated -> if (line == null) viewModel.addCustomLine(updated.name, updated.rate, updated.qtyMilli, updated.taxRateBp) else viewModel.updateLine(editIndex, updated) },
            onRemove = { if (editIndex >= 0) viewModel.removeLine(editIndex) },
            onDismiss = { editIndex = -2 },
        )
    }
    if (editNumber) {
        TextEditDialog(
            title = if (docType == DocType.PURCHASE) "Supplier bill number" else "${business.docShortTitle(docType)} number",
            initial = viewModel.number,
            label = "Number",
            onDone = { viewModel.number = it },
            onDismiss = { editNumber = false },
        )
    }
}
