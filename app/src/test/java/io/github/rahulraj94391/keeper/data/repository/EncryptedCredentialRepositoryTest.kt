package io.github.rahulraj94391.keeper.data.repository

import io.github.rahulraj94391.keeper.data.local.CredentialEntity
import io.github.rahulraj94391.keeper.security.VaultLockedException
import io.github.rahulraj94391.keeper.security.VaultSession
import io.github.rahulraj94391.keeper.testing.FakeCredentialDao
import io.github.rahulraj94391.keeper.testing.testCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class EncryptedCredentialRepositoryTest {

    private val dao = FakeCredentialDao()
    private val session = VaultSession().apply { open(randomKey()) }
    private val repository = EncryptedCredentialRepository(dao, session, dispatcher = Dispatchers.Unconfined)
    private val credential = testCredential("rec-1", title = "Example Mail", url = "https://mail.example.com")

    @Test
    fun saveThenRead_roundTrips() = runTest {
        repository.save(credential)

        assertEquals(credential, repository.get("rec-1"))
        assertEquals(listOf(credential), repository.observeAll().first())
        assertEquals(credential, repository.observe("rec-1").first())
    }

    @Test
    fun storedRowContainsNoPlaintext() = runTest {
        repository.save(credential)

        val row = dao.rows.value.getValue("rec-1")
        val stored = String(row.nonce + row.ciphertext, Charsets.ISO_8859_1)
        listOf(credential.title, credential.username, credential.password, credential.url).forEach {
            assertFalse("stored row contains '$it'", stored.contains(it))
        }
        assertEquals(CredentialPayloadCodec.SCHEMA_VERSION, row.payloadSchemaVersion)
    }

    @Test
    fun listIsSortedFavoritesFirst() = runTest {
        repository.save(testCredential("1", title = "beta"))
        repository.save(testCredential("2", title = "Alpha"))
        repository.save(testCredential("3", title = "zulu", favorite = true))

        assertEquals(listOf("zulu", "Alpha", "beta"), repository.observeAll().first().map { it.title })
    }

    @Test
    fun whenLocked_readsReturnNothingAndWritesThrow() = runTest {
        repository.save(credential)
        session.close()

        assertTrue(repository.observeAll().first().isEmpty())
        assertNull(repository.get("rec-1"))
        assertNull(repository.observe("rec-1").first())
        assertTrue(runCatching { repository.save(credential) }.exceptionOrNull() is VaultLockedException)
        assertTrue(runCatching { repository.delete("rec-1") }.exceptionOrNull() is VaultLockedException)
    }

    @Test
    fun ciphertextMovedToAnotherRecord_isNotShown() = runTest {
        repository.save(credential)
        val row = dao.rows.value.getValue("rec-1")
        dao.upsert(CredentialEntity("rec-2", row.payloadSchemaVersion, row.nonce, row.ciphertext))

        assertNull(repository.get("rec-2"))
        assertEquals(listOf("rec-1"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun tamperedOrUnsupportedRows_areLeftOut() = runTest {
        repository.save(credential)
        repository.save(testCredential("rec-2"))
        val row = dao.rows.value.getValue("rec-2")
        val flipped = row.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        dao.rows.update { it + ("rec-2" to CredentialEntity("rec-2", row.payloadSchemaVersion, row.nonce, flipped)) }
        dao.upsert(CredentialEntity("rec-3", 99, row.nonce, row.ciphertext))

        assertEquals(listOf("rec-1"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun differentVaultKey_cannotReadRecords() = runTest {
        repository.save(credential)
        session.open(randomKey())

        assertNull(repository.get("rec-1"))
    }

    @Test
    fun delete_removesTheRow() = runTest {
        repository.save(credential)

        repository.delete("rec-1")

        assertTrue(dao.rows.value.isEmpty())
    }

    private fun randomKey() = ByteArray(32).also(SecureRandom()::nextBytes)
}
