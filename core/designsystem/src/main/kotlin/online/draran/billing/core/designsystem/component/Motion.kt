package online.draran.billing.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import online.draran.billing.core.designsystem.icon.AppIcons
import online.draran.billing.core.designsystem.theme.Spacing
import online.draran.billing.core.model.Money

/**
 * An amount that counts to its new value instead of jumping. On first display it
 * counts up from zero when [countUp] is set (dashboard figures), otherwise it starts
 * at its value. The final frame always shows the exact amount.
 */
@Composable
fun AnimatedAmountText(
    amount: Money,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    showPaise: Boolean = true,
    countUp: Boolean = false,
    durationMillis: Int = 700,
) {
    var from by remember { mutableLongStateOf(if (countUp) 0L else amount.paise) }
    var to by remember { mutableLongStateOf(if (countUp) 0L else amount.paise) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(amount.paise) {
        if (amount.paise == to && progress.value >= 1f) return@LaunchedEffect
        val current = from + ((to - from) * progress.value.toDouble()).toLong()
        from = current
        to = amount.paise
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    val shown = if (progress.value >= 1f) to else from + ((to - from) * progress.value.toDouble()).toLong()
    AmountText(Money(shown), modifier = modifier, style = style, color = color, showPaise = showPaise)
}

/**
 * Click with a gentle press-in: the element shrinks slightly while held, which
 * makes tiles and buttons feel physical. Keeps the normal ripple.
 */
fun Modifier.bounceClick(
    role: Role = Role.Button,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/** Small set of vibrations used across the app so they feel consistent. */
class Haptics internal constructor(private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    /** A light tick: adding an item, changing a quantity, picking a chip. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Something was saved or completed. */
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something was refused (validation error). */
    fun error() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}

/** Soft colours for initials avatars; the same name always gets the same colour. */
private val AvatarHues = listOf(
    0xFF4F46E5, 0xFF0F766E, 0xFFB45309, 0xFFBE185D, 0xFF1D4ED8,
    0xFF15803D, 0xFF7E22CE, 0xFFC2410C, 0xFF0E7490, 0xFF9F1239,
).map { Color(it) }

fun avatarColor(name: String): Color = AvatarHues[(name.trim().lowercase().hashCode() and 0x7fffffff) % AvatarHues.size]

/** Up to two initials: "Ravi Kumar" -> "RK", "குமார்" -> "கு". */
fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    if (words.isEmpty()) return "?"
    fun firstGrapheme(w: String): String {
        // Keep a following combining mark so Indic initials stay readable
        val end = (1 until w.length).firstOrNull { i ->
            val t = Character.getType(w[i])
            t != Character.NON_SPACING_MARK.toInt() && t != Character.COMBINING_SPACING_MARK.toInt()
        } ?: w.length
        return w.substring(0, end)
    }
    return (if (words.size == 1) firstGrapheme(words[0]) else firstGrapheme(words[0]) + firstGrapheme(words[1])).uppercase()
}

/**
 * Round initials avatar tinted by the name, so lists of customers, items and
 * bills are easier to scan than identical grey icons.
 */
@Composable
fun NameAvatar(name: String, modifier: Modifier = Modifier, size: Int = 40) {
    val hue = avatarColor(name)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val container = if (dark) hue.copy(alpha = 0.32f) else hue.copy(alpha = 0.14f)
    val content = if (dark) Color.White.copy(alpha = 0.92f) else hue
    Box(
        modifier.size(size.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initialsOf(name),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
        )
    }
}

/** Moving highlight used on placeholder blocks while data loads. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val shine = MaterialTheme.colorScheme.surfaceContainerLow
    background(Brush.linearGradient(listOf(base, shine, base), start = Offset(x, 0f), end = Offset(x + 300f, 120f)))
}

/** Placeholder rows shown instead of an empty or spinning screen while a list loads. */
@Composable
fun SkeletonRows(count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).shimmer())
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(5.dp)).shimmer())
                }
                Box(Modifier.width(64.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
            }
        }
    }
}

/**
 * A banner that pops in to confirm something worked ("Bill saved") and can be
 * hidden by the caller after a moment.
 */
@Composable
fun SuccessBanner(visible: Boolean, text: String, modifier: Modifier = Modifier, detail: String? = null) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.9f),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val received = online.draran.billing.core.designsystem.theme.BillingTheme.extendedColors.received
        val tickScale = remember { Animatable(0.4f) }
        LaunchedEffect(Unit) { tickScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(online.draran.billing.core.designsystem.theme.BillingTheme.extendedColors.receivedContainer)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).scale(tickScale.value).clip(CircleShape).background(received), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column {
                Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
