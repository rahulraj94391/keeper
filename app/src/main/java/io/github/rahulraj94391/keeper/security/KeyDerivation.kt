package io.github.rahulraj94391.keeper.security

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/** Argon2id settings. Persisted next to every salt so old vaults and backups stay readable. */
data class Argon2Params(
    val memoryKiB: Int = 65_536,
    val iterations: Int = 3,
    val parallelism: Int = 1,
    val outputBytes: Int = 32,
    val version: Int = VERSION_13,
) {
    companion object {
        const val VERSION_13 = 0x13
        val DEFAULT = Argon2Params()
    }
}

/** Thrown when stored or imported KDF settings are outside what Keeper will run. */
class UnsupportedKdfParametersException(message: String) : IllegalArgumentException(message)

interface KeyDerivation {
    /**
     * Derives a key from already-encoded password bytes (see [MasterPasswordEncoding]).
     * Throws [UnsupportedKdfParametersException] before doing any work if [params] or [salt]
     * are out of bounds.
     */
    fun deriveKey(password: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray
}

class Argon2idKeyDerivation : KeyDerivation {

    override fun deriveKey(password: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray {
        validate(salt, params)
        return argon2id(password, salt, params)
    }

    companion object {
        // Bounds from docs/design.md section 1.1. Lower bounds follow OWASP's Argon2id minimum.
        val MEMORY_KIB_RANGE = 19_456..262_144
        val ITERATIONS_RANGE = 2..10
        val PARALLELISM_RANGE = 1..4
        const val OUTPUT_BYTES = 32
        val SALT_BYTES_RANGE = 16..64

        /** Rejects untrusted settings before any memory is allocated (PRD 6.2, 7.2). */
        fun validate(salt: ByteArray, params: Argon2Params) {
            fun check(ok: Boolean, what: String) {
                if (!ok) throw UnsupportedKdfParametersException("Unsupported Argon2id $what")
            }
            check(params.version == Argon2Params.VERSION_13, "version")
            check(params.memoryKiB in MEMORY_KIB_RANGE, "memory")
            check(params.iterations in ITERATIONS_RANGE, "iterations")
            check(params.parallelism in PARALLELISM_RANGE, "parallelism")
            check(params.outputBytes == OUTPUT_BYTES, "output length")
            check(salt.size in SALT_BYTES_RANGE, "salt length")
        }

        /** Unchecked Argon2id, exposed for RFC 9106 test vectors that use out-of-range settings. */
        internal fun argon2id(
            password: ByteArray,
            salt: ByteArray,
            params: Argon2Params,
            secret: ByteArray? = null,
            associatedData: ByteArray? = null,
        ): ByteArray {
            val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(params.version)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(salt)
            secret?.let(builder::withSecret)
            associatedData?.let(builder::withAdditional)
            val bcParams = builder.build()
            val output = ByteArray(params.outputBytes)
            try {
                Argon2BytesGenerator().apply { init(bcParams) }.generateBytes(password, output)
            } finally {
                // Wipes Bouncy Castle's copies of salt, secret and associated data.
                bcParams.clear()
            }
            return output
        }
    }
}
