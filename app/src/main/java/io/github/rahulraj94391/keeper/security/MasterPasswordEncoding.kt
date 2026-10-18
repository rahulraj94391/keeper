package io.github.rahulraj94391.keeper.security

import java.nio.CharBuffer
import java.text.Normalizer

/**
 * The single conversion from a typed password to key-derivation input (PRD 6.2 and 7.2).
 * Use it for vault creation, unlock and backup restore alike.
 *
 * Policy: Unicode NFC normalization, then UTF-8. Whitespace is never trimmed.
 * NFC makes the same visible text typed on different keyboards produce the same bytes.
 *
 * Limitation: [Normalizer] returns an immutable String, so one copy of the password exists
 * briefly on the JVM heap and cannot be wiped.
 */
object MasterPasswordEncoding {

    fun encode(password: CharArray): ByteArray {
        val normalized = Normalizer.normalize(CharBuffer.wrap(password), Normalizer.Form.NFC)
        val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(normalized))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        // Wipe the encoder's backing array; only the returned copy remains.
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }
}
