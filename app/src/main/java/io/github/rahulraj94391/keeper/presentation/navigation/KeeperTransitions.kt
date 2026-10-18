package io.github.rahulraj94391.keeper.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute

/*
 * Screen transitions.
 *
 * - Opening a screen: it slides in from the end edge (right in LTR) while the current screen
 *   slides out toward the start edge.
 * - Going back: the top screen slides out to the end edge while the previous one slides back in.
 * - Lock, unlock and setup replace the whole back stack instead of moving forward or back, so
 *   they cross-fade.
 *
 * Start/End directions follow the layout direction, so right-to-left languages are mirrored.
 */

private const val DURATION_MS = 300
private fun <T> motion() = tween<T>(DURATION_MS, easing = FastOutSlowInEasing)

private typealias NavScope = AnimatedContentTransitionScope<NavBackStackEntry>

/** True when either side of the transition is Setup or Unlock, i.e. the vault state changed. */
private fun NavScope.isVaultStateChange(): Boolean =
    listOf(initialState, targetState).any {
        it.destination.hasRoute<SetupRoute>() || it.destination.hasRoute<UnlockRoute>()
    }

internal val keeperEnterTransition: NavScope.() -> EnterTransition = {
    if (isVaultStateChange()) fadeIn(motion())
    else slideIntoContainer(SlideDirection.Start, motion())
}

internal val keeperExitTransition: NavScope.() -> ExitTransition = {
    if (isVaultStateChange()) fadeOut(motion())
    else slideOutOfContainer(SlideDirection.Start, motion())
}

internal val keeperPopEnterTransition: NavScope.() -> EnterTransition = {
    if (isVaultStateChange()) fadeIn(motion())
    else slideIntoContainer(SlideDirection.End, motion())
}

internal val keeperPopExitTransition: NavScope.() -> ExitTransition = {
    if (isVaultStateChange()) fadeOut(motion())
    else slideOutOfContainer(SlideDirection.End, motion())
}
