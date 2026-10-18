package io.github.rahulraj94391.keeper.data.vault

import io.github.rahulraj94391.keeper.security.VaultKeyMetadata

/** In-memory stand-in for the Room-backed store in JVM tests. */
class InMemoryVaultMetadataStore(private var metadata: VaultKeyMetadata? = null) : VaultMetadataStore {
    override suspend fun load(): VaultKeyMetadata? = metadata

    override suspend fun save(metadata: VaultKeyMetadata) {
        this.metadata = metadata
    }
}
