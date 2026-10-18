package io.github.rahulraj94391.keeper.testing

import io.github.rahulraj94391.keeper.security.ThrottleClock
import io.github.rahulraj94391.keeper.security.ThrottleState
import io.github.rahulraj94391.keeper.security.ThrottleStore
import io.github.rahulraj94391.keeper.security.UnlockThrottle

class InMemoryThrottleStore : ThrottleStore {
    var state = ThrottleState()
    override fun load() = state
    override fun save(state: ThrottleState) {
        this.state = state
    }
}

/** Both clocks move together unless a test changes one; reboot() resets elapsed time. */
class FakeThrottleClock(var wall: Long = 1_700_000_000_000, var elapsed: Long = 50_000, var boot: Int = 1) : ThrottleClock {
    override fun wallMillis() = wall
    override fun elapsedMillis() = elapsed
    override fun bootCount() = boot

    fun advance(millis: Long) {
        wall += millis
        elapsed += millis
    }

    fun reboot(downtimeMillis: Long = 10_000) {
        wall += downtimeMillis
        elapsed = 5_000
        boot++
    }
}

fun testThrottle(clock: FakeThrottleClock = FakeThrottleClock(), store: InMemoryThrottleStore = InMemoryThrottleStore()) =
    UnlockThrottle(store, clock)
