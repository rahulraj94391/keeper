package io.github.rahulraj94391.keeper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultMetadataDao {
    @Query("SELECT * FROM vault_metadata WHERE id = ${VaultMetadataEntity.SINGLETON_ID}")
    suspend fun get(): VaultMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: VaultMetadataEntity)
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials")
    fun observeAll(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE id = :id")
    fun observe(id: String): Flow<CredentialEntity?>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun get(id: String): CredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(credential: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM credentials")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(credentials: List<CredentialEntity>)

    /** Room runs this in a single transaction: a failure part-way rolls everything back. */
    @Transaction
    suspend fun replaceAll(credentials: List<CredentialEntity>) {
        deleteAll()
        upsertAll(credentials)
    }
}
