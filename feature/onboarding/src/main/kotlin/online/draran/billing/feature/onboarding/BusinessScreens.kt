package online.draran.billing.feature.onboarding

import androidx.compose.foundation.Image
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import online.draran.billing.core.designsystem.component.BillColourPicker
import online.draran.billing.core.designsystem.component.BusinessTypePicker
import online.draran.billing.core.designsystem.component.LogoBox
import online.draran.billing.core.designsystem.component.SignatureBox
import online.draran.billing.core.designsystem.component.SignaturePadDialog
import online.draran.billing.core.model.BusinessType
import online.draran.billing.core.model.MsmeCategory
import online.draran.billing.core.model.Udyam
import androidx.compose.material3.FilterChip
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
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel, snackbar)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onDone) }, enabled = !viewModel.saving && !viewModel.working, modifier = Modifier.fillMaxWidth().height(52.dp)) {
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
                Text("Welcome to", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
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
                Text("Tell us about your business. This appears on every bill. You can change it later in Settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BusinessFormFields(viewModel)
            }
        }
    }
}

/** Edit the business profile from Settings. */
@Composable
fun BusinessProfileRoute(onBack: () -> Unit, viewModel: BusinessFormViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel, snackbar)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { AppTopBar("Business profile", onBack = onBack) },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(Spacing.lg)) {
                Button(onClick = { viewModel.save(onBack) }, enabled = !viewModel.saving && !viewModel.working, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
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
private fun MessageEffect(viewModel: BusinessFormViewModel, snackbar: SnackbarHostState) {
    val message = viewModel.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.message = null
        }
    }
}

@Composable
private fun BusinessFormFields(viewModel: BusinessFormViewModel) {
    val form = viewModel.form
    val errors = viewModel.showErrors
    SectionCard(title = "Type of business") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "Sets the words, bill fields and starter services. You can change it later.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BusinessTypePicker(selected = form.type, onSelect = viewModel::selectType)
            TypeSummary(form.type)
            if (form.type.presets.isNotEmpty() && viewModel.typeChanged) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .toggleable(value = form.addStarter, role = Role.Checkbox, onValueChange = { v -> viewModel.update { it.copy(addStarter = v) } })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = form.addStarter, onCheckedChange = null)
                    Spacer(Modifier.width(Spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text("Add starter ${form.type.items.lowercase()}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            form.type.presets.take(4).joinToString(", ") { it.name } + " and more. Prices are editable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    SectionCard(title = "Business details") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LogoRow(viewModel)
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
    SectionCard(title = "Bill colour") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                if (viewModel.logoColours.isEmpty()) "Colour of the bill's top bar, title, table header and total. Upload a logo to get matching colours."
                else "Colour of the bill's top bar, title, table header and total. Colours from your logo come first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BillColourPicker(
                selected = form.billColor,
                logoColours = viewModel.logoColours,
                businessName = form.name,
                billTitle = if (form.gstEnabled) "Tax Invoice" else form.type.billTitle,
                onSelect = { c -> viewModel.update { it.copy(billColor = c) } },
            )
        }
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GST registered", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (form.gstEnabled) "Tax invoices with CGST / SGST / IGST" else "Bills without tax (non-GST or composition scheme)",
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
    MsmeSection(viewModel)
    SectionCard(title = "UPI payment") {
        FormField(
            form.upiId, { v -> viewModel.update { it.copy(upiId = v.trim()) } }, "UPI ID (optional)",
            capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Email,
            error = viewModel.upiError.takeIf { errors },
            supporting = "A UPI QR code with the bill amount is printed on every bill",
            trailing = { Icon(AppIcons.QrCode, contentDescription = null) },
        )
    }
    SignatorySection(viewModel)
}

/** Optional Udyam registration; printed on bills with the MSME payment-term note for micro and small units. */
@Composable
private fun MsmeSection(viewModel: BusinessFormViewModel) {
    val form = viewModel.form
    SectionCard(title = "MSME / Udyam (optional)") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "Add your Udyam registration if you have one. It is printed on your bills. Under the MSMED Act, 2006, business buyers must pay micro and small enterprises within the agreed period, not later than 45 days. Leave blank if not registered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormField(
                form.udyamNumber, { v -> viewModel.update { it.copy(udyamNumber = v.uppercase().take(24)) } }, "Udyam registration no.",
                capitalization = KeyboardCapitalization.Characters,
                error = viewModel.udyamError.takeIf { viewModel.showErrors || Udyam.isValid(form.udyamNumber) },
                supporting = "e.g. UDYAM-TN-02-0012345",
            )
            if (form.udyamNumber.isNotBlank()) {
                Text("Enterprise type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MsmeCategory.entries.forEach { c ->
                        FilterChip(
                            selected = form.msmeCategory == c,
                            onClick = { viewModel.update { it.copy(msmeCategory = c) } },
                            label = { Text(c.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeSummary(type: BusinessType) {
    val parts = buildList {
        add("${type.parties} · ${type.items}")
        add(if (type.tracksStock) "Stock tracked" else "No stock")
        if (type.customFields.isNotEmpty()) add("Bill fields: " + type.customFields.joinToString(", "))
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
    ) {
        Text(type.description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            "Bill title without GST: ${type.billTitle} · " + parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogoRow(viewModel: BusinessFormViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.setLogo(uri) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoBox(viewModel.logo)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text("Business logo", style = MaterialTheme.typography.titleSmall)
            Text("Printed on bills, statements and receipts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !viewModel.working) {
                    Icon(AppIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (viewModel.logo == null) "Upload logo" else "Change")
                }
                if (viewModel.logo != null) {
                    TextButton(onClick = viewModel::removeLogo, enabled = !viewModel.working) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun SignatorySection(viewModel: BusinessFormViewModel) {
    val form = viewModel.form
    var drawing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.setSignaturePhoto(uri) }
    SectionCard(title = "Authorised signatory") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "Printed above \"Authorised signatory\" on every bill. Upload a photo of your signature on white paper (the paper is removed automatically) or sign on the screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignatureBox(viewModel.signature)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !viewModel.working, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Upload")
                }
                OutlinedButton(onClick = { drawing = true }, enabled = !viewModel.working, modifier = Modifier.weight(1f)) {
                    Icon(AppIcons.PenNib, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sign here")
                }
            }
            if (viewModel.signature != null) {
                TextButton(onClick = viewModel::removeSignature, enabled = !viewModel.working) { Text("Remove signature") }
            }
            FormField(form.signatoryName, { v -> viewModel.update { it.copy(signatoryName = v) } }, "Signatory name", capitalization = KeyboardCapitalization.Words)
            FormField(
                form.signatoryDesignation, { v -> viewModel.update { it.copy(signatoryDesignation = v) } }, "Designation",
                capitalization = KeyboardCapitalization.Words,
                supporting = "e.g. Proprietor, Partner, Principal, Director",
            )
        }
    }
    if (drawing) {
        SignaturePadDialog(onDismiss = { drawing = false }, onSave = { strokes -> drawing = false; viewModel.setSignatureDrawn(strokes) })
    }
}
