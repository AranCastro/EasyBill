package online.draran.billing.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.AppFab
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.ChipRow
import online.draran.billing.core.designsystem.component.ConfirmDialog
import online.draran.billing.core.designsystem.component.DateField
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.core.designsystem.component.FormField
import online.draran.billing.core.designsystem.component.IconBadge
import online.draran.billing.core.designsystem.component.KpiCard
import online.draran.billing.core.designsystem.component.MoneyField
import online.draran.billing.core.designsystem.component.SectionCard
import online.draran.billing.core.designsystem.component.SectionHeader
import online.draran.billing.core.designsystem.component.ShortDateFormat
import online.draran.billing.core.designsystem.component.StatusPill
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.component.cardPages
import online.draran.billing.core.designsystem.component.rememberContactPicker
import online.draran.billing.core.designsystem.component.rememberDiscardGuard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.Capital
import online.draran.billing.core.model.CapitalKind
import online.draran.billing.core.model.LoanBalance
import online.draran.billing.core.model.PaymentMode

/** What each kind means, in one line under the choice. */
internal val CapitalKind.explanation: String
    get() = when (this) {
        CapitalKind.LOAN_RECEIVED -> "Money borrowed for the business from family, a friend or a bank. It has to be paid back."
        CapitalKind.LOAN_REPAID -> "Part or all of a loan paid back to the lender."
        CapitalKind.OWNER_IN -> "Your own money put into the business. It is not paid back."
        CapitalKind.OWNER_OUT -> "Money you take from the business for personal or home use."
    }

@Composable
fun CapitalRoute(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: (kind: CapitalKind?, person: String) -> Unit,
    viewModel: CapitalViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val ext = BillingTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = { AppTopBar("Capital and loans", onBack = onBack) },
        floatingActionButton = { AppFab("Add entry", onClick = { onNew(null, "") }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    KpiCard("Loans to repay", summary.toRepay, AppIcons.Bank, scheme.error, scheme.errorContainer, Modifier.weight(1f))
                    KpiCard("Own capital", summary.ownCapital, AppIcons.PiggyBank, ext.received, ext.receivedContainer, Modifier.weight(1f))
                }
            }
            if (entries.isEmpty()) {
                item {
                    SurfaceCard {
                        EmptyState(
                            AppIcons.PiggyBank,
                            "No capital or loans yet",
                            "Record money you put into the business or borrow from family or a bank. It is kept out of sales and profit, and you can see what is left to repay.",
                            actionLabel = "Add entry",
                            onAction = { onNew(null, "") },
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "Loans and your own money are not sales, so they do not change your profit. They do show in the cash flow report.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    )
                }
                if (summary.loans.isNotEmpty()) {
                    item { SectionHeader("Loans", Modifier.padding(top = Spacing.sm)) }
                    item {
                        SurfaceCard {
                            Column {
                                summary.loans.forEachIndexed { i, loan ->
                                    LenderRow(loan, onRepay = { onNew(CapitalKind.LOAN_REPAID, loan.lender) })
                                    if (i < summary.loans.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = scheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
                item { SectionHeader("All entries", Modifier.padding(top = Spacing.sm)) }
                cardPages(entries, "capital") { e ->
                    val (tint, container) = if (e.kind.moneyIn) ext.received to ext.receivedContainer else scheme.error to scheme.errorContainer
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(if (e.kind.isLoan) AppIcons.Bank else AppIcons.PiggyBank, tint, container, size = 40)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(e.person.ifBlank { e.kind.label }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                listOfNotNull(if (e.person.isBlank()) null else e.kind.label, e.date.format(ShortDateFormat), e.mode.label, e.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        AmountText(if (e.kind.moneyIn) e.amount else -e.amount, style = MaterialTheme.typography.titleSmall, showPaise = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun LenderRow(loan: LoanBalance, onRepay: () -> Unit) {
    val ext = BillingTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.Bank, if (loan.isCleared) ext.received else scheme.error, if (loan.isCleared) ext.receivedContainer else scheme.errorContainer, size = 40)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(loan.lender, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    "Received ${IndianFormat.rupees(loan.received, showPaise = false)} · Repaid ${IndianFormat.rupees(loan.repaid, showPaise = false)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (loan.isCleared) {
                StatusPill("Repaid in full")
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    AmountText(loan.outstanding, style = MaterialTheme.typography.titleSmall, showPaise = false)
                    Text("to repay", style = MaterialTheme.typography.labelSmall, color = scheme.error)
                }
            }
        }
        if (!loan.isCleared) {
            TextButton(onClick = onRepay, modifier = Modifier.align(Alignment.End)) { Text("Record repayment") }
        }
    }
}

@Composable
fun CapitalEditorRoute(
    entryId: Long,
    kind: CapitalKind?,
    person: String,
    onBack: () -> Unit,
    viewModel: CapitalEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load(entryId, kind, person) }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val leave = rememberDiscardGuard(viewModel.isDirty, onExit = onBack)
    val pickFromContacts = rememberContactPicker { c -> if (c.name.isNotBlank()) viewModel.selectPerson(c.name) }
    val k = viewModel.kind
    Scaffold(
        topBar = {
            AppTopBar(if (entryId == 0L) "New entry" else "Edit entry", subtitle = "Capital and loans", onBack = leave, actions = {
                if (entryId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Trash, "Delete") }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                viewModel.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.sm)) }
                Button(onClick = { viewModel.save(onBack) }, enabled = !viewModel.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save entry") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionCard(title = "Type") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // All four kinds stay in view, wrapping to a second line on narrow phones
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CapitalKind.entries.forEach { option ->
                            FilterChip(selected = option == k, onClick = { viewModel.selectKind(option) }, label = { Text(option.label) })
                        }
                    }
                    Text(k.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FormField(
                        viewModel.person,
                        { viewModel.selectPerson(it) },
                        when (k) {
                            CapitalKind.LOAN_RECEIVED -> "Lent by *"
                            CapitalKind.LOAN_REPAID -> "Repaid to *"
                            else -> "Owner or partner (optional)"
                        },
                        placeholder = if (k.isLoan) "For example, Father or Canara Bank" else null,
                        error = viewModel.personError.takeIf { viewModel.showErrors },
                        capitalization = KeyboardCapitalization.Words,
                        trailing = { IconButton(onClick = pickFromContacts) { Icon(AppIcons.UserPlus, "Choose from phone contacts") } },
                    )
                    val choices = viewModel.lenderChoices(summary)
                    if (choices.isNotEmpty()) {
                        // Nothing is selected while the typed name matches no lender
                        ChipRow(choices, choices.firstOrNull { Capital.key(it) == Capital.key(viewModel.person) } ?: "", { it }, { viewModel.selectPerson(it) })
                    }
                    val owed = if (k == CapitalKind.LOAN_REPAID) viewModel.owedTo(summary) else null
                    MoneyField(
                        viewModel.amount,
                        { viewModel.amount = it },
                        "Amount *",
                        error = viewModel.amountError.takeIf { viewModel.showErrors },
                        supporting = owed?.let { if (it.paise > 0) "Still owed: ${IndianFormat.rupees(it)}" else "Nothing is owed to this lender now" },
                    )
                    DateField("Date", viewModel.date, { viewModel.date = it })
                }
            }
            SectionCard(title = if (k.moneyIn) "Received by" else "Paid by") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ChipRow(PaymentMode.entries, viewModel.mode, { it.label }, { viewModel.mode = it })
                    FormField(viewModel.note, { viewModel.note = it }, "Note (optional)", placeholder = if (k.isLoan) "For example, to be repaid by March" else null)
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete entry?", "The balances will be updated.", "Delete", { viewModel.delete(onBack) }, { confirmDelete = false }, destructive = true)
}
