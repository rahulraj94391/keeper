package io.github.rahulraj94391.keeper.presentation.unlock

import io.github.rahulraj94391.keeper.data.vault.InMemoryVaultMetadataStore
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.security.VaultSession
import io.github.rahulraj94391.keeper.testing.FakeBiometricKeyStore
import io.github.rahulraj94391.keeper.testing.FakeThrottleClock
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import io.github.rahulraj94391.keeper.testing.testAuthenticator
import io.github.rahulraj94391.keeper.testing.testThrottle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UnlockLockoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeThrottleClock()
    private val authenticator = testAuthenticator(InMemoryVaultMetadataStore(), VaultSession(), FakeBiometricKeyStore(), testThrottle(clock))

    private suspend fun lockedVault() {
        authenticator.createVault("fake master password".toCharArray())
        authenticator.lock()
    }

    private fun UnlockViewModel.tryWrong() {
        onPasswordChange("wrong fake password")
        unlock()
    }

    /** Moves virtual time and the throttle's clock together. */
    private fun TestScope.advanceSeconds(seconds: Int) {
        repeat(seconds) {
            clock.advance(1_000)
            advanceTimeBy(1_000)
            runCurrent()
        }
    }

    @Test
    fun warningAppearsOnTheThirdWrongPassword() = runTest {
        lockedVault()
        val viewModel = UnlockViewModel(authenticator) { BiometricAvailability.Available }

        viewModel.tryWrong()
        viewModel.tryWrong()
        assertNull(viewModel.uiState.attemptsBeforeDelay)
        viewModel.tryWrong()
        assertEquals(2, viewModel.uiState.attemptsBeforeDelay)
        assertTrue(viewModel.uiState.showError)
    }

    @Test
    fun fifthWrongPassword_disablesUnlockAndCountsDown() = runTest {
        lockedVault()
        val viewModel = UnlockViewModel(authenticator) { BiometricAvailability.Available }
        repeat(5) { viewModel.tryWrong() }

        assertEquals(30.seconds, viewModel.uiState.lockoutRemaining)
        viewModel.onPasswordChange("fake master password")
        assertFalse(viewModel.uiState.canSubmit)

        advanceSeconds(10)
        assertEquals(20.seconds, viewModel.uiState.lockoutRemaining)

        advanceSeconds(20)
        assertNull(viewModel.uiState.lockoutRemaining)
        viewModel.onPasswordChange("fake master password")
        assertTrue(viewModel.uiState.canSubmit)
    }

    @Test
    fun reopeningTheScreen_showsTheRunningWait() = runTest {
        lockedVault()
        repeat(5) { authenticator.unlock("wrong fake password".toCharArray()) }
        clock.advance(12_000)

        val viewModel = UnlockViewModel(authenticator) { BiometricAvailability.Available }

        assertEquals(18.seconds, viewModel.uiState.lockoutRemaining)

        // Let the wait finish, so the countdown loop ends with the test.
        advanceSeconds(18)
        assertNull(viewModel.uiState.lockoutRemaining)
    }

    @Test
    fun countdownFormat() {
        assertEquals("0:30", formatCountdown(30.seconds))
        assertEquals("0:01", formatCountdown(0.2.seconds))
        assertEquals("4:00", formatCountdown(240.seconds))
        assertEquals("2:08:00", formatCountdown(7_680.seconds))
        assertEquals("3:00:00", formatCountdown(10_800.seconds))
    }
}
