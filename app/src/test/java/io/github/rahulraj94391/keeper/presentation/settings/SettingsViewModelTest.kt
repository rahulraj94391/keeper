package io.github.rahulraj94391.keeper.presentation.settings

import io.github.rahulraj94391.keeper.data.repository.InMemoryCredentialRepository
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.security.VaultSession
import io.github.rahulraj94391.keeper.testing.FakeBackupFiles
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import io.github.rahulraj94391.keeper.testing.testAuthenticator
import io.github.rahulraj94391.keeper.testing.testBackupService
import io.github.rahulraj94391.keeper.testing.testCredential
import io.github.rahulraj94391.keeper.testing.testSettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = VaultSession()
    private val authenticator = testAuthenticator(session = session)
    private val repository = InMemoryCredentialRepository(listOf(testCredential("a"), testCredential("b")))
    private val files = FakeBackupFiles()
    private lateinit var viewModel: SettingsViewModel

    private val backupPassphrase = "a long fake backup phrase"

    @Before
    fun setUp() = runTest {
        authenticator.createVault("fake master password".toCharArray())
        viewModel = testSettingsViewModel(
            authenticator,
            repository = repository,
            backupService = testBackupService(repository, session, files),
        )
    }

    private fun exportTo(uri: String?) {
        viewModel.onExportClick()
        viewModel.onPassphraseChange(backupPassphrase)
        viewModel.onConfirmationChange(backupPassphrase)
        viewModel.submitExport()
        viewModel.onExportPickerShown()
        viewModel.onExportDestination(uri)
    }

    private fun restoreFrom(uri: String, passphrase: String) {
        viewModel.onRestoreClick()
        viewModel.onRestoreConfirmed()
        viewModel.onRestorePickerShown()
        viewModel.onRestoreSource(uri)
        viewModel.onPassphraseChange(passphrase)
        viewModel.submitRestore()
    }

    @Test
    fun lockNow_locksTheVault() {
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
        assertNull(viewModel.uiState.exportPickerRequest)
    }

    @Test
    fun export_validPassphrase_asksWhereToSaveWithDatedName() {
        viewModel.onExportClick()
        viewModel.onPassphraseChange(backupPassphrase)
        viewModel.onConfirmationChange(backupPassphrase)

        viewModel.submitExport()

        assertNull(viewModel.uiState.dialog)
        assertEquals("", viewModel.uiState.backupForm.passphrase)
        assertEquals("keeper-backup-2026-10-18.kbak", viewModel.uiState.exportPickerRequest)
    }

    @Test
    fun export_writesTheFileAndReports() {
        exportTo("content://backup/1")

        assertTrue(files.documents.containsKey("content://backup/1"))
        assertEquals(BackupMessage.Exported(2), viewModel.uiState.backupMessage)
        assertNull(viewModel.uiState.backupWork)
    }

    @Test
    fun export_cancelledPicker_writesNothing() {
        exportTo(null)

        assertTrue(files.documents.isEmpty())
        assertNull(viewModel.uiState.backupMessage)
    }

    @Test
    fun export_writeFailure_removesThePartialFile() {
        files.failWrites = true

        exportTo("content://backup/1")

        assertFalse(files.documents.containsKey("content://backup/1"))
        assertEquals(BackupMessage.ExportFailed, viewModel.uiState.backupMessage)
    }

    @Test
    fun restore_asksForConfirmationThenFileThenPassphrase() {
        viewModel.onRestoreClick()
        assertEquals(SettingsDialog.RestoreConfirm, viewModel.uiState.dialog)

        viewModel.onRestoreConfirmed()
        assertNull(viewModel.uiState.dialog)
        assertTrue(viewModel.uiState.restorePickerRequest)

        viewModel.onRestorePickerShown()
        viewModel.onRestoreSource("content://backup/1")
        assertEquals(SettingsDialog.RestorePassphrase, viewModel.uiState.dialog)

        viewModel.submitRestore()
        assertEquals(PassphraseError.Required, viewModel.uiState.backupForm.passphraseError)
    }

    @Test
    fun restore_cancelledPicker_showsNoPassphraseDialog() {
        viewModel.onRestoreClick()
        viewModel.onRestoreConfirmed()

        viewModel.onRestoreSource(null)

        assertNull(viewModel.uiState.dialog)
    }

    @Test
    fun restore_replacesTheVaultsLogins() = runTest {
        exportTo("content://backup/1")
        repository.replaceAll(listOf(testCredential("added-later")))

        restoreFrom("content://backup/1", backupPassphrase)

        assertEquals(BackupMessage.Restored(2), viewModel.uiState.backupMessage)
        assertEquals(listOf("a", "b"), repository.observeAll().first().map { it.id }.sorted())
    }

    @Test
    fun restore_wrongPassphrase_leavesTheVaultAlone() = runTest {
        exportTo("content://backup/1")
        repository.replaceAll(listOf(testCredential("current")))

        restoreFrom("content://backup/1", "not the backup passphrase")

        assertEquals(BackupMessage.WrongPassphraseOrDamaged, viewModel.uiState.backupMessage)
        assertEquals(listOf("current"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun restore_notABackup_leavesTheVaultAlone() = runTest {
        files.documents["content://photo"] = "definitely a photo".toByteArray()

        restoreFrom("content://photo", backupPassphrase)

        assertEquals(BackupMessage.NotABackup, viewModel.uiState.backupMessage)
        assertEquals(2, repository.observeAll().first().size)
    }

    @Test
    fun dismiss_clearsTypedPassphrase() {
        viewModel.onExportClick()
        viewModel.onPassphraseChange("typed but cancelled")

        viewModel.dismissDialog()

        assertNull(viewModel.uiState.dialog)
        assertEquals("", viewModel.uiState.backupForm.passphrase)
    }

    @Test
    fun addSampleLogins_savesFortyAndRunningAgainReplacesThem() = runTest {
        val empty = InMemoryCredentialRepository()
        val viewModel = testSettingsViewModel(authenticator, repository = empty)

        viewModel.addSampleLogins()
        viewModel.addSampleLogins()

        assertEquals(40, empty.observeAll().first().size)
        assertEquals(40, viewModel.uiState.samplesAdded)
    }
}
