package io.github.rahulraj94391.keeper.security

/**
 * Everything needed to unwrap the vault key with the master password (PRD 6.1, 8).
 * None of it is secret on its own: the vault key appears here only as authenticated ciphertext.
 */
class VaultKeyMetadata(
    val schemaVersion: Int,
    val kdfAlgorithm: String,
    val kdfParams: Argon2Params,
    val masterSalt: ByteArray,
    val wrapAlgorithm: String,
    val masterWrappedVek: SealedBox,
) {
    override fun toString(): String = "VaultKeyMetadata(schemaVersion=$schemaVersion, kdf=$kdfAlgorithm $kdfParams)"

    companion object {
        const val SCHEMA_VERSION = 1
        const val KDF_ARGON2ID = "argon2id"
        const val WRAP_AES_256_GCM = "aes-256-gcm"
    }
}
