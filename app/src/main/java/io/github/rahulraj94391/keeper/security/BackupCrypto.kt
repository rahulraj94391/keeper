package io.github.rahulraj94391.keeper.security

import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.model.CredentialLimits
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.SecureRandom

/** Outcome of reading a backup file. Nothing about the current vault changes in any case. */
sealed interface BackupReadResult {
    class Success(val credentials: List<Credential>) : BackupReadResult {
        override fun toString(): String = "Success(count=${credentials.size})"
    }

    /** Authentication failed: a wrong passphrase, or the file was damaged or altered. */
    data object WrongPassphraseOrDamaged : BackupReadResult

    /** A Keeper backup, but from a format or with settings this version doesn't accept. */
    data object Unsupported : BackupReadResult

    /** Not a Keeper backup, truncated, or with contents that don't pass validation. */
    data object Malformed : BackupReadResult

    data object TooLarge : BackupReadResult
}

/**
 * Encrypted backup files (PRD 6.7, 6.8). Layout of format version 1, all integers big-endian:
 *
 * | Field | Size |
 * |---|---|
 * | magic `KEEPERBK` | 8 |
 * | format version (1) | 1 |
 * | KDF id (1 = Argon2id) | 1 |
 * | KDF version (0x13) | 1 |
 * | memory KiB | 4 |
 * | iterations | 4 |
 * | parallelism | 1 |
 * | key length | 1 |
 * | salt length + salt | 1 + n |
 * | cipher id (1 = AES-256-GCM) | 1 |
 * | nonce length (12) + nonce | 1 + 12 |
 * | ciphertext length | 4 |
 * | ciphertext + 16-byte tag | rest of file |
 *
 * Everything from the magic up to the cipher id is the AES-GCM associated data. The nonce is
 * authenticated by GCM itself, and the ciphertext length must match the file exactly.
 */
class BackupCrypto(
    private val kdf: KeyDerivation = Argon2idKeyDerivation(),
    private val cipher: AeadCipher = AesGcmCipher(),
    private val random: SecureRandom = SecureRandom(),
    private val kdfParams: Argon2Params = Argon2Params.DEFAULT,
) {

    fun encrypt(credentials: List<Credential>, passphrase: CharArray): ByteArray {
        require(credentials.size <= MAX_RECORDS) { "Too many records for one backup" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = header(kdfParams, salt)
        val key = deriveKey(passphrase, salt, kdfParams)
        val plaintext = encodePayload(credentials)
        try {
            val box = cipher.encrypt(key, plaintext, header)
            val out = ByteArrayOutputStream(header.size + 1 + box.nonce.size + 4 + box.ciphertext.size)
            DataOutputStream(out).run {
                write(header)
                writeByte(box.nonce.size)
                write(box.nonce)
                writeInt(box.ciphertext.size)
                write(box.ciphertext)
            }
            return out.toByteArray()
        } finally {
            key.fill(0)
            plaintext.fill(0)
        }
    }

    fun decrypt(file: ByteArray, passphrase: CharArray): BackupReadResult {
        if (file.size > MAX_FILE_BYTES) return BackupReadResult.TooLarge
        val parsed = try {
            parse(file)
        } catch (_: BufferUnderflowException) {
            return BackupReadResult.Malformed
        }
        val envelope = when (parsed) {
            is Parsed.Ok -> parsed.envelope
            is Parsed.Rejected -> return parsed.result
        }

        // Settings are checked before any key derivation, so a hostile file can't make the app
        // allocate huge amounts of memory or run for minutes (PRD 6.8, 7.2).
        try {
            Argon2idKeyDerivation.validate(envelope.salt, envelope.params)
        } catch (_: UnsupportedKdfParametersException) {
            return BackupReadResult.Unsupported
        }

        val key = deriveKey(passphrase, envelope.salt, envelope.params)
        val plaintext = try {
            cipher.decrypt(key, SealedBox(envelope.nonce, envelope.ciphertext), envelope.header)
        } catch (_: DecryptionFailedException) {
            return BackupReadResult.WrongPassphraseOrDamaged
        } finally {
            key.fill(0)
        }
        return try {
            decodePayload(plaintext)?.let(BackupReadResult::Success) ?: BackupReadResult.Malformed
        } finally {
            plaintext.fill(0)
        }
    }

    private class Envelope(
        val header: ByteArray,
        val params: Argon2Params,
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
    )

    private sealed interface Parsed {
        class Ok(val envelope: Envelope) : Parsed
        class Rejected(val result: BackupReadResult) : Parsed
    }

    private fun parse(file: ByteArray): Parsed {
        val buffer = ByteBuffer.wrap(file)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) return Parsed.Rejected(BackupReadResult.Malformed)
        if (buffer.get().toInt() != FORMAT_VERSION) return Parsed.Rejected(BackupReadResult.Unsupported)
        if (buffer.get().toInt() != KDF_ARGON2ID) return Parsed.Rejected(BackupReadResult.Unsupported)
        val kdfVersion = buffer.get().toInt() and 0xFF
        val memoryKiB = buffer.int
        val iterations = buffer.int
        val parallelism = buffer.get().toInt() and 0xFF
        val outputBytes = buffer.get().toInt() and 0xFF
        val salt = ByteArray(buffer.get().toInt() and 0xFF).also(buffer::get)
        if (buffer.get().toInt() != CIPHER_AES_256_GCM) return Parsed.Rejected(BackupReadResult.Unsupported)
        val header = file.copyOf(buffer.position())

        val nonceLength = buffer.get().toInt() and 0xFF
        if (nonceLength != AesGcmCipher.NONCE_BYTES) return Parsed.Rejected(BackupReadResult.Malformed)
        val nonce = ByteArray(nonceLength).also(buffer::get)
        val ciphertextLength = buffer.int
        if (ciphertextLength < AesGcmCipher.TAG_BYTES || ciphertextLength != buffer.remaining()) {
            return Parsed.Rejected(BackupReadResult.Malformed)
        }
        val ciphertext = ByteArray(ciphertextLength).also(buffer::get)
        val params = Argon2Params(memoryKiB, iterations, parallelism, outputBytes, kdfVersion)
        return Parsed.Ok(Envelope(header, params, salt, nonce, ciphertext))
    }

    private fun header(params: Argon2Params, salt: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).run {
            write(MAGIC)
            writeByte(FORMAT_VERSION)
            writeByte(KDF_ARGON2ID)
            writeByte(params.version)
            writeInt(params.memoryKiB)
            writeInt(params.iterations)
            writeByte(params.parallelism)
            writeByte(params.outputBytes)
            writeByte(salt.size)
            write(salt)
            writeByte(CIPHER_AES_256_GCM)
        }
        return out.toByteArray()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, params: Argon2Params): ByteArray {
        val encoded = MasterPasswordEncoding.encode(passphrase)
        return try {
            kdf.deriveKey(encoded, salt, params)
        } finally {
            encoded.fill(0)
        }
    }

    @Serializable
    private class PayloadV1(val format: Int, val credentials: List<RecordV1>) {
        override fun toString(): String = "PayloadV1(count=${credentials.size})"
    }

    @Serializable
    private class RecordV1(
        val id: String,
        val title: String,
        val username: String,
        val password: String,
        val url: String,
        val notes: String,
        val favorite: Boolean,
    ) {
        override fun toString(): String = "RecordV1(id=$id)"
    }

    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    /** Limitation: JSON goes through an immutable String, which can't be wiped. */
    private fun encodePayload(credentials: List<Credential>): ByteArray {
        val records = credentials.map { RecordV1(it.id, it.title, it.username, it.password, it.url, it.notes, it.favorite) }
        return json.encodeToString(PayloadV1.serializer(), PayloadV1(FORMAT_VERSION, records)).toByteArray(Charsets.UTF_8)
    }

    /** Strictly validates the whole payload; returns null if anything is off (PRD 6.8). */
    private fun decodePayload(bytes: ByteArray): List<Credential>? {
        val payload = try {
            json.decodeFromString(PayloadV1.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (payload.format != FORMAT_VERSION || payload.credentials.size > MAX_RECORDS) return null
        val ids = HashSet<String>()
        val credentials = payload.credentials.map { r ->
            val valid = r.id.isNotBlank() && r.id.length <= MAX_ID_LENGTH && ids.add(r.id) &&
                r.title.isNotBlank() && r.title.length <= CredentialLimits.TITLE &&
                r.username.length <= CredentialLimits.USERNAME &&
                r.password.isNotEmpty() && r.password.length <= CredentialLimits.PASSWORD &&
                r.url.length <= CredentialLimits.URL &&
                r.notes.length <= CredentialLimits.NOTES
            if (!valid) return null
            Credential(r.id, r.title, r.username, r.password, r.url, r.notes, r.favorite)
        }
        return credentials
    }

    companion object {
        val MAGIC = "KEEPERBK".toByteArray(Charsets.US_ASCII)
        const val FORMAT_VERSION = 1
        const val KDF_ARGON2ID = 1
        const val CIPHER_AES_256_GCM = 1
        const val SALT_BYTES = 16

        /** PRD 12 starting limits. */
        const val MAX_FILE_BYTES = 50 * 1024 * 1024
        const val MAX_RECORDS = 10_000
        const val MAX_ID_LENGTH = 100

        const val FILE_EXTENSION = "kbak"
        const val MIME_TYPE = "application/octet-stream"
    }
}
