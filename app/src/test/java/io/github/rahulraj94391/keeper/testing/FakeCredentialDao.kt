package io.github.rahulraj94391.keeper.testing

import io.github.rahulraj94391.keeper.data.local.CredentialDao
import io.github.rahulraj94391.keeper.data.local.CredentialEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Stands in for Room in JVM tests; [rows] exposes exactly what would be stored. */
class FakeCredentialDao : CredentialDao {
    val rows = MutableStateFlow<Map<String, CredentialEntity>>(emptyMap())

    override fun observeAll(): Flow<List<CredentialEntity>> = rows.map { it.values.toList() }
    override fun observe(id: String): Flow<CredentialEntity?> = rows.map { it[id] }
    override suspend fun get(id: String): CredentialEntity? = rows.value[id]
    override suspend fun upsert(credential: CredentialEntity) = rows.update { it + (credential.id to credential) }
    override suspend fun delete(id: String) = rows.update { it - id }
}
