package io.github.rahulraj94391.keeper.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A nonce plus the ciphertext with its authentication tag appended. Both must be stored. */
class SealedBox(val nonce: ByteArray, val ciphertext: ByteArray) {
    override fun toString(): String = "SealedBox(ciphertext.size=${ciphertext.size})"
}

/** Thrown for every decryption failure. Deliberately carries no detail about why. */
class DecryptionFailedException : GeneralSecurityException("Decryption failed")

/** Authenticated encryption. Implementations choose the nonce; callers never supply one. */
interface AeadCipher {
    fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): SealedBox

    /** Returns the plaintext, or throws [DecryptionFailedException]. Never returns partial output. */
    fun decrypt(key: ByteArray, box: SealedBox, associatedData: ByteArray): ByteArray
}

/** AES-256-GCM with 96-bit random nonces and 128-bit tags (PRD 7.3). */
class AesGcmCipher(private val random: SecureRandom = SecureRandom()) : AeadCipher {

    override fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): SealedBox {
        requireKey(key)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, key, nonce, associatedData)
        return SealedBox(nonce, cipher.doFinal(plaintext))
    }

    override fun decrypt(key: ByteArray, box: SealedBox, associatedData: ByteArray): ByteArray {
        requireKey(key)
        if (box.nonce.size != NONCE_BYTES || box.ciphertext.size < TAG_BYTES) throw DecryptionFailedException()
        return try {
            cipher(Cipher.DECRYPT_MODE, key, box.nonce, associatedData).doFinal(box.ciphertext)
        } catch (_: GeneralSecurityException) {
            // Covers AEADBadTagException and any other provider failure; detail is dropped on purpose.
            throw DecryptionFailedException()
        }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, associatedData: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(associatedData)
        }

    private fun requireKey(key: ByteArray) {
        require(key.size == KEY_BYTES) { "AES-256 needs a $KEY_BYTES-byte key" }
    }

    companion object {
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
