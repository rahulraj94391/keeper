package io.github.rahulraj94391.keeper.presentation.unlock

import io.github.rahulraj94391.keeper.data.vault.InMemoryVaultMetadataStore
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.presentation.common.BiometricPromptResult
import io.github.rahulraj94391.keeper.security.VaultSession
import io.github.rahulraj94391.keeper.testing.FakeBiometricKeyStore
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import io.github.rahulraj94391.keeper.testing.testAuthenticator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UnlockBiometricViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val keyStore = FakeBiometricKeyStore()
    private val authenticator = testAuthenticator(InMemoryVaultMetadataStore(), VaultSession(), keyStore)
    private var availability = BiometricAvailability.Available

    private suspend fun lockedVaultWithBiometrics() {
        authenticator.createVault("fake master password".toCharArray())
        authenticator.completeBiometricEnrollment(authenticator.startBiometricEnrollment())
        authenticator.lock()
    }

    private fun viewModel() = UnlockViewModel(authenticator) { availability }

    @Test
    fun buttonShowsOnlyWhenEnabledAndAvailable() = runTest {
        lockedVaultWithBiometrics()
        assertTrue(viewModel().uiState.showBiometricButton)

        availability = BiometricAvailability.NotEnrolled
        assertFalse(viewModel().uiState.showBiometricButton)
    }

    @Test
    fun autoPrompt_happensOnce() = runTest {
        lockedVaultWithBiometrics()
        val viewModel = viewModel()

        viewModel.autoPromptOnce()
        assertNotNull(viewModel.uiState.biometricRequest)
        viewModel.onBiometricRequestShown()

        viewModel.autoPromptOnce()
        assertNull(viewModel.uiState.biometricRequest)
    }

    @Test
    fun successfulPrompt_unlocks() = runTest {
        lockedVaultWithBiometrics()
        val viewModel = viewModel()
        viewModel.requestBiometric()
        val cipher = checkNotNull(viewModel.uiState.biometricRequest)

        viewModel.onBiometricResult(BiometricPromptResult.Success(cipher))

        assertEquals(VaultState.Unlocked, authenticator.state.value)
    }

    @Test
    fun cancelShowsNothingAndLockoutShowsMessage() = runTest {
        lockedVaultWithBiometrics()
        val viewModel = viewModel()

        viewModel.onBiometricResult(BiometricPromptResult.Cancelled)
        assertNull(viewModel.uiState.biometricMessage)

        viewModel.onBiometricResult(BiometricPromptResult.Lockout)
        assertEquals(BiometricMessage.Lockout, viewModel.uiState.biometricMessage)
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun invalidatedKey_hidesButtonAndExplains() = runTest {
        lockedVaultWithBiometrics()
        keyStore.invalidate()
        val viewModel = viewModel()

        viewModel.requestBiometric()

        assertFalse(viewModel.uiState.showBiometricButton)
        assertEquals(BiometricMessage.KeyInvalidated, viewModel.uiState.biometricMessage)
    }
}
