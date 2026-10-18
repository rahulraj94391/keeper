package io.github.rahulraj94391.keeper.security

import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.testing.testAuthenticator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoLockControllerTest {

    private val password = "fake master password"
    private val authenticator = testAuthenticator()

    /** The controller watches the vault state forever, so it lives in backgroundScope. */
    private suspend fun TestScope.unlockedController(): AutoLockController {
        val controller = AutoLockController(authenticator, backgroundScope)
        authenticator.createVault(password.toCharArray())
        runCurrent()
        return controller
    }

    private fun TestScope.advanceSeconds(seconds: Long) {
        advanceTimeBy(seconds * 1_000)
        runCurrent()
    }

    private fun assertState(expected: VaultState) = assertEquals(expected, authenticator.state.value)

    // --- Leaving the app ---

    @Test
    fun leavingTheApp_locksImmediately() = runTest {
        val controller = unlockedController()

        controller.onAppBackgrounded()

        assertState(VaultState.Locked)
    }

    @Test
    fun screenOff_locksImmediately() = runTest {
        val controller = unlockedController()

        controller.onScreenOff()

        assertState(VaultState.Locked)
    }

    // --- Inactivity ---

    @Test
    fun thirtySecondsWithoutActivity_locks() = runTest {
        unlockedController()

        advanceSeconds(29)
        assertState(VaultState.Unlocked)
        advanceSeconds(1)
        assertState(VaultState.Locked)
    }

    @Test
    fun activity_restartsTheThirtySeconds() = runTest {
        val controller = unlockedController()

        repeat(4) {
            advanceSeconds(20)
            controller.onUserInteraction()
        }
        assertState(VaultState.Unlocked) // 80 s open, never 30 s idle

        advanceSeconds(30)
        assertState(VaultState.Locked)
    }

    @Test
    fun unlockingAgain_startsAFreshTimer() = runTest {
        val controller = unlockedController()
        controller.onAppBackgrounded()
        controller.onAppForegrounded()
        advanceSeconds(5) // the user types the password

        authenticator.unlock(password.toCharArray())
        runCurrent()
        advanceSeconds(29)
        assertState(VaultState.Unlocked)
        advanceSeconds(1)
        assertState(VaultState.Locked)
    }

    @Test
    fun lockedVault_hasNoTimerRunning() = runTest {
        val controller = unlockedController()
        controller.onScreenOff()

        controller.onUserInteraction() // e.g. typing on the Unlock screen
        advanceSeconds(60)

        assertState(VaultState.Locked)
    }

    // --- Keeper's own system screens (BiometricPrompt, file picker) ---

    @Test
    fun systemScreen_neitherLocksOnLeavingNorTimesOut() = runTest {
        val controller = unlockedController()

        controller.onSystemUiStarted()
        controller.onAppBackgrounded() // the file picker always does this
        advanceSeconds(120)
        assertState(VaultState.Unlocked)

        controller.onAppForegrounded()
        controller.onSystemUiFinished()
        assertState(VaultState.Unlocked)
    }

    @Test
    fun afterASystemScreen_theInactivityTimerStartsAgain() = runTest {
        val controller = unlockedController()
        controller.onSystemUiStarted()
        advanceSeconds(60)
        controller.onSystemUiFinished()

        advanceSeconds(29)
        assertState(VaultState.Unlocked)
        advanceSeconds(1)
        assertState(VaultState.Locked)
    }

    @Test
    fun leavingKeeperDuringASystemScreen_locksWhenItCloses() = runTest {
        val controller = unlockedController()

        controller.onSystemUiStarted()
        controller.onAppBackgrounded()
        controller.onSystemUiFinished() // e.g. cancelled because the user switched apps

        assertState(VaultState.Locked)
    }

    @Test
    fun screenOff_locksEvenDuringASystemScreen() = runTest {
        val controller = unlockedController()

        controller.onSystemUiStarted()
        controller.onScreenOff()

        assertState(VaultState.Locked)
    }
}
