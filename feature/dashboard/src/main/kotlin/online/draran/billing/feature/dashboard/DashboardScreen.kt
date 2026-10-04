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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.LocalTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import online.draran.billing.core.model.BusinessType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
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
import online.draran.billing.core.designsystem.component.AnimatedAmountText
import online.draran.billing.core.designsystem.component.NameAvatar
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.core.designsystem.component.IconBadge
import online.draran.billing.core.designsystem.component.KpiCard
import online.draran.billing.core.designsystem.component.QuickAction
import online.draran.billing.core.designsystem.component.SectionHeader
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.component.WeeklyBarChart
import online.draran.billing.core.designsystem.icon.AppIcons
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
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    contentPadding: PaddingValues,
    onOpenRecent: (RecentTransaction) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logo by viewModel.logo.collectAsStateWithLifecycle()
    val businessType by viewModel.businessType.collectAsStateWithLifecycle()
    // Read from the clock here, not from the saved state: the greeting must change while the app stays open
    var greeting by remember { mutableStateOf(Greeting.at(LocalTime.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            greeting = Greeting.at(LocalTime.now())
        }
    }
    DashboardScreen(
        state = state.copy(greeting = greeting),
        logo = logo,
        businessType = businessType,
        onNavigate = onNavigate,
        isDarkTheme = isDarkTheme,
        onToggleTheme = onToggleTheme,
        contentPadding = contentPadding,
        onOpenRecent = onOpenRecent,
    )
}

/** Stateless dashboard; rendered directly in previews and screenshot tests. */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onNavigate: (DashboardDestination) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    onOpenRecent: (RecentTransaction) -> Unit = {},
    logo: ImageBitmap? = null,
    businessType: BusinessType = BusinessType.RETAIL,
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
                Header(
                    greeting = state.greeting,
                    businessName = state.businessName,
                    logo = logo,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onSettings = { onNavigate(DashboardDestination.SETTINGS) },
                )
            }
            item(key = "hero") {
                val context = androidx.compose.ui.platform.LocalContext.current
                HeroSalesCard(state, onShare = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, state.summaryText(java.time.LocalDate.now()))
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share today's summary")) }
                })
            }
            item(key = "kpis") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    KpiCard(
                        label = stringResource(R.string.dashboard_to_collect),
                        amount = state.toCollect,
                        icon = AppIcons.ArrowDownLeft,
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
                        icon = AppIcons.ArrowUpRight,
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
            if (state.overdueCount > 0) {
                item(key = "overdue") {
                    OverdueCard(state.overdueCount, state.overdue, onClick = { onNavigate(DashboardDestination.OVERDUE) })
                }
            }
            item(key = "actions") {
                QuickActionsCard(onNavigate, businessType)
            }
            if (state.lowStockCount > 0) {
                item(key = "low-stock") {
                    LowStockBanner(state.lowStockCount, onClick = { onNavigate(DashboardDestination.LOW_STOCK) })
                }
            }
            item(key = "chart") {
                WeekCard(state)
            }
            if (state.topItems.isNotEmpty()) {
                item(key = "top-items") {
                    TopItemsCard(state.topItems, onSeeAll = { onNavigate(DashboardDestination.ITEM_SALES) })
                }
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
                            icon = AppIcons.Receipt,
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
                                TransactionRow(txn, onClick = { onOpenRecent(txn) })
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
private fun Header(
    greeting: Greeting,
    businessName: String,
    logo: ImageBitmap?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (logo != null) Color.White else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                Image(logo, contentDescription = "Business logo", contentScale = ContentScale.Fit, modifier = Modifier.padding(4.dp))
            } else {
                Text(
                    text = businessName.initials(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    when (greeting) {
                        Greeting.MORNING -> R.string.dashboard_greeting_morning
                        Greeting.AFTERNOON -> R.string.dashboard_greeting_afternoon
                        Greeting.EVENING -> R.string.dashboard_greeting_evening
                        Greeting.NIGHT -> R.string.dashboard_greeting_night
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
        // One tap to switch between light and dark
        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (isDarkTheme) AppIcons.Sun else AppIcons.Moon,
                contentDescription = stringResource(
                    if (isDarkTheme) R.string.dashboard_switch_light else R.string.dashboard_switch_dark,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                AppIcons.Settings,
                contentDescription = stringResource(R.string.dashboard_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroSalesCard(state: DashboardUiState, onShare: () -> Unit) {
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
                    AppIcons.CurrencyInr,
                    contentDescription = null,
                    tint = ext.onHero.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.dashboard_today_sales),
                    style = MaterialTheme.typography.labelLarge,
                    color = ext.onHero.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(AppIcons.Share, contentDescription = "Share today's summary", tint = ext.onHero.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            AnimatedAmountText(
                amount = state.todaySales,
                style = MaterialTheme.typography.displaySmall,
                color = ext.onHero,
                countUp = true,
                durationMillis = 900,
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
            imageVector = if (up) AppIcons.TrendUp else AppIcons.TrendDown,
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
    ActionSpec(R.string.action_new_sale, AppIcons.Receipt, DashboardDestination.NEW_SALE, Tone.PRIMARY),
    ActionSpec(R.string.action_counter, AppIcons.CashRegister, DashboardDestination.COUNTER, Tone.PRIMARY),
    ActionSpec(R.string.action_payment_in, AppIcons.HandCoins, DashboardDestination.PAYMENT_IN, Tone.RECEIVED),
    ActionSpec(R.string.action_add_item, AppIcons.Package, DashboardDestination.ADD_ITEM, Tone.PRIMARY),
    ActionSpec(R.string.action_purchase, AppIcons.ShoppingCart, DashboardDestination.PURCHASE, Tone.DUE),
    ActionSpec(R.string.action_expense, AppIcons.Wallet, DashboardDestination.EXPENSE, Tone.ERROR),
    ActionSpec(R.string.action_estimate, AppIcons.NotePencil, DashboardDestination.ESTIMATE, Tone.DUE),
    ActionSpec(R.string.action_reports, AppIcons.ChartBar, DashboardDestination.REPORTS, Tone.RECEIVED),
)

@Composable
private fun QuickActionsCard(onNavigate: (DashboardDestination) -> Unit, businessType: BusinessType) {
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
                            label = when {
                                businessType == BusinessType.RETAIL -> stringResource(spec.labelRes)
                                spec.destination == DashboardDestination.NEW_SALE -> businessType.newSaleLabel
                                spec.destination == DashboardDestination.ADD_ITEM -> "Add ${businessType.item.lowercase()}"
                                else -> stringResource(spec.labelRes)
                            },
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
private fun OverdueCard(count: Int, amount: online.draran.billing.core.model.Money, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(AppIcons.Calendar, MaterialTheme.colorScheme.onError, MaterialTheme.colorScheme.error, size = 40)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count == 1) "1 bill is overdue" else "$count bills are overdue",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text("Bills past their due date. Tap to view.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
            }
            AnimatedAmountText(amount, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer, showPaise = false, countUp = true)
        }
    }
}

@Composable
private fun TopItemsCard(items: List<TopItemUi>, onSeeAll: () -> Unit) {
    val max = items.maxOf { it.amount.paise }.coerceAtLeast(1)
    val ext = BillingTheme.extendedColors
    SurfaceCard {
        Column(Modifier.padding(Spacing.lg)) {
            SectionHeader(title = "Top sellers this month", action = { TextButton(onClick = onSeeAll) { Text("See all") } })
            Spacer(Modifier.height(Spacing.sm))
            items.forEachIndexed { index, item ->
                // Bars grow in, a little after one another
                val grow = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
                androidx.compose.runtime.LaunchedEffect(item.amount) {
                    grow.animateTo(item.amount.paise.toFloat() / max, androidx.compose.animation.core.tween(600, delayMillis = 120 * index))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NameAvatar(item.name, size = 36)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            AmountText(item.amount, style = MaterialTheme.typography.titleSmall, showPaise = false)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ext.chartBar.copy(alpha = 0.35f))) {
                            Box(Modifier.fillMaxWidth(grow.value.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(ext.chartBarHighlight))
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("${item.quantity} sold", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
            Icon(AppIcons.Warning, contentDescription = null, tint = ext.due)
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
        TransactionType.SALE -> Quad(AppIcons.Receipt, scheme.primary, scheme.primaryContainer, R.string.txn_sale)
        TransactionType.PURCHASE -> Quad(AppIcons.ShoppingCart, ext.due, ext.dueContainer, R.string.txn_purchase)
        TransactionType.PAYMENT_IN -> Quad(AppIcons.HandCoins, ext.received, ext.receivedContainer, R.string.txn_payment_in)
        TransactionType.PAYMENT_OUT -> Quad(AppIcons.ArrowUpRight, scheme.error, scheme.errorContainer, R.string.txn_payment_out)
        TransactionType.EXPENSE -> Quad(AppIcons.Wallet, scheme.error, scheme.errorContainer, R.string.txn_expense)
        TransactionType.ESTIMATE -> Quad(AppIcons.NotePencil, ext.due, ext.dueContainer, R.string.txn_estimate)
        TransactionType.SALE_RETURN -> Quad(AppIcons.Return, scheme.error, scheme.errorContainer, R.string.txn_sale_return)
        TransactionType.PURCHASE_RETURN -> Quad(AppIcons.Return, ext.due, ext.dueContainer, R.string.txn_purchase_return)
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
            imageVector = if (ok) AppIcons.CloudCheck else AppIcons.CloudSlash,
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
