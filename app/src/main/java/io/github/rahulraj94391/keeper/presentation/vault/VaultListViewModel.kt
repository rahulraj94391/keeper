package io.github.rahulraj94391.keeper.presentation.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rahulraj94391.keeper.di.appContainer
import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One list row. Only the title is shown; usernames and passwords stay off the list (PRD 9.3). */
data class VaultListItem(val id: String, val title: String, val favorite: Boolean)

data class VaultListUiState(
    val isLoading: Boolean = true,
    val items: List<VaultListItem> = emptyList(),
    val vaultIsEmpty: Boolean = false,
)

class VaultListViewModel(repository: CredentialRepository) : ViewModel() {

    // Compose state keeps the text field in sync; the flow drives filtering.
    var query by mutableStateOf("")
        private set
    private val queryFlow = MutableStateFlow("")

    val uiState: StateFlow<VaultListUiState> =
        combine(repository.observeAll(), queryFlow) { credentials, query ->
            VaultListUiState(
                isLoading = false,
                items = credentials.filter { it.matches(query) }
                    .map { VaultListItem(it.id, it.title, it.favorite) },
                vaultIsEmpty = credentials.isEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultListUiState())

    fun onQueryChange(newQuery: String) {
        query = newQuery
        queryFlow.value = newQuery
    }

    private fun Credential.matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || title.contains(q, ignoreCase = true) || url.contains(q, ignoreCase = true)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { VaultListViewModel(appContainer().credentialRepository) }
        }
    }
}
