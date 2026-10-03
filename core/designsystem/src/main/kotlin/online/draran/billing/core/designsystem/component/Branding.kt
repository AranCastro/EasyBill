package online.draran.billing.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import online.draran.billing.core.model.BillColors
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.BusinessType

/** Icon shown for each industry mode. */
val BusinessType.icon: ImageVector
    get() = when (this) {
        BusinessType.RETAIL -> AppIcons.Storefront
        BusinessType.EDUCATION -> AppIcons.GraduationCap
        BusinessType.RESEARCH -> AppIcons.Flask
        BusinessType.SALON -> AppIcons.Scissors
        BusinessType.CLINIC -> AppIcons.Stethoscope
        BusinessType.REPAIR -> AppIcons.Wrench
        BusinessType.PROFESSIONAL -> AppIcons.Briefcase
    }

/** Two-column grid of business types; the selected tile is outlined in the primary colour. */
@Composable
fun BusinessTypePicker(selected: BusinessType, onSelect: (BusinessType) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        BusinessType.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { type -> BusinessTypeTile(type, type == selected, { onSelect(type) }, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BusinessTypeTile(type: BusinessType, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .height(104.dp)
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerLowest)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant), shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(type.icon, contentDescription = null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        Column {
            Text(type.label, style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onPrimaryContainer else scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Square preview of the logo, or a placeholder with an image icon. */
@Composable
fun LogoBox(logo: ImageBitmap?, modifier: Modifier = Modifier, size: Int = 72) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (logo != null) Color.White else scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(logo, contentDescription = "Business logo", contentScale = ContentScale.Fit, modifier = Modifier.padding(6.dp))
        } else {
            Icon(AppIcons.ImageIcon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size((size * 0.42).dp))
        }
    }
}

/**
 * Signature preview on a white strip, like it appears on paper. The ink is
 * dark blue, so the strip stays white in dark mode too.
 */
@Composable
fun SignatureBox(signature: ImageBitmap?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (signature != null) {
            Image(signature, contentDescription = "Authorised signature", contentScale = ContentScale.Fit)
        } else {
            Text("No signature yet", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6B7280))
        }
    }
}

/** Strokes drawn on the pad, in pixels of a canvas of [size]. */
class SignatureStrokes(val strokes: List<List<Offset>>, val size: IntSize, val strokeWidthPx: Float)

/**
 * Full-width pad to sign with a finger. Returns the strokes; the caller turns
 * them into a bitmap (kept out of Compose so it can be tested).
 */
@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onSave: (SignatureStrokes) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val ink = Color(0xFF14183C)
    val strokeWidth = with(LocalDensity.current) { 3.dp.toPx() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign here") },
        text = {
            Column {
                Text("Sign with your finger inside the box.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.md))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                ) {
                    Canvas(
                        Modifier
                            .matchParentSizeCompat()
                            .testTag("signature_pad")
                            .onSizeChanged { size = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { current = listOf(it) },
                                    onDrag = { change, _ -> current = current + change.position },
                                    onDragEnd = { if (current.size > 1) strokes.add(current); current = emptyList() },
                                    onDragCancel = { current = emptyList() },
                                )
                            },
                    ) {
                        // Baseline guide
                        drawLine(Color(0xFFD1D5DB), Offset(16f, this.size.height * 0.75f), Offset(this.size.width - 16f, this.size.height * 0.75f), strokeWidth = 2f)
                        (strokes + listOf(current)).filter { it.size > 1 }.forEach { pts ->
                            val path = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) {
                                    val mid = (pts[i - 1] + pts[i]) / 2f
                                    quadraticTo(pts[i - 1].x, pts[i - 1].y, mid.x, mid.y)
                                }
                                lineTo(pts.last().x, pts.last().y)
                            }
                            drawPath(path, ink, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                OutlinedButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) {
                    Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(SignatureStrokes(strokes.toList(), size, strokeWidth)) }, enabled = strokes.isNotEmpty()) { Text("Use signature") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().height(180.dp)

/**
 * Bill colour choice: a small bill preview, colours taken from the logo, then
 * ready-made colours. Values are ARGB ints; 0 means the default colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillColourPicker(
    selected: Int,
    logoColours: List<Int>,
    businessName: String,
    billTitle: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = BillColors.accentOf(selected)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        BillColourPreview(Color(current), businessName, billTitle)
        if (logoColours.isNotEmpty()) {
            Text("From your logo", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                logoColours.forEachIndexed { i, c -> ColourSwatch(c, "Logo colour ${i + 1}", current == BillColors.accentOf(c)) { onSelect(c) } }
            }
        }
        Text(if (logoColours.isEmpty()) "Colours" else "More colours", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BillColors.PRESETS.forEach { (name, c) -> ColourSwatch(c, name, current == c) { onSelect(if (c == BillColors.DEFAULT) 0 else c) } }
        }
        if (selected != 0 && BillColors.readable(selected) != selected) {
            Text(
                "Darkened slightly so white text on it stays readable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColourSwatch(argb: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .padding(if (selected) 4.dp else 0.dp)
            .clip(CircleShape)
            .background(Color(BillColors.readable(argb)))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = "Bill colour $name" },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Miniature bill header, rows and total band in the chosen colour; white like paper in both themes. */
@Composable
fun BillColourPreview(accent: Color, businessName: String, billTitle: String, modifier: Modifier = Modifier) {
    val tint = Color(BillColors.tint(accent.toArgb()))
    val ink = Color(0xFF14142B)
    val line = Color(0xFFE5E7EB)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).background(accent))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(businessName.ifBlank { "Your business" }, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(billTitle.uppercase(), style = MaterialTheme.typography.labelLarge, color = accent)
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(tint).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("Item", style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.weight(1f))
                Text("Amount", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            repeat(2) { Box(Modifier.fillMaxWidth(if (it == 0) 0.8f else 0.6f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(line)) }
            Row(
                Modifier.fillMaxWidth(0.55f).align(Alignment.End).clip(RoundedCornerShape(6.dp)).background(accent).padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("Total", style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.weight(1f))
                Text("₹1,250.00", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}
