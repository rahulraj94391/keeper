package io.github.rahulraj94391.keeper.security

import java.security.SecureRandom

/** A newly created vault: metadata to persist, and the vault key for the first session. */
class NewVault(val metadata: VaultKeyMetadata, val vek: ByteArray) {
    override fun toString(): String = "NewVault($metadata)"
}

/**
 * Creates the vault encryption key (VEK) and wraps/unwraps it with a key derived from the
 * master password (PRD 6.1, 6.2, 7.1). Callers must run these off the main thread: each call
 * runs Argon2id once.
 */
class VaultKeyManager(
    private val kdf: KeyDerivation = Argon2idKeyDerivation(),
    private val cipher: AeadCipher = AesGcmCipher(),
    private val random: SecureRandom = SecureRandom(),
    private val kdfParams: Argon2Params = Argon2Params.DEFAULT,
) {

    fun createVault(masterPassword: CharArray): NewVault {
        val vek = randomBytes(VEK_BYTES)
        val salt = randomBytes(SALT_BYTES)
        val kek = deriveKek(masterPassword, salt, kdfParams)
        try {
            val wrapped = cipher.encrypt(kek, vek, wrapAad(kdfParams, salt))
            val metadata = VaultKeyMetadata(
                schemaVersion = VaultKeyMetadata.SCHEMA_VERSION,
                kdfAlgorithm = VaultKeyMetadata.KDF_ARGON2ID,
                kdfParams = kdfParams,
                masterSalt = salt,
                wrapAlgorithm = VaultKeyMetadata.WRAP_AES_256_GCM,
                masterWrappedVek = wrapped,
            )
            return NewVault(metadata, vek)
        } catch (e: Throwable) {
            vek.fill(0)
            throw e
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Returns the VEK, or null for a wrong password, tampered metadata, or settings Keeper
     * doesn't support. All of these look the same to the caller on purpose (PRD 6.2).
     */
    fun unwrap(masterPassword: CharArray, metadata: VaultKeyMetadata): ByteArray? {
        if (metadata.schemaVersion != VaultKeyMetadata.SCHEMA_VERSION ||
            metadata.kdfAlgorithm != VaultKeyMetadata.KDF_ARGON2ID ||
            metadata.wrapAlgorithm != VaultKeyMetadata.WRAP_AES_256_GCM
        ) return null

        val kek = try {
            deriveKek(masterPassword, metadata.masterSalt, metadata.kdfParams)
        } catch (_: UnsupportedKdfParametersException) {
            return null
        }
        return try {
            val vek = cipher.decrypt(kek, metadata.masterWrappedVek, wrapAad(metadata.kdfParams, metadata.masterSalt))
            if (vek.size == VEK_BYTES) vek else null.also { vek.fill(0) }
        } catch (_: DecryptionFailedException) {
            null
        } finally {
            kek.fill(0)
        }
    }

    private fun deriveKek(masterPassword: CharArray, salt: ByteArray, params: Argon2Params): ByteArray {
        val encoded = MasterPasswordEncoding.encode(masterPassword)
        return try {
            kdf.deriveKey(encoded, salt, params)
        } finally {
            encoded.fill(0)
        }
    }

    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    companion object {
        const val VEK_BYTES = 32
        const val SALT_BYTES = 16

        /**
         * Binds the KDF settings and salt to the wrapped key, so changing any stored value makes
         * unwrapping fail instead of silently deriving a different key.
         */
        internal fun wrapAad(params: Argon2Params, salt: ByteArray): ByteArray = buildString {
            append("keeper/master-wrapped-vek/v").append(VaultKeyMetadata.SCHEMA_VERSION)
            append(";kdf=").append(VaultKeyMetadata.KDF_ARGON2ID)
            append(";v=").append(params.version)
            append(";m=").append(params.memoryKiB)
            append(";t=").append(params.iterations)
            append(";p=").append(params.parallelism)
            append(";len=").append(params.outputBytes)
            append(";salt=").append(salt.joinToString("") { "%02x".format(it) })
        }.toByteArray(Charsets.UTF_8)
    }
}
