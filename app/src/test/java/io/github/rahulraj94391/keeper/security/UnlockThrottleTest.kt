package io.github.rahulraj94391.keeper.security

import io.github.rahulraj94391.keeper.testing.FakeThrottleClock
import io.github.rahulraj94391.keeper.testing.InMemoryThrottleStore
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class UnlockThrottleTest {

    private val clock = FakeThrottleClock()
    private val store = InMemoryThrottleStore()
    private val throttle = UnlockThrottle(store, clock)

    private fun fail(times: Int): ThrottleStatus {
        var status: ThrottleStatus = throttle.status()
        repeat(times) {
            // Let any running wait pass first, as a real user would have to.
            (throttle.status() as? ThrottleStatus.LockedOut)?.let { clock.advance(it.remaining.inWholeMilliseconds) }
            status = throttle.recordFailure()
        }
        return status
    }

    @Test
    fun backoffSchedule() {
        val expected = listOf(
            1 to Duration.ZERO, 4 to Duration.ZERO,
            5 to 30.seconds, 6 to 1.minutes, 7 to 2.minutes, 8 to 4.minutes, 9 to 8.minutes,
            10 to 16.minutes, 11 to 32.minutes, 12 to 64.minutes, 13 to 128.minutes,
            14 to 3.hours, 20 to 3.hours, 1_000 to 3.hours,
        )
        expected.forEach { (failures, delay) ->
            assertEquals("after $failures failures", delay, UnlockThrottle.delayAfter(failures))
        }
    }

    @Test
    fun firstFourAreFree_withAWarningForTheLastTwo() {
        assertEquals(ThrottleStatus.Allowed(null), fail(1))
        assertEquals(ThrottleStatus.Allowed(null), fail(1))
        assertEquals(ThrottleStatus.Allowed(attemptsBeforeDelay = 2), fail(1))
        assertEquals(ThrottleStatus.Allowed(attemptsBeforeDelay = 1), fail(1))
    }

    @Test
    fun fifthFailure_startsA30SecondWait() {
        assertEquals(ThrottleStatus.LockedOut(30.seconds), fail(5))

        clock.advance(29_000)
        assertEquals(ThrottleStatus.LockedOut(1.seconds), throttle.status())
        clock.advance(1_000)
        assertEquals(ThrottleStatus.Allowed(null), throttle.status())
    }

    @Test
    fun eachFurtherFailure_doublesTheWait() {
        fail(5)
        assertEquals(ThrottleStatus.LockedOut(1.minutes), fail(1))
        assertEquals(ThrottleStatus.LockedOut(2.minutes), fail(1))
    }

    @Test
    fun success_resetsEverything() {
        fail(6)
        throttle.recordSuccess()

        assertEquals(ThrottleStatus.Allowed(null), throttle.status())
        assertEquals(ThrottleStatus.Allowed(null), fail(1))
    }

    @Test
    fun survivesAppRestart() {
        fail(5)
        clock.advance(10_000)

        val restarted = UnlockThrottle(store, clock)

        assertEquals(ThrottleStatus.LockedOut(20.seconds), restarted.status())
    }

    @Test
    fun movingTheWallClockForward_doesNotShortenTheWaitWithinABoot() {
        fail(5)

        clock.wall += 1.hours.inWholeMilliseconds

        assertEquals(ThrottleStatus.LockedOut(30.seconds), throttle.status())
    }

    @Test
    fun afterAReboot_wallClockTimeCounts() {
        fail(6) // 1-minute wait
        clock.reboot(downtimeMillis = 20_000)

        assertEquals(ThrottleStatus.LockedOut(40.seconds), throttle.status())
    }

    @Test
    fun afterARebootWithTheClockMovedBack_theFullWaitRestarts() {
        fail(6) // 1-minute wait
        clock.reboot()
        clock.wall -= 1.hours.inWholeMilliseconds

        assertEquals(ThrottleStatus.LockedOut(1.minutes), throttle.status())
        clock.advance(30_000)
        assertEquals(ThrottleStatus.LockedOut(30.seconds), throttle.status())
    }
}
