package online.draran.billing.core.designsystem.component

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.BillColors

/**
 * Colour of one bill. [selected] is the bill's own colour (ARGB), or 0 when it follows the
 * business colour ([businessColor], as stored in the business profile, 0 = app default).
 * A choice is applied at once through [onSelect]; Done only closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BillColourSheet(
    selected: Int,
    businessColor: Int,
    logoColours: List<Int>,
    businessName: String,
    billTitle: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val shown = BillColors.accentOf(if (selected != 0) selected else businessColor)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("Colour of this bill", style = MaterialTheme.typography.titleLarge)
            Text(
                "Only this bill changes. Other bills keep the business colour.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BillColourPreview(Color(shown), businessName, billTitle)
            FilterChip(
                selected = selected == 0,
                onClick = { onSelect(0) },
                label = { Text("Business colour") },
            )
            if (logoColours.isNotEmpty()) {
                Text("From your logo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    logoColours.forEachIndexed { i, c ->
                        ColourSwatch(c, "Logo colour ${i + 1}", selected != 0 && shown == BillColors.accentOf(c)) { onSelect(c) }
                    }
                }
            }
            Text(if (logoColours.isEmpty()) "Ready-made colours" else "More colours", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BillColors.PRESETS.forEach { (name, c) ->
                    // Indigo is stored as itself here: 0 would mean "business colour"
                    ColourSwatch(c, name, selected != 0 && shown == c) { onSelect(c) }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Done") }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}
