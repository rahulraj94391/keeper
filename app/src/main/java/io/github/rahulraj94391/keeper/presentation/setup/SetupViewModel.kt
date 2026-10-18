package io.github.rahulraj94391.keeper.presentation.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rahulraj94391.keeper.di.appContainer
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import kotlinx.coroutines.launch

enum class SetupError { TooShort, Mismatch }

data class SetupUiState(
    val password: String = "",
    val confirmation: String = "",
    val acknowledged: Boolean = false,
    val passwordError: SetupError? = null,
    val confirmationError: SetupError? = null,
    val acknowledgeError: Boolean = false,
    val isCreating: Boolean = false,
) {
    val strength: PasswordStrength
        get() = PasswordStrength.of(password, SetupViewModel.MIN_PASSWORD_LENGTH)

    /** Errors only appear after a failed attempt; from then on they refresh as the user types. */
    val showsErrors: Boolean
        get() = passwordError != null || confirmationError != null || acknowledgeError

    override fun toString(): String = "SetupUiState(isCreating=$isCreating)"
}

class SetupViewModel(private val authenticator: VaultAuthenticator) : ViewModel() {

    var uiState by mutableStateOf(SetupUiState())
        private set

    fun onPasswordChange(value: String) = update { copy(password = value) }
    fun onConfirmationChange(value: String) = update { copy(confirmation = value) }
    fun onAcknowledgedChange(value: Boolean) = update { copy(acknowledged = value) }

    fun createVault() {
        val state = uiState
        if (state.isCreating) return
        val validated = state.validated()
        if (validated.showsErrors) {
            uiState = validated
            return
        }
        uiState = validated.copy(isCreating = true)
        viewModelScope.launch {
            val chars = state.password.toCharArray()
            try {
                authenticator.createVault(chars)
            } finally {
                chars.fill('\u0000')
            }
            // Drop the typed text; the app moves on to the vault once the state changes.
            uiState = SetupUiState()
        }
    }

    private inline fun update(change: SetupUiState.() -> SetupUiState) {
        val next = uiState.change()
        uiState = if (uiState.showsErrors) next.validated() else next
    }

    private fun SetupUiState.validated(): SetupUiState {
        val length = password.codePointCount(0, password.length)
        return copy(
            passwordError = if (length < MIN_PASSWORD_LENGTH) SetupError.TooShort else null,
            confirmationError = if (confirmation != password) SetupError.Mismatch else null,
            acknowledgeError = !acknowledged,
        )
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 12

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SetupViewModel(appContainer().vaultAuthenticator) }
        }
    }
}
