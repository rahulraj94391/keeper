package io.github.rahulraj94391.keeper.data.repository

import io.github.rahulraj94391.keeper.data.local.CredentialDao
import io.github.rahulraj94391.keeper.data.local.CredentialEntity
import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.model.CredentialDisplayOrder
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.security.AeadCipher
import io.github.rahulraj94391.keeper.security.AesGcmCipher
import io.github.rahulraj94391.keeper.security.DecryptionFailedException
import io.github.rahulraj94391.keeper.security.SealedBox
import io.github.rahulraj94391.keeper.security.VaultLockedException
import io.github.rahulraj94391.keeper.security.VaultSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Stores every credential as one AES-256-GCM ciphertext under the vault key (PRD 6.4).
 *
 * - Reads while locked return nothing (an empty list or null); writes while locked throw
 *   [VaultLockedException].
 * - A record that fails to decrypt or validate is left out instead of shown half-read.
 */
class EncryptedCredentialRepository(
    private val dao: CredentialDao,
    private val session: VaultSession,
    private val cipher: AeadCipher = AesGcmCipher(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CredentialRepository {

    override fun observeAll(): Flow<List<Credential>> = dao.observeAll()
        .map { entities -> entities.mapNotNull(::decryptOrNull).sortedWith(CredentialDisplayOrder) }
        .flowOn(dispatcher)

    override fun observe(id: String): Flow<Credential?> = dao.observe(id)
        .map { entity -> entity?.let(::decryptOrNull) }
        .distinctUntilChanged()
        .flowOn(dispatcher)

    override suspend fun get(id: String): Credential? = withContext(dispatcher) {
        dao.get(id)?.let(::decryptOrNull)
    }

    override suspend fun save(credential: Credential) {
        val entity = withContext(dispatcher) { encrypt(credential) }
        dao.upsert(entity)
    }

    override suspend fun delete(id: String) {
        if (!session.isOpen) throw VaultLockedException()
        dao.delete(id)
    }

    private fun encrypt(credential: Credential): CredentialEntity {
        val schema = CredentialPayloadCodec.SCHEMA_VERSION
        val plaintext = CredentialPayloadCodec.encode(credential)
        try {
            val box = session.withKey { key ->
                cipher.encrypt(key, plaintext, CredentialPayloadCodec.associatedData(credential.id, schema))
            }
            return CredentialEntity(credential.id, schema, box.nonce, box.ciphertext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun decryptOrNull(entity: CredentialEntity): Credential? {
        if (entity.payloadSchemaVersion != CredentialPayloadCodec.SCHEMA_VERSION ||
            entity.ciphertext.size > CredentialPayloadCodec.MAX_CIPHERTEXT_BYTES
        ) return null
        val plaintext = try {
            session.withKey { key ->
                cipher.decrypt(
                    key,
                    SealedBox(entity.nonce, entity.ciphertext),
                    CredentialPayloadCodec.associatedData(entity.id, entity.payloadSchemaVersion),
                )
            }
        } catch (_: VaultLockedException) {
            return null
        } catch (_: DecryptionFailedException) {
            return null
        }
        return try {
            CredentialPayloadCodec.decode(entity.id, plaintext)
        } finally {
            plaintext.fill(0)
        }
    }
}
