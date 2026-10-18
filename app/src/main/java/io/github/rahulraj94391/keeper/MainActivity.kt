package io.github.rahulraj94391.keeper

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.presentation.common.LocalUserActivity
import io.github.rahulraj94391.keeper.presentation.navigation.KeeperNavHost
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme

// FragmentActivity (a ComponentActivity) because BiometricPrompt requires one.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording and the recent-apps thumbnail (PRD 6.6). Set for
        // the whole window, not per screen, so no vault screen can slip through during a
        // transition or at the moment of locking.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val authenticator = (application as KeeperApplication).container.vaultAuthenticator
        setContent {
            KeeperTheme {
                val vaultState by authenticator.state.collectAsStateWithLifecycle()
                val autoLock = (application as KeeperApplication).container.autoLock
                if (vaultState == VaultState.Loading) {
                    // Brief blank screen while stored vault data is checked.
                    Surface(Modifier.fillMaxSize()) {}
                } else {
                    CompositionLocalProvider(LocalUserActivity provides autoLock::onUserInteraction) {
                        KeeperNavHost(vaultState = vaultState, onLock = authenticator::lock)
                    }
                }
            }
        }
    }

    /** Any touch or key press on this window restarts the inactivity timer. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        (application as KeeperApplication).container.autoLock.onUserInteraction()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Clipboard reads need window focus on Android 10+; the guard re-checks when it returns.
        (application as KeeperApplication).container.clipboardGuard.onWindowFocusChanged(hasFocus)
    }
}
