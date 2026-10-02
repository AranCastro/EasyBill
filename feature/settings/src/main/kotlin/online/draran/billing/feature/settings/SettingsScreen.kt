package online.draran.billing.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.draran.billing.core.designsystem.component.ListRow
import online.draran.billing.core.designsystem.component.StatusPill
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.ThemeMode
import online.draran.billing.core.model.UserPreferences

@Composable
fun SettingsRoute(
    versionName: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    SettingsScreen(
        preferences = preferences,
        versionName = versionName,
        dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    versionName: String,
    dynamicColorSupported: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = padding.calculateTopPadding() + Spacing.xs,
                bottom = padding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { SectionLabel(stringResource(R.string.settings_appearance)) }
            item {
                SurfaceCard {
                    Column(Modifier.padding(Spacing.lg)) {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(Spacing.md))
                        ThemePicker(selected = preferences.themeMode, onSelect = onThemeModeChange)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(
                            if (dynamicColorSupported) {
                                R.string.settings_dynamic_color_summary
                            } else {
                                R.string.settings_dynamic_color_unsupported
                            },
                        ),
                        icon = AppIcons.Palette,
                        enabled = dynamicColorSupported,
                        onClick = { onDynamicColorChange(!preferences.dynamicColor) },
                        trailing = {
                            Switch(
                                checked = preferences.dynamicColor && dynamicColorSupported,
                                onCheckedChange = onDynamicColorChange,
                                enabled = dynamicColorSupported,
                            )
                        },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_business)) }
            item {
                val ext = BillingTheme.extendedColors
                SettingsGroup(
                    listOf(
                        SettingEntry(AppIcons.Storefront, R.string.settings_business_profile, R.string.settings_business_profile_summary, "Phase 1"),
                        SettingEntry(AppIcons.Receipt, R.string.settings_invoice, R.string.settings_invoice_summary, "Phase 1"),
                        SettingEntry(AppIcons.Printer, R.string.settings_printer, R.string.settings_printer_summary, "Phase 1", ext.due, ext.dueContainer),
                    ),
                )
            }

            item { SectionLabel(stringResource(R.string.settings_data)) }
            item {
                val ext = BillingTheme.extendedColors
                SettingsGroup(
                    listOf(
                        SettingEntry(AppIcons.Database, R.string.settings_backup, R.string.settings_backup_summary, "Phase 1", ext.received, ext.receivedContainer),
                        SettingEntry(AppIcons.ShieldCheck, R.string.settings_app_lock, R.string.settings_app_lock_summary, "Phase 4", ext.received, ext.receivedContainer),
                    ),
                )
            }

            item { SectionLabel(stringResource(R.string.settings_about)) }
            item {
                SurfaceCard {
                    ListRow(
                        title = stringResource(R.string.settings_version, versionName),
                        subtitle = stringResource(R.string.settings_about_summary),
                        icon = AppIcons.Info,
                        trailing = null,
                    )
                }
            }
        }
    }
}

private data class SettingEntry(
    val icon: ImageVector,
    val title: Int,
    val summary: Int,
    val phase: String,
    val tint: Color? = null,
    val container: Color? = null,
)

@Composable
private fun SettingsGroup(rows: List<SettingEntry>) {
    SurfaceCard {
        Column {
            rows.forEachIndexed { index, row ->
                ListRow(
                    title = stringResource(row.title),
                    subtitle = stringResource(row.summary),
                    icon = row.icon,
                    tint = row.tint ?: MaterialTheme.colorScheme.primary,
                    container = row.container ?: MaterialTheme.colorScheme.primaryContainer,
                    enabled = false,
                    trailing = { StatusPill(row.phase) },
                )
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.md, bottom = Spacing.xs),
    )
}

/** Three cards with a miniature app preview: Light, Dark, System. */
@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        listOf(
            Triple(ThemeMode.LIGHT, R.string.settings_theme_light, AppIcons.Sun),
            Triple(ThemeMode.DARK, R.string.settings_theme_dark, AppIcons.Moon),
            Triple(ThemeMode.SYSTEM, R.string.settings_theme_system, AppIcons.DeviceMobile),
        ).forEach { (mode, label, icon) ->
            val isSelected = mode == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(mode) }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    MiniPreview(
                        mode = mode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .border(
                                border = if (isSelected) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                },
                                shape = MaterialTheme.shapes.medium,
                            ),
                    )
                    if (isSelected) {
                        Icon(
                            AppIcons.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(22.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(label),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

// Fixed colours so each preview shows its own theme regardless of the current one.
private data class PreviewPalette(val bg: Color, val card: Color, val line: Color, val hero: List<Color>)

private val LightPreview = PreviewPalette(
    bg = Color(0xFFF7F7FB),
    card = Color(0xFFFFFFFF),
    line = Color(0xFFDCDCE8),
    hero = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
)
private val DarkPreview = PreviewPalette(
    bg = Color(0xFF0F0F1A),
    card = Color(0xFF1A1A2A),
    line = Color(0xFF34344A),
    hero = listOf(Color(0xFF3730A3), Color(0xFF6D28D9)),
)

@Composable
private fun MiniPreview(mode: ThemeMode, modifier: Modifier = Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.medium)) {
        when (mode) {
            ThemeMode.LIGHT -> MiniScreen(LightPreview, Modifier.fillMaxSize())
            ThemeMode.DARK -> MiniScreen(DarkPreview, Modifier.fillMaxSize())
            ThemeMode.SYSTEM -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(0.dp))) {
                    MiniScreen(LightPreview, Modifier.fillMaxSize())
                }
                Box(Modifier.weight(1f).fillMaxSize()) {
                    MiniScreen(DarkPreview, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun MiniScreen(palette: PreviewPalette, modifier: Modifier) {
    Column(modifier.background(palette.bg).padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth(0.55f).height(5.dp).clip(CircleShape).background(palette.line))
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(palette.hero)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) {
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(palette.card))
            }
        }
        repeat(2) {
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(palette.card))
        }
    }
}
