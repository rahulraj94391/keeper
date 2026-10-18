package io.github.rahulraj94391.keeper.presentation.credential

import io.github.rahulraj94391.keeper.data.repository.InMemoryCredentialRepository
import io.github.rahulraj94391.keeper.domain.model.CredentialLimits
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import io.github.rahulraj94391.keeper.testing.testCredential
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CredentialEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun save_withMissingTitleAndPassword_showsErrorsAndDoesNotSave() = runTest {
        val repository = InMemoryCredentialRepository()
        val viewModel = CredentialEditViewModel(null, repository)

        viewModel.onTitleChange("   ")
        viewModel.save()

        val state = viewModel.uiState
        assertEquals(FieldError.Required, state.errors.title)
        assertEquals(FieldError.Required, state.errors.password)
        assertFalse(state.isSaved)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun errors_updateWhileTypingAfterFailedSave() = runTest {
        val viewModel = CredentialEditViewModel(null, InMemoryCredentialRepository())
        viewModel.save()

        viewModel.onTitleChange("Mail")

        assertNull(viewModel.uiState.errors.title)
        assertEquals(FieldError.Required, viewModel.uiState.errors.password)
    }

    @Test
    fun validate_rejectsValuesOverTheLimit() {
        val errors = CredentialEditViewModel.validate(
            CredentialForm(title = "t", password = "x".repeat(CredentialLimits.PASSWORD + 1))
        )

        assertEquals(FieldError.TooLong(CredentialLimits.PASSWORD), errors.password)
    }

    @Test
    fun save_newCredential_storesTrimmedTitleAndExactPassword() = runTest {
        val repository = InMemoryCredentialRepository()
        val viewModel = CredentialEditViewModel(null, repository)

        viewModel.onTitleChange("  Example Mail  ")
        viewModel.onPasswordChange(" secret with spaces ")
        viewModel.save()

        val saved = repository.observeAll().first().single()
        assertEquals("Example Mail", saved.title)
        assertEquals(" secret with spaces ", saved.password)
        assertTrue(viewModel.uiState.isSaved)
    }

    @Test
    fun existingCredential_isLoadedAndUpdatedInPlace() = runTest {
        val repository = InMemoryCredentialRepository(listOf(testCredential("1", title = "Old")))
        val viewModel = CredentialEditViewModel("1", repository)

        assertFalse(viewModel.uiState.isNew)
        assertEquals("Old", viewModel.uiState.form.title)
        assertFalse(viewModel.uiState.hasUnsavedChanges)

        viewModel.onTitleChange("New")
        assertTrue(viewModel.uiState.hasUnsavedChanges)
        viewModel.save()

        assertEquals("New", repository.get("1")?.title)
        assertEquals(1, repository.observeAll().first().size)
    }
}
