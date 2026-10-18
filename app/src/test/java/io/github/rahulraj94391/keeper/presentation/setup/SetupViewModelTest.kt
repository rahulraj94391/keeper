package io.github.rahulraj94391.keeper.presentation.setup

import io.github.rahulraj94391.keeper.data.auth.InMemoryVaultAuthenticator
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authenticator = InMemoryVaultAuthenticator()
    private val viewModel = SetupViewModel(authenticator)

    @Test
    fun noErrorsShowBeforeTheFirstAttempt() {
        viewModel.onPasswordChange("short")

        assertFalse(viewModel.uiState.showsErrors)
    }

    @Test
    fun shortMismatchedUnacknowledged_showsAllErrorsAndCreatesNothing() {
        viewModel.onPasswordChange("short")
        viewModel.onConfirmationChange("other")
        viewModel.createVault()

        val state = viewModel.uiState
        assertEquals(SetupError.TooShort, state.passwordError)
        assertEquals(SetupError.Mismatch, state.confirmationError)
        assertTrue(state.acknowledgeError)
        assertEquals(VaultState.NoVault, authenticator.state.value)
    }

    @Test
    fun errorsClearAsTheUserFixesThem() {
        viewModel.createVault()

        viewModel.onPasswordChange("a long fake passphrase")
        viewModel.onConfirmationChange("a long fake passphrase")
        viewModel.onAcknowledgedChange(true)

        assertNull(viewModel.uiState.passwordError)
        assertNull(viewModel.uiState.confirmationError)
        assertFalse(viewModel.uiState.acknowledgeError)
    }

    @Test
    fun validInput_createsUnlockedVaultAndClearsTheForm() {
        viewModel.onPasswordChange("a long fake passphrase")
        viewModel.onConfirmationChange("a long fake passphrase")
        viewModel.onAcknowledgedChange(true)
        viewModel.createVault()

        assertEquals(VaultState.Unlocked, authenticator.state.value)
        assertEquals("", viewModel.uiState.password)
        assertEquals("", viewModel.uiState.confirmation)
    }
}
