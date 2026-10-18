package io.github.rahulraj94391.keeper.presentation.unlock

import io.github.rahulraj94391.keeper.testing.testAuthenticator
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UnlockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authenticator = testAuthenticator()
    private lateinit var viewModel: UnlockViewModel

    @Before
    fun setUp() = runTest {
        authenticator.createVault("fake master password".toCharArray())
        authenticator.lock()
        viewModel = UnlockViewModel(authenticator) { BiometricAvailability.Available }
    }

    @Test
    fun emptyPassword_cannotBeSubmitted() {
        assertFalse(viewModel.uiState.canSubmit)
        viewModel.unlock()
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun wrongPassword_staysLockedShowsErrorAndClearsField() {
        viewModel.onPasswordChange("wrong password")
        viewModel.unlock()

        assertEquals(VaultState.Locked, authenticator.state.value)
        assertTrue(viewModel.uiState.showError)
        assertEquals("", viewModel.uiState.password)
    }

    @Test
    fun typingAgain_hidesTheError() {
        viewModel.onPasswordChange("wrong password")
        viewModel.unlock()

        viewModel.onPasswordChange("f")

        assertFalse(viewModel.uiState.showError)
    }

    @Test
    fun correctPassword_unlocks() {
        viewModel.onPasswordChange("fake master password")
        viewModel.unlock()

        assertEquals(VaultState.Unlocked, authenticator.state.value)
        assertFalse(viewModel.uiState.showError)
    }
}
