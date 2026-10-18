package io.github.rahulraj94391.keeper.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.presentation.credential.CredentialDetailScreen
import io.github.rahulraj94391.keeper.presentation.credential.CredentialEditScreen
import io.github.rahulraj94391.keeper.presentation.setup.SetupScreen
import io.github.rahulraj94391.keeper.presentation.unlock.UnlockScreen
import io.github.rahulraj94391.keeper.presentation.vault.VaultListScreen

/**
 * The vault state decides which part of the graph is reachable. Whenever it changes, the whole
 * back stack is replaced, so locking discards every vault screen and its ViewModel (PRD 7.4)
 * and Back can never return to them.
 */
@Composable
fun KeeperNavHost(
    vaultState: VaultState,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = remember { entryRouteFor(vaultState) }

    LaunchedEffect(vaultState) {
        val current = navController.currentDestination ?: return@LaunchedEffect
        if (current.belongsTo(vaultState)) return@LaunchedEffect
        navController.navigate(entryRouteFor(vaultState)) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // dropUnlessResumed ignores repeat taps that arrive while a transition is already running.
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<SetupRoute> { SetupScreen() }
        composable<UnlockRoute> { UnlockScreen() }
        composable<VaultListRoute> {
            VaultListScreen(
                onAddClick = dropUnlessResumed { navController.navigate(CredentialEditRoute()) },
                onCredentialClick = { id -> navController.navigateIfResumed(CredentialDetailRoute(id)) },
                onLockClick = onLock,
            )
        }
        composable<CredentialDetailRoute> {
            CredentialDetailScreen(
                onBack = dropUnlessResumed { navController.popBackStack() },
                onEdit = { id -> navController.navigateIfResumed(CredentialEditRoute(id)) },
            )
        }
        composable<CredentialEditRoute> {
            CredentialEditScreen(onDone = dropUnlessResumed { navController.popBackStack() })
        }
    }
}

private fun entryRouteFor(state: VaultState): Any = when (state) {
    VaultState.NoVault -> SetupRoute
    VaultState.Locked -> UnlockRoute
    VaultState.Unlocked -> VaultListRoute
}

private fun NavDestination.belongsTo(state: VaultState): Boolean = when (state) {
    VaultState.NoVault -> hasRoute<SetupRoute>()
    VaultState.Locked -> hasRoute<UnlockRoute>()
    VaultState.Unlocked ->
        hasRoute<VaultListRoute>() || hasRoute<CredentialDetailRoute>() || hasRoute<CredentialEditRoute>()
}

private fun <T : Any> NavHostController.navigateIfResumed(route: T) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route)
    }
}
