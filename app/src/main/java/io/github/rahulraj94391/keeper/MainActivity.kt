package io.github.rahulraj94391.keeper

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.presentation.navigation.KeeperNavHost
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme

// FragmentActivity (a ComponentActivity) because BiometricPrompt requires one.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val authenticator = (application as KeeperApplication).container.vaultAuthenticator
        setContent {
            KeeperTheme {
                val vaultState by authenticator.state.collectAsStateWithLifecycle()
                if (vaultState == VaultState.Loading) {
                    // Brief blank screen while stored vault data is checked.
                    Surface(Modifier.fillMaxSize()) {}
                } else {
                    KeeperNavHost(vaultState = vaultState, onLock = authenticator::lock)
                }
            }
        }
    }
}
