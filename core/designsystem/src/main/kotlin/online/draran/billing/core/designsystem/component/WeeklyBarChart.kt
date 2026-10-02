package online.draran.billing.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.draran.billing.core.common.IndianFormat
import online.draran.billing.core.model.Money

/**
 * Lightweight 7-bar chart drawn on a Canvas (no chart library needed).
 * The last bar (today) is highlighted. Bars grow in on first display.
 */
@Composable
fun WeeklyBarChart(
    values: List<Money>,
    labels: List<String>,
    barColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Int = 120,
) {
    require(values.size == labels.size) { "values and labels must have the same size" }
    val max = (values.maxOfOrNull { it.paise } ?: 0L).coerceAtLeast(1L)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(durationMillis = 600)) }

    val description = labels.zip(values).joinToString { (l, v) -> "$l ${IndianFormat.rupees(v, showPaise = false)}" }

    Column(modifier.semantics { contentDescription = "Sales for the last 7 days: $description" }) {
        Canvas(Modifier.fillMaxWidth().height(chartHeight.dp)) {
            val count = values.size
            if (count == 0) return@Canvas
            val slot = size.width / count
            val barWidth = slot * 0.46f
            values.forEachIndexed { index, value ->
                val fraction = value.paise.toFloat() / max
                // Minimum height so zero days still show a stub
                val barHeight = (size.height * fraction * progress.value).coerceAtLeast(4.dp.toPx())
                val left = slot * index + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (index == count - 1) highlightColor else barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == labels.lastIndex) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
