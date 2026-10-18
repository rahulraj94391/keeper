package io.github.rahulraj94391.keeper.security

import io.github.rahulraj94391.keeper.testing.FAST_KDF_PARAMS
import io.github.rahulraj94391.keeper.testing.testKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VaultKeyManagerTest {

    private val password = "fake master password".toCharArray()
    private val keyManager = testKeyManager()
    private val vault = keyManager.createVault(password)

    @Test
    fun createVault_recordsVersionedSettings() {
        val metadata = vault.metadata

        assertEquals(VaultKeyMetadata.SCHEMA_VERSION, metadata.schemaVersion)
        assertEquals(VaultKeyMetadata.KDF_ARGON2ID, metadata.kdfAlgorithm)
        assertEquals(FAST_KDF_PARAMS, metadata.kdfParams)
        assertEquals(VaultKeyMetadata.WRAP_AES_256_GCM, metadata.wrapAlgorithm)
        assertEquals(16, metadata.masterSalt.size)
        assertEquals(32, vault.vek.size)
    }

    @Test
    fun metadataNeverContainsTheVaultKeyInPlain() {
        val stored = vault.metadata.masterSalt + vault.metadata.masterWrappedVek.nonce +
            vault.metadata.masterWrappedVek.ciphertext

        assertFalse(stored.toList().windowed(vault.vek.size).any { it == vault.vek.toList() })
    }

    @Test
    fun correctPassword_unwrapsTheSameKey() {
        assertArrayEquals(vault.vek, keyManager.unwrap(password, vault.metadata))
    }

    @Test
    fun wrongPassword_unwrapsNothing() {
        assertNull(keyManager.unwrap("fake master passworD".toCharArray(), vault.metadata))
        assertNull(keyManager.unwrap("".toCharArray(), vault.metadata))
    }

    @Test
    fun eachVaultGetsItsOwnKeyAndSalt() {
        val other = keyManager.createVault(password)

        assertFalse(other.vek.contentEquals(vault.vek))
        assertFalse(other.metadata.masterSalt.contentEquals(vault.metadata.masterSalt))
    }

    @Test
    fun changedSalt_fails() {
        assertNull(keyManager.unwrap(password, vault.metadata.copy(masterSalt = vault.metadata.masterSalt.flip(0))))
    }

    @Test
    fun changedKdfSettingsWithinBounds_fail() {
        val tampered = vault.metadata.copy(kdfParams = FAST_KDF_PARAMS.copy(iterations = 3))

        assertNull(keyManager.unwrap(password, tampered))
    }

    @Test
    fun changedWrappedKey_fails() {
        val box = vault.metadata.masterWrappedVek
        val tampered = vault.metadata.copy(masterWrappedVek = SealedBox(box.nonce, box.ciphertext.flip(3)))

        assertNull(keyManager.unwrap(password, tampered))
    }

    @Test
    fun unknownAlgorithmsOrSchema_fail() {
        assertNull(keyManager.unwrap(password, vault.metadata.copy(kdfAlgorithm = "scrypt")))
        assertNull(keyManager.unwrap(password, vault.metadata.copy(wrapAlgorithm = "aes-128-cbc")))
        assertNull(keyManager.unwrap(password, vault.metadata.copy(schemaVersion = 99)))
    }

    @Test
    fun outOfBoundsKdfSettings_failWithoutRunningArgon2() {
        val countingKdf = CountingKdf()
        val manager = VaultKeyManager(kdf = countingKdf, kdfParams = FAST_KDF_PARAMS)
        val huge = vault.metadata.copy(kdfParams = Argon2Params(memoryKiB = 4_194_304))

        assertNull(manager.unwrap(password, huge))
        assertEquals(0, countingKdf.runs)
    }

    @Test
    fun aadCoversEveryKdfSetting() {
        val salt = ByteArray(16)
        val base = VaultKeyManager.wrapAad(FAST_KDF_PARAMS, salt)
        val variants = listOf(
            FAST_KDF_PARAMS.copy(memoryKiB = FAST_KDF_PARAMS.memoryKiB + 1),
            FAST_KDF_PARAMS.copy(iterations = 3),
            FAST_KDF_PARAMS.copy(parallelism = 2),
        )
        variants.forEach { assertFalse(base.contentEquals(VaultKeyManager.wrapAad(it, salt))) }
    }

    private class CountingKdf : KeyDerivation {
        var runs = 0
        override fun deriveKey(password: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray {
            Argon2idKeyDerivation.validate(salt, params)
            runs++
            return ByteArray(32)
        }
    }

    private fun VaultKeyMetadata.copy(
        schemaVersion: Int = this.schemaVersion,
        kdfAlgorithm: String = this.kdfAlgorithm,
        kdfParams: Argon2Params = this.kdfParams,
        masterSalt: ByteArray = this.masterSalt,
        wrapAlgorithm: String = this.wrapAlgorithm,
        masterWrappedVek: SealedBox = this.masterWrappedVek,
    ) = VaultKeyMetadata(schemaVersion, kdfAlgorithm, kdfParams, masterSalt, wrapAlgorithm, masterWrappedVek)

    private fun ByteArray.flip(index: Int): ByteArray =
        copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
}
