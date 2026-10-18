package io.github.rahulraj94391.keeper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rahulraj94391.keeper.presentation.navigation.KeeperNavHost
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val authenticator = (application as KeeperApplication).container.vaultAuthenticator
        setContent {
            KeeperTheme {
                val vaultState by authenticator.state.collectAsStateWithLifecycle()
                KeeperNavHost(vaultState = vaultState, onLock = authenticator::lock)
            }
        }
    }
}
