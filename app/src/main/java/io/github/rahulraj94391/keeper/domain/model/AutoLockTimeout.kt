package io.github.rahulraj94391.keeper.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How long the vault stays unlocked after Keeper leaves the screen. Screen off always locks. */
enum class AutoLockTimeout(val delay: Duration) {
    Immediately(Duration.ZERO),
    ThirtySeconds(30.seconds),
    OneMinute(1.minutes),
    ;

    companion object {
        /** Most secure option; users can choose a longer one in Settings (PRD 6.6). */
        val DEFAULT = Immediately
    }
}
