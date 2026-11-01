package online.draran.billing.feature.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.component.cardPages
import online.draran.billing.core.designsystem.component.pretty
import online.draran.billing.core.designsystem.component.rememberDiscardGuard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.CashMoveKind
import online.draran.billing.core.model.CashMovement
import online.draran.billing.core.model.MoneyAccount

private fun rs(m: online.draran.billing.core.model.Money) = IndianFormat.rupees(m, showPaise = false)

@Composable
fun CashBankRoute(
    onBack: () -> Unit,
    onSetup: () -> Unit,
    onMove: () -> Unit,
    onCount: () -> Unit,
    onOpen: (CashMovement) -> Unit,
    viewModel: CashBankViewModel = hiltViewModel(),
) {
    val balances by viewModel.balances.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val ext = BillingTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    Scaffold(topBar = { AppTopBar("Cash and bank", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    KpiCard(
                        "Cash in hand", balances.cash, AppIcons.Money,
                        if (balances.cash.isNegative) scheme.error else ext.received,
                        if (balances.cash.isNegative) scheme.errorContainer else ext.receivedContainer,
                        Modifier.weight(1f),
                        onClick = { viewModel.account.value = MoneyAccount.CASH },
                    )
                    KpiCard(
                        "Bank and UPI", balances.bank, AppIcons.Bank, scheme.primary, scheme.primaryContainer, Modifier.weight(1f),
                        onClick = { viewModel.account.value = MoneyAccount.BANK },
                    )
                }
            }
            item {
                val setup = balances.setup
                SurfaceCard {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (setup == null) {
                            Text("Set your opening balance", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Enter the cash in your drawer and your bank balance on a date. From then on, every cash entry " +
                                    "updates cash in hand and every UPI, card, bank or cheque entry updates the bank balance.",
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                            )
                            Button(onClick = onSetup, modifier = Modifier.padding(top = Spacing.xs)) { Text("Set opening balance") }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Counted from ${setup.startDate.pretty()}: opening cash ${rs(setup.cash)}, bank ${rs(setup.bank)}",
                                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = onSetup) { Icon(AppIcons.Edit, "Change opening balance") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                OutlinedButton(onClick = onMove, modifier = Modifier.weight(1f)) { Text("Move money") }
                                OutlinedButton(onClick = onCount, modifier = Modifier.weight(1f)) { Text("Count cash") }
                            }
                        }
                    }
                }
            }
            if (balances.cash.isNegative) {
                item {
                    Surface(color = scheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            "Cash in hand is below zero. An entry may be missing, or recorded as cash when it was paid another way.",
                            modifier = Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodySmall, color = scheme.onErrorContainer,
                        )
                    }
                }
            }
            item {
                Text(
                    "Worked out from your bills, payments, expenses, and capital and loans. Credit sales change it only when the money is received. Profit is not affected.",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                )
            }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
                    SegmentedButton(account == MoneyAccount.CASH, { viewModel.account.value = MoneyAccount.CASH }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Cash book") }
                    SegmentedButton(account == MoneyAccount.BANK, { viewModel.account.value = MoneyAccount.BANK }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Bank and UPI") }
                }
            }
            if (lines.isEmpty()) {
                item {
                    SurfaceCard {
                        EmptyState(
                            AppIcons.Money,
                            if (account == MoneyAccount.CASH) "No cash entries yet" else "No bank or UPI entries yet",
                            "Cash sales, payments and expenses appear here with the balance after each one.",
                        )
                    }
                }
            } else {
                item { SectionHeader(if (account == MoneyAccount.CASH) "Cash book, newest first" else "Bank and UPI, newest first", Modifier.padding(top = Spacing.xs)) }
                cardPages(lines, "cash-$account") { line ->
                    val m = line.movement
                    val incoming = !m.amount.isNegative
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(m) }.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            if (incoming) AppIcons.ArrowDownLeft else AppIcons.ArrowUpRight,
                            if (incoming) ext.received else scheme.error,
                            if (incoming) ext.receivedContainer else scheme.errorContainer,
                            size = 40,
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                listOfNotNull(m.date.format(ShortDateFormat), m.describe().takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                (if (incoming) "+" else "−") + rs(m.amount.let { if (it.isNegative) -it else it }),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (incoming) ext.received else scheme.error,
                            )
                            Text("Bal ${rs(line.balance)}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CashSetupRoute(onBack: () -> Unit, viewModel: CashSetupViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val leave = rememberDiscardGuard(viewModel.isDirty, onExit = onBack)
    Scaffold(
        topBar = { AppTopBar("Opening balance", subtitle = "Cash and bank", onBack = leave) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                viewModel.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.sm)) }
                Button(onClick = { viewModel.save(onBack) }, enabled = !viewModel.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    DateField("Balances on", viewModel.date, { viewModel.date = it })
                    MoneyField(viewModel.cash, { viewModel.cash = it }, "Cash in hand", supporting = "The cash in your drawer at the start of that day")
                    MoneyField(viewModel.bank, { viewModel.bank = it }, "Bank and UPI", supporting = "Your bank balance at the start of that day (optional)")
                }
            }
            Text(
                "Entries from this date onwards are counted. Entries before it are left out of the balance.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}

@Composable
fun CashMoveRoute(moveId: Long, kind: CashMoveKind?, onBack: () -> Unit, viewModel: CashMoveViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load(moveId, kind) }
    var confirmDelete by remember { mutableStateOf(false) }
    val leave = rememberDiscardGuard(viewModel.isDirty, onExit = onBack)
    val count = viewModel.count
    Scaffold(
        topBar = {
            AppTopBar(if (count != null) "Cash count" else "Move money", subtitle = "Cash and bank", onBack = leave, actions = {
                if (moveId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Trash, "Delete") }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                viewModel.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.sm)) }
                if (count == null) {
                    Button(onClick = { viewModel.save(onBack) }, enabled = !viewModel.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (count != null) {
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text("Counted on ${count.date.pretty()}", style = MaterialTheme.typography.titleSmall)
                        count.counted?.let { Text("Cash found: ${IndianFormat.rupees(it)}", style = MaterialTheme.typography.bodyMedium) }
                        Text(differenceText(count.amount), style = MaterialTheme.typography.bodyMedium)
                        if (count.note.isNotBlank()) Text(count.note, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "A count cannot be changed. Delete it and count again if it was wrong.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                SectionCard(title = "Type") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ChipRow(listOf(CashMoveKind.DEPOSIT, CashMoveKind.WITHDRAWAL), viewModel.kind, { if (it == CashMoveKind.DEPOSIT) "Cash to bank" else "Bank to cash" }, { viewModel.kind = it })
                        Text(
                            if (viewModel.kind == CashMoveKind.DEPOSIT) "Cash put into the bank: cash in hand goes down and the bank balance goes up."
                            else "Cash taken out of the bank or an ATM: the bank balance goes down and cash in hand goes up.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SectionCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        MoneyField(viewModel.amount, { viewModel.amount = it }, "Amount *", error = viewModel.amountError.takeIf { viewModel.showErrors })
                        DateField("Date", viewModel.date, { viewModel.date = it })
                        FormField(viewModel.note, { viewModel.note = it }, "Note (optional)")
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete entry?", "The balances will be updated.", "Delete", { viewModel.delete(onBack) }, { confirmDelete = false }, destructive = true)
}

internal fun differenceText(diff: online.draran.billing.core.model.Money): String = when {
    diff.isNegative -> "Short by ${IndianFormat.rupees(-diff)}"
    diff.isZero -> "Matches the entries"
    else -> "Excess of ${IndianFormat.rupees(diff)}"
}

@Composable
fun CashCountRoute(onBack: () -> Unit, viewModel: CashCountViewModel = hiltViewModel()) {
    val expected by viewModel.expected.collectAsStateWithLifecycle()
    val leave = rememberDiscardGuard(viewModel.isDirty, onExit = onBack)
    Scaffold(
        topBar = { AppTopBar("Count cash", subtitle = viewModel.date.pretty(), onBack = leave) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                viewModel.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.sm)) }
                Button(onClick = { viewModel.save(onBack) }, enabled = !viewModel.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save count") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("As per your entries: ${expected?.let { IndianFormat.rupees(it) } ?: "…"}", style = MaterialTheme.typography.titleSmall)
                    MoneyField(viewModel.counted, { viewModel.counted = it }, "Cash in the drawer *", error = viewModel.countedError.takeIf { viewModel.showErrors })
                    val c = viewModel.countedMoney
                    val e = expected
                    if (c != null && e != null) {
                        val diff = c - e
                        Text(differenceText(diff), style = MaterialTheme.typography.bodyMedium, color = if (diff.isNegative) MaterialTheme.colorScheme.error else BillingTheme.extendedColors.received)
                    }
                    FormField(viewModel.note, { viewModel.note = it }, "Note (optional)")
                }
            }
            Text(
                "Saving keeps any shortage or excess as an entry, so cash in hand then matches the drawer. Your bills and profit do not change.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}
