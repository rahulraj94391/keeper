package io.github.rahulraj94391.keeper.testing

import io.github.rahulraj94391.keeper.security.BiometricKeyInvalidatedException
import io.github.rahulraj94391.keeper.security.BiometricKeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * JVM stand-in for the Android Keystore: a plain AES key with no user authentication.
 * Treat every cipher it returns as already "authorized by the prompt".
 */
class FakeBiometricKeyStore : BiometricKeyStore {
    override val keyAlias = "test_biometric_key"
    var key: SecretKey? = null
        private set

    override fun newEncryptCipher(): Cipher {
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    override fun decryptCipher(iv: ByteArray): Cipher {
        val current = key ?: throw BiometricKeyInvalidatedException()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, current, GCMParameterSpec(128, iv))
        }
    }

    override fun deleteKey() {
        key = null
    }

    /** Simulates the system invalidating the key after an enrollment change. */
    fun invalidate() = deleteKey()
}
