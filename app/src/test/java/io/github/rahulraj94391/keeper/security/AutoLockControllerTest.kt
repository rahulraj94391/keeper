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

    private val authenticator = testAuthenticator()

    private suspend fun TestScope.unlockedController(): AutoLockController {
        authenticator.createVault("fake master password".toCharArray())
        return AutoLockController(authenticator, this)
    }

    private fun TestScope.advanceSeconds(seconds: Long) {
        advanceTimeBy(seconds * 1_000)
        runCurrent()
    }

    @Test
    fun locksThirtySecondsAfterGoingToBackground() = runTest {
        val controller = unlockedController()

        controller.onAppBackgrounded()
        advanceSeconds(29)
        assertEquals(VaultState.Unlocked, authenticator.state.value)

        advanceSeconds(1)
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun comingBackInTime_cancelsTheLock() = runTest {
        val controller = unlockedController()

        controller.onAppBackgrounded()
        advanceSeconds(20)
        controller.onAppForegrounded()
        advanceSeconds(60)

        assertEquals(VaultState.Unlocked, authenticator.state.value)
    }

    @Test
    fun screenOff_locksImmediately() = runTest {
        val controller = unlockedController()

        controller.onScreenOff()

        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun biometricPrompt_doesNotStartTheTimer() = runTest {
        val controller = unlockedController()

        controller.onSystemAuthStarted()
        controller.onAppBackgrounded() // some devices stop the activity behind the prompt
        advanceSeconds(120)
        assertEquals(VaultState.Unlocked, authenticator.state.value)

        controller.onAppForegrounded()
        controller.onSystemAuthFinished()
        advanceSeconds(120)
        assertEquals(VaultState.Unlocked, authenticator.state.value)
    }

    @Test
    fun leavingTheAppDuringAPrompt_countsFromWhenThePromptEnds() = runTest {
        val controller = unlockedController()

        controller.onSystemAuthStarted()
        controller.onAppBackgrounded()
        advanceSeconds(10)
        controller.onSystemAuthFinished() // e.g. cancelled because the user switched apps

        advanceSeconds(29)
        assertEquals(VaultState.Unlocked, authenticator.state.value)
        advanceSeconds(1)
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun screenOff_locksEvenDuringAPrompt() = runTest {
        val controller = unlockedController()

        controller.onSystemAuthStarted()
        controller.onScreenOff()

        assertEquals(VaultState.Locked, authenticator.state.value)
    }
}
