package online.draran.billing.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Asks before leaving a screen with unsaved work. Returns the function to call for the back arrow
 * in the top bar; the system back button and the back gesture are covered as well.
 *
 * @param dirty true while there is something that would be lost.
 * @param onExit leaves the screen.
 */
@Composable
fun rememberDiscardGuard(
    dirty: Boolean,
    title: String = "Discard what you entered?",
    message: String = "It has not been saved and will be lost.",
    confirmText: String = "Discard",
    onExit: () -> Unit,
): () -> Unit {
    var asking by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = dirty) { asking = true }
    if (asking) {
        ConfirmDialog(
            title = title,
            message = message,
            confirmText = confirmText,
            onConfirm = onExit,
            onDismiss = { asking = false },
            destructive = true,
        )
    }
    return { if (dirty) asking = true else onExit() }
}
