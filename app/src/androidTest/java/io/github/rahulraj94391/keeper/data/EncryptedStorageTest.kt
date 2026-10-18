package io.github.rahulraj94391.keeper.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rahulraj94391.keeper.data.auth.LocalVaultAuthenticator
import io.github.rahulraj94391.keeper.data.local.KeeperDatabase
import io.github.rahulraj94391.keeper.data.repository.EncryptedCredentialRepository
import io.github.rahulraj94391.keeper.data.vault.RoomVaultMetadataStore
import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.security.AndroidBiometricKeyStore
import io.github.rahulraj94391.keeper.security.Argon2Params
import io.github.rahulraj94391.keeper.security.VaultKeyManager
import io.github.rahulraj94391.keeper.security.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room, real crypto (PRD 11, "Room/repository tests"). Uses the smallest accepted
 * Argon2id cost so the suite stays quick; all values are fake.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {

    private lateinit var db: KeeperDatabase
    private val password = "fake master password"
    private val credential = Credential(
        id = "rec-1",
        title = "Example Mail",
        username = "alex@example.com",
        password = "fake-password-value",
        url = "https://mail.example.com",
        notes = "fake recovery note",
    )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KeeperDatabase::class.java).build()
    }

    @After
    fun closeDb() = db.close()

    private fun authenticator(session: VaultSession) = LocalVaultAuthenticator(
        store = RoomVaultMetadataStore(db.vaultMetadataDao()),
        keyManager = VaultKeyManager(kdfParams = Argon2Params(memoryKiB = 19_456, iterations = 2)),
        session = session,
        biometricKeyStore = AndroidBiometricKeyStore("keeper_test_biometric_key"),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        cryptoDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun vaultSurvivesRestartAndUnlocksOnlyWithTheRightPassword() = runTest {
        val firstSession = VaultSession()
        authenticator(firstSession).createVault(password.toCharArray())
        EncryptedCredentialRepository(db.credentialDao(), firstSession).save(credential)

        // A fresh authenticator and session over the same database simulate an app restart.
        val session = VaultSession()
        val restarted = authenticator(session)
        assertEquals(VaultState.Locked, restarted.state.first { it != VaultState.Loading })
        val repository = EncryptedCredentialRepository(db.credentialDao(), session)
        assertTrue(repository.observeAll().first().isEmpty())

        assertFalse(restarted.unlock("wrong fake password".toCharArray()))
        assertNull(repository.get("rec-1"))

        assertTrue(restarted.unlock(password.toCharArray()))
        assertEquals(listOf(credential), repository.observeAll().first())
    }

    @Test
    fun databaseFileHoldsNoPlaintext() = runTest {
        val session = VaultSession()
        authenticator(session).createVault(password.toCharArray())
        EncryptedCredentialRepository(db.credentialDao(), session).save(credential)

        val dump = StringBuilder()
        db.openHelper.readableDatabase.query("SELECT * FROM credentials").use { cursor ->
            while (cursor.moveToNext()) {
                for (i in 0 until cursor.columnCount) {
                    val value = when (cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_BLOB -> String(cursor.getBlob(i), Charsets.ISO_8859_1)
                        else -> cursor.getString(i)
                    }
                    dump.append(value).append('|')
                }
            }
        }
        db.openHelper.readableDatabase.query("SELECT * FROM vault_metadata").use { cursor ->
            assertEquals(1, cursor.count)
        }

        listOf(credential.title, credential.username, credential.password, credential.url, credential.notes, password)
            .forEach { assertFalse("plaintext '$it' found in database", dump.contains(it)) }
        assertTrue(dump.contains("rec-1")) // the random id is the only readable column
    }

    @Test
    fun deleteRemovesTheRow() = runTest {
        val session = VaultSession()
        authenticator(session).createVault(password.toCharArray())
        val repository = EncryptedCredentialRepository(db.credentialDao(), session)
        repository.save(credential)

        repository.delete("rec-1")

        assertNull(db.credentialDao().get("rec-1"))
    }

    @Test
    fun replaceAll_swapsEveryRowInOneTransaction() = runTest {
        val session = VaultSession()
        authenticator(session).createVault(password.toCharArray())
        val repository = EncryptedCredentialRepository(db.credentialDao(), session)
        repository.save(credential)
        repository.save(credential.copy(id = "rec-2", title = "Second"))

        val restored = (1..25).map { credential.copy(id = "restored-$it", title = "Restored $it") }
        repository.replaceAll(restored)

        val ids = repository.observeAll().first().map { it.id }.toSet()
        assertEquals(restored.map { it.id }.toSet(), ids)
        assertNull(db.credentialDao().get("rec-1"))
    }
}
