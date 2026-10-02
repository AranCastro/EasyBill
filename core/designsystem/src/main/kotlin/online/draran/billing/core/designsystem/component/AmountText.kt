package online.draran.billing.core.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.designsystem.theme.TABULAR_NUMBERS
import online.draran.billing.core.model.Money

/** Shows an amount as "₹1,23,456.00" with tabular digits so columns align. */
@Composable
fun AmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    showPaise: Boolean = true,
) {
    Text(
        text = IndianFormat.rupees(amount, showPaise = showPaise),
        modifier = modifier,
        style = style.copy(fontFeatureSettings = TABULAR_NUMBERS),
        color = color,
        maxLines = 1,
    )
}
