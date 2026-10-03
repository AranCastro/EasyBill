package online.draran.billing.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import online.draran.billing.core.designsystem.component.ListRow
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing

/** Destinations reachable from the More tab. */
enum class MoreDestination { COUNTER, PURCHASES, ESTIMATES, PAYMENTS, EXPENSES, REPORTS, BACKUP, SETTINGS }

/** "More" tab: everything that is not on the bottom bar. */
@Composable
fun MoreScreen(
    onOpen: (MoreDestination) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val ext = BillingTheme.extendedColors
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = contentPadding.calculateTopPadding() + Spacing.lg,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Text("More", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs))
        }
        item { SectionLabel("Billing") }
        item {
            Group(
                listOf(
                    Entry(AppIcons.CashRegister, "Counter billing", "Tap-to-add quick sales with UPI QR", MoreDestination.COUNTER, scheme.primary, scheme.primaryContainer),
                    Entry(AppIcons.ShoppingCart, "Purchases", "Purchase bills and returns", MoreDestination.PURCHASES, ext.due, ext.dueContainer),
                    Entry(AppIcons.NotePencil, "Estimates", "Quotations you can convert to a sale", MoreDestination.ESTIMATES, ext.due, ext.dueContainer),
                ),
                onOpen,
            )
        }
        item { SectionLabel("Money") }
        item {
            Group(
                listOf(
                    Entry(AppIcons.HandCoins, "Payments", "Money received and paid", MoreDestination.PAYMENTS, ext.received, ext.receivedContainer),
                    Entry(AppIcons.Wallet, "Expenses", "Rent, salary, electricity and more", MoreDestination.EXPENSES, scheme.error, scheme.errorContainer),
                    Entry(AppIcons.ChartBar, "Reports", "Sales, profit, stock and GST reports", MoreDestination.REPORTS, ext.received, ext.receivedContainer),
                ),
                onOpen,
            )
        }
        item { SectionLabel("App") }
        item {
            Group(
                listOf(
                    Entry(AppIcons.Database, "Backup & restore", "Keep a copy on Google Drive or a pen drive", MoreDestination.BACKUP, ext.received, ext.receivedContainer),
                    Entry(AppIcons.Settings, "Settings", "Business, invoice, printer, theme", MoreDestination.SETTINGS, scheme.primary, scheme.primaryContainer),
                ),
                onOpen,
            )
        }
    }
}

private data class Entry(
    val icon: ImageVector,
    val title: String,
    val summary: String,
    val destination: MoreDestination,
    val tint: Color,
    val container: Color,
)

@Composable
private fun Group(entries: List<Entry>, onOpen: (MoreDestination) -> Unit) {
    SurfaceCard {
        Column {
            entries.forEachIndexed { index, e ->
                ListRow(title = e.title, subtitle = e.summary, icon = e.icon, tint = e.tint, container = e.container, onClick = { onOpen(e.destination) })
                if (index < entries.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
