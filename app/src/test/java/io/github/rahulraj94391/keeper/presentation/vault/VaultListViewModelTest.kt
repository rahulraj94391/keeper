package io.github.rahulraj94391.keeper.presentation.vault

import io.github.rahulraj94391.keeper.data.repository.InMemoryCredentialRepository
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import io.github.rahulraj94391.keeper.testing.testCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = InMemoryCredentialRepository(
        listOf(
            testCredential("mail", title = "Example Mail", url = "https://mail.example.com"),
            testCredential("bank", title = "Demo Bank", url = "https://bank.example.org"),
        )
    )

    @Test
    fun query_filtersByTitleOrUrlIgnoringCase() = runTest {
        val viewModel = VaultListViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onQueryChange("  BANK ")
        assertEquals(listOf("bank"), viewModel.uiState.value.items.map { it.id })

        viewModel.onQueryChange("mail.example")
        assertEquals(listOf("mail"), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun noMatches_isNotReportedAsEmptyVault() = runTest {
        val viewModel = VaultListViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onQueryChange("nothing matches this")

        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertFalse(state.vaultIsEmpty)
    }

    @Test
    fun emptyRepository_isReportedAsEmptyVault() = runTest {
        val viewModel = VaultListViewModel(InMemoryCredentialRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertTrue(viewModel.uiState.value.vaultIsEmpty)
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
