package io.github.rahulraj94391.keeper.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.rahulraj94391.keeper.presentation.credential.CredentialDetailScreen
import io.github.rahulraj94391.keeper.presentation.credential.CredentialEditScreen
import io.github.rahulraj94391.keeper.presentation.vault.VaultListScreen

@Composable
fun KeeperNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // dropUnlessResumed ignores repeat taps that arrive while a transition is already running.
    NavHost(navController = navController, startDestination = VaultListRoute, modifier = modifier) {
        composable<VaultListRoute> {
            VaultListScreen(
                onAddClick = dropUnlessResumed { navController.navigate(CredentialEditRoute()) },
                onCredentialClick = { id -> navController.navigateIfResumed(CredentialDetailRoute(id)) },
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

private fun <T : Any> NavHostController.navigateIfResumed(route: T) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route)
    }
}
