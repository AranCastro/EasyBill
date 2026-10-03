package online.draran.billing.feature.money

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import online.draran.billing.core.designsystem.component.SelectField
import online.draran.billing.core.designsystem.component.ShortDateFormat
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.EXPENSE_CATEGORIES
import online.draran.billing.core.model.Money
import online.draran.billing.core.model.PaymentDirection
import online.draran.billing.core.model.PaymentMode
import online.draran.billing.core.model.abs

@Composable
fun PaymentsRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: (PaymentDirection) -> Unit, viewModel: PaymentsViewModel = hiltViewModel()) {
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val ext = BillingTheme.extendedColors
    Scaffold(
        topBar = { AppTopBar("Payments", onBack = onBack) },
        floatingActionButton = {
            AppFab(if (direction == PaymentDirection.IN) "Payment in" else "Payment out", onClick = { onNew(direction) })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(direction == PaymentDirection.IN, { viewModel.direction.value = PaymentDirection.IN }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Received") }
                    SegmentedButton(direction == PaymentDirection.OUT, { viewModel.direction.value = PaymentDirection.OUT }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Paid") }
                }
            }
            item {
                KpiCard(
                    if (direction == PaymentDirection.IN) "Received this month" else "Paid this month",
                    Money(payments.filter { !it.date.isBefore(java.time.LocalDate.now().withDayOfMonth(1)) }.sumOf { it.amount.paise }),
                    if (direction == PaymentDirection.IN) AppIcons.ArrowDownLeft else AppIcons.ArrowUpRight,
                    if (direction == PaymentDirection.IN) ext.received else MaterialTheme.colorScheme.error,
                    if (direction == PaymentDirection.IN) ext.receivedContainer else MaterialTheme.colorScheme.errorContainer,
                )
            }
            if (payments.isEmpty()) {
                item { SurfaceCard { EmptyState(AppIcons.HandCoins, "No payments yet", "Payments taken with bills and separate payments appear here.") } }
            } else {
                item {
                    SurfaceCard {
                        Column {
                            payments.forEachIndexed { i, p ->
                                Row(Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(AppIcons.HandCoins, if (p.direction == PaymentDirection.IN) ext.received else MaterialTheme.colorScheme.error, if (p.direction == PaymentDirection.IN) ext.receivedContainer else MaterialTheme.colorScheme.errorContainer, size = 40)
                                    Spacer(Modifier.width(Spacing.md))
                                    Column(Modifier.weight(1f)) {
                                        Text(p.partyName.ifBlank { "Cash" }, style = MaterialTheme.typography.titleSmall)
                                        Text("${p.number} · ${p.date.format(ShortDateFormat)} · ${p.mode.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    AmountText(p.amount, style = MaterialTheme.typography.titleSmall, showPaise = false)
                                }
                                if (i < payments.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentEditorRoute(
    direction: PaymentDirection,
    paymentId: Long,
    partyId: Long,
    onBack: () -> Unit,
    viewModel: PaymentEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load(direction, paymentId, partyId) }
    val parties by viewModel.allParties.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val isIn = viewModel.direction == PaymentDirection.IN
    val candidates = parties.filter { it.party.type == viewModel.partyType || it.party.id == viewModel.partyId }
    val selected = parties.firstOrNull { it.party.id == viewModel.partyId }
    Scaffold(
        topBar = {
            AppTopBar(if (isIn) "Payment received" else "Payment made", subtitle = viewModel.number, onBack = onBack, actions = {
                if (viewModel.paymentId != 0L && !viewModel.linkedToBill) IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Trash, "Delete") }
            })
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onBack) }, enabled = !viewModel.linkedToBill, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save payment") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (viewModel.linkedToBill) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text("This payment was taken with a bill. Edit the bill to change it.", modifier = Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodyMedium)
                }
            }
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SelectField(
                        label = if (isIn) "Received from *" else "Paid to *",
                        options = candidates,
                        selected = selected,
                        optionLabel = { p -> p.party.name + if (!p.balance.isZero) " (${if (p.balance.paise > 0) "owes" else "advance"} ${IndianFormat.rupees(p.balance.abs(), showPaise = false)})" else "" },
                        onSelect = { viewModel.partyId = it.party.id },
                        error = viewModel.partyError.takeIf { viewModel.showErrors },
                        supporting = if (candidates.isEmpty()) "Add the ${viewModel.partyType.label.lowercase()} in Parties first" else null,
                    )
                    MoneyField(viewModel.amount, { viewModel.amount = it }, "Amount *", error = viewModel.amountError.takeIf { viewModel.showErrors }, supporting = "Settles the oldest unpaid bills first")
                    DateField("Date", viewModel.date, { viewModel.date = it })
                }
            }
            SectionCard(title = "Mode") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ChipRow(PaymentMode.entries, viewModel.mode, { it.label }, { viewModel.mode = it })
                    if (viewModel.mode != PaymentMode.CASH) FormField(viewModel.reference, { viewModel.reference = it }, if (viewModel.mode == PaymentMode.CHEQUE) "Cheque number" else "Reference / UTR (optional)")
                    FormField(viewModel.note, { viewModel.note = it }, "Note (optional)")
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete payment?", "The party balance will be updated.", "Delete", { viewModel.delete(onBack) }, { confirmDelete = false }, destructive = true)
}

@Composable
fun ExpensesRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: () -> Unit, viewModel: ExpensesViewModel = hiltViewModel()) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val monthStart = java.time.LocalDate.now().withDayOfMonth(1)
    Scaffold(
        topBar = { AppTopBar("Expenses", onBack = onBack) },
        floatingActionButton = { AppFab("Add expense", onClick = onNew) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item {
                KpiCard("This month", Money(expenses.filter { !it.date.isBefore(monthStart) }.sumOf { it.amount.paise }), AppIcons.Wallet, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
            }
            if (expenses.isEmpty()) {
                item { SurfaceCard { EmptyState(AppIcons.Wallet, "No expenses yet", "Record rent, salary, electricity and other costs to see true profit.", actionLabel = "Add expense", onAction = onNew) } }
            } else {
                item {
                    SurfaceCard {
                        Column {
                            expenses.forEachIndexed { i, e ->
                                Row(Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(AppIcons.Wallet, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer, size = 40)
                                    Spacer(Modifier.width(Spacing.md))
                                    Column(Modifier.weight(1f)) {
                                        Text(e.category, style = MaterialTheme.typography.titleSmall)
                                        Text(listOfNotNull(e.date.format(ShortDateFormat), e.mode.label, e.note.takeIf { it.isNotBlank() }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    AmountText(e.amount, style = MaterialTheme.typography.titleSmall, showPaise = false)
                                }
                                if (i < expenses.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseEditorRoute(expenseId: Long, onBack: () -> Unit, viewModel: ExpenseEditorViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load(expenseId) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AppTopBar(if (expenseId == 0L) "New expense" else "Edit expense", onBack = onBack, actions = {
                if (expenseId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Trash, "Delete") }
            })
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save expense") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SelectField("Category", (EXPENSE_CATEGORIES + viewModel.category).distinct(), viewModel.category, { it }, { viewModel.category = it })
                    MoneyField(viewModel.amount, { viewModel.amount = it }, "Amount *", error = viewModel.amountError.takeIf { viewModel.showErrors })
                    DateField("Date", viewModel.date, { viewModel.date = it })
                    ChipRow(PaymentMode.entries, viewModel.mode, { it.label }, { viewModel.mode = it })
                    FormField(viewModel.note, { viewModel.note = it }, "Note (optional)")
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete expense?", "This cannot be undone.", "Delete", { viewModel.delete(onBack) }, { confirmDelete = false }, destructive = true)
}
