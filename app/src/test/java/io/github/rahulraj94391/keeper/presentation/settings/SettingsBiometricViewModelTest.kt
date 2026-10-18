package io.github.rahulraj94391.keeper.presentation.settings

import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.presentation.common.BiometricPromptResult
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import io.github.rahulraj94391.keeper.testing.testAuthenticator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsBiometricViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authenticator = testAuthenticator()
    private var availability = BiometricAvailability.Available
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() = runTest {
        authenticator.createVault("fake master password".toCharArray())
        viewModel = SettingsViewModel(authenticator) { availability }
    }

    @Test
    fun turningOn_promptsThenEnables() {
        viewModel.onBiometricToggle(true)
        val cipher = checkNotNull(viewModel.uiState.biometricRequest)

        viewModel.onBiometricResult(BiometricPromptResult.Success(cipher))

        assertTrue(viewModel.uiState.biometricEnabled)
        assertEquals(BiometricSettingsMessage.Enabled, viewModel.uiState.biometricMessage)
    }

    @Test
    fun cancellingThePrompt_leavesItOff() {
        viewModel.onBiometricToggle(true)

        viewModel.onBiometricResult(BiometricPromptResult.Cancelled)

        assertFalse(viewModel.uiState.biometricEnabled)
        assertNull(viewModel.uiState.biometricMessage)
    }

    @Test
    fun turningOff_disables() {
        viewModel.onBiometricToggle(true)
        viewModel.onBiometricResult(BiometricPromptResult.Success(viewModel.uiState.biometricRequest!!))

        viewModel.onBiometricToggle(false)

        assertFalse(viewModel.uiState.biometricEnabled)
        assertFalse(authenticator.biometricEnabled.value)
    }

    @Test
    fun withoutEnrolledBiometrics_nothingHappens() {
        availability = BiometricAvailability.NotEnrolled
        viewModel.refreshBiometricAvailability()

        viewModel.onBiometricToggle(true)

        assertNull(viewModel.uiState.biometricRequest)
        assertEquals(BiometricAvailability.NotEnrolled, viewModel.uiState.biometricAvailability)
    }
}
