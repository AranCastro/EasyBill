package online.draran.billing.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Colours that Material 3 has no role for but a billing app needs:
 * money received (green), money due (amber), and the hero-card gradient.
 */
@Immutable
data class ExtendedColors(
    val received: Color,
    val onReceived: Color,
    val receivedContainer: Color,
    val due: Color,
    val dueContainer: Color,
    val heroGradient: List<Color>,
    val onHero: Color,
    val chartBar: Color,
    val chartBarHighlight: Color,
) {
    val heroBrush: Brush get() = Brush.linearGradient(heroGradient)
}

internal val LightExtendedColors = ExtendedColors(
    received = Color(0xFF047857),
    onReceived = Color(0xFFFFFFFF),
    receivedContainer = Color(0xFFD1FAE5),
    due = Color(0xFFB45309),
    dueContainer = Color(0xFFFEF3C7),
    heroGradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
    onHero = Color(0xFFFFFFFF),
    chartBar = Color(0xFFC7D2FE),
    chartBarHighlight = Color(0xFF4F46E5),
)

internal val DarkExtendedColors = ExtendedColors(
    received = Color(0xFF34D399),
    onReceived = Color(0xFF052E22),
    receivedContainer = Color(0xFF064E3B),
    due = Color(0xFFFBBF24),
    dueContainer = Color(0xFF5B3A0A),
    heroGradient = listOf(Color(0xFF3730A3), Color(0xFF6D28D9)),
    onHero = Color(0xFFFFFFFF),
    chartBar = Color(0xFF3B3B66),
    chartBarHighlight = Color(0xFFA5B4FC),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
