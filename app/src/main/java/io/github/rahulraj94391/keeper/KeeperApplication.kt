package io.github.rahulraj94391.keeper

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.rahulraj94391.keeper.di.AppContainer

class KeeperApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate() {
        super.onCreate()
        // Hide every Keeper text field from autofill services (Samsung Pass, Google, other
        // password managers), so none can offer to fill or save the master password, a backup
        // passphrase, or the logins in the Add/Edit form. Compose 1.10 has no per-field opt-out:
        // every text field is autofillable, and neither semantics nor View.importantForAutofill
        // stops it. This must run before the first composition.
        // The flag is experimental; recheck it when updating Compose (docs/design.md 5.5).
        ComposeUiFlags.isSemanticAutofillEnabled = false
        val autoLock = container.autoLock

        // Whole-app foreground/background, not per screen.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = autoLock.onAppForegrounded()

            override fun onStop(owner: LifecycleOwner) = autoLock.onAppBackgrounded()
        })

        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = autoLock.onScreenOff()
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
