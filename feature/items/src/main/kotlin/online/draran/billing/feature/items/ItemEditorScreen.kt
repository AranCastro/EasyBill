package online.draran.billing.feature.items

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.ConfirmDialog
import online.draran.billing.core.designsystem.component.FormField
import online.draran.billing.core.designsystem.component.MoneyField
import online.draran.billing.core.designsystem.component.NumberField
import online.draran.billing.core.designsystem.component.SectionCard
import online.draran.billing.core.designsystem.component.SelectField
import online.draran.billing.core.designsystem.component.ShortDateFormat
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.designsystem.theme.TABULAR_NUMBERS
import online.draran.billing.core.model.COMMON_UNITS
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.GST_RATES_BP
import online.draran.billing.core.model.ItemType
import online.draran.billing.core.model.Percent
import online.draran.billing.core.model.Qty

/** Add or edit an item. Existing goods also show stock and its history. */
@Composable
fun ItemEditorRoute(
    itemId: Long,
    prefillBarcode: String,
    onBack: () -> Unit,
    prefillName: String = "",
    onSaved: (Long) -> Unit = { onBack() },
    viewModel: ItemEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(itemId) { viewModel.load(itemId, prefillBarcode, prefillName) }
    val business by viewModel.business.collectAsStateWithLifecycle()
    val stock by viewModel.stock.collectAsStateWithLifecycle()
    val moves by viewModel.moves.collectAsStateWithLifecycle()
    val form = viewModel.form
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf(false) }
    val editing = itemId != 0L

    Scaffold(
        topBar = {
            AppTopBar(
                title = (if (editing) "Edit " else "New ") + viewModel.business.collectAsStateWithLifecycle().value.type.item.lowercase(),
                onBack = onBack,
                actions = {
                    if (editing) {
                        IconButton(onClick = { viewModel.update { it.copy(favourite = !it.favourite) } }) {
                            Icon(if (form.favourite) AppIcons.StarFilled else AppIcons.Star, contentDescription = "Favourite", tint = if (form.favourite) BillingTheme.extendedColors.due else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Trash, contentDescription = "Delete item") }
                    }
                },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onSaved) }, enabled = !viewModel.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (editing) "Save changes" else "Save item")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (editing && form.type == ItemType.GOODS) {
                stock?.let { s ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Current stock", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "${Qty.format(s.stockMilli)} ${s.item.unit}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                                    color = if (s.isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                )
                                if (s.isLow) Text("Low stock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            OutlinedButton(onClick = { adjusting = true }) {
                                Icon(AppIcons.Scales, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Adjust")
                            }
                        }
                    }
                }
            }

            SectionCard(title = "Basic details") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ItemType.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = form.type == t,
                                onClick = { viewModel.update { it.copy(type = t) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ItemType.entries.size),
                            ) { Text(if (t == ItemType.GOODS) "Product" else "Service") }
                        }
                    }
                    FormField(form.name, { v -> viewModel.update { it.copy(name = v) } }, "Item name *", capitalization = KeyboardCapitalization.Words, error = viewModel.nameError.takeIf { viewModel.showErrors })
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        MoneyField(form.salePrice, { v -> viewModel.update { it.copy(salePrice = v) } }, "Sale price", modifier = Modifier.weight(1f))
                        SelectField(
                            label = "Unit",
                            options = (COMMON_UNITS + form.unit).distinct(),
                            selected = form.unit,
                            optionLabel = { it },
                            onSelect = { u -> viewModel.update { it.copy(unit = u) } },
                            modifier = Modifier.weight(0.8f),
                        )
                    }
                    MoneyField(form.purchasePrice, { v -> viewModel.update { it.copy(purchasePrice = v) } }, "Purchase price (cost)", supporting = "Used for profit reports")
                }
            }

            if (business.gstEnabled) {
                SectionCard(title = "GST") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SelectField(
                                label = "GST rate",
                                options = (GST_RATES_BP + form.taxRateBp).distinct().sorted(),
                                selected = form.taxRateBp,
                                optionLabel = { "${Percent.format(it)}%" },
                                onSelect = { r -> viewModel.update { it.copy(taxRateBp = r) } },
                                modifier = Modifier.weight(1f),
                            )
                            FormField(form.hsn, { v -> viewModel.update { it.copy(hsn = v.filter(Char::isDigit).take(8)) } }, if (form.type == ItemType.SERVICE) "SAC code" else "HSN code", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, modifier = Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Sale price includes GST", style = MaterialTheme.typography.bodyMedium)
                                Text(if (form.taxInclusive) "Tax is taken out of the price" else "Tax is added on top of the price", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = form.taxInclusive, onCheckedChange = { v -> viewModel.update { it.copy(taxInclusive = v) } })
                        }
                    }
                }
            }

            if (form.type == ItemType.GOODS) {
                SectionCard(title = "Stock") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        NumberField(form.openingStock, { v -> viewModel.update { it.copy(openingStock = v) } }, "Opening stock", suffix = form.unit, modifier = Modifier.weight(1f))
                        NumberField(form.lowStock, { v -> viewModel.update { it.copy(lowStock = v) } }, "Alert below", suffix = form.unit, modifier = Modifier.weight(1f))
                    }
                }
            }

            SectionCard(title = "Quick billing") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FormField(
                        form.barcode, { v -> viewModel.update { it.copy(barcode = v.trim()) } }, "Barcode",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        trailing = { IconButton(onClick = { scanBarcode(context) { code -> viewModel.update { it.copy(barcode = code) } } }) { Icon(AppIcons.Barcode, contentDescription = "Scan barcode") } },
                    )
                    FormField(form.code, { v -> viewModel.update { it.copy(code = v) } }, "Item code (short)", capitalization = KeyboardCapitalization.Characters)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Favourite", style = MaterialTheme.typography.bodyMedium)
                            Text("Shown first in Counter billing and the item picker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = form.favourite, onCheckedChange = { v -> viewModel.update { it.copy(favourite = v) } })
                    }
                }
            }

            if (editing && moves.isNotEmpty()) {
                SectionCard(title = "Stock history") {
                    Column {
                        moves.take(30).forEachIndexed { i, m ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val label = DocType.entries.firstOrNull { it.name == m.reason }?.shortTitle ?: "Adjustment"
                                    Text(label + if (m.reference.isNotBlank()) " · ${m.reference}" else "", style = MaterialTheme.typography.bodyMedium)
                                    Text(m.date.format(ShortDateFormat), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    (if (m.qtyMilli > 0) "+" else "") + Qty.format(m.qtyMilli),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                                    color = if (m.qtyMilli >= 0) BillingTheme.extendedColors.received else MaterialTheme.colorScheme.error,
                                )
                            }
                            if (i < moves.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete item?",
            message = "Past bills keep the item name. Stock history for this item will be removed.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
    if (adjusting) {
        AdjustStockDialog(unit = form.unit, onDismiss = { adjusting = false }, onConfirm = { qty, add, note -> viewModel.adjust(qty, add, note) })
    }
}

@Composable
private fun AdjustStockDialog(unit: String, onDismiss: () -> Unit, onConfirm: (String, Boolean, String) -> Unit) {
    var qty by remember { mutableStateOf("") }
    var add by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = add, onClick = { add = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Add") }
                    SegmentedButton(selected = !add, onClick = { add = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Reduce") }
                }
                NumberField(qty, { qty = it }, "Quantity", suffix = unit)
                FormField(note, { note = it }, "Reason (optional)", placeholder = if (add) "New stock, correction" else "Damaged, expired, own use")
            }
        },
        confirmButton = {
            TextButton(enabled = Qty.parse(qty)?.let { it > 0 } == true, onClick = { onConfirm(qty, add, note); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
