package online.draran.billing.feature.billing

import online.draran.billing.core.designsystem.component.NameAvatar
import online.draran.billing.core.designsystem.component.rememberHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.data.CASH_CUSTOMER
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.FormField
import online.draran.billing.core.designsystem.component.IconBadge
import online.draran.billing.core.designsystem.component.MoneyField
import online.draran.billing.core.designsystem.component.NumberField
import online.draran.billing.core.designsystem.component.SearchField
import online.draran.billing.core.designsystem.component.SelectField
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.designsystem.theme.TABULAR_NUMBERS
import online.draran.billing.core.model.GST_RATES_BP
import online.draran.billing.core.model.InvoiceLine
import online.draran.billing.core.model.Item
import online.draran.billing.core.model.ItemWithStock
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.MoneyParse
import online.draran.billing.core.model.Party
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.PartyWithBalance
import online.draran.billing.core.model.Percent
import online.draran.billing.core.model.Qty
import online.draran.billing.core.model.TaxEngine
import online.draran.billing.core.model.abs

/** Pick a customer/supplier, cash customer, or add one inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartyPickerSheet(
    parties: List<PartyWithBalance>,
    type: PartyType,
    allowCash: Boolean,
    onPick: (Party, Money) -> Unit,
    onCash: () -> Unit,
    onQuickAdd: (String, String) -> Unit,
    onDismiss: () -> Unit,
    partyWord: String = type.label,
) {
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ext = BillingTheme.extendedColors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.9f).padding(horizontal = Spacing.lg).imePadding()) {
            Text("Select ${partyWord.lowercase()}", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.md))
            if (adding) {
                FormField(query, { query = it }, "Name *", capitalization = KeyboardCapitalization.Words)
                Spacer(Modifier.height(Spacing.sm))
                FormField(phone, { phone = it }, "Mobile number", keyboardType = KeyboardType.Phone)
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = { adding = false }, modifier = Modifier.weight(1f)) { Text("Back") }
                    Button(onClick = { onQuickAdd(query.trim(), phone.trim()); onDismiss() }, enabled = query.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Add & select") }
                }
                return@Column
            }
            SearchField(query, { query = it }, "Search name or phone")
            Spacer(Modifier.height(Spacing.sm))
            val needle = query.trim().lowercase()
            val shown = parties.filter { it.party.type == type }
                .filter { needle.isEmpty() || it.party.name.lowercase().contains(needle) || it.party.phone.contains(needle) ||
                    // "9876543210" also finds a number saved as "98765 43210" or "+91 98765-43210"
                    needle.filter(Char::isDigit).let { d -> d.length >= 3 && it.party.phone.filter(Char::isDigit).contains(d) } }
            LazyColumn(Modifier.weight(1f)) {
                item {
                    PickerRow(AppIcons.UserPlus, if (query.isBlank()) "Add new ${partyWord.lowercase()}" else "Add \"${query.trim()}\"", null) { adding = true }
                }
                if (allowCash) {
                    item { PickerRow(AppIcons.Money, CASH_CUSTOMER, "Walk-in, paid immediately") { onCash(); onDismiss() } }
                }
                items(shown, key = { it.party.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(p.party, p.balance); onDismiss() }.padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            Text(p.party.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(p.party.name, style = MaterialTheme.typography.titleSmall)
                            if (p.party.phone.isNotBlank()) Text(p.party.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!p.balance.isZero) {
                            AmountText(p.balance.abs(), style = MaterialTheme.typography.bodyMedium, showPaise = false, color = if (p.balance.paise > 0) ext.received else MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun PickerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, size = 40)
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Search and tap items; steppers show what is already on the bill. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemPickerSheet(
    items: List<ItemWithStock>,
    purchase: Boolean,
    quantityOf: (Long) -> Long,
    onSetQuantity: (Item, Long) -> Unit,
    onScan: () -> Unit,
    onNewItem: (String) -> Unit,
    onCustomLine: () -> Unit,
    onDismiss: () -> Unit,
    itemWord: String = "Item",
    itemsWord: String = "Items",
) {
    var query by remember { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add ${itemsWord.lowercase()}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Done") }
            }
            Spacer(Modifier.height(Spacing.sm))
            SearchField(query, { query = it }, "Search ${itemsWord.lowercase()}", trailing = { IconButton(onClick = onScan) { Icon(AppIcons.Barcode, contentDescription = "Scan barcode") } })
            Spacer(Modifier.height(Spacing.sm))
            val haptics = rememberHaptics()
            val needle = query.trim().lowercase()
            val shown = items.filter {
                needle.isEmpty() || it.item.name.lowercase().contains(needle) || it.item.code.lowercase() == needle || it.item.barcode == needle
            }
            LazyColumn(Modifier.weight(1f).imePadding()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.sm)) {
                        OutlinedButton(onClick = { onNewItem(query.trim()) }) {
                            Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("New ${itemWord.lowercase()}")
                        }
                        OutlinedButton(onClick = onCustomLine) {
                            Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("One-time ${itemWord.lowercase()}")
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (items.isEmpty()) "No ${itemsWord.lowercase()} saved yet. Add a new ${itemWord.lowercase()} or a one-time ${itemWord.lowercase()}." else "No matching ${itemsWord.lowercase()}.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.lg),
                        )
                    }
                }
                items(shown, key = { it.item.id }) { row ->
                    val qty = quantityOf(row.item.id)
                    val price = if (purchase) row.item.purchasePrice else row.item.salePrice
                    Row(
                        Modifier.fillMaxWidth().clickable { haptics.tick(); onSetQuantity(row.item, qty + Qty.ONE) }.padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NameAvatar(row.item.name, size = 36)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(row.item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${IndianFormat.rupees(price)}/${row.item.unit}" + if (row.item.tracksStock) " · Stock ${Qty.format(row.stockMilli)}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.item.tracksStock && row.stockMilli <= 0 && !purchase) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (qty > 0) {
                            QtyStepper(qty, onMinus = { onSetQuantity(row.item, qty - Qty.ONE) }, onPlus = { onSetQuantity(row.item, qty + Qty.ONE) })
                        } else {
                            OutlinedIconButton(onClick = { haptics.tick(); onSetQuantity(row.item, Qty.ONE) }) { Icon(AppIcons.Plus, contentDescription = "Add ${row.item.name}") }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
internal fun QtyStepper(qty: Long, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    Row(
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { haptics.tick(); onMinus() }, modifier = Modifier.size(36.dp)) {
            Icon(if (qty <= Qty.ONE) AppIcons.Trash else AppIcons.Minus, contentDescription = "Decrease quantity", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Text(
            Qty.format(qty),
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        FilledIconButton(onClick = { haptics.tick(); onPlus() }, modifier = Modifier.size(36.dp), colors = IconButtonDefaults.filledIconButtonColors()) {
            Icon(AppIcons.Plus, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
        }
    }
}

/** Edit a line: quantity, price, discount and GST. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LineEditSheet(
    line: InvoiceLine?,
    gstEnabled: Boolean,
    interState: Boolean,
    onSave: (InvoiceLine) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isNew = line == null
    var name by remember { mutableStateOf(line?.name.orEmpty()) }
    var qty by remember { mutableStateOf(line?.let { Qty.format(it.qtyMilli) } ?: "1") }
    var rate by remember { mutableStateOf(line?.let { MoneyParse.toInput(it.rate) }.orEmpty()) }
    var discount by remember { mutableStateOf(line?.discountBp?.takeIf { it > 0 }?.let { Percent.format(it) }.orEmpty()) }
    var tax by remember { mutableStateOf(line?.taxRateBp ?: 0) }
    var inclusive by remember { mutableStateOf(line?.taxInclusive ?: false) }
    var unit by remember { mutableStateOf(line?.unit ?: "pcs") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val parsed = InvoiceLine(
        id = line?.id ?: 0, itemId = line?.itemId, name = name.trim(), hsn = line?.hsn.orEmpty(), unit = unit,
        qtyMilli = Qty.parse(qty) ?: 0, rate = MoneyParse.parse(rate) ?: Money.ZERO,
        discountBp = (Percent.parse(discount) ?: 0).coerceIn(0, 10_000), taxRateBp = tax, taxInclusive = inclusive,
        costRate = line?.costRate ?: Money.ZERO,
    )
    val amounts = TaxEngine.line(parsed.toInput(), interState, gstEnabled)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(if (isNew) "One-time item" else "Edit line", style = MaterialTheme.typography.titleLarge)
            FormField(name, { name = it }, "Item name", capitalization = KeyboardCapitalization.Words)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField(qty, { qty = it }, "Quantity", suffix = unit, modifier = Modifier.weight(1f))
                MoneyField(rate, { rate = it }, "Price / $unit", modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NumberField(discount, { discount = it }, "Discount", suffix = "%", decimals = 2, modifier = Modifier.weight(1f))
                if (gstEnabled) {
                    SelectField("GST", (GST_RATES_BP + tax).distinct().sorted(), tax, { "${Percent.format(it)}%" }, { tax = it }, Modifier.weight(1f))
                } else {
                    FormField(unit, { unit = it }, "Unit", modifier = Modifier.weight(1f))
                }
            }
            // With GST on, the rate takes the second slot above; the unit still needs a place
            if (gstEnabled) FormField(unit, { unit = it }, "Unit (for example kg, pcs, hr)")
            if (gstEnabled && tax > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Price includes GST", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = inclusive, onCheckedChange = { inclusive = it })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Line total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (gstEnabled && !amounts.tax.isZero) {
                        Text("incl. GST ${IndianFormat.rupees(amounts.tax)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AmountText(amounts.total, style = MaterialTheme.typography.titleLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.lg)) {
                if (!isNew) {
                    OutlinedButton(onClick = { onRemove(); onDismiss() }, modifier = Modifier.weight(1f)) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = { onSave(parsed); onDismiss() },
                    enabled = parsed.name.isNotBlank() && parsed.qtyMilli > 0,
                    modifier = Modifier.weight(1f),
                ) { Text(if (isNew) "Add" else "Update") }
            }
        }
    }
}

@Composable
internal fun TextEditDialog(title: String, initial: String, label: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { FormField(value, { value = it }, label, capitalization = KeyboardCapitalization.Characters) },
        confirmButton = { TextButton(onClick = { onDone(value.trim()); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
