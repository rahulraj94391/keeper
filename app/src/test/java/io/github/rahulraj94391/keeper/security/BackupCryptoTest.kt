package io.github.rahulraj94391.keeper.security

import io.github.rahulraj94391.keeper.testing.FAST_KDF_PARAMS
import io.github.rahulraj94391.keeper.testing.testCredential
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.security.SecureRandom

class BackupCryptoTest {

    private val passphrase = "fake backup passphrase"
    private val credentials = listOf(
        testCredential("rec-1", title = "Example Mail", url = "https://mail.example.com", favorite = true),
        testCredential("rec-2", title = "Ünïcödé ✓").copy(notes = "line one\nline two", password = " spaced "),
    )
    private val crypto = BackupCrypto(kdfParams = FAST_KDF_PARAMS)
    private val file by lazy { crypto.encrypt(credentials, passphrase.toCharArray()) }

    private fun read(bytes: ByteArray, pass: String = passphrase) = crypto.decrypt(bytes, pass.toCharArray())

    @Test
    fun roundTrip() {
        val result = read(file)

        assertTrue(result is BackupReadResult.Success)
        assertEquals(credentials, (result as BackupReadResult.Success).credentials)
    }

    @Test
    fun emptyVault_roundTrips() {
        val result = read(crypto.encrypt(emptyList(), passphrase.toCharArray()))

        assertEquals(emptyList<Any>(), (result as BackupReadResult.Success).credentials)
    }

    @Test
    fun fileStartsWithMagicAndHoldsNoPlaintext() {
        assertArrayEquals(BackupCrypto.MAGIC, file.copyOf(8))
        val text = String(file, Charsets.ISO_8859_1)
        credentials.flatMap { listOf(it.title, it.username, it.password, it.url) }.filter { it.isNotBlank() }.forEach {
            assertFalse("file contains '$it'", text.contains(it))
        }
    }

    @Test
    fun eachExportUsesFreshSaltAndNonce() {
        val other = crypto.encrypt(credentials, passphrase.toCharArray())

        assertFalse(file.contentEquals(other))
    }

    @Test
    fun wrongPassphrase() {
        assertEquals(BackupReadResult.WrongPassphraseOrDamaged, read(file, "fake backup passphrasE"))
    }

    @Test
    fun anyChangedHeaderOrBodyByte_isDetected() {
        // Flip each byte that isn't a length field the parser checks structurally.
        val saltEnd = 22 + 16
        val positions = listOf(8) + (11..21) + (22 until saltEnd) + listOf(saltEnd) +
            ((saltEnd + 2) until (saltEnd + 14)) + listOf(file.size - 1, file.size - 20)
        positions.forEach { i ->
            val tampered = file.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            val result = read(tampered)
            assertFalse("byte $i change went unnoticed", result is BackupReadResult.Success)
        }
    }

    @Test
    fun notABackup_isMalformed() {
        assertEquals(BackupReadResult.Malformed, read("hello, not a backup".toByteArray()))
        assertEquals(BackupReadResult.Malformed, read(ByteArray(0)))
    }

    @Test
    fun truncatedOrExtendedFile_isMalformed() {
        assertEquals(BackupReadResult.Malformed, read(file.copyOf(file.size - 1)))
        assertEquals(BackupReadResult.Malformed, read(file.copyOf(40)))
        assertEquals(BackupReadResult.Malformed, read(file + byteArrayOf(0)))
    }

    @Test
    fun unknownVersionsOrAlgorithms_areUnsupported() {
        listOf(8 to 2, 9 to 7, 22 + 16 to 9).forEach { (index, value) ->
            val changed = file.copyOf().also { it[index] = value.toByte() }
            assertEquals("byte $index", BackupReadResult.Unsupported, read(changed))
        }
    }

    @Test
    fun hostileKdfSettings_areRejectedBeforeArgon2Runs() {
        val counting = CountingKdf()
        val reader = BackupCrypto(kdf = counting)
        // 4 GiB of memory and a million iterations.
        val hostile = file.copyOf().also {
            ByteBuffer.wrap(it).putInt(11, 4 * 1024 * 1024).putInt(15, 1_000_000)
        }

        assertEquals(BackupReadResult.Unsupported, reader.decrypt(hostile, passphrase.toCharArray()))
        assertEquals(0, counting.runs)
    }

    @Test
    fun oversizedFile_isRejectedWithoutParsing() {
        assertEquals(BackupReadResult.TooLarge, read(ByteArray(BackupCrypto.MAX_FILE_BYTES + 1)))
    }

    @Test
    fun invalidRecords_failTheWholeRestore() {
        val duplicateIds = listOf(testCredential("same"), testCredential("same"))
        val blankTitle = listOf(testCredential("x").copy(title = " "))
        val tooLong = listOf(testCredential("x").copy(notes = "n".repeat(10_001)))
        listOf(duplicateIds, blankTitle, tooLong).forEach { bad ->
            val badFile = crypto.encrypt(bad, passphrase.toCharArray())
            assertEquals(BackupReadResult.Malformed, read(badFile))
        }
    }

    // --- Format fixture: a version-1 file frozen in test resources. ---

    @Test
    fun frozenV1Fixture_stillReads() {
        val fixture = javaClass.getResourceAsStream("/backup/v1-fixture.kbak")!!.readBytes()

        val result = read(fixture, FIXTURE_PASSPHRASE)

        assertTrue(result is BackupReadResult.Success)
        assertEquals(fixtureCredentials(), (result as BackupReadResult.Success).credentials)
    }

    @Test
    fun deterministicEncoding_matchesTheFixtureByteForByte() {
        val fixture = javaClass.getResourceAsStream("/backup/v1-fixture.kbak")!!.readBytes()

        assertArrayEquals(fixture, fixtureWriter().encrypt(fixtureCredentials(), FIXTURE_PASSPHRASE.toCharArray()))
    }

    private class CountingKdf : KeyDerivation {
        var runs = 0
        override fun deriveKey(password: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray {
            Argon2idKeyDerivation.validate(salt, params)
            runs++
            return ByteArray(32)
        }
    }

    companion object {
        const val FIXTURE_PASSPHRASE = "fixture passphrase v1"

        fun fixtureCredentials() = listOf(
            testCredential("fixture-1", title = "Fixture Mail", url = "https://mail.example.com", favorite = true),
            testCredential("fixture-2", title = "Fixture Bank"),
        )

        /** Counts upward instead of being random, so the output is reproducible. Tests only. */
        private class CountingRandom : SecureRandom() {
            private var next = 0
            override fun nextBytes(bytes: ByteArray) {
                for (i in bytes.indices) bytes[i] = (next++).toByte()
            }
        }

        fun fixtureWriter(): BackupCrypto {
            val random = CountingRandom()
            return BackupCrypto(cipher = AesGcmCipher(random), random = random, kdfParams = FAST_KDF_PARAMS)
        }
    }
}
