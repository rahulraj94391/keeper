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
 * Some system screens that Keeper opens on purpose push it into the background: the file picker
 * for backup export/restore always does, and BiometricPrompt does on some devices. While one of
 * those is showing, leaving the foreground does not lock or start the timer; when it closes,
 * normal rules apply. Screen off still locks immediately.
 */
class AutoLockController(
    private val authenticator: VaultAuthenticator,
    private val scope: CoroutineScope,
    /** Read each time the app leaves the foreground, so a new setting applies right away. */
    private val backgroundTimeout: () -> Duration,
) {
    private var pendingLock: Job? = null
    private var systemUiInProgress = false
    private var inBackground = false

    /** The whole app went to the background (no activity visible). */
    fun onAppBackgrounded() {
        inBackground = true
        if (!systemUiInProgress) scheduleLock()
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

    /** Call right before showing BiometricPrompt or the system file picker. */
    fun onSystemUiStarted() {
        systemUiInProgress = true
        pendingLock?.cancel()
        pendingLock = null
    }

    /** Call when that system screen returns any result, including cancel. */
    fun onSystemUiFinished() {
        systemUiInProgress = false
        // If the user left the app while the system screen was up, apply the setting from now.
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
