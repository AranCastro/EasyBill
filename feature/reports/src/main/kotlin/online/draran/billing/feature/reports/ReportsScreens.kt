package online.draran.billing.feature.reports

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import online.draran.billing.core.data.BusinessRepository
import online.draran.billing.core.designsystem.component.AmountText
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.EmptyState
import online.draran.billing.core.designsystem.component.IconBadge
import online.draran.billing.core.designsystem.component.huedColors
import online.draran.billing.core.designsystem.component.ListRow
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.component.pretty
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.DateRange
import online.draran.billing.core.print.PdfColumn
import online.draran.billing.core.print.Sharing
import online.draran.billing.core.print.TablePdf
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

internal fun ReportKind.icon(): ImageVector = when (this) {
    ReportKind.SALES -> AppIcons.Receipt
    ReportKind.PURCHASES -> AppIcons.ShoppingCart
    ReportKind.PROFIT_LOSS -> AppIcons.ChartLineUp
    ReportKind.DAY_BOOK -> AppIcons.ListBullets
    ReportKind.ITEM_SALES -> AppIcons.Tag
    ReportKind.STOCK -> AppIcons.Stack
    ReportKind.GST -> AppIcons.Calculator
    ReportKind.PARTY_BALANCES -> AppIcons.User
    ReportKind.EXPENSES -> AppIcons.Wallet
    ReportKind.CASH_FLOW -> AppIcons.Coins
}

@Composable
fun ReportsHubRoute(onBack: () -> Unit, onOpen: (ReportKind) -> Unit) {
    Scaffold(topBar = { AppTopBar("Reports", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.lg)) {
            item {
                SurfaceCard {
                    Column {
                        ReportKind.entries.forEachIndexed { i, k ->
                            // Each report keeps its own colour, so the list reads at a glance
                            val (tint, container) = huedColors(k.name)
                            ListRow(title = k.title, subtitle = k.description, icon = k.icon(), tint = tint, container = container, onClick = { onOpen(k) })
                            if (i < ReportKind.entries.lastIndex) HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val builder: ReportBuilder,
    private val businessRepository: BusinessRepository,
    private val branding: online.draran.billing.core.data.BrandingManager,
) : ViewModel() {
    var kind by mutableStateOf(ReportKind.SALES)
        private set
    var range by mutableStateOf(DateRange.thisMonth(LocalDate.now()))
        private set
    var content by mutableStateOf<ReportContent?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    /** Shown once as a snackbar when an export fails. */
    var message by mutableStateOf<String?>(null)

    private var loadJob: kotlinx.coroutines.Job? = null

    fun load(k: ReportKind, r: DateRange = range) {
        kind = k
        range = r
        loading = true
        // A slower earlier range must not finish last and show under the newer chip
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            content = builder.build(k, r)
            loading = false
        }
    }

    private fun subtitle() = if (kind.usesRange) "${range.start.pretty()} to ${range.end.pretty()}" else "As on ${LocalDate.now().pretty()}"

    fun exportPdf(context: Context) {
        val c = content ?: return
        viewModelScope.launch {
            runCatching {
                val b = businessRepository.get()
                val file = withContext(Dispatchers.IO) {
                    val table = ReportExport.pdfTable(c)
                    File(Sharing.sharedDir(context), Sharing.safeName("${kind.title}_${range.start}_${range.end}") + ".pdf").also {
                        TablePdf(
                            context, b.name, listOf(b.address.replace("\n", ", "), b.phone).filter { s -> s.isNotBlank() }.joinToString(" · "),
                            kind.title, subtitle(), table.columns, table.rows,
                            logo = branding.logo(b.logoFile), brand = b.accent(), autoAlign = true,
                        ).writeTo(it)
                    }
                }
                Sharing.shareFile(context, file, "application/pdf", "${kind.title} · ${subtitle()}")
            }.onFailure { message = "Could not create the PDF: ${it.message ?: "unknown error"}" }
        }
    }

    fun exportCsv(context: Context) {
        val c = content ?: return
        viewModelScope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val text = ReportExport.csv(kind.title, subtitle(), c)
                    File(Sharing.sharedDir(context), Sharing.safeName("${kind.title}_${range.start}_${range.end}") + ".csv").also { it.writeText("\uFEFF" + text) /* BOM so Excel reads text correctly */ }
                }
                Sharing.shareFile(context, file, "text/csv", kind.title)
            }.onFailure { message = "Could not create the file: ${it.message ?: "unknown error"}" }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportRoute(kind: ReportKind, onBack: () -> Unit, viewModel: ReportViewModel = hiltViewModel()) {
    LaunchedEffect(kind) { viewModel.load(kind) }
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let { snackbar.showSnackbar(it); viewModel.message = null }
    }
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(kind.title, onBack = onBack, subtitle = if (kind.usesRange) viewModel.range.label else null, actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(AppIcons.Share, contentDescription = "Export") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Share as PDF") }, leadingIcon = { Icon(AppIcons.FilePdf, null) }, onClick = { menu = false; viewModel.exportPdf(context) })
                        DropdownMenuItem(text = { Text("Share as Excel (CSV)") }, leadingIcon = { Icon(AppIcons.FileCsv, null) }, onClick = { menu = false; viewModel.exportCsv(context) })
                    }
                }
            })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (kind.usesRange) {
                item {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(DateRange.presets(today)) { r ->
                            FilterChip(selected = viewModel.range.label == r.label, onClick = { viewModel.load(kind, r) }, label = { Text(r.label) })
                        }
                        item {
                            FilterChip(selected = viewModel.range.label == "Custom", onClick = { customRange = true }, label = { Text("Custom") }, leadingIcon = { Icon(AppIcons.Calendar, null, Modifier.padding(0.dp)) })
                        }
                    }
                }
            }
            val c = viewModel.content
            if (viewModel.loading || c == null) {
                item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                return@LazyColumn
            }
            item { KpiGrid(c.kpis) }
            c.note?.let { note ->
                item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(Spacing.md))
                    }
                }
            }
            c.sections.forEach { s -> item { SectionTable(s) } }
        }
    }
    if (customRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { customRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis
                    val e = state.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        fun d(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.load(kind, DateRange(d(s), d(e), "Custom"))
                    }
                    customRange = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { customRange = false }) { Text("Cancel") } },
        ) { DateRangePicker(state = state, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun KpiGrid(kpis: List<Kpi>) {
    val ext = BillingTheme.extendedColors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        kpis.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { k ->
                    SurfaceCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(Spacing.lg)) {
                            Text(k.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            AmountText(
                                k.value, style = MaterialTheme.typography.titleLarge, showPaise = false,
                                color = when (k.tone) {
                                    Tone.GOOD -> ext.received
                                    Tone.BAD -> MaterialTheme.colorScheme.error
                                    Tone.BRAND -> MaterialTheme.colorScheme.primary
                                    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Compact mobile table: first column as title, middle columns as detail, last as value. */
@Composable
private fun SectionTable(s: ReportSection) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = Spacing.sm)) {
            s.title?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) }
            if (s.rows.isEmpty()) {
                Text("No entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            }
            s.rows.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.firstOrNull().orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val middle = r.drop(1).dropLast(1).mapIndexedNotNull { j, v -> v.takeIf { it.isNotBlank() && it != "₹0.00" }?.let { if (s.columns.size > 3) "${s.columns.getOrNull(j + 1)}: $it" else it } }
                        if (middle.isNotEmpty() && r.size > 2) Text(middle.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (r.size > 1) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(r.last(), style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                    }
                }
                if (i < s.rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (s.totals.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                s.totals.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 6.dp)) {
                        Text(k, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
    }
}
