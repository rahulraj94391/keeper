package io.github.rahulraj94391.keeper.presentation.settings

import io.github.rahulraj94391.keeper.testing.testAuthenticator
import io.github.rahulraj94391.keeper.domain.model.AutoLockTimeout
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.testing.FakeAutoLockPreferences
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authenticator = testAuthenticator()
    private val viewModel by lazy { SettingsViewModel(authenticator, { BiometricAvailability.Available }, FakeAutoLockPreferences()) }

    @Test
    fun lockNow_locksTheVault() = runTest {
        authenticator.createVault("fake master password".toCharArray())

        viewModel.lockNow()

        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun export_rejectsShortAndMismatchedPassphrases() {
        viewModel.onExportClick()
        viewModel.onPassphraseChange("short")
        viewModel.onConfirmationChange("different")

        viewModel.submitExport()

        val form = viewModel.uiState.backupForm
        assertEquals(PassphraseError.TooShort, form.passphraseError)
        assertEquals(PassphraseError.Mismatch, form.confirmationError)
        assertEquals(SettingsDialog.ExportPassphrase, viewModel.uiState.dialog)
    }

    @Test
    fun export_validPassphrase_closesDialogClearsFormAndReportsUnavailable() {
        viewModel.onExportClick()
        viewModel.onPassphraseChange("a long fake backup phrase")
        viewModel.onConfirmationChange("a long fake backup phrase")

        viewModel.submitExport()

        assertNull(viewModel.uiState.dialog)
        assertEquals("", viewModel.uiState.backupForm.passphrase)
        assertTrue(viewModel.uiState.showBackupUnavailable)
    }

    @Test
    fun restore_asksForConfirmationBeforePassphrase() {
        viewModel.onRestoreClick()
        assertEquals(SettingsDialog.RestoreConfirm, viewModel.uiState.dialog)

        viewModel.onRestoreConfirmed()
        assertEquals(SettingsDialog.RestorePassphrase, viewModel.uiState.dialog)

        viewModel.submitRestore()
        assertEquals(PassphraseError.Required, viewModel.uiState.backupForm.passphraseError)
    }

    @Test
    fun dismiss_clearsTypedPassphrase() {
        viewModel.onExportClick()
        viewModel.onPassphraseChange("typed but cancelled")

        viewModel.dismissDialog()

        assertNull(viewModel.uiState.dialog)
        assertEquals("", viewModel.uiState.backupForm.passphrase)
        assertFalse(viewModel.uiState.showBackupUnavailable)
    }

    @Test
    fun autoLock_choiceIsSavedAndShown() {
        val preferences = FakeAutoLockPreferences()
        val viewModel = SettingsViewModel(authenticator, { BiometricAvailability.Available }, preferences)
        assertEquals(AutoLockTimeout.Immediately, viewModel.uiState.autoLockTimeout)

        viewModel.onAutoLockClick()
        assertEquals(SettingsDialog.AutoLock, viewModel.uiState.dialog)
        viewModel.onAutoLockSelected(AutoLockTimeout.OneMinute)

        assertEquals(AutoLockTimeout.OneMinute, preferences.timeout.value)
        assertEquals(AutoLockTimeout.OneMinute, viewModel.uiState.autoLockTimeout)
        assertNull(viewModel.uiState.dialog)
    }
}
