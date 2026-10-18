package io.github.rahulraj94391.keeper.data.vault

import io.github.rahulraj94391.keeper.security.VaultKeyMetadata

/** Persists the vault's key metadata. Each save replaces the whole record in one step. */
interface VaultMetadataStore {
    suspend fun load(): VaultKeyMetadata?

    suspend fun save(metadata: VaultKeyMetadata)
}

/**
 * Keeps metadata in memory only, so the vault is gone when the process dies. Replaced by the
 * Room-backed store in the encrypted storage phase.
 */
class InMemoryVaultMetadataStore(private var metadata: VaultKeyMetadata? = null) : VaultMetadataStore {
    override suspend fun load(): VaultKeyMetadata? = metadata

    override suspend fun save(metadata: VaultKeyMetadata) {
        this.metadata = metadata
    }
}
