package io.github.rahulraj94391.keeper.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The biometric key is missing or was invalidated, for example because enrollments changed. */
class BiometricKeyInvalidatedException : GeneralSecurityException("Biometric key is no longer usable")

/**
 * The Android Keystore key that wraps the vault key for biometric unlock (PRD 6.3, 7.1).
 * Ciphers returned here can only be used after a successful BiometricPrompt that carries them
 * as a CryptoObject; the key material itself never leaves the Keystore.
 */
interface BiometricKeyStore {
    val keyAlias: String

    /** Replaces any existing key with a new one and returns a cipher ready to encrypt. */
    fun newEncryptCipher(): Cipher

    /** Returns a cipher ready to decrypt, or throws [BiometricKeyInvalidatedException]. */
    fun decryptCipher(iv: ByteArray): Cipher

    fun deleteKey()
}

class AndroidBiometricKeyStore(
    override val keyAlias: String = "keeper_biometric_vek_wrap_v1",
) : BiometricKeyStore {

    override fun newEncryptCipher(): Cipher {
        deleteKey()
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Every single use needs a fresh strong-biometric authentication.
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            // Adding or removing a fingerprint/face makes the key unusable.
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
        // The Keystore picks a random nonce itself; it's read back from the cipher after use.
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    override fun decryptCipher(iv: ByteArray): Cipher {
        val key = keyStore().getKey(keyAlias, null) as? SecretKey ?: throw BiometricKeyInvalidatedException()
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException()
        }
    }

    override fun deleteKey() {
        keyStore().run { if (containsAlias(keyAlias)) deleteEntry(keyAlias) }
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}

/** Wraps and unwraps the vault key with a cipher that a BiometricPrompt has just authorized. */
object BiometricVekWrapping {

    fun wrap(authorizedCipher: Cipher, vek: ByteArray, keyAlias: String): SealedBox {
        authorizedCipher.updateAAD(associatedData(keyAlias))
        val ciphertext = authorizedCipher.doFinal(vek)
        return SealedBox(authorizedCipher.iv, ciphertext)
    }

    /** Returns the vault key, or null if decryption or authentication fails. */
    fun unwrap(authorizedCipher: Cipher, wrapped: SealedBox, keyAlias: String): ByteArray? = try {
        authorizedCipher.updateAAD(associatedData(keyAlias))
        authorizedCipher.doFinal(wrapped.ciphertext).takeIf { it.size == VaultKeyManager.VEK_BYTES }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalStateException) {
        null // e.g. the cipher wasn't actually authorized
    }

    private fun associatedData(keyAlias: String) =
        "keeper/biometric-wrapped-vek/v1;alias=$keyAlias".toByteArray(Charsets.UTF_8)
}
