package online.draran.billing.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import online.draran.billing.core.designsystem.component.AppTopBar
import online.draran.billing.core.designsystem.component.ListRow
import online.draran.billing.core.designsystem.component.NameAvatar
import online.draran.billing.core.designsystem.component.SectionCard
import online.draran.billing.core.designsystem.component.SurfaceCard
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.designsystem.theme.Spacing

/** Who made the app, where to reach them, and what the app is built on. */
object AboutInfo {
    const val AUTHOR = "Dr Aran Castro"
    const val EMAIL = "arancastro17@gmail.com"
    const val WEBSITE = "https://draran.online"
    const val WEBSITE_SHOWN = "draran.online"
    const val PHONE_DIAL = "+917418742406"
    const val PHONE_SHOWN = "+91 74187 42406"
    const val REPOSITORY = "https://github.com/AranCastro/EasyBill"
    const val PRIVACY_POLICY = "https://github.com/AranCastro/EasyBill/blob/main/docs/privacy-policy.md"
    const val COPYRIGHT = "Copyright © 2026 Dr Aran Castro"

    /** Components the app is built with: name, what it is used for, licence. */
    val CREDITS: List<Triple<String, String, String>> = listOf(
        Triple("Inter", "Typeface", "SIL Open Font License 1.1"),
        Triple("Phosphor Icons", "Icons", "MIT"),
        Triple("ZXing", "QR codes on bills and at the counter", "Apache License 2.0"),
        Triple("Android Jetpack (Compose, Room, Navigation, DataStore, Biometric)", "Screens, database, settings and app lock", "Apache License 2.0"),
        Triple("Hilt and Dagger", "Wiring of the app's parts", "Apache License 2.0"),
        Triple("Kotlin and kotlinx libraries", "Programming language and its libraries", "Apache License 2.0"),
        Triple("Google Play services and ML Kit code scanner", "Barcode scanning", "Google's own terms"),
    )

    const val MIT_TEXT = "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and " +
        "associated documentation files (the \"Software\"), to deal in the Software without restriction, including without " +
        "limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the " +
        "Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: " +
        "The above copyright notice and this permission notice shall be included in all copies or substantial portions of " +
        "the Software. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT " +
        "NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT " +
        "SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF " +
        "CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS " +
        "IN THE SOFTWARE."
}

@Composable
fun AboutRoute(versionName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var showLicence by rememberSaveable { mutableStateOf(false) }
    val ext = BillingTheme.extendedColors

    Scaffold(topBar = { AppTopBar("About and credits", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // App name and version
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(ext.heroBrush).padding(Spacing.xl),
            ) {
                Text("Modern Kallaa Petti", style = MaterialTheme.typography.headlineSmall, color = ext.onHero)
                Text("கல்லாப்பெட்டி", style = MaterialTheme.typography.titleMedium, color = ext.onHero.copy(alpha = 0.85f))
                Spacer(Modifier.height(Spacing.sm))
                Text("Version $versionName", style = MaterialTheme.typography.labelLarge, color = ext.onHero.copy(alpha = 0.85f))
                Text("Free billing, stock and accounts for Indian businesses. Your data stays on your phone.", style = MaterialTheme.typography.bodyMedium, color = ext.onHero.copy(alpha = 0.9f))
            }

            // Credits: the maker
            SectionLabel("Created by")
            SurfaceCard {
                Column {
                    Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            NameAvatar(AboutInfo.AUTHOR, size = 56)
                            Spacer(Modifier.padding(start = Spacing.md))
                            Column {
                                Text(AboutInfo.AUTHOR, style = MaterialTheme.typography.titleMedium)
                                Text("Design and development", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListRow(
                        title = AboutInfo.EMAIL, subtitle = "Email", icon = AppIcons.Share,
                        onClick = { open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${AboutInfo.EMAIL}")).putExtra(Intent.EXTRA_SUBJECT, "Modern Kallaa Petti $versionName")) },
                    )
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ListRow(
                        title = AboutInfo.WEBSITE_SHOWN, subtitle = "Website", icon = AppIcons.Home,
                        onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(AboutInfo.WEBSITE))) },
                    )
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ListRow(
                        title = AboutInfo.PHONE_SHOWN, subtitle = "Phone", icon = AppIcons.Phone,
                        onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${AboutInfo.PHONE_DIAL}"))) },
                    )
                }
            }

            // Licence
            SectionLabel("Licence")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("MIT Licence", style = MaterialTheme.typography.titleSmall)
                    Text(AboutInfo.COPYRIGHT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "You may use, copy, change and share this app and its source code, free of charge, as long as the copyright notice and the licence text stay with it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = { showLicence = !showLicence }) { Text(if (showLicence) "Hide licence text" else "Read licence text") }
                        TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(AboutInfo.REPOSITORY))) }) { Text("Source code") }
                    }
                    AnimatedVisibility(showLicence) {
                        Text(
                            AboutInfo.COPYRIGHT + "\n\n" + AboutInfo.MIT_TEXT,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(Spacing.md),
                        )
                    }
                }
            }

            // Privacy
            SectionLabel("Privacy")
            SurfaceCard {
                ListRow(
                    title = "Privacy policy",
                    subtitle = "What the app stores, and what leaves your phone",
                    icon = AppIcons.ShieldCheck,
                    onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(AboutInfo.PRIVACY_POLICY))) },
                )
            }

            // Open-source credits
            SectionLabel("Built with")
            SurfaceCard {
                Column {
                    AboutInfo.CREDITS.forEachIndexed { i, (name, use, licence) ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text("$use · $licence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (i < AboutInfo.CREDITS.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** Opens a link, mail or dialler; says nothing if the phone has no app for it. */
private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { android.widget.Toast.makeText(context, "No app was found to open this.", android.widget.Toast.LENGTH_SHORT).show() }
}
