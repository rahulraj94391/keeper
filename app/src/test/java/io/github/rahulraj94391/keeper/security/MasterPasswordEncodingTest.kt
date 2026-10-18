package io.github.rahulraj94391.keeper.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MasterPasswordEncodingTest {

    @Test
    fun composedAndDecomposedFormsEncodeIdentically() {
        val composed = "café".toCharArray() // é as one code point
        val decomposed = "café".toCharArray() // e + combining acute accent

        assertArrayEquals(MasterPasswordEncoding.encode(composed), MasterPasswordEncoding.encode(decomposed))
    }

    @Test
    fun encodesAsUtf8() {
        assertArrayEquals(
            byteArrayOf(0x63, 0x61, 0x66, 0xC3.toByte(), 0xA9.toByte()),
            MasterPasswordEncoding.encode("café".toCharArray()),
        )
    }

    @Test
    fun whitespaceIsPreserved() {
        val plain = MasterPasswordEncoding.encode("secret".toCharArray())
        val padded = MasterPasswordEncoding.encode(" secret ".toCharArray())

        assertFalse(plain.contentEquals(padded))
    }
}
