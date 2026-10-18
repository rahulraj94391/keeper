package io.github.rahulraj94391.keeper.presentation.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.rahulraj94391.keeper.debug.SampleCredentials
import io.github.rahulraj94391.keeper.di.appContainer
import io.github.rahulraj94391.keeper.domain.model.AutoLockTimeout
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.domain.repository.AutoLockPreferences
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.presentation.common.BiometricPromptResult
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException
import javax.crypto.Cipher

enum class SettingsDialog { AutoLock, ExportPassphrase, RestoreConfirm, RestorePassphrase, Limitations }

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

data class SettingsUiState(
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.DEFAULT,
    val biometricEnabled: Boolean = false,
    val biometricAvailability: BiometricAvailability = BiometricAvailability.Unavailable,
    /** A cipher waiting to be shown in BiometricPrompt to turn biometrics on. No key material. */
    val biometricRequest: Cipher? = null,
    val biometricMessage: BiometricSettingsMessage? = null,
    val dialog: SettingsDialog? = null,
    val backupForm: BackupPassphraseForm = BackupPassphraseForm(),
    /** Set after sample logins were added, until the screen has said so. */
    val samplesAdded: Int? = null,
    /** True after a backup flow ends, until the screen has told the user it isn't available yet. */
    val showBackupUnavailable: Boolean = false,
)

class SettingsViewModel(
    private val authenticator: VaultAuthenticator,
    private val biometricAvailability: () -> BiometricAvailability,
    private val autoLockPreferences: AutoLockPreferences,
    private val credentialRepository: CredentialRepository,
) : ViewModel() {

    var uiState by mutableStateOf(SettingsUiState(biometricAvailability = biometricAvailability()))
        private set

    init {
        viewModelScope.launch {
            authenticator.biometricEnabled.collect { uiState = uiState.copy(biometricEnabled = it) }
        }
        viewModelScope.launch {
            autoLockPreferences.timeout.collect { uiState = uiState.copy(autoLockTimeout = it) }
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

    fun onAutoLockClick() = openDialog(SettingsDialog.AutoLock)

    fun onAutoLockSelected(timeout: AutoLockTimeout) {
        autoLockPreferences.setTimeout(timeout)
        dismissDialog()
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
        uiState = uiState.copy(dialog = null, backupForm = BackupPassphraseForm(), showBackupUnavailable = true)
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
                    container.autoLockPreferences,
                    container.credentialRepository,
                )
            }
        }
    }
}
