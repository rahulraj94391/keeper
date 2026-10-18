package io.github.rahulraj94391.keeper.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.SecureRandom

class AesGcmCipherTest {

    private val cipher = AesGcmCipher()
    private val key = randomBytes(32)
    private val aad = "record:42|schema:1".toByteArray()
    private val plaintext = "fake secret payload".toByteArray()

    @Test
    fun roundTrip() {
        val box = cipher.encrypt(key, plaintext, aad)

        assertArrayEquals(plaintext, cipher.decrypt(key, box, aad))
    }

    @Test
    fun emptyPlaintext_roundTrips() {
        val box = cipher.encrypt(key, ByteArray(0), aad)

        assertEquals(AesGcmCipher.TAG_BYTES, box.ciphertext.size)
        assertArrayEquals(ByteArray(0), cipher.decrypt(key, box, aad))
    }

    @Test
    fun outputHasNonceAndTagSizes() {
        val box = cipher.encrypt(key, plaintext, aad)

        assertEquals(AesGcmCipher.NONCE_BYTES, box.nonce.size)
        assertEquals(plaintext.size + AesGcmCipher.TAG_BYTES, box.ciphertext.size)
    }

    @Test
    fun everyEncryptionUsesAFreshNonce() {
        val nonces = List(1_000) { cipher.encrypt(key, plaintext, aad).nonce.toList() }.toSet()

        assertEquals(1_000, nonces.size)
    }

    @Test
    fun ciphertextDoesNotContainPlaintext() {
        val box = cipher.encrypt(key, plaintext, aad)

        assertFalse(box.ciphertext.toList().windowed(plaintext.size).any { it == plaintext.toList() })
    }

    @Test
    fun changedCiphertextFails() = assertFails { box -> SealedBox(box.nonce, box.ciphertext.flip(0)) }

    @Test
    fun changedTagFails() = assertFails { box -> SealedBox(box.nonce, box.ciphertext.flip(box.ciphertext.lastIndex)) }

    @Test
    fun changedNonceFails() = assertFails { box -> SealedBox(box.nonce.flip(0), box.ciphertext) }

    @Test
    fun truncatedCiphertextFails() = assertFails { box -> SealedBox(box.nonce, box.ciphertext.copyOf(5)) }

    @Test
    fun wrongNonceLengthFails() = assertFails { box -> SealedBox(box.nonce.copyOf(16), box.ciphertext) }

    @Test
    fun changedAssociatedDataFails() {
        val box = cipher.encrypt(key, plaintext, aad)

        assertThrows(DecryptionFailedException::class.java) {
            cipher.decrypt(key, box, "record:43|schema:1".toByteArray())
        }
    }

    @Test
    fun wrongKeyFails() {
        val box = cipher.encrypt(key, plaintext, aad)

        assertThrows(DecryptionFailedException::class.java) { cipher.decrypt(randomBytes(32), box, aad) }
    }

    @Test
    fun failureMessageRevealsNothing() {
        val box = cipher.encrypt(key, plaintext, aad)
        val error = assertThrows(DecryptionFailedException::class.java) {
            cipher.decrypt(randomBytes(32), box, aad)
        }

        assertEquals("Decryption failed", error.message)
        assertEquals(null, error.cause)
    }

    @Test
    fun nonAes256KeyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { cipher.encrypt(randomBytes(16), plaintext, aad) }
    }

    private fun assertFails(tamper: (SealedBox) -> SealedBox) {
        val box = cipher.encrypt(key, plaintext, aad)

        assertThrows(DecryptionFailedException::class.java) { cipher.decrypt(key, tamper(box), aad) }
    }

    private fun ByteArray.flip(index: Int): ByteArray =
        copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }

    private fun randomBytes(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)
}
