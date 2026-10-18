package io.github.rahulraj94391.keeper.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
