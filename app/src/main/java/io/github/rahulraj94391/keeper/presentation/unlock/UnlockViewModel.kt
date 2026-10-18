package io.github.rahulraj94391.keeper.presentation.unlock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rahulraj94391.keeper.di.appContainer
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.domain.repository.BiometricUnlockStart
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.presentation.common.BiometricPromptResult
import kotlinx.coroutines.launch
import javax.crypto.Cipher

enum class BiometricMessage { KeyInvalidated, Lockout, Failed }

data class UnlockUiState(
    val password: String = "",
    val isUnlocking: Boolean = false,
    val showError: Boolean = false,
    val showBiometricButton: Boolean = false,
    val biometricMessage: BiometricMessage? = null,
    /** A cipher waiting to be shown in BiometricPrompt. Holds no key material. */
    val biometricRequest: Cipher? = null,
) {
    val canSubmit: Boolean get() = password.isNotEmpty() && !isUnlocking

    override fun toString(): String = "UnlockUiState(isUnlocking=$isUnlocking, showError=$showError)"
}

class UnlockViewModel(
    private val authenticator: VaultAuthenticator,
    private val biometricAvailability: () -> BiometricAvailability,
) : ViewModel() {

    var uiState by mutableStateOf(UnlockUiState())
        private set

    private var autoPrompted = false

    init {
        viewModelScope.launch {
            authenticator.biometricEnabled.collect { enabled ->
                uiState = uiState.copy(
                    showBiometricButton = enabled && biometricAvailability() == BiometricAvailability.Available,
                )
            }
        }
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, showError = false)
    }

    fun unlock() {
        val state = uiState
        if (!state.canSubmit) return
        uiState = state.copy(isUnlocking = true, showError = false, biometricMessage = null)
        viewModelScope.launch {
            val chars = state.password.toCharArray()
            val unlocked = try {
                authenticator.unlock(chars)
            } finally {
                chars.fill('\u0000')
            }
            // Clear the field either way. A wrong attempt shows only a generic error (PRD 6.2).
            uiState = uiState.copy(password = "", isUnlocking = false, showError = !unlocked)
        }
    }

    /** Biometrics are the primary action when on (PRD 9.2), so prompt once when the screen opens. */
    fun autoPromptOnce() {
        if (autoPrompted || !uiState.showBiometricButton) return
        autoPrompted = true
        requestBiometric()
    }

    fun requestBiometric() {
        if (uiState.isUnlocking) return
        viewModelScope.launch {
            uiState = when (val start = authenticator.startBiometricUnlock()) {
                is BiometricUnlockStart.Ready -> uiState.copy(biometricRequest = start.cipher, biometricMessage = null)
                BiometricUnlockStart.NotEnabled -> uiState.copy(showBiometricButton = false)
                BiometricUnlockStart.KeyInvalidated ->
                    uiState.copy(showBiometricButton = false, biometricMessage = BiometricMessage.KeyInvalidated)
                BiometricUnlockStart.Unavailable -> uiState.copy(biometricMessage = BiometricMessage.Failed)
            }
        }
    }

    fun onBiometricRequestShown() {
        uiState = uiState.copy(biometricRequest = null)
    }

    fun onBiometricResult(result: BiometricPromptResult) {
        when (result) {
            is BiometricPromptResult.Success -> {
                uiState = uiState.copy(isUnlocking = true, biometricMessage = null)
                viewModelScope.launch {
                    val unlocked = authenticator.completeBiometricUnlock(result.cipher)
                    uiState = uiState.copy(
                        isUnlocking = false,
                        biometricMessage = if (unlocked) null else BiometricMessage.Failed,
                    )
                }
            }
            BiometricPromptResult.Cancelled -> Unit // The password field is right there.
            BiometricPromptResult.Lockout -> uiState = uiState.copy(biometricMessage = BiometricMessage.Lockout)
            BiometricPromptResult.Error -> uiState = uiState.copy(biometricMessage = BiometricMessage.Failed)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                UnlockViewModel(container.vaultAuthenticator, container.biometricAvailability::check)
            }
        }
    }
}
