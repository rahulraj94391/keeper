package io.github.rahulraj94391.keeper.presentation.credential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.rahulraj94391.keeper.di.appContainer
import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.presentation.navigation.CredentialDetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CredentialDetailUiState {
    data object Loading : CredentialDetailUiState
    data class Loaded(val credential: Credential) : CredentialDetailUiState

    /** The record was deleted or never existed; the screen should close. */
    data object NotFound : CredentialDetailUiState
}

class CredentialDetailViewModel(
    private val credentialId: String,
    private val repository: CredentialRepository,
) : ViewModel() {

    val uiState: StateFlow<CredentialDetailUiState> = repository.observe(credentialId)
        .map { credential ->
            if (credential == null) CredentialDetailUiState.NotFound
            else CredentialDetailUiState.Loaded(credential)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CredentialDetailUiState.Loading)

    /** Deleting moves the state to [CredentialDetailUiState.NotFound], which closes the screen. */
    fun delete() {
        viewModelScope.launch { repository.delete(credentialId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CredentialDetailViewModel(
                    credentialId = createSavedStateHandle().toRoute<CredentialDetailRoute>().id,
                    repository = appContainer().credentialRepository,
                )
            }
        }
    }
}
