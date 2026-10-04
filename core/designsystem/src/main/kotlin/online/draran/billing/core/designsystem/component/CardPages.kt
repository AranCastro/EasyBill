package online.draran.billing.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A long list shown as cards of [pageSize] rows each. Only the cards on screen are drawn, so a list of
 * thousands of rows opens at once, and each card keeps the familiar look of a list in a rounded card.
 */
fun <T> LazyListScope.cardPages(
    rows: List<T>,
    keyPrefix: String,
    pageSize: Int = 25,
    dividerStart: Dp = 0.dp,
    row: @Composable (T) -> Unit,
) {
    rows.chunked(pageSize).forEachIndexed { page, chunk ->
        item(key = "$keyPrefix-$page") {
            SurfaceCard {
                Column {
                    chunk.forEachIndexed { i, r ->
                        row(r)
                        if (i < chunk.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = dividerStart), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}
