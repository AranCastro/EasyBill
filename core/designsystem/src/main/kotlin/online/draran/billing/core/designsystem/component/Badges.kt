package online.draran.billing.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import online.draran.billing.core.designsystem.theme.BillingTheme
import online.draran.billing.core.model.Money

enum class PayStatus { PAID, PARTIAL, UNPAID, NONE }

fun payStatus(total: Money, paid: Money): PayStatus = when {
    total.paise <= 0 -> PayStatus.NONE
    paid.paise >= total.paise -> PayStatus.PAID
    paid.paise > 0 -> PayStatus.PARTIAL
    else -> PayStatus.UNPAID
}

/** Coloured pill: Paid / Partly paid / Unpaid. */
@Composable
fun StatusBadge(status: PayStatus, modifier: Modifier = Modifier, labelOverride: String? = null) {
    val ext = BillingTheme.extendedColors
    val (text, fg, bg) = when (status) {
        PayStatus.PAID -> Triple("Paid", ext.received, ext.receivedContainer)
        PayStatus.PARTIAL -> Triple("Partly paid", ext.due, ext.dueContainer)
        PayStatus.UNPAID -> Triple("Unpaid", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer)
        PayStatus.NONE -> Triple("", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
    }
    Pill(labelOverride ?: text, fg, bg, modifier)
}

@Composable
fun Pill(text: String, color: Color, container: Color, modifier: Modifier = Modifier) {
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
