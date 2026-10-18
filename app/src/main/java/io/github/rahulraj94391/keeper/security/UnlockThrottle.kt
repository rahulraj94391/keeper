package io.github.rahulraj94391.keeper.security

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** What the throttle remembers between app runs. Not secret. */
data class ThrottleState(
    val failedAttempts: Int = 0,
    /** Length of the current wait, or 0 when there is none. */
    val lockoutMillis: Long = 0,
    /** When the current wait started, on each clock (see [ThrottleClock]). */
    val lockoutStartWallMillis: Long = 0,
    val lockoutStartElapsedMillis: Long = 0,
    val lockoutStartBootCount: Int = -1,
)

interface ThrottleStore {
    fun load(): ThrottleState

    /** Must be written to disk before returning, so killing the app can't undo a failure. */
    fun save(state: ThrottleState)
}

/**
 * Two clocks, because neither is enough alone: elapsed time since boot can't be changed by the
 * user but restarts at zero on reboot; wall-clock time survives reboots but can be changed.
 */
interface ThrottleClock {
    fun wallMillis(): Long
    fun elapsedMillis(): Long
    fun bootCount(): Int
}

sealed interface ThrottleStatus {
    /** Unlocking is allowed. [attemptsBeforeDelay] is set once a delay is close. */
    data class Allowed(val attemptsBeforeDelay: Int?) : ThrottleStatus

    data class LockedOut(val remaining: Duration) : ThrottleStatus
}

/**
 * Rate-limits wrong master passwords on the Unlock screen. The first [FREE_ATTEMPTS] - 1 wrong
 * attempts in a row are free; the [FREE_ATTEMPTS]th starts a 30-second wait, and every further
 * wrong attempt doubles it, up to 3 hours. Any successful unlock (password or fingerprint) resets
 * it.
 *
 * Limitation: this only guards the app's own Unlock screen. Someone who copies the vault file off
 * the device can guess offline, slowed only by Argon2id.
 */
class UnlockThrottle(
    private val store: ThrottleStore,
    private val clock: ThrottleClock,
) {
    @Synchronized
    fun status(): ThrottleStatus {
        val state = store.load()
        val remaining = remaining(state)
        if (remaining > Duration.ZERO) return ThrottleStatus.LockedOut(remaining)
        val left = FREE_ATTEMPTS - state.failedAttempts
        return ThrottleStatus.Allowed(attemptsBeforeDelay = left.takeIf { it in 1..WARN_WHEN_LEFT })
    }

    /** Call after a wrong master password. Returns the status the user should see next. */
    @Synchronized
    fun recordFailure(): ThrottleStatus {
        val failures = store.load().failedAttempts + 1
        val delay = delayAfter(failures)
        store.save(
            ThrottleState(
                failedAttempts = failures,
                lockoutMillis = delay.inWholeMilliseconds,
                lockoutStartWallMillis = clock.wallMillis(),
                lockoutStartElapsedMillis = clock.elapsedMillis(),
                lockoutStartBootCount = clock.bootCount(),
            )
        )
        return status()
    }

    /** Call after any successful unlock. */
    @Synchronized
    fun recordSuccess() {
        if (store.load() != ThrottleState()) store.save(ThrottleState())
    }

    private fun remaining(state: ThrottleState): Duration {
        if (state.lockoutMillis <= 0) return Duration.ZERO
        val sameBoot = state.lockoutStartBootCount == clock.bootCount() &&
            clock.elapsedMillis() >= state.lockoutStartElapsedMillis
        val passed = if (sameBoot) {
            // Monotonic: changing the system clock doesn't help.
            clock.elapsedMillis() - state.lockoutStartElapsedMillis
        } else {
            val wallPassed = clock.wallMillis() - state.lockoutStartWallMillis
            if (wallPassed < 0) {
                // The clock was moved back: restart the full wait from now rather than trust it.
                store.save(
                    state.copy(
                        lockoutStartWallMillis = clock.wallMillis(),
                        lockoutStartElapsedMillis = clock.elapsedMillis(),
                        lockoutStartBootCount = clock.bootCount(),
                    )
                )
                0L
            } else {
                wallPassed
            }
        }
        return (state.lockoutMillis - passed).coerceAtLeast(0).milliseconds
    }

    companion object {
        /** The 5th wrong attempt in a row starts the first wait. */
        const val FREE_ATTEMPTS = 5

        /** Warn when this many attempts (or fewer) are left before a wait. */
        const val WARN_WHEN_LEFT = 2

        val FIRST_DELAY = 30.seconds
        val MAX_DELAY = 3.hours

        /** 0 for the first 4 failures, then 30 s, 1 min, 2 min, … capped at 3 h. */
        fun delayAfter(failures: Int): Duration {
            if (failures < FREE_ATTEMPTS) return Duration.ZERO
            val doublings = failures - FREE_ATTEMPTS
            // Stop doubling well before overflow; the cap applies long before that.
            if (doublings >= 20) return MAX_DELAY
            val delay = FIRST_DELAY * (1L shl doublings).toInt()
            return if (delay > MAX_DELAY) MAX_DELAY else delay
        }
    }
}
