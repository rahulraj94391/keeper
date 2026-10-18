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
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import kotlinx.coroutines.launch

data class UnlockUiState(
    val password: String = "",
    val isUnlocking: Boolean = false,
    val showError: Boolean = false,
) {
    val canSubmit: Boolean get() = password.isNotEmpty() && !isUnlocking

    override fun toString(): String = "UnlockUiState(isUnlocking=$isUnlocking, showError=$showError)"
}

class UnlockViewModel(private val authenticator: VaultAuthenticator) : ViewModel() {

    var uiState by mutableStateOf(UnlockUiState())
        private set

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, showError = false)
    }

    fun unlock() {
        val state = uiState
        if (!state.canSubmit) return
        uiState = state.copy(isUnlocking = true, showError = false)
        viewModelScope.launch {
            val chars = state.password.toCharArray()
            val unlocked = try {
                authenticator.unlock(chars)
            } finally {
                chars.fill('\u0000')
            }
            // Clear the field either way. A wrong attempt shows only a generic error (PRD 6.2).
            uiState = UnlockUiState(showError = !unlocked)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { UnlockViewModel(appContainer().vaultAuthenticator) }
        }
    }
}
