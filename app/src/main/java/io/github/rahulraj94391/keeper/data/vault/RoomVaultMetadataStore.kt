package io.github.rahulraj94391.keeper.data.vault

import io.github.rahulraj94391.keeper.data.local.VaultMetadataDao
import io.github.rahulraj94391.keeper.data.local.VaultMetadataEntity
import io.github.rahulraj94391.keeper.security.Argon2Params
import io.github.rahulraj94391.keeper.security.SealedBox
import io.github.rahulraj94391.keeper.security.VaultKeyMetadata

/** Stores key metadata as the single row of the `vault_metadata` table. */
class RoomVaultMetadataStore(private val dao: VaultMetadataDao) : VaultMetadataStore {

    override suspend fun load(): VaultKeyMetadata? = dao.get()?.toMetadata()

    // One row written in one statement, so setup can't leave half-written key material.
    override suspend fun save(metadata: VaultKeyMetadata) = dao.upsert(metadata.toEntity())

    private fun VaultMetadataEntity.toMetadata() = VaultKeyMetadata(
        schemaVersion = schemaVersion,
        kdfAlgorithm = kdfAlgorithm,
        kdfParams = Argon2Params(
            memoryKiB = kdfMemoryKiB,
            iterations = kdfIterations,
            parallelism = kdfParallelism,
            outputBytes = kdfOutputBytes,
            version = kdfVersion,
        ),
        masterSalt = masterSalt,
        wrapAlgorithm = wrapAlgorithm,
        masterWrappedVek = SealedBox(masterWrappedVekNonce, masterWrappedVekCiphertext),
    )

    private fun VaultKeyMetadata.toEntity() = VaultMetadataEntity(
        schemaVersion = schemaVersion,
        kdfAlgorithm = kdfAlgorithm,
        kdfVersion = kdfParams.version,
        kdfMemoryKiB = kdfParams.memoryKiB,
        kdfIterations = kdfParams.iterations,
        kdfParallelism = kdfParams.parallelism,
        kdfOutputBytes = kdfParams.outputBytes,
        masterSalt = masterSalt,
        wrapAlgorithm = wrapAlgorithm,
        masterWrappedVekNonce = masterWrappedVek.nonce,
        masterWrappedVekCiphertext = masterWrappedVek.ciphertext,
    )
}
