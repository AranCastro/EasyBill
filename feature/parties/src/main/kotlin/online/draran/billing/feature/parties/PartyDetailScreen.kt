package online.draran.billing.feature.parties

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.core.designsystem.component.ShortDateFormat
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.IndianStates
import online.draran.billing.core.model.LedgerEntry
import online.draran.billing.core.model.PartyType
import online.draran.billing.core.model.abs
import online.draran.billing.core.print.Sharing

@Composable
fun PartyDetailRoute(
    partyId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNewBill: (PartyType) -> Unit,
    onPayment: (PartyType) -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onOpenPayment: (Long) -> Unit,
    viewModel: PartyDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(partyId) { viewModel.load(partyId) }
    val partyWithBalance by viewModel.party.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ext = BillingTheme.extendedColors
    // If the party is deleted while this screen is open, leave instead of showing a blank page
    var wasShown by remember { mutableStateOf(false) }
    LaunchedEffect(partyWithBalance) {
        if (partyWithBalance != null) wasShown = true else if (wasShown) onBack()
    }
    val p = partyWithBalance ?: return
    val party = p.party
    val isCustomer = party.type == PartyType.CUSTOMER
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let { snackbar.showSnackbar(it); viewModel.message = null }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(party.name, onBack = onBack, subtitle = party.type.label, actions = {
                IconButton(onClick = onEdit) { Icon(AppIcons.Edit, contentDescription = "Edit") }
            })
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                FilledTonalButton(onClick = { onPayment(party.type) }, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text(if (isCustomer) "Payment in" else "Payment out")
                }
                Button(onClick = { onNewBill(party.type) }, modifier = Modifier.weight(1f).height(50.dp)) {
                    Text(if (isCustomer) "New sale" else "New purchase")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                val bal = p.balance
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(ext.heroBrush).padding(Spacing.xl),
                ) {
                    Text(
                        when {
                            bal.paise > 0 -> "To collect"
                            bal.paise < 0 -> "To pay"
                            else -> "Settled"
                        },
                        style = MaterialTheme.typography.labelLarge, color = ext.onHero.copy(alpha = 0.85f),
                    )
                    AmountText(bal.abs(), style = MaterialTheme.typography.displaySmall, color = ext.onHero)
                    val info = listOfNotNull(
                        party.phone.takeIf { it.isNotBlank() },
                        party.gstin.takeIf { it.isNotBlank() }?.let { "GSTIN $it" },
                        IndianStates.byCode(party.stateCode)?.name,
                    ).joinToString(" · ")
                    if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall, color = ext.onHero.copy(alpha = 0.85f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    QuickButton("Call", AppIcons.Phone, enabled = party.phone.isNotBlank(), modifier = Modifier.weight(1f)) { Sharing.dial(context, party.phone) }
                    // The reminder says money is pending with the shop, so only customers who owe something get one
                    QuickButton("Remind", AppIcons.WhatsApp, enabled = isCustomer && party.phone.isNotBlank() && p.balance.paise > 0L, modifier = Modifier.weight(1f)) {
                        Sharing.whatsAppMessage(context, party.phone, viewModel.reminderText())
                    }
                    QuickButton("Statement", AppIcons.FilePdf, modifier = Modifier.weight(1f)) { viewModel.shareStatement(context) }
                }
            }
            item { Text("Transactions", style = MaterialTheme.typography.titleMedium) }
            if (ledger.isEmpty()) {
                item {
                    SurfaceCard {
                        EmptyState(AppIcons.ListBullets, "No transactions yet", "Bills and payments with ${party.name} will be listed here.")
                    }
                }
            } else {
                item {
                    SurfaceCard {
                        Column {
                            val rows = ledger.asReversed()
                            rows.forEachIndexed { i, e ->
                                LedgerRow(e) { if (e.kind != "OPENING") (if (e.isPayment) onOpenPayment else onOpenInvoice)(e.refId) }
                                if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    SurfaceCard(modifier = modifier, onClick = if (enabled) onClick else null) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun LedgerRow(e: LedgerEntry, onClick: () -> Unit) {
    val ext = BillingTheme.extendedColors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(PartyDetailViewModel.kindLabel(e.kind) + if (e.kind != "OPENING") " · ${e.number}" else "", style = MaterialTheme.typography.titleSmall)
            Text(e.date.format(ShortDateFormat) + " · Balance " + PartyDetailViewModel.balanceLabel(e.balance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val color: Color = if (e.amount.paise >= 0) MaterialTheme.colorScheme.onSurface else ext.received
        Column(horizontalAlignment = Alignment.End) {
            AmountText(e.amount.abs(), style = MaterialTheme.typography.titleSmall, color = color)
            Text(if (e.amount.paise >= 0) "Debit" else "Credit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
