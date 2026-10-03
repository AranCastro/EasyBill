package online.draran.billing.feature.parties

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.ConfirmDialog
import online.draran.billing.core.designsystem.component.FormField
import online.draran.billing.core.designsystem.component.MoneyField
import online.draran.billing.core.designsystem.component.SectionCard
import online.draran.billing.core.designsystem.component.SelectField
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.IndianStates
import online.draran.billing.core.model.IndianState
import online.draran.billing.core.model.PartyType

@Composable
fun PartyEditorRoute(
    partyId: Long,
    type: PartyType,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit = { onBack() },
    prefillName: String = "",
    viewModel: PartyEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(partyId) { viewModel.load(partyId, type, prefillName) }
    val business by viewModel.business.collectAsStateWithLifecycle()
    val form = viewModel.form
    val errors = viewModel.showErrors
    var confirmDelete by remember { mutableStateOf(false) }
    val editing = partyId != 0L
    val noun = if (form.type == PartyType.CUSTOMER) "customer" else "supplier"

    Scaffold(
        topBar = {
            AppTopBar(if (editing) "Edit $noun" else "New $noun", onBack = onBack, actions = {
                if (editing && viewModel.canDelete) IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Trash, contentDescription = "Delete") }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                viewModel.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = Spacing.sm)) }
                Button(onClick = { viewModel.save(onSaved) }, enabled = !viewModel.saving, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save $noun") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        PartyType.entries.forEachIndexed { i, t ->
                            // Once bills or payments exist the type is fixed: switching would move their balance to the other list
                            SegmentedButton(
                                form.type == t, { viewModel.update { it.copy(type = t, openingReceivable = t == PartyType.CUSTOMER) } },
                                SegmentedButtonDefaults.itemShape(i, 2), enabled = partyId == 0L || viewModel.canDelete,
                            ) { Text(t.label) }
                        }
                    }
                    FormField(form.name, { v -> viewModel.update { it.copy(name = v) } }, "Name *", capitalization = KeyboardCapitalization.Words, error = viewModel.nameError.takeIf { errors })
                    FormField(form.phone, { v -> viewModel.update { it.copy(phone = v) } }, "Mobile number", keyboardType = KeyboardType.Phone, error = viewModel.phoneError.takeIf { errors }, supporting = "For calls and WhatsApp reminders")
                    FormField(form.address, { v -> viewModel.update { it.copy(address = v) } }, "Address", singleLine = false, minLines = 2)
                }
            }
            SectionCard(title = "GST details") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FormField(
                        form.gstin, { v -> viewModel.update { it.copy(gstin = v.uppercase().take(15)) } }, "GSTIN (optional)",
                        capitalization = KeyboardCapitalization.Characters,
                        error = viewModel.gstinError.takeIf { errors || form.gstin.length == 15 },
                        supporting = "Bills to a GSTIN appear under B2B in GST reports",
                    )
                    SelectField(
                        label = "State",
                        options = listOf(IndianState("", "Same as my business")) + IndianStates.all,
                        selected = IndianStates.byCode(form.stateCode) ?: IndianState("", "Same as my business"),
                        optionLabel = { it.name },
                        onSelect = { s -> viewModel.update { it.copy(stateCode = s.code) } },
                        supporting = if (business.gstEnabled && form.stateCode.isNotBlank() && form.stateCode != business.stateCode) "Other state: IGST will apply" else "Same state: CGST + SGST",
                    )
                }
            }
            SectionCard(title = "Opening balance") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MoneyField(form.opening, { v -> viewModel.update { it.copy(opening = v) } }, "Amount from before using this app")
                    Row {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(form.openingReceivable, { viewModel.update { it.copy(openingReceivable = true) } }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("They owe me") }
                            SegmentedButton(!form.openingReceivable, { viewModel.update { it.copy(openingReceivable = false) } }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("I owe them") }
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete $noun?", "This cannot be undone.", "Delete", onConfirm = { viewModel.delete(onBack) }, onDismiss = { confirmDelete = false }, destructive = true)
    }
}
