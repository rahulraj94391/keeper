package io.github.rahulraj94391.keeper.domain.repository

import io.github.rahulraj94391.keeper.domain.model.Credential
import kotlinx.coroutines.flow.Flow

interface CredentialRepository {
    /** All credentials, favorites first, then by title. */
    fun observeAll(): Flow<List<Credential>>

    /** A single credential, or null once it no longer exists. */
    fun observe(id: String): Flow<Credential?>

    suspend fun get(id: String): Credential?

    /** Inserts a new credential or replaces the one with the same id. */
    suspend fun save(credential: Credential)

    suspend fun delete(id: String)

    /**
     * Replaces every credential with [credentials] in one step (backup restore). Either all of
     * them end up stored or nothing changes.
     */
    suspend fun replaceAll(credentials: List<Credential>)
}
