package io.github.rahulraj94391.keeper.presentation.common

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.rahulraj94391.keeper.KeeperApplication

/**
 * Returns a function that copies a value through [io.github.rahulraj94391.keeper.security.ClipboardGuard],
 * so it's cleared about 30 seconds later if still on the clipboard.
 */
@Composable
fun rememberSecretCopier(): (label: String, value: String, sensitive: Boolean) -> Unit {
    val guard = (LocalContext.current.applicationContext as KeeperApplication).container.clipboardGuard
    return remember(guard) { { label, value, sensitive -> guard.copy(label, value, sensitive) } }
}

/** Android 13+ shows its own confirmation when something is copied, so the app only needs one below that. */
val systemShowsCopyConfirmation: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
