package online.draran.billing.feature.billing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import online.draran.billing.core.data.InvoiceRepository
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.AppFab
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.ChipRow
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.core.designsystem.component.KpiCard
import online.draran.billing.core.designsystem.component.Pill
import online.draran.billing.core.designsystem.component.SearchField
import online.draran.billing.core.designsystem.component.ShortDateFormat
import online.draran.billing.core.designsystem.component.StatusBadge
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.component.payStatus
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.designsystem.component.NameAvatar
import online.draran.billing.core.designsystem.component.SkeletonRows
import online.draran.billing.core.model.DocType
import online.draran.billing.core.model.InvoiceSummary
import online.draran.billing.core.model.Money
import java.time.LocalDate
import javax.inject.Inject

enum class DocFilter(val label: String) { ALL("All"), UNPAID("Unpaid"), OVERDUE("Overdue"), PAID("Paid") }

/** Which list to show. */
enum class DocList(val title: String, val types: List<DocType>, val newType: DocType, val noun: String) {
    SALES("Sales", listOf(DocType.SALE, DocType.SALE_RETURN), DocType.SALE, "sale"),
    PURCHASES("Purchases", listOf(DocType.PURCHASE, DocType.PURCHASE_RETURN), DocType.PURCHASE, "purchase"),
    ESTIMATES("Estimates", listOf(DocType.ESTIMATE), DocType.ESTIMATE, "estimate"),
}

data class DocsUi(
    val docs: List<InvoiceSummary> = emptyList(),
    val monthTotal: Money = Money.ZERO,
    val unpaid: Money = Money.ZERO,
    val count: Int = 0,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentsViewModel @Inject constructor(repository: InvoiceRepository) : ViewModel() {
    val list = MutableStateFlow<DocList?>(null)
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(DocFilter.ALL)

    val ui: StateFlow<DocsUi> = combine(
        list.flatMapLatest { l -> if (l == null) flowOf(emptyList()) else repository.summaries(l.types) },
        query, filter, list,
    ) { all, q, f, l ->
        val needle = q.trim().lowercase()
        val monthStart = LocalDate.now().withDayOfMonth(1)
        val main = l?.newType
        DocsUi(
            docs = all
                .filter { needle.isEmpty() || it.number.lowercase().contains(needle) || it.partyName.lowercase().contains(needle) }
                .filter {
                    when (f) {
                        DocFilter.ALL -> true
                        DocFilter.UNPAID -> it.type.tracksPayment && it.balance.paise > 0
                        DocFilter.OVERDUE -> it.overdueDays(LocalDate.now()) > 0
                        DocFilter.PAID -> it.type.tracksPayment && it.balance.paise <= 0
                    }
                },
            monthTotal = Money(all.filter { it.type == main && !it.date.isBefore(monthStart) }.sumOf { it.total.paise }),
            unpaid = Money(all.filter { it.type == main && it.type.tracksPayment }.sumOf { it.balance.paise.coerceAtLeast(0) }),
            count = all.size,
            loading = l == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocsUi())
}

/** Sales tab content (inside the main scaffold). */
@Composable
fun SalesTabRoute(contentPadding: PaddingValues, onOpen: (Long) -> Unit, viewModel: DocumentsViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.list.value = DocList.SALES }
    DocumentsContent(DocList.SALES, viewModel, contentPadding, onOpen, onNew = null, showTitle = true)
}

/** Full-screen list for purchases or estimates. */
@Composable
fun DocumentsRoute(
    list: DocList,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: (DocType) -> Unit,
    initialFilter: DocFilter = DocFilter.ALL,
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    LaunchedEffect(list) {
        viewModel.list.value = list
        viewModel.filter.value = initialFilter
    }
    Scaffold(
        topBar = { AppTopBar(list.title, onBack = onBack) },
        floatingActionButton = {
            AppFab("New ${list.noun}", onClick = { onNew(list.newType) })
        },
    ) { padding ->
        DocumentsContent(list, viewModel, padding, onOpen, onNew, showTitle = false)
    }
}

@Composable
private fun DocumentsContent(
    list: DocList,
    viewModel: DocumentsViewModel,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onNew: ((DocType) -> Unit)?,
    showTitle: Boolean,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val ext = BillingTheme.extendedColors
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg, end = Spacing.lg,
            top = contentPadding.calculateTopPadding() + if (showTitle) Spacing.lg else Spacing.xs,
            bottom = contentPadding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (showTitle) item { Text(list.title, style = MaterialTheme.typography.headlineSmall) }
        if (list != DocList.ESTIMATES) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    KpiCard("This month", ui.monthTotal, AppIcons.ChartLineUp, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                    KpiCard(if (list == DocList.SALES) "To collect" else "To pay", ui.unpaid, AppIcons.Clipboard, ext.due, ext.dueContainer, Modifier.weight(1f))
                }
            }
        }
        item { SearchField(query, { viewModel.query.value = it }, "Search number or party") }
        if (list != DocList.ESTIMATES) {
            // Only sale bills have due dates
            item { ChipRow(if (list == DocList.SALES) DocFilter.entries else DocFilter.entries - DocFilter.OVERDUE, filter, { it.label }, { viewModel.filter.value = it }) }
        }
        if (ui.loading) {
            item(key = "loading") { SurfaceCard { SkeletonRows(6) } }
        } else if (ui.docs.isEmpty()) {
            item {
                SurfaceCard {
                    EmptyState(
                        AppIcons.Receipt,
                        if (ui.count == 0) "No ${list.title.lowercase()} yet" else "Nothing matches",
                        if (ui.count == 0) "Your ${list.title.lowercase()} will appear here." else "Try a different search or filter.",
                        actionLabel = onNew?.let { "New ${list.noun}" },
                        onAction = { onNew?.invoke(list.newType) },
                    )
                }
            }
        } else {
            // One card per day, with the day's total, newest first
            val today = LocalDate.now()
            ui.docs.groupBy { it.date }.forEach { (day, docs) ->
                item(key = "day-$day") {
                    val dayTotal = Money(docs.filter { it.type == list.newType }.sumOf { it.total.paise })
                    Row(Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabel(day, today), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(
                            "${docs.size} · " + online.draran.billing.core.common.IndianFormat.rupees(dayTotal, showPaise = false),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "docs-$day") {
                    SurfaceCard {
                        Column {
                            docs.forEachIndexed { i, d ->
                                DocRow(d, today) { onOpen(d.id) }
                                if (i < docs.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocRow(d: InvoiceSummary, today: LocalDate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameAvatar(d.partyName)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.partyName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (d.type == DocType.SALE_RETURN || d.type == DocType.PURCHASE_RETURN) {
                    Spacer(Modifier.width(6.dp))
                    Pill("Return", MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                }
            }
            Text(d.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            AmountText(d.total, style = MaterialTheme.typography.titleSmall, showPaise = false)
            val late = d.overdueDays(today)
            when {
                late > 0 -> Pill(if (late == 1) "Overdue · 1 day" else "Overdue · $late days", MaterialTheme.colorScheme.onError, MaterialTheme.colorScheme.error)
                d.type.tracksPayment -> StatusBadge(payStatus(d.total, d.paid))
            }
        }
    }
}

/** "Today", "Yesterday", "Monday" (this week) or "28 Sep 2026". */
internal fun dayLabel(day: LocalDate, today: LocalDate): String = when {
    day == today -> "Today"
    day == today.minusDays(1) -> "Yesterday"
    day.isAfter(today.minusDays(7)) && !day.isAfter(today) -> day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    else -> day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH))
}
