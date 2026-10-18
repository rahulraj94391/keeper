package io.github.rahulraj94391.keeper.data.backup

import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.security.BackupCrypto
import io.github.rahulraj94391.keeper.security.BackupReadResult
import io.github.rahulraj94391.keeper.security.VaultSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ExportResult {
    data class Saved(val count: Int) : ExportResult

    /** Nothing usable was written; a partly written file was removed where possible. */
    data object Failed : ExportResult
}

sealed interface RestoreResult {
    data class Restored(val count: Int) : RestoreResult
    data object WrongPassphraseOrDamaged : RestoreResult
    data object NotABackup : RestoreResult
    data object Unsupported : RestoreResult
    data object TooLarge : RestoreResult
    data object Failed : RestoreResult
}

/**
 * Encrypted export and restore (PRD 6.7, 6.8). Both need the vault unlocked. The backup holds
 * only the logins, never the vault key, Keystore keys or biometric settings; a restore
 * re-encrypts them under this vault's own key and leaves the master password and biometric
 * unlock as they are.
 */
class VaultBackupService(
    private val repository: CredentialRepository,
    private val session: VaultSession,
    private val files: BackupFiles,
    private val crypto: BackupCrypto = BackupCrypto(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun export(uri: String, passphrase: CharArray): ExportResult {
        if (!session.isOpen) return ExportResult.Failed
        return withContext(dispatcher) {
            try {
                val credentials = repository.observeAll().first()
                val bytes = crypto.encrypt(credentials, passphrase)
                files.write(uri, bytes)
                ExportResult.Saved(credentials.size)
            } catch (_: IOException) {
                files.delete(uri)
                ExportResult.Failed
            } catch (_: IllegalStateException) {
                files.delete(uri)
                ExportResult.Failed // e.g. the vault locked part-way
            } catch (_: IllegalArgumentException) {
                files.delete(uri)
                ExportResult.Failed // more records than one backup allows
            }
        }
    }

    suspend fun restore(uri: String, passphrase: CharArray): RestoreResult {
        if (!session.isOpen) return RestoreResult.Failed
        return withContext(dispatcher) {
            val bytes = try {
                files.read(uri, BackupCrypto.MAX_FILE_BYTES) ?: return@withContext RestoreResult.TooLarge
            } catch (_: IOException) {
                return@withContext RestoreResult.Failed
            } catch (_: SecurityException) {
                return@withContext RestoreResult.Failed
            }
            when (val read = crypto.decrypt(bytes, passphrase)) {
                is BackupReadResult.Success -> try {
                    // Fully decrypted and validated before anything in the vault changes.
                    repository.replaceAll(read.credentials)
                    RestoreResult.Restored(read.credentials.size)
                } catch (_: IllegalStateException) {
                    RestoreResult.Failed
                }
                BackupReadResult.WrongPassphraseOrDamaged -> RestoreResult.WrongPassphraseOrDamaged
                BackupReadResult.Malformed -> RestoreResult.NotABackup
                BackupReadResult.Unsupported -> RestoreResult.Unsupported
                BackupReadResult.TooLarge -> RestoreResult.TooLarge
            }
        }
    }
}
