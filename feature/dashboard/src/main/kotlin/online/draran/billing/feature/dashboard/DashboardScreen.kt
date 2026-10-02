package online.draran.billing.feature.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.core.designsystem.component.IconBadge
import online.draran.billing.core.designsystem.component.KpiCard
import online.draran.billing.core.designsystem.component.QuickAction
import online.draran.billing.core.designsystem.component.SectionHeader
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.component.WeeklyBarChart
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.designsystem.theme.TABULAR_NUMBERS
import online.draran.billing.core.model.RecentTransaction
import online.draran.billing.core.model.TransactionType
import online.draran.billing.core.model.sum
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** Stateful entry point used by the app's navigation graph. */
@Composable
fun DashboardRoute(
    onNavigate: (DashboardDestination) -> Unit,
    contentPadding: PaddingValues,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardScreen(state = state, onNavigate = onNavigate, contentPadding = contentPadding)
}

/** Stateless dashboard; rendered directly in previews and screenshot tests. */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onNavigate: (DashboardDestination) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val ext = BillingTheme.extendedColors
    // Surface sets the content colour so text and icons follow light/dark theme
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = contentPadding.calculateTopPadding() + Spacing.sm,
                // Leave room for the floating "New Sale" button
                bottom = contentPadding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "header") {
                Header(state.greeting, state.businessName, onSettings = { onNavigate(DashboardDestination.SETTINGS) })
            }
            item(key = "hero") {
                HeroSalesCard(state)
            }
            item(key = "kpis") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    KpiCard(
                        label = stringResource(R.string.dashboard_to_collect),
                        amount = state.toCollect,
                        icon = Icons.AutoMirrored.Rounded.CallReceived,
                        accent = ext.due,
                        accentContainer = ext.dueContainer,
                        caption = pluralStringResource(
                            R.plurals.dashboard_parties,
                            state.toCollectPartyCount,
                            state.toCollectPartyCount,
                        ).takeIf { state.toCollectPartyCount > 0 },
                        onClick = { onNavigate(DashboardDestination.TO_COLLECT) },
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        label = stringResource(R.string.dashboard_to_pay),
                        amount = state.toPay,
                        icon = Icons.AutoMirrored.Rounded.CallMade,
                        accent = MaterialTheme.colorScheme.error,
                        accentContainer = MaterialTheme.colorScheme.errorContainer,
                        caption = pluralStringResource(
                            R.plurals.dashboard_parties_to,
                            state.toPayPartyCount,
                            state.toPayPartyCount,
                        ).takeIf { state.toPayPartyCount > 0 },
                        onClick = { onNavigate(DashboardDestination.TO_PAY) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "actions") {
                QuickActionsCard(onNavigate)
            }
            if (state.lowStockCount > 0) {
                item(key = "low-stock") {
                    LowStockBanner(state.lowStockCount, onClick = { onNavigate(DashboardDestination.LOW_STOCK) })
                }
            }
            item(key = "chart") {
                WeekCard(state)
            }
            item(key = "recent-header") {
                SectionHeader(
                    title = stringResource(R.string.dashboard_recent_activity),
                    action = if (state.recent.isNotEmpty()) {
                        {
                            TextButton(onClick = { onNavigate(DashboardDestination.ALL_TRANSACTIONS) }) {
                                Text(stringResource(R.string.dashboard_see_all))
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            if (state.recent.isEmpty()) {
                item(key = "recent-empty") {
                    SurfaceCard {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            title = stringResource(R.string.dashboard_empty_title),
                            message = stringResource(R.string.dashboard_empty_message),
                            actionLabel = stringResource(R.string.action_new_sale),
                            onAction = { onNavigate(DashboardDestination.NEW_SALE) },
                        )
                    }
                }
            } else {
                item(key = "recent-list") {
                    SurfaceCard {
                        Column {
                            state.recent.forEachIndexed { index, txn ->
                                TransactionRow(txn, onClick = { onNavigate(DashboardDestination.ALL_TRANSACTIONS) })
                                if (index < state.recent.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "backup") {
                BackupStatus(state.lastBackupDaysAgo, onBackup = { onNavigate(DashboardDestination.BACKUP) })
            }
        }
    }
}

@Composable
private fun Header(greeting: Greeting, businessName: String, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = businessName.initials(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (greeting) {
                        Greeting.MORNING -> R.string.dashboard_greeting_morning
                        Greeting.AFTERNOON -> R.string.dashboard_greeting_afternoon
                        Greeting.EVENING -> R.string.dashboard_greeting_evening
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = businessName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.dashboard_settings))
        }
    }
}

@Composable
private fun HeroSalesCard(state: DashboardUiState) {
    val ext = BillingTheme.extendedColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(ext.heroBrush),
    ) {
        // Decorative circles for depth
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 0.dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp)
                .size(90.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Column(Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Wallet,
                    contentDescription = null,
                    tint = ext.onHero.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.dashboard_today_sales),
                    style = MaterialTheme.typography.labelLarge,
                    color = ext.onHero.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            AmountText(
                amount = state.todaySales,
                style = MaterialTheme.typography.displaySmall,
                color = ext.onHero,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.dashboard_bills_today, state.todayBillCount, state.todayBillCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.onHero.copy(alpha = 0.85f),
                )
                state.changeVsYesterdayPercent?.let { change ->
                    Spacer(Modifier.width(Spacing.md))
                    ChangeChip(change, ext.onHero)
                }
            }
        }
    }
}

@Composable
private fun ChangeChip(percent: Int, contentColor: Color) {
    val up = percent >= 0
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.dashboard_vs_yesterday, "${if (up) "+" else "−"}${abs(percent)}%"),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

private data class ActionSpec(
    val labelRes: Int,
    val icon: ImageVector,
    val destination: DashboardDestination,
    val tone: Tone,
)

private enum class Tone { PRIMARY, RECEIVED, DUE, ERROR }

private val quickActions = listOf(
    ActionSpec(R.string.action_new_sale, Icons.AutoMirrored.Rounded.ReceiptLong, DashboardDestination.NEW_SALE, Tone.PRIMARY),
    ActionSpec(R.string.action_counter, Icons.Rounded.PointOfSale, DashboardDestination.COUNTER, Tone.PRIMARY),
    ActionSpec(R.string.action_payment_in, Icons.Rounded.Payments, DashboardDestination.PAYMENT_IN, Tone.RECEIVED),
    ActionSpec(R.string.action_add_item, Icons.Rounded.Inventory2, DashboardDestination.ADD_ITEM, Tone.PRIMARY),
    ActionSpec(R.string.action_purchase, Icons.Rounded.AddShoppingCart, DashboardDestination.PURCHASE, Tone.DUE),
    ActionSpec(R.string.action_expense, Icons.Rounded.ShoppingBag, DashboardDestination.EXPENSE, Tone.ERROR),
    ActionSpec(R.string.action_estimate, Icons.Rounded.EditNote, DashboardDestination.ESTIMATE, Tone.DUE),
    ActionSpec(R.string.action_reports, Icons.Rounded.BarChart, DashboardDestination.REPORTS, Tone.RECEIVED),
)

@Composable
private fun QuickActionsCard(onNavigate: (DashboardDestination) -> Unit) {
    val ext = BillingTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    SurfaceCard {
        Column(Modifier.padding(vertical = Spacing.lg, horizontal = Spacing.sm)) {
            SectionHeader(
                title = stringResource(R.string.dashboard_quick_actions),
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
            Spacer(Modifier.height(Spacing.sm))
            quickActions.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { spec ->
                        val (tint, container) = when (spec.tone) {
                            Tone.PRIMARY -> scheme.primary to scheme.primaryContainer
                            Tone.RECEIVED -> ext.received to ext.receivedContainer
                            Tone.DUE -> ext.due to ext.dueContainer
                            Tone.ERROR -> scheme.error to scheme.errorContainer
                        }
                        QuickAction(
                            label = stringResource(spec.labelRes),
                            icon = spec.icon,
                            tint = tint,
                            container = container,
                            onClick = { onNavigate(spec.destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(state: DashboardUiState) {
    val ext = BillingTheme.extendedColors
    val weekTotal = state.week.map { it.total }.sum()
    SurfaceCard {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dashboard_last_7_days),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AmountText(
                    amount = weekTotal,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    showPaise = false,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            WeeklyBarChart(
                values = state.week.map { it.total },
                labels = state.week.map { it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) },
                barColor = ext.chartBar,
                highlightColor = ext.chartBarHighlight,
            )
        }
    }
}

@Composable
private fun LowStockBanner(count: Int, onClick: () -> Unit) {
    val ext = BillingTheme.extendedColors
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = ext.dueContainer,
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = ext.due)
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(
                    stringResource(R.string.dashboard_low_stock_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    pluralStringResource(R.plurals.dashboard_low_stock_message, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

@Composable
private fun TransactionRow(txn: RecentTransaction, onClick: () -> Unit) {
    val ext = BillingTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    val (icon, tint, container, typeLabel) = when (txn.type) {
        TransactionType.SALE -> Quad(Icons.AutoMirrored.Rounded.ReceiptLong, scheme.primary, scheme.primaryContainer, R.string.txn_sale)
        TransactionType.PURCHASE -> Quad(Icons.Rounded.AddShoppingCart, ext.due, ext.dueContainer, R.string.txn_purchase)
        TransactionType.PAYMENT_IN -> Quad(Icons.AutoMirrored.Rounded.CallReceived, ext.received, ext.receivedContainer, R.string.txn_payment_in)
        TransactionType.PAYMENT_OUT -> Quad(Icons.AutoMirrored.Rounded.CallMade, scheme.error, scheme.errorContainer, R.string.txn_payment_out)
        TransactionType.EXPENSE -> Quad(Icons.Rounded.ShoppingBag, scheme.error, scheme.errorContainer, R.string.txn_expense)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, tint = tint, container = container, size = 40)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                txn.partyName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${stringResource(typeLabel)} · ${txn.number} · ${txn.date.format(dateFormat)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(amount = txn.total, style = MaterialTheme.typography.titleSmall, showPaise = false)
            val isSaleOrPurchase = txn.type == TransactionType.SALE || txn.type == TransactionType.PURCHASE
            if (isSaleOrPurchase) {
                val paid = txn.balanceDue.isZero
                Text(
                    text = if (paid) {
                        stringResource(R.string.dashboard_paid)
                    } else {
                        stringResource(R.string.dashboard_due, IndianFormat.rupees(txn.balanceDue, showPaise = false))
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_NUMBERS),
                    color = if (paid) ext.received else ext.due,
                )
            }
        }
    }
}

private data class Quad(val icon: ImageVector, val tint: Color, val container: Color, val label: Int)

@Composable
private fun BackupStatus(daysAgo: Int?, onBackup: () -> Unit) {
    val ext = BillingTheme.extendedColors
    val ok = daysAgo != null && daysAgo <= 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ok) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = if (ok) ext.received else ext.due,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = when {
                daysAgo == null -> stringResource(R.string.dashboard_backup_never)
                daysAgo == 0 -> stringResource(R.string.dashboard_backup_today)
                else -> pluralStringResource(R.plurals.dashboard_backup_days, daysAgo, daysAgo)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onBackup) { Text(stringResource(R.string.dashboard_backup_now)) }
    }
}

private fun String.initials(): String =
    split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "B" }
