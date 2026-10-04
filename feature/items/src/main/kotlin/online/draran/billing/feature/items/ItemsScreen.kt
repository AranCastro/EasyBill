package online.draran.billing.feature.items

import online.draran.billing.core.designsystem.component.cardPages
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import online.draran.billing.core.model.BusinessType
import online.draran.billing.core.designsystem.component.NameAvatar
import online.draran.billing.core.designsystem.component.icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.designsystem.component.ChipRow
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.core.designsystem.component.Pill
import online.draran.billing.core.designsystem.component.SearchField
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.designsystem.theme.TABULAR_NUMBERS
import online.draran.billing.core.model.ItemWithStock
import online.draran.billing.core.model.Percent
import online.draran.billing.core.model.Qty

/** Items tab. */
@Composable
fun ItemsRoute(
    contentPadding: PaddingValues,
    onOpenItem: (Long) -> Unit,
    onAddItem: (barcode: String) -> Unit,
    viewModel: ItemsViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    // Stock filters are hidden for service businesses; a filter left on from before must not hide the list
    val businessForFilter by viewModel.business.collectAsStateWithLifecycle()
    LaunchedEffect(businessForFilter.type, filter) {
        if (!businessForFilter.type.tracksStock && filter != ItemFilter.ALL && filter != ItemFilter.FAVOURITES) viewModel.filter.value = ItemFilter.ALL
    }
    val business by viewModel.business.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ItemsScreen(
        ui = ui,
        type = business.type,
        onAddStarter = viewModel::addStarter,
        query = query,
        filter = filter,
        contentPadding = contentPadding,
        onQuery = { viewModel.query.value = it },
        onFilter = { viewModel.filter.value = it },
        onOpenItem = onOpenItem,
        // After scanning an unknown barcode, the new item starts with that code
        onAddItem = { onAddItem(if (ui.items.isEmpty() && query.length >= 6 && query.all(Char::isDigit)) query else "") },
        onScan = {
            scanBarcode(context) { code -> viewModel.query.value = code }
        },
    )
}

@Composable
fun ItemsScreen(
    ui: ItemsUi,
    query: String,
    filter: ItemFilter,
    contentPadding: PaddingValues,
    onQuery: (String) -> Unit,
    onFilter: (ItemFilter) -> Unit,
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    onScan: () -> Unit,
    type: BusinessType = BusinessType.RETAIL,
    onAddStarter: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg, end = Spacing.lg,
            top = contentPadding.calculateTopPadding() + Spacing.lg,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(type.items, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        (if (ui.totalCount == 1) "1 ${type.item.lowercase()}" else "${ui.totalCount} ${type.items.lowercase()}") +
                            (if (type.tracksStock) " · Stock value ${IndianFormat.rupees(ui.stockValue, showPaise = false)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SearchField(
                query = query,
                onQueryChange = onQuery,
                placeholder = "Search name, code or barcode",
                trailing = { IconButton(onClick = onScan) { Icon(AppIcons.Barcode, contentDescription = "Scan barcode") } },
            )
        }
        item {
            ChipRow(
                options = if (type.tracksStock) ItemFilter.entries else listOf(ItemFilter.ALL, ItemFilter.FAVOURITES),
                selected = filter,
                label = { if (it == ItemFilter.LOW && ui.lowCount > 0) "${it.label} (${ui.lowCount})" else it.label },
                onSelect = onFilter,
            )
        }
        if (!ui.loading && ui.items.isEmpty()) {
            item {
                SurfaceCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            icon = if (type.tracksStock) AppIcons.Package else type.icon,
                            title = if (ui.totalCount == 0) "Add your first ${type.item.lowercase()}" else "No matching ${type.items.lowercase()}",
                            message = when {
                                ui.totalCount != 0 -> "Try another name, or add it as a new ${type.item.lowercase()}."
                                type.tracksStock -> "Save each item once with its price, GST rate and stock. Billing then takes a few taps."
                                else -> "Save each ${type.item.lowercase()} with its price once. Billing then takes a few taps."
                            },
                            actionLabel = "Add ${type.item.lowercase()}",
                            onAction = onAddItem,
                        )
                        if (ui.totalCount == 0 && type.presets.isNotEmpty()) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = onAddStarter,
                                modifier = Modifier.padding(bottom = Spacing.lg),
                            ) { Text("Add starter ${type.items.lowercase()}") }
                        }
                    }
                }
            }
        } else {
            cardPages(ui.items, "items", dividerStart = 68.dp) { item -> ItemRow(item, onClick = { onOpenItem(item.item.id) }) }
        }
    }
}

@Composable
internal fun ItemRow(row: ItemWithStock, onClick: () -> Unit) {
    val item = row.item
    val ext = BillingTheme.extendedColors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameAvatar(item.name)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.favourite) {
                    Spacer(Modifier.width(4.dp))
                    Icon(AppIcons.StarFilled, contentDescription = "Favourite", tint = ext.due, modifier = Modifier.size(14.dp))
                }
            }
            val details = listOfNotNull(
                "${IndianFormat.rupees(item.salePrice)}/${item.unit}",
                item.taxRateBp.takeIf { it > 0 }?.let { "GST ${Percent.format(it)}%" },
                item.code.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (item.tracksStock) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${Qty.format(row.stockMilli)} ${item.unit}",
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                    color = if (row.isLow || row.stockMilli < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (row.isLow) Pill("Low", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
                else Text("in stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Pill("Service", MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
        }
    }
}
