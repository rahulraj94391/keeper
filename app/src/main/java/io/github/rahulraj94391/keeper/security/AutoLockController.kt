package io.github.rahulraj94391.keeper.security

import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Locks the vault automatically (PRD 6.6):
 * - when the app leaves the foreground: at once, or after the delay the user chose in Settings
 *   if it doesn't come back first;
 * - immediately when the screen turns off, whatever the setting.
 *
 * The system BiometricPrompt can push the app into the background on some devices. While a
 * prompt Keeper started is showing, leaving the foreground does not start the timer; the
 * prompt itself ends with success, cancel or error, after which normal rules apply.
 */
class AutoLockController(
    private val authenticator: VaultAuthenticator,
    private val scope: CoroutineScope,
    /** Read each time the app leaves the foreground, so a new setting applies right away. */
    private val backgroundTimeout: () -> Duration,
) {
    private var pendingLock: Job? = null
    private var systemAuthInProgress = false
    private var inBackground = false

    /** The whole app went to the background (no activity visible). */
    fun onAppBackgrounded() {
        inBackground = true
        if (!systemAuthInProgress) scheduleLock()
    }

    /** An app activity is visible again. */
    fun onAppForegrounded() {
        inBackground = false
        pendingLock?.cancel()
        pendingLock = null
    }

    fun onScreenOff() {
        pendingLock?.cancel()
        pendingLock = null
        authenticator.lock()
    }

    /** Call right before showing BiometricPrompt. */
    fun onSystemAuthStarted() {
        systemAuthInProgress = true
        pendingLock?.cancel()
        pendingLock = null
    }

    /** Call when BiometricPrompt reports any result. */
    fun onSystemAuthFinished() {
        systemAuthInProgress = false
        // If the user left the app while the prompt was up, start counting from now.
        if (inBackground) scheduleLock()
    }

    private fun scheduleLock() {
        pendingLock?.cancel()
        pendingLock = null
        val timeout = backgroundTimeout()
        if (timeout <= Duration.ZERO) {
            authenticator.lock()
            return
        }
        pendingLock = scope.launch {
            delay(timeout)
            authenticator.lock()
        }
    }
}
