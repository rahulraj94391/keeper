package io.github.rahulraj94391.keeper.data.auth

import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.security.MasterPasswordEncoding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Placeholder used while the UI is built. NOT the real security design: it checks the
 * password against a salted SHA-256 digest held in memory only, so the vault is gone when
 * the process dies. The crypto phase replaces it with Argon2id + AES-GCM key wrapping.
 */
class InMemoryVaultAuthenticator(
    private val random: SecureRandom = SecureRandom(),
) : VaultAuthenticator {

    private val _state = MutableStateFlow(VaultState.NoVault)
    override val state: StateFlow<VaultState> = _state.asStateFlow()

    private var salt: ByteArray? = null
    private var digest: ByteArray? = null

    override suspend fun createVault(masterPassword: CharArray) {
        check(_state.value == VaultState.NoVault) { "A vault already exists" }
        val newSalt = ByteArray(SALT_BYTES).also(random::nextBytes)
        digest = digestOf(newSalt, masterPassword)
        salt = newSalt
        _state.value = VaultState.Unlocked
    }

    override suspend fun unlock(masterPassword: CharArray): Boolean {
        val storedSalt = salt ?: return false
        val candidate = digestOf(storedSalt, masterPassword)
        val matches = MessageDigest.isEqual(candidate, digest)
        candidate.fill(0)
        if (matches) _state.value = VaultState.Unlocked
        return matches
    }

    override fun lock() {
        if (_state.value == VaultState.Unlocked) _state.value = VaultState.Locked
    }

    private fun digestOf(salt: ByteArray, password: CharArray): ByteArray {
        val encoded = MasterPasswordEncoding.encode(password)
        return try {
            MessageDigest.getInstance("SHA-256").run {
                update(salt)
                digest(encoded)
            }
        } finally {
            encoded.fill(0)
        }
    }

    private companion object {
        const val SALT_BYTES = 16
    }
}
