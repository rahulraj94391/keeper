package io.github.rahulraj94391.keeper.data.vault

import io.github.rahulraj94391.keeper.security.VaultKeyMetadata

/** Persists the vault's key metadata. Each save replaces the whole record in one step. */
interface VaultMetadataStore {
    suspend fun load(): VaultKeyMetadata?

    suspend fun save(metadata: VaultKeyMetadata)
}
