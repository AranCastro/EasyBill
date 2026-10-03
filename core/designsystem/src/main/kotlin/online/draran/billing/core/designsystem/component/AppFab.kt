package online.draran.billing.core.designsystem.component

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import online.draran.billing.core.designsystem.icon.AppIcons

/**
 * Extended floating button with its label exposed to TalkBack
 * (the Material component does not merge the text into the button node).
 */
@Composable
fun AppFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(AppIcons.Plus, contentDescription = null) },
        text = { Text(text) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.semantics { contentDescription = text },
    )
}
