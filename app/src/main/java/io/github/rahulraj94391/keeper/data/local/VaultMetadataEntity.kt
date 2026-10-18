package io.github.rahulraj94391.keeper.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The single row of key metadata (PRD 8). Holds no secret in plain form: the vault key is
 * stored only as AES-GCM ciphertext.
 */
@Entity(tableName = "vault_metadata")
class VaultMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "kdf_algorithm") val kdfAlgorithm: String,
    @ColumnInfo(name = "kdf_version") val kdfVersion: Int,
    @ColumnInfo(name = "kdf_memory_kib") val kdfMemoryKiB: Int,
    @ColumnInfo(name = "kdf_iterations") val kdfIterations: Int,
    @ColumnInfo(name = "kdf_parallelism") val kdfParallelism: Int,
    @ColumnInfo(name = "kdf_output_bytes") val kdfOutputBytes: Int,
    @ColumnInfo(name = "master_salt") val masterSalt: ByteArray,
    @ColumnInfo(name = "wrap_algorithm") val wrapAlgorithm: String,
    @ColumnInfo(name = "master_wrapped_vek_nonce") val masterWrappedVekNonce: ByteArray,
    @ColumnInfo(name = "master_wrapped_vek_ciphertext") val masterWrappedVekCiphertext: ByteArray,
    // Added in schema 2. Not secret: the vault key appears only as Keystore-wrapped ciphertext.
    @ColumnInfo(name = "biometric_enabled", defaultValue = "0") val biometricEnabled: Boolean = false,
    @ColumnInfo(name = "biometric_keystore_alias") val biometricKeystoreAlias: String? = null,
    @ColumnInfo(name = "biometric_wrapped_vek_nonce") val biometricWrappedVekNonce: ByteArray? = null,
    @ColumnInfo(name = "biometric_wrapped_vek_ciphertext") val biometricWrappedVekCiphertext: ByteArray? = null,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
