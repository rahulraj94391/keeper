package io.github.rahulraj94391.keeper.security

import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Locks the vault automatically (PRD 6.6, tightened):
 * - **Leaving Keeper** (Home, recent apps, switching apps) locks at once; every return needs
 *   an unlock.
 * - **Screen off** locks at once.
 * - **Inactivity:** while Keeper is open and unlocked, 30 seconds with no touch, key press or
 *   typing locks it.
 *
 * Some system screens that Keeper opens on purpose push it into the background or cover it:
 * the file picker for backup export/restore, and BiometricPrompt. While one of those is
 * showing, leaving the foreground does not lock and the inactivity timer is paused. If the
 * user left Keeper while it was up, the vault locks as soon as it closes. Screen off still
 * locks immediately.
 */
class AutoLockController(
    private val authenticator: VaultAuthenticator,
    private val scope: CoroutineScope,
    private val idleTimeout: Duration = IDLE_TIMEOUT,
) {
    private var idleTimer: Job? = null
    private var systemUiInProgress = false
    private var inBackground = false

    init {
        // Start counting as soon as the vault opens; stop as soon as it locks.
        scope.launch {
            authenticator.state.collect { state ->
                if (state == VaultState.Unlocked) restartIdleTimer() else cancelIdleTimer()
            }
        }
    }

    /** The whole app went to the background (no activity visible). */
    fun onAppBackgrounded() {
        inBackground = true
        cancelIdleTimer()
        if (!systemUiInProgress) authenticator.lock()
    }

    /** An app activity is visible again. */
    fun onAppForegrounded() {
        inBackground = false
        restartIdleTimer()
    }

    fun onScreenOff() {
        cancelIdleTimer()
        authenticator.lock()
    }

    /** Any touch, key press or text change in Keeper. Restarts the inactivity timer. */
    fun onUserInteraction() = restartIdleTimer()

    /** Call right before showing BiometricPrompt or the system file picker. */
    fun onSystemUiStarted() {
        systemUiInProgress = true
        cancelIdleTimer()
    }

    /** Call when that system screen returns any result, including cancel. */
    fun onSystemUiFinished() {
        systemUiInProgress = false
        // The user left Keeper while the system screen was up: lock now.
        if (inBackground) authenticator.lock() else restartIdleTimer()
    }

    private fun restartIdleTimer() {
        cancelIdleTimer()
        if (inBackground || systemUiInProgress || authenticator.state.value != VaultState.Unlocked) return
        idleTimer = scope.launch {
            delay(idleTimeout)
            authenticator.lock()
        }
    }

    private fun cancelIdleTimer() {
        idleTimer?.cancel()
        idleTimer = null
    }

    companion object {
        val IDLE_TIMEOUT: Duration = 30.seconds
    }
}
