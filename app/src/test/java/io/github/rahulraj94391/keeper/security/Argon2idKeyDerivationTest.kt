package io.github.rahulraj94391.keeper.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class Argon2idKeyDerivationTest {

    private val kdf = Argon2idKeyDerivation()

    // Smallest accepted cost, so the tests stay fast. Production uses Argon2Params.DEFAULT.
    private val fastParams = Argon2Params(memoryKiB = 19_456, iterations = 2)
    private val salt = ByteArray(16) { it.toByte() }
    private val password = MasterPasswordEncoding.encode("fake master password".toCharArray())

    @Test
    fun rfc9106Argon2idTestVector() {
        // RFC 9106 section 5.3.
        val tag = Argon2idKeyDerivation.argon2id(
            password = ByteArray(32) { 0x01 },
            salt = ByteArray(16) { 0x02 },
            params = Argon2Params(memoryKiB = 32, iterations = 3, parallelism = 4),
            secret = ByteArray(8) { 0x03 },
            associatedData = ByteArray(12) { 0x04 },
        )

        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", tag.toHex())
    }

    @Test
    fun referenceImplementationTestVector() {
        // phc-winner-argon2 reference test: Argon2id v1.3, t=2, m=2^16 KiB, p=1.
        val tag = Argon2idKeyDerivation.argon2id(
            password = "password".toByteArray(),
            salt = "somesalt".toByteArray(),
            params = Argon2Params(memoryKiB = 65_536, iterations = 2, parallelism = 1),
        )

        assertEquals("09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7", tag.toHex())
    }

    @Test
    fun sameInputsGiveSameKey() {
        assertArrayEquals(kdf.deriveKey(password, salt, fastParams), kdf.deriveKey(password, salt, fastParams))
    }

    @Test
    fun outputIs32Bytes() {
        assertEquals(32, kdf.deriveKey(password, salt, fastParams).size)
    }

    @Test
    fun differentPasswordOrSaltGivesDifferentKey() {
        val base = kdf.deriveKey(password, salt, fastParams)
        val otherPassword = kdf.deriveKey(MasterPasswordEncoding.encode("fake master passworD".toCharArray()), salt, fastParams)
        val otherSalt = kdf.deriveKey(password, salt.copyOf().also { it[0] = 99 }, fastParams)

        assertFalse(base.contentEquals(otherPassword))
        assertFalse(base.contentEquals(otherSalt))
    }

    @Test
    fun defaultsAreAccepted() {
        Argon2idKeyDerivation.validate(salt, Argon2Params.DEFAULT)
    }

    @Test
    fun outOfRangeSettingsAreRejectedBeforeRunning() {
        val rejected = listOf(
            Argon2Params(memoryKiB = 1_048_576), // 1 GiB
            Argon2Params(memoryKiB = 8),
            Argon2Params(iterations = 0),
            Argon2Params(iterations = 1_000_000),
            Argon2Params(parallelism = 64),
            Argon2Params(outputBytes = 16),
            Argon2Params(version = 0x10),
        )
        rejected.forEach { params ->
            assertThrows(UnsupportedKdfParametersException::class.java) { kdf.deriveKey(password, salt, params) }
        }
    }

    @Test
    fun shortOrHugeSaltIsRejected() {
        listOf(ByteArray(8), ByteArray(1024)).forEach { badSalt ->
            assertThrows(UnsupportedKdfParametersException::class.java) { kdf.deriveKey(password, badSalt, fastParams) }
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
