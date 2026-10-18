package io.github.rahulraj94391.keeper.security

/** Thrown when vault data is requested while no session is open. */
class VaultLockedException : IllegalStateException("The vault is locked")

/**
 * The only holder of the vault encryption key while the vault is unlocked (PRD 7.4).
 * The key never leaves this class except for the duration of a [withKey] call.
 */
class VaultSession {

    private var vek: ByteArray? = null

    val isOpen: Boolean
        @Synchronized get() = vek != null

    /** Starts a session and takes ownership of [vek]: the caller must not use or keep it afterwards. */
    @Synchronized
    fun open(vek: ByteArray) {
        require(vek.size == VaultKeyManager.VEK_BYTES) { "Unexpected vault key size" }
        close()
        this.vek = vek
    }

    /**
     * Runs [block] with the key. [block] must not store the array; it is wiped when the session
     * closes. Throws [VaultLockedException] when no session is open.
     */
    @Synchronized
    fun <T> withKey(block: (ByteArray) -> T): T {
        val key = vek ?: throw VaultLockedException()
        return block(key)
    }

    /** Ends the session and overwrites the key. Safe to call when already closed. */
    @Synchronized
    fun close() {
        vek?.fill(0)
        vek = null
    }
}
