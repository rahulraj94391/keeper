package io.github.rahulraj94391.keeper.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rahulraj94391.keeper.di.appContainer
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator

enum class SettingsDialog { ExportPassphrase, RestoreConfirm, RestorePassphrase, Limitations }

enum class PassphraseError { Required, TooShort, Mismatch }

/** Typed backup passphrases live here only while a dialog is open. */
data class BackupPassphraseForm(
    val passphrase: String = "",
    val confirmation: String = "",
    val passphraseError: PassphraseError? = null,
    val confirmationError: PassphraseError? = null,
) {
    override fun toString(): String = "BackupPassphraseForm(passphraseError=$passphraseError)"
}

data class SettingsUiState(
    val dialog: SettingsDialog? = null,
    val backupForm: BackupPassphraseForm = BackupPassphraseForm(),
    /** True after a backup flow ends, until the screen has told the user it isn't available yet. */
    val showBackupUnavailable: Boolean = false,
)

class SettingsViewModel(private val authenticator: VaultAuthenticator) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState())
        private set

    fun lockNow() = authenticator.lock()

    fun onExportClick() = openDialog(SettingsDialog.ExportPassphrase)
    fun onRestoreClick() = openDialog(SettingsDialog.RestoreConfirm)
    fun onRestoreConfirmed() = openDialog(SettingsDialog.RestorePassphrase)
    fun onLimitationsClick() = openDialog(SettingsDialog.Limitations)

    fun dismissDialog() {
        uiState = uiState.copy(dialog = null, backupForm = BackupPassphraseForm())
    }

    fun onPassphraseChange(value: String) {
        uiState = uiState.copy(backupForm = uiState.backupForm.copy(passphrase = value, passphraseError = null))
    }

    fun onConfirmationChange(value: String) {
        uiState = uiState.copy(backupForm = uiState.backupForm.copy(confirmation = value, confirmationError = null))
    }

    fun submitExport() {
        val form = uiState.backupForm
        val length = form.passphrase.codePointCount(0, form.passphrase.length)
        val checked = form.copy(
            passphraseError = if (length < MIN_PASSPHRASE_LENGTH) PassphraseError.TooShort else null,
            confirmationError = if (form.confirmation != form.passphrase) PassphraseError.Mismatch else null,
        )
        if (checked.passphraseError != null || checked.confirmationError != null) {
            uiState = uiState.copy(backupForm = checked)
            return
        }
        finishBackupFlow()
    }

    fun submitRestore() {
        val form = uiState.backupForm
        if (form.passphrase.isEmpty()) {
            uiState = uiState.copy(backupForm = form.copy(passphraseError = PassphraseError.Required))
            return
        }
        finishBackupFlow()
    }

    fun onBackupUnavailableShown() {
        uiState = uiState.copy(showBackupUnavailable = false)
    }

    // Export and restore need encrypted storage and the backup format, which come in a later
    // phase. Until then the flow ends here without touching any file or the vault.
    private fun finishBackupFlow() {
        uiState = SettingsUiState(showBackupUnavailable = true)
    }

    private fun openDialog(dialog: SettingsDialog) {
        uiState = uiState.copy(dialog = dialog, backupForm = BackupPassphraseForm())
    }

    companion object {
        /** Same minimum as the master password; the backup protects the same data. */
        const val MIN_PASSPHRASE_LENGTH = 12

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(appContainer().vaultAuthenticator) }
        }
    }
}
