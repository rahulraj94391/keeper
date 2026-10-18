package io.github.rahulraj94391.keeper.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rahulraj94391.keeper.data.local.KeeperDatabase
import io.github.rahulraj94391.keeper.data.vault.RoomVaultMetadataStore
import io.github.rahulraj94391.keeper.security.Argon2Params
import io.github.rahulraj94391.keeper.security.VaultKeyManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.Room

/** Migrations must keep existing vaults unlockable (PRD 11). */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KeeperDatabase::class.java)

    @Test
    fun v1VaultStillUnlocksAfterMigratingToV2() = runTest {
        val keyManager = VaultKeyManager(kdfParams = Argon2Params(memoryKiB = 19_456, iterations = 2))
        val password = "fake master password".toCharArray()
        val vault = keyManager.createVault(password.copyOf())
        val m = vault.metadata

        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO vault_metadata (id, schema_version, kdf_algorithm, kdf_version, kdf_memory_kib, " +
                    "kdf_iterations, kdf_parallelism, kdf_output_bytes, master_salt, wrap_algorithm, " +
                    "master_wrapped_vek_nonce, master_wrapped_vek_ciphertext) VALUES (1,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any>(
                    m.schemaVersion, m.kdfAlgorithm, m.kdfParams.version, m.kdfParams.memoryKiB,
                    m.kdfParams.iterations, m.kdfParams.parallelism, m.kdfParams.outputBytes, m.masterSalt,
                    m.wrapAlgorithm, m.masterWrappedVek.nonce, m.masterWrappedVek.ciphertext,
                ),
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).close()

        val db = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            KeeperDatabase::class.java,
            DB,
        ).build()
        try {
            val migrated = RoomVaultMetadataStore(db.vaultMetadataDao()).load()
            assertNotNull(migrated)
            assertFalse(migrated!!.biometricEnabled)
            assertNotNull(keyManager.unwrap(password, migrated))
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
