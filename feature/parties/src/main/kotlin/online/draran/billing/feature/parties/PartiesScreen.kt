package online.draran.billing.feature.parties

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.draran.billing.core.designsystem.component.NameAvatar
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.core.designsystem.component.KpiCard
import online.draran.billing.core.designsystem.component.SearchField
import online.draran.billing.core.designsystem.component.ShortDateFormat
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.PartyWithBalance
import online.draran.billing.core.model.abs

/** Parties tab: customers and suppliers with what they owe. */
@Composable
fun PartiesRoute(
    contentPadding: PaddingValues,
    onOpenParty: (Long) -> Unit,
    onAddParty: (PartyType) -> Unit,
    onTypeChanged: (PartyType) -> Unit = {},
    viewModel: PartiesViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val business by viewModel.business.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(type) { onTypeChanged(type) }
    PartiesScreen(ui, query, type, contentPadding, { viewModel.query.value = it }, { viewModel.type.value = it }, onOpenParty, { onAddParty(type) }, customerWord = business.type.party, partiesWord = business.type.parties)
}

@Composable
fun PartiesScreen(
    ui: PartiesUi,
    query: String,
    type: PartyType,
    contentPadding: PaddingValues,
    onQuery: (String) -> Unit,
    onType: (PartyType) -> Unit,
    onOpenParty: (Long) -> Unit,
    onAdd: () -> Unit,
    customerWord: String = "Customer",
    partiesWord: String = "Parties",
) {
    val ext = BillingTheme.extendedColors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg, end = Spacing.lg,
            top = contentPadding.calculateTopPadding() + Spacing.lg,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item { Text(partiesWord, style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                KpiCard("To collect", ui.toCollect, AppIcons.ArrowDownLeft, ext.received, ext.receivedContainer, Modifier.weight(1f))
                KpiCard("To pay", ui.toPay, AppIcons.ArrowUpRight, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer, Modifier.weight(1f))
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(type == PartyType.CUSTOMER, { onType(PartyType.CUSTOMER) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("${customerWord}s (${ui.customerCount})") }
                SegmentedButton(type == PartyType.SUPPLIER, { onType(PartyType.SUPPLIER) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Suppliers (${ui.supplierCount})") }
            }
        }
        item { SearchField(query, onQuery, "Search name or phone") }
        if (!ui.loading && ui.parties.isEmpty()) {
            item {
                SurfaceCard {
                    EmptyState(
                        icon = if (type == PartyType.CUSTOMER) AppIcons.User else AppIcons.Truck,
                        title = if (query.isBlank()) "No ${if (type == PartyType.CUSTOMER) customerWord.lowercase() + "s" else "suppliers"} yet" else "No matches",
                        message = if (type == PartyType.CUSTOMER) "Add ${customerWord.lowercase()}s to give credit, track dues and share statements on WhatsApp." else "Add suppliers to record purchases and track what you owe them.",
                        actionLabel = "Add ${if (type == PartyType.CUSTOMER) customerWord.lowercase() else "supplier"}",
                        onAction = onAdd,
                    )
                }
            }
        } else {
            cardPages(ui.parties, "parties", dividerStart = 68.dp) { p -> PartyRow(p) { onOpenParty(p.party.id) } }
        }
    }
}

/** Coloured initials: each customer keeps the same colour everywhere, which makes long lists easier to scan. */
@Composable
internal fun Avatar(name: String, size: Int = 40) = NameAvatar(name, size = size)

@Composable
private fun PartyRow(p: PartyWithBalance, onClick: () -> Unit) {
    val ext = BillingTheme.extendedColors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.party.name)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(p.party.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(p.party.phone.takeIf { it.isNotBlank() }, p.lastActivity?.let { "Last activity ${it.format(ShortDateFormat)}" }).joinToString(" · ").ifEmpty { "No transactions yet" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val bal = p.balance
            AmountText(
                bal.abs(),
                style = MaterialTheme.typography.titleSmall,
                showPaise = false,
                color = when {
                    bal.paise > 0 -> ext.received
                    bal.paise < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                when {
                    bal.paise > 0 -> "To collect"
                    bal.paise < 0 -> "To pay"
                    else -> "Settled"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
