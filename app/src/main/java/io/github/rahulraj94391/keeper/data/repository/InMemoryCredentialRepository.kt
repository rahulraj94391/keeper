package io.github.rahulraj94391.keeper.data.repository

import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Temporary, unencrypted store used while the UI is built. It keeps data in memory only
 * and is replaced by the encrypted Room repository in a later phase.
 */
class InMemoryCredentialRepository(
    initial: List<Credential> = emptyList(),
) : CredentialRepository {

    private val credentials = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<Credential>> = credentials.map { byId ->
        byId.values.sortedWith(
            compareByDescending<Credential> { it.favorite }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
    }

    override fun observe(id: String): Flow<Credential?> =
        credentials.map { it[id] }.distinctUntilChanged()

    override suspend fun get(id: String): Credential? = credentials.value[id]

    override suspend fun save(credential: Credential) {
        credentials.update { it + (credential.id to credential) }
    }

    override suspend fun delete(id: String) {
        credentials.update { it - id }
    }
}
