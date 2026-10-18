package io.github.rahulraj94391.keeper.data.backup

import io.github.rahulraj94391.keeper.data.repository.EncryptedCredentialRepository
import io.github.rahulraj94391.keeper.security.BackupCrypto
import io.github.rahulraj94391.keeper.security.VaultSession
import io.github.rahulraj94391.keeper.testing.FakeBackupFiles
import io.github.rahulraj94391.keeper.testing.FakeCredentialDao
import io.github.rahulraj94391.keeper.testing.testBackupService
import io.github.rahulraj94391.keeper.testing.testCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/** Uses the real encrypted repository, so restore re-encrypts under the receiving vault's key. */
class VaultBackupServiceTest {

    private val files = FakeBackupFiles()
    private val passphrase = "a long fake backup phrase"

    private class Vault {
        val session = VaultSession().apply { open(ByteArray(32).also(SecureRandom()::nextBytes)) }
        val dao = FakeCredentialDao()
        val repository = EncryptedCredentialRepository(dao, session, dispatcher = Dispatchers.Unconfined)
    }

    @Test
    fun exportFromOneVault_restoresIntoAnotherWithADifferentKey() = runTest {
        val source = Vault()
        listOf(testCredential("a", title = "Alpha"), testCredential("b", title = "Bravo")).forEach { source.repository.save(it) }
        testBackupService(source.repository, source.session, files).export("doc", passphrase.toCharArray())

        val target = Vault()
        target.repository.save(testCredential("old"))
        val result = testBackupService(target.repository, target.session, files).restore("doc", passphrase.toCharArray())

        assertEquals(RestoreResult.Restored(2), result)
        assertEquals(source.repository.observeAll().first(), target.repository.observeAll().first())
        // Re-encrypted locally: the stored ciphertexts differ between the two vaults.
        assertTrue(source.dao.rows.value.getValue("a").ciphertext.contentEquals(target.dao.rows.value.getValue("a").ciphertext).not())
    }

    @Test
    fun export_whenLocked_writesNothing() = runTest {
        val vault = Vault()
        vault.session.close()

        val result = testBackupService(vault.repository, vault.session, files).export("doc", passphrase.toCharArray())

        assertEquals(ExportResult.Failed, result)
        assertTrue(files.documents.isEmpty())
    }

    @Test
    fun restore_tooLargeFile_isRejectedBeforeDecrypting() = runTest {
        val vault = Vault()
        files.documents["huge"] = ByteArray(BackupCrypto.MAX_FILE_BYTES + 1)

        assertEquals(RestoreResult.TooLarge, testBackupService(vault.repository, vault.session, files).restore("huge", passphrase.toCharArray()))
    }

    @Test
    fun restore_unreadableDocument_fails() = runTest {
        val vault = Vault()

        assertEquals(RestoreResult.Failed, testBackupService(vault.repository, vault.session, files).restore("missing", passphrase.toCharArray()))
    }
}
