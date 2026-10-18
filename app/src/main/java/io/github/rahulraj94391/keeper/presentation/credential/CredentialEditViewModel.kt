package io.github.rahulraj94391.keeper.presentation.credential

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import io.github.rahulraj94391.keeper.di.appContainer
import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.model.CredentialLimits
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.presentation.navigation.CredentialEditRoute
import io.github.rahulraj94391.keeper.security.CharacterGroup
import io.github.rahulraj94391.keeper.security.PasswordGenerator
import io.github.rahulraj94391.keeper.security.PasswordOptions
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface FieldError {
    data object Required : FieldError
    data class TooLong(val max: Int) : FieldError
}

/** The form's values live only in this ViewModel, never in saved state (PRD 7.4). */
data class CredentialForm(
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
) {
    override fun toString(): String = "CredentialForm(title.length=${title.length})"
}

data class CredentialFormErrors(
    val title: FieldError? = null,
    val username: FieldError? = null,
    val password: FieldError? = null,
    val url: FieldError? = null,
    val notes: FieldError? = null,
) {
    val isEmpty: Boolean get() = listOf(title, username, password, url, notes).all { it == null }
}

/** The password generator sheet. Present while the sheet is open, null otherwise. */
data class GeneratorUiState(
    val options: PasswordOptions,
    val password: String,
) {
    override fun toString(): String = "GeneratorUiState(options=$options)"
}

data class CredentialEditUiState(
    val isNew: Boolean,
    val isLoading: Boolean,
    val form: CredentialForm = CredentialForm(),
    val errors: CredentialFormErrors = CredentialFormErrors(),
    val hasUnsavedChanges: Boolean = false,
    val isSaved: Boolean = false,
    val generator: GeneratorUiState? = null,
)

class CredentialEditViewModel(
    private val credentialId: String?,
    private val repository: CredentialRepository,
    private val passwordGenerator: PasswordGenerator = PasswordGenerator(),
) : ViewModel() {

    var uiState by mutableStateOf(
        CredentialEditUiState(isNew = credentialId == null, isLoading = credentialId != null)
    )
        private set

    private var original = CredentialForm()

    // Kept between openings of the sheet so the user's preferred settings stick.
    private var generatorOptions = PasswordOptions()

    init {
        if (credentialId != null) {
            viewModelScope.launch {
                val existing = repository.get(credentialId)
                original = existing?.toForm() ?: CredentialForm()
                uiState = uiState.copy(isLoading = false, form = original)
            }
        }
    }

    fun onTitleChange(value: String) = updateForm { copy(title = value) }
    fun onUsernameChange(value: String) = updateForm { copy(username = value) }
    fun onPasswordChange(value: String) = updateForm { copy(password = value) }
    fun onUrlChange(value: String) = updateForm { copy(url = value) }
    fun onNotesChange(value: String) = updateForm { copy(notes = value) }
    fun onFavoriteChange(value: Boolean) = updateForm { copy(favorite = value) }

    fun save() {
        val state = uiState
        if (state.isLoading || state.isSaved) return
        val errors = validate(state.form)
        if (!errors.isEmpty) {
            uiState = state.copy(errors = errors)
            return
        }
        val form = state.form
        val credential = Credential(
            id = credentialId ?: UUID.randomUUID().toString(),
            title = form.title.trim(),
            username = form.username,
            password = form.password,
            url = form.url.trim(),
            notes = form.notes,
            favorite = form.favorite,
        )
        viewModelScope.launch {
            repository.save(credential)
            uiState = uiState.copy(isSaved = true, hasUnsavedChanges = false)
        }
    }

    fun openGenerator() = showGenerated(generatorOptions)

    fun closeGenerator() {
        uiState = uiState.copy(generator = null)
    }

    fun regeneratePassword() = showGenerated(generatorOptions)

    fun onGeneratorLengthChange(length: Int) {
        if (length != generatorOptions.length) showGenerated(generatorOptions.copy(length = length))
    }

    /** Turning off the last remaining group is ignored; at least one must stay on. */
    fun onGeneratorGroupChange(group: CharacterGroup, enabled: Boolean) {
        val groups = if (enabled) generatorOptions.groups + group else generatorOptions.groups - group
        if (groups.isEmpty() || groups == generatorOptions.groups) return
        showGenerated(generatorOptions.copy(groups = groups))
    }

    fun useGeneratedPassword() {
        val generated = uiState.generator?.password ?: return
        updateForm { copy(password = generated) }
        closeGenerator()
    }

    private fun showGenerated(options: PasswordOptions) {
        generatorOptions = options
        uiState = uiState.copy(generator = GeneratorUiState(options, passwordGenerator.generate(options)))
    }

    private inline fun updateForm(change: CredentialForm.() -> CredentialForm) {
        val form = uiState.form.change()
        // Once a save attempt has shown errors, keep them current as the user types.
        val errors = if (uiState.errors.isEmpty) uiState.errors else validate(form)
        uiState = uiState.copy(form = form, errors = errors, hasUnsavedChanges = form != original)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CredentialEditViewModel(
                    credentialId = createSavedStateHandle().toRoute<CredentialEditRoute>().id,
                    repository = appContainer().credentialRepository,
                )
            }
        }

        // Passwords and usernames are kept exactly as typed: spaces can be part of a secret.
        fun validate(form: CredentialForm) = CredentialFormErrors(
            title = when {
                form.title.isBlank() -> FieldError.Required
                else -> checkLength(form.title.trim(), CredentialLimits.TITLE)
            },
            username = checkLength(form.username, CredentialLimits.USERNAME),
            password = when {
                form.password.isEmpty() -> FieldError.Required
                else -> checkLength(form.password, CredentialLimits.PASSWORD)
            },
            url = checkLength(form.url.trim(), CredentialLimits.URL),
            notes = checkLength(form.notes, CredentialLimits.NOTES),
        )

        private fun checkLength(value: String, max: Int): FieldError? =
            if (value.length > max) FieldError.TooLong(max) else null

        private fun Credential.toForm() = CredentialForm(title, username, password, url, notes, favorite)
    }
}
