package io.github.rahulraj94391.keeper.presentation.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Tells auto-lock the user is active (restarts the 30-second inactivity timer). MainActivity
 * provides it; previews get a no-op.
 *
 * Touches on the main window already count, through Activity.onUserInteraction. This covers
 * what doesn't reach it: soft-keyboard typing, and touches inside dialogs and bottom sheets,
 * which live in their own windows.
 */
val LocalUserActivity = staticCompositionLocalOf<() -> Unit> { {} }

/** Reports every pointer event inside this element as activity, without consuming it. */
fun Modifier.reportUserActivity(report: () -> Unit): Modifier = pointerInput(report) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            report()
        }
    }
}
