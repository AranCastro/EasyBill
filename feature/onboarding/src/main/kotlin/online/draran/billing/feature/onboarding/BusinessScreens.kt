package online.draran.billing.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.FormField
import online.draran.billing.core.designsystem.component.SectionCard
import online.draran.billing.core.designsystem.component.SelectField
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.IndianStates

/** First run: one screen to set up the shop, then straight to billing. */
@Composable
fun OnboardingRoute(onDone: () -> Unit, viewModel: BusinessFormViewModel = hiltViewModel()) {
    val ext = BillingTheme.extendedColors
    Scaffold(
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onDone) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Start billing", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            // Hero
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(ext.heroBrush)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
            ) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // The drawable is a 108 dp adaptive-icon canvas; scale it up so the cash box fills the tile
                    Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.onboarding_cash_box),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(124.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                Text("Vanakkam! Welcome to", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
                Text("Modern Kallaa Petti", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text("கல்லாப்பெட்டி · Your digital cash box", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf("Works offline", "GST & non-GST", "Free").forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Tell us about your shop. This appears on every bill. You can change it later in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BusinessFormFields(viewModel)
            }
        }
    }
}

/** Edit the business profile from Settings. */
@Composable
fun BusinessProfileRoute(onBack: () -> Unit, viewModel: BusinessFormViewModel = hiltViewModel()) {
    Scaffold(
        topBar = { AppTopBar("Business profile", onBack = onBack) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (viewModel.loaded) BusinessFormFields(viewModel)
        }
    }
}

@Composable
private fun BusinessFormFields(viewModel: BusinessFormViewModel) {
    val form = viewModel.form
    val errors = viewModel.showErrors
    SectionCard(title = "Shop details") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FormField(form.name, { v -> viewModel.update { it.copy(name = v) } }, "Business name *", capitalization = KeyboardCapitalization.Words, error = viewModel.nameError.takeIf { errors })
            FormField(form.phone, { v -> viewModel.update { it.copy(phone = v) } }, "Mobile number", keyboardType = KeyboardType.Phone, error = viewModel.phoneError.takeIf { errors })
            FormField(form.address, { v -> viewModel.update { it.copy(address = v) } }, "Address", singleLine = false, minLines = 2)
            SelectField(
                label = "State",
                options = IndianStates.all,
                selected = IndianStates.byCode(form.stateCode),
                optionLabel = { it.name },
                onSelect = { s -> viewModel.update { it.copy(stateCode = s.code) } },
                supporting = "Used to decide CGST + SGST or IGST",
            )
            FormField(form.email, { v -> viewModel.update { it.copy(email = v) } }, "Email (optional)", keyboardType = KeyboardType.Email, capitalization = KeyboardCapitalization.None)
        }
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GST registered", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (form.gstEnabled) "Tax invoices with CGST / SGST / IGST" else "Simple bills without tax (non-GST / composition)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Switch(checked = form.gstEnabled, onCheckedChange = { v -> viewModel.update { it.copy(gstEnabled = v) } })
        }
        if (form.gstEnabled) {
            Spacer(Modifier.height(Spacing.sm))
            FormField(
                form.gstin, { v -> viewModel.update { it.copy(gstin = v.uppercase().take(15)) } }, "GSTIN *",
                capitalization = KeyboardCapitalization.Characters,
                error = viewModel.gstinError.takeIf { errors || form.gstin.length == 15 },
                supporting = "15 characters, e.g. 33ABCDE1234F1Z5",
            )
        }
    }
    SectionCard(title = "Get paid faster") {
        FormField(
            form.upiId, { v -> viewModel.update { it.copy(upiId = v.trim()) } }, "UPI ID (optional)",
            capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Email,
            error = viewModel.upiError.takeIf { errors },
            supporting = "A UPI QR code with the bill amount is printed on every bill",
            trailing = { androidx.compose.material3.Icon(AppIcons.QrCode, contentDescription = null) },
        )
    }
}
