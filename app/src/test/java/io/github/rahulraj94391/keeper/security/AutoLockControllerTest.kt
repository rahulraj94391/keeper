package io.github.rahulraj94391.keeper.security

import io.github.rahulraj94391.keeper.domain.model.AutoLockTimeout
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.testing.FakeAutoLockPreferences
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
    private val preferences = FakeAutoLockPreferences(AutoLockTimeout.ThirtySeconds)

    private suspend fun TestScope.unlockedController(): AutoLockController {
        authenticator.createVault("fake master password".toCharArray())
        return AutoLockController(authenticator, this) { preferences.timeout.value.delay }
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

        controller.onSystemUiStarted()
        controller.onAppBackgrounded() // some devices stop the activity behind the prompt
        advanceSeconds(120)
        assertEquals(VaultState.Unlocked, authenticator.state.value)

        controller.onAppForegrounded()
        controller.onSystemUiFinished()
        advanceSeconds(120)
        assertEquals(VaultState.Unlocked, authenticator.state.value)
    }

    @Test
    fun leavingTheAppDuringAPrompt_countsFromWhenThePromptEnds() = runTest {
        val controller = unlockedController()

        controller.onSystemUiStarted()
        controller.onAppBackgrounded()
        advanceSeconds(10)
        controller.onSystemUiFinished() // e.g. cancelled because the user switched apps

        advanceSeconds(29)
        assertEquals(VaultState.Unlocked, authenticator.state.value)
        advanceSeconds(1)
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun screenOff_locksEvenDuringAPrompt() = runTest {
        val controller = unlockedController()

        controller.onSystemUiStarted()
        controller.onScreenOff()

        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun immediately_locksAsSoonAsTheAppLeaves() = runTest {
        preferences.setTimeout(AutoLockTimeout.Immediately)
        val controller = unlockedController()

        controller.onAppBackgrounded()

        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun immediately_stillWaitsForAnOpenBiometricPrompt() = runTest {
        preferences.setTimeout(AutoLockTimeout.Immediately)
        val controller = unlockedController()

        controller.onSystemUiStarted()
        controller.onAppBackgrounded()
        assertEquals(VaultState.Unlocked, authenticator.state.value)

        controller.onSystemUiFinished() // prompt closed while the app was still away
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun oneMinute_waitsSixtySeconds() = runTest {
        preferences.setTimeout(AutoLockTimeout.OneMinute)
        val controller = unlockedController()

        controller.onAppBackgrounded()
        advanceSeconds(59)
        assertEquals(VaultState.Unlocked, authenticator.state.value)
        advanceSeconds(1)
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun newSetting_appliesToTheNextTimeTheAppLeaves() = runTest {
        val controller = unlockedController()
        controller.onAppBackgrounded()
        controller.onAppForegrounded()

        preferences.setTimeout(AutoLockTimeout.Immediately)
        controller.onAppBackgrounded()

        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun defaultIsImmediately() {
        assertEquals(AutoLockTimeout.Immediately, AutoLockTimeout.DEFAULT)
    }
}
