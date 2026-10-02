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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import online.draran.billing.core.designsystem.component.ListRow
import online.draran.billing.core.designsystem.component.StatusPill
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing

/** "More" tab: entry points to everything that is not on the bottom bar. */
@Composable
fun MoreScreen(
    onOpenSettings: () -> Unit,
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
            Text(
                stringResource(R.string.more_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
            )
        }
        item { SectionLabel(stringResource(R.string.more_transactions)) }
        item {
            Group(
                listOf(
                    Entry(AppIcons.ShoppingCart, R.string.more_purchases, R.string.more_purchases_summary, "Phase 2", ext.due, ext.dueContainer),
                    Entry(AppIcons.Wallet, R.string.more_expenses, R.string.more_expenses_summary, "Phase 2", scheme.error, scheme.errorContainer),
                    Entry(AppIcons.NotePencil, R.string.more_estimates, R.string.more_estimates_summary, "Phase 2", ext.due, ext.dueContainer),
                ),
            )
        }
        item { SectionLabel(stringResource(R.string.more_insights)) }
        item {
            Group(
                listOf(
                    Entry(AppIcons.ChartBar, R.string.more_reports, R.string.more_reports_summary, "Phase 3", ext.received, ext.receivedContainer),
                ),
            )
        }
        item { SectionLabel(stringResource(R.string.more_app)) }
        item {
            SurfaceCard {
                ListRow(
                    title = stringResource(R.string.settings_title),
                    subtitle = stringResource(R.string.more_settings_summary),
                    icon = AppIcons.Settings,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

private data class Entry(
    val icon: ImageVector,
    val title: Int,
    val summary: Int,
    val phase: String,
    val tint: Color,
    val container: Color,
)

@Composable
private fun Group(entries: List<Entry>) {
    SurfaceCard {
        Column {
            entries.forEachIndexed { index, e ->
                ListRow(
                    title = stringResource(e.title),
                    subtitle = stringResource(e.summary),
                    icon = e.icon,
                    tint = e.tint,
                    container = e.container,
                    enabled = false,
                    trailing = { StatusPill(e.phase) },
                )
                if (index < entries.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
