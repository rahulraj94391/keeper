package io.github.rahulraj94391.keeper.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VaultMetadataEntity::class, CredentialEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // 1 -> 2: biometric unlock columns on vault_metadata.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class KeeperDatabase : RoomDatabase() {
    abstract fun vaultMetadataDao(): VaultMetadataDao
    abstract fun credentialDao(): CredentialDao

    companion object {
        const val FILE_NAME = "keeper_vault.db"

        // No destructive fallback: a failed migration must never wipe the user's only vault (PRD 8).
        fun create(context: Context): KeeperDatabase =
            Room.databaseBuilder(context, KeeperDatabase::class.java, FILE_NAME).build()
    }
}
