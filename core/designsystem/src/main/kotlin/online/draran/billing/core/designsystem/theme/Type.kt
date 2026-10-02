package online.draran.billing.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import online.draran.billing.core.designsystem.R

/** Inter (SIL Open Font License), bundled so it works offline. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Tabular figures keep digits the same width so amounts line up in columns. */
const val TABULAR_NUMBERS = "tnum"

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

internal val AppTypography = Typography(
    displayLarge = style(FontWeight.SemiBold, 52, 60, -1.0),
    displayMedium = style(FontWeight.SemiBold, 42, 50, -0.8),
    displaySmall = style(FontWeight.SemiBold, 34, 42, -0.6),
    headlineLarge = style(FontWeight.SemiBold, 30, 38, -0.4),
    headlineMedium = style(FontWeight.SemiBold, 26, 34, -0.3),
    headlineSmall = style(FontWeight.SemiBold, 22, 30, -0.2),
    titleLarge = style(FontWeight.SemiBold, 20, 28, -0.1),
    titleMedium = style(FontWeight.SemiBold, 16, 24),
    titleSmall = style(FontWeight.Medium, 14, 20),
    bodyLarge = style(FontWeight.Normal, 16, 24),
    bodyMedium = style(FontWeight.Normal, 14, 20),
    bodySmall = style(FontWeight.Normal, 12, 16),
    labelLarge = style(FontWeight.SemiBold, 14, 20),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.2),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.3),
)
