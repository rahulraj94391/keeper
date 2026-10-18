package io.github.rahulraj94391.keeper.data.auth

import io.github.rahulraj94391.keeper.data.vault.VaultMetadataStore
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.security.VaultKeyManager
import io.github.rahulraj94391.keeper.security.VaultSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Master-password unlock (PRD 6.1, 6.2): the vault key is random, wrapped with an
 * Argon2id-derived key, and held only by [session] while unlocked.
 */
class MasterPasswordVaultAuthenticator(
    private val store: VaultMetadataStore,
    private val keyManager: VaultKeyManager,
    private val session: VaultSession,
    appScope: CoroutineScope,
    /** Argon2id is slow by design; it never runs on the main thread (PRD 7.2). */
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VaultAuthenticator {

    private val _state = MutableStateFlow(VaultState.Loading)
    override val state: StateFlow<VaultState> = _state.asStateFlow()

    // One create or unlock at a time, so repeated taps can't run Argon2id twice in parallel.
    private val mutex = Mutex()

    init {
        appScope.launch {
            val exists = store.load() != null
            _state.compareAndSet(VaultState.Loading, if (exists) VaultState.Locked else VaultState.NoVault)
        }
    }

    override suspend fun createVault(masterPassword: CharArray) = mutex.withLock {
        check(awaitLoaded() == VaultState.NoVault) { "A vault can only be created when none exists" }
        val newVault = withContext(cryptoDispatcher) { keyManager.createVault(masterPassword) }
        try {
            // Metadata is saved as one record, so an interrupted setup leaves either a
            // complete vault or none (PRD 6.1).
            store.save(newVault.metadata)
        } catch (e: Throwable) {
            newVault.vek.fill(0)
            throw e
        }
        session.open(newVault.vek)
        _state.value = VaultState.Unlocked
    }

    override suspend fun unlock(masterPassword: CharArray): Boolean = mutex.withLock {
        if (awaitLoaded() == VaultState.Unlocked) return true
        val metadata = store.load() ?: return false
        val vek = withContext(cryptoDispatcher) { keyManager.unwrap(masterPassword, metadata) }
            ?: return false
        session.open(vek)
        _state.value = VaultState.Unlocked
        true
    }

    /** Waits for the start-up check of stored data, so no call acts on a guess. */
    private suspend fun awaitLoaded(): VaultState = _state.first { it != VaultState.Loading }

    override fun lock() {
        session.close()
        if (_state.value == VaultState.Unlocked) _state.value = VaultState.Locked
    }
}
