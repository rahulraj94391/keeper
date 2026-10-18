package io.github.rahulraj94391.keeper.presentation.credential

import io.github.rahulraj94391.keeper.data.repository.InMemoryCredentialRepository
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import io.github.rahulraj94391.keeper.testing.testCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CredentialDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun existingCredential_isLoaded() = runTest {
        val credential = testCredential("1")
        val viewModel = CredentialDetailViewModel("1", InMemoryCredentialRepository(listOf(credential)))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(CredentialDetailUiState.Loaded(credential), viewModel.uiState.value)
    }

    @Test
    fun delete_removesCredentialAndReportsNotFound() = runTest {
        val repository = InMemoryCredentialRepository(listOf(testCredential("1")))
        val viewModel = CredentialDetailViewModel("1", repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.delete()

        assertNull(repository.get("1"))
        assertEquals(CredentialDetailUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun unknownId_reportsNotFound() = runTest {
        val viewModel = CredentialDetailViewModel("missing", InMemoryCredentialRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(CredentialDetailUiState.NotFound, viewModel.uiState.value)
    }
}
