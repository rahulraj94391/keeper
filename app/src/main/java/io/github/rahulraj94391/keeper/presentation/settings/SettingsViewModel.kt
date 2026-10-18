package io.github.rahulraj94391.keeper.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rahulraj94391.keeper.data.backup.ExportResult
import io.github.rahulraj94391.keeper.data.backup.RestoreResult
import io.github.rahulraj94391.keeper.data.backup.VaultBackupService
import io.github.rahulraj94391.keeper.debug.SampleCredentials
import io.github.rahulraj94391.keeper.di.appContainer
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.presentation.common.BiometricPromptResult
import io.github.rahulraj94391.keeper.security.BackupCrypto
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException
import java.time.LocalDate
import javax.crypto.Cipher

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

enum class BiometricSettingsMessage { Enabled, EnableFailed, Lockout }

enum class BackupWork { Exporting, Restoring }

sealed interface BackupMessage {
    data class Exported(val count: Int) : BackupMessage
    data object ExportFailed : BackupMessage
    data class Restored(val count: Int) : BackupMessage
    data object WrongPassphraseOrDamaged : BackupMessage
    data object NotABackup : BackupMessage
    data object Unsupported : BackupMessage
    data object TooLarge : BackupMessage
    data object RestoreFailed : BackupMessage
}

data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.Unavailable,
    /** A cipher waiting to be shown in BiometricPrompt to turn biometrics on. No key material. */
    val biometricRequest: Cipher? = null,
    val biometricMessage: BiometricSettingsMessage? = null,
    val dialog: SettingsDialog? = null,
    val backupForm: BackupPassphraseForm = BackupPassphraseForm(),
    /** Set after sample logins were added, until the screen has said so. */
    val samplesAdded: Int? = null,
    /** Asks the screen to open the system "save" picker, suggesting this file name. */
    val exportPickerRequest: String? = null,
    /** Asks the screen to open the system "open" picker for a backup file. */
    val restorePickerRequest: Boolean = false,
    val backupWork: BackupWork? = null,
    val backupMessage: BackupMessage? = null,
)

class SettingsViewModel(
    private val authenticator: VaultAuthenticator,
    private val biometricAvailability: () -> BiometricAvailability,
    private val credentialRepository: CredentialRepository,
    private val backupService: VaultBackupService,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState(biometricAvailability = biometricAvailability()))
        private set

    // Held only between the passphrase dialog and the system picker; wiped right after use.
    private var exportPassphrase: CharArray? = null
    private var restoreUri: String? = null

    init {
        viewModelScope.launch {
            authenticator.biometricEnabled.collect { uiState = uiState.copy(biometricEnabled = it) }
        }
    }

    /** Debug builds only: saves the fake sample logins through the normal encrypted path. */
    fun addSampleLogins() {
        if (!SampleCredentials.AVAILABLE) return
        viewModelScope.launch {
            val samples = SampleCredentials.all()
            samples.forEach { credentialRepository.save(it) }
            uiState = uiState.copy(samplesAdded = samples.size)
        }
    }

    fun onSamplesAddedShown() {
        uiState = uiState.copy(samplesAdded = null)
    }

    fun lockNow() = authenticator.lock()

    /** Re-checks the device, e.g. after the user returns from enrolling a fingerprint. */
    fun refreshBiometricAvailability() {
        uiState = uiState.copy(biometricAvailability = biometricAvailability())
    }

    fun onBiometricToggle(enable: Boolean) {
        if (!enable) {
            viewModelScope.launch { authenticator.disableBiometric() }
            return
        }
        if (uiState.biometricAvailability != BiometricAvailability.Available) return
        viewModelScope.launch {
            uiState = try {
                uiState.copy(biometricRequest = authenticator.startBiometricEnrollment(), biometricMessage = null)
            } catch (_: GeneralSecurityException) {
                uiState.copy(biometricMessage = BiometricSettingsMessage.EnableFailed)
            } catch (_: IllegalStateException) {
                // Keystore refuses when no biometric is enrolled; also covers a locked vault.
                uiState.copy(biometricMessage = BiometricSettingsMessage.EnableFailed)
            }
        }
    }

    fun onBiometricRequestShown() {
        uiState = uiState.copy(biometricRequest = null)
    }

    fun onBiometricResult(result: BiometricPromptResult) {
        when (result) {
            is BiometricPromptResult.Success -> viewModelScope.launch {
                val enabled = authenticator.completeBiometricEnrollment(result.cipher)
                uiState = uiState.copy(
                    biometricMessage = if (enabled) BiometricSettingsMessage.Enabled else BiometricSettingsMessage.EnableFailed,
                )
            }
            BiometricPromptResult.Cancelled -> Unit
            BiometricPromptResult.Lockout -> uiState = uiState.copy(biometricMessage = BiometricSettingsMessage.Lockout)
            BiometricPromptResult.Error -> uiState = uiState.copy(biometricMessage = BiometricSettingsMessage.EnableFailed)
        }
    }

    fun onBiometricMessageShown() {
        uiState = uiState.copy(biometricMessage = null)
    }

    fun onExportClick() = openDialog(SettingsDialog.ExportPassphrase)
    fun onRestoreClick() = openDialog(SettingsDialog.RestoreConfirm)
    fun onLimitationsClick() = openDialog(SettingsDialog.Limitations)

    fun dismissDialog() {
        if (uiState.dialog == SettingsDialog.RestorePassphrase) restoreUri = null
        uiState = uiState.copy(dialog = null, backupForm = BackupPassphraseForm())
    }

    fun onPassphraseChange(value: String) {
        uiState = uiState.copy(backupForm = uiState.backupForm.copy(passphrase = value, passphraseError = null))
    }

    fun onConfirmationChange(value: String) {
        uiState = uiState.copy(backupForm = uiState.backupForm.copy(confirmation = value, confirmationError = null))
    }

    /** Export step 1: a valid passphrase, then ask where to save. */
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
        exportPassphrase?.fill('\u0000')
        exportPassphrase = form.passphrase.toCharArray()
        uiState = uiState.copy(
            dialog = null,
            backupForm = BackupPassphraseForm(),
            exportPickerRequest = "keeper-backup-${today()}.${BackupCrypto.FILE_EXTENSION}",
        )
    }

    fun onExportPickerShown() {
        uiState = uiState.copy(exportPickerRequest = null)
    }

    /** Export step 2: the picker returned a document, or null if the user cancelled. */
    fun onExportDestination(uri: String?) {
        val passphrase = exportPassphrase ?: return
        exportPassphrase = null
        if (uri == null) {
            passphrase.fill('\u0000')
            return
        }
        uiState = uiState.copy(backupWork = BackupWork.Exporting)
        viewModelScope.launch {
            val result = try {
                backupService.export(uri, passphrase)
            } finally {
                passphrase.fill('\u0000')
            }
            uiState = uiState.copy(
                backupWork = null,
                backupMessage = when (result) {
                    is ExportResult.Saved -> BackupMessage.Exported(result.count)
                    ExportResult.Failed -> BackupMessage.ExportFailed
                },
            )
        }
    }

    /** Restore step 1 confirmed: pick the file. */
    fun onRestoreConfirmed() {
        uiState = uiState.copy(dialog = null, restorePickerRequest = true)
    }

    fun onRestorePickerShown() {
        uiState = uiState.copy(restorePickerRequest = false)
    }

    /** Restore step 2: the picked file, or null if the user cancelled. */
    fun onRestoreSource(uri: String?) {
        if (uri == null) return
        restoreUri = uri
        openDialog(SettingsDialog.RestorePassphrase)
    }

    /** Restore step 3: decrypt, validate, then replace the vault's logins. */
    fun submitRestore() {
        val form = uiState.backupForm
        if (form.passphrase.isEmpty()) {
            uiState = uiState.copy(backupForm = form.copy(passphraseError = PassphraseError.Required))
            return
        }
        val uri = restoreUri ?: return
        restoreUri = null
        val passphrase = form.passphrase.toCharArray()
        uiState = uiState.copy(dialog = null, backupForm = BackupPassphraseForm(), backupWork = BackupWork.Restoring)
        viewModelScope.launch {
            val result = try {
                backupService.restore(uri, passphrase)
            } finally {
                passphrase.fill('\u0000')
            }
            uiState = uiState.copy(
                backupWork = null,
                backupMessage = when (result) {
                    is RestoreResult.Restored -> BackupMessage.Restored(result.count)
                    RestoreResult.WrongPassphraseOrDamaged -> BackupMessage.WrongPassphraseOrDamaged
                    RestoreResult.NotABackup -> BackupMessage.NotABackup
                    RestoreResult.Unsupported -> BackupMessage.Unsupported
                    RestoreResult.TooLarge -> BackupMessage.TooLarge
                    RestoreResult.Failed -> BackupMessage.RestoreFailed
                },
            )
        }
    }

    fun onBackupMessageShown() {
        uiState = uiState.copy(backupMessage = null)
    }

    override fun onCleared() {
        exportPassphrase?.fill('\u0000')
        exportPassphrase = null
    }

    private fun openDialog(dialog: SettingsDialog) {
        uiState = uiState.copy(dialog = dialog, backupForm = BackupPassphraseForm())
    }

    companion object {
        /** Same minimum as the master password; the backup protects the same data. */
        const val MIN_PASSPHRASE_LENGTH = 12

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SettingsViewModel(
                    container.vaultAuthenticator,
                    container.biometricAvailability::check,
                    container.credentialRepository,
                    container.backupService,
                )
            }
        }
    }
}
