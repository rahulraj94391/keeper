package io.github.rahulraj94391.keeper.data.repository

import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.model.CredentialDisplayOrder
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Unencrypted in-memory fake for ViewModel tests. */
class InMemoryCredentialRepository(
    initial: List<Credential> = emptyList(),
) : CredentialRepository {

    private val credentials = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<Credential>> = credentials.map { byId ->
        byId.values.sortedWith(CredentialDisplayOrder)
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
