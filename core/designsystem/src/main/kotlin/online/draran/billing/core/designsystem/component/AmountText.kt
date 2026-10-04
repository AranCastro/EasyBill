package online.draran.billing.core.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.designsystem.theme.TABULAR_NUMBERS
import online.draran.billing.core.model.Money

/**
 * Shows an amount as "₹1,23,456.00" with tabular digits so columns align.
 * An amount that does not fit its space is made smaller, never cut: "₹12,34,56" for
 * ₹12,34,567 would read as a different, valid amount.
 */
@Composable
fun AmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    showPaise: Boolean = true,
) {
    val text = IndianFormat.rupees(amount, showPaise = showPaise)
    var scale by remember(text, style.fontSize) { mutableFloatStateOf(1f) }
    val base: TextUnit = style.fontSize
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontFeatureSettings = TABULAR_NUMBERS,
            fontSize = if (base.isSpecified && scale < 1f) base * scale else base,
        ),
        color = color,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && scale > MIN_SCALE) scale = (scale * 0.9f).coerceAtLeast(MIN_SCALE)
        },
    )
}

private const val MIN_SCALE = 0.5f
