package io.github.rahulraj94391.keeper.data.auth

import io.github.rahulraj94391.keeper.data.vault.VaultMetadataStore
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.domain.repository.BiometricUnlockStart
import io.github.rahulraj94391.keeper.domain.repository.UnlockResult
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.security.BiometricKeyInvalidatedException
import io.github.rahulraj94391.keeper.security.BiometricKeyStore
import io.github.rahulraj94391.keeper.security.BiometricVekWrapping
import io.github.rahulraj94391.keeper.security.BiometricWrap
import io.github.rahulraj94391.keeper.security.ThrottleStatus
import io.github.rahulraj94391.keeper.security.UnlockThrottle
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
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import kotlin.time.Duration

/**
 * Unlocks the on-device vault with the master password (PRD 6.1, 6.2) or, when turned on,
 * with a Keystore key that needs a strong biometric (PRD 6.3). The vault key is random and is
 * held only by [session] while unlocked.
 */
class LocalVaultAuthenticator(
    private val store: VaultMetadataStore,
    private val keyManager: VaultKeyManager,
    private val session: VaultSession,
    private val biometricKeyStore: BiometricKeyStore,
    private val unlockThrottle: UnlockThrottle,
    appScope: CoroutineScope,
    /** Argon2id is slow by design and Keystore calls can block; neither runs on the main thread. */
    private val cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : VaultAuthenticator {

    private val _state = MutableStateFlow(VaultState.Loading)
    override val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(false)
    override val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    // One create, unlock or metadata change at a time, so repeated taps can't overlap.
    private val mutex = Mutex()

    init {
        appScope.launch {
            val metadata = store.load()
            _biometricEnabled.value = metadata?.biometricEnabled == true
            _state.compareAndSet(VaultState.Loading, if (metadata != null) VaultState.Locked else VaultState.NoVault)
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
        unlockThrottle.recordSuccess()
        session.open(newVault.vek)
        _state.value = VaultState.Unlocked
    }

    override suspend fun unlock(masterPassword: CharArray): UnlockResult = mutex.withLock {
        if (awaitLoaded() == VaultState.Unlocked) return UnlockResult.Success
        val throttle = unlockThrottle.status()
        if (throttle is ThrottleStatus.LockedOut) return UnlockResult.LockedOut(throttle.remaining)
        val metadata = store.load() ?: return UnlockResult.WrongPassword(attemptsBeforeDelay = null, lockout = null)
        val vek = withContext(cryptoDispatcher) { keyManager.unwrap(masterPassword, metadata) }
        if (vek == null) {
            // Saved to disk before the caller learns the result.
            return when (val next = unlockThrottle.recordFailure()) {
                is ThrottleStatus.Allowed -> UnlockResult.WrongPassword(next.attemptsBeforeDelay, lockout = null)
                is ThrottleStatus.LockedOut -> UnlockResult.WrongPassword(attemptsBeforeDelay = null, lockout = next.remaining)
            }
        }
        unlockThrottle.recordSuccess()
        openSession(vek)
        UnlockResult.Success
    }

    override fun passwordLockoutRemaining(): Duration? =
        (unlockThrottle.status() as? ThrottleStatus.LockedOut)?.remaining

    override fun lock() {
        session.close()
        if (_state.value == VaultState.Unlocked) _state.value = VaultState.Locked
    }

    override suspend fun startBiometricEnrollment(): Cipher {
        check(awaitLoaded() == VaultState.Unlocked) { "Unlock the vault before turning on biometrics" }
        return withContext(cryptoDispatcher) { biometricKeyStore.newEncryptCipher() }
    }

    override suspend fun completeBiometricEnrollment(authorizedCipher: Cipher): Boolean = mutex.withLock {
        val metadata = store.load() ?: return false
        val alias = biometricKeyStore.keyAlias
        val wrapped = try {
            withContext(cryptoDispatcher) {
                session.withKey { vek -> BiometricVekWrapping.wrap(authorizedCipher, vek, alias) }
            }
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalStateException) {
            null // vault locked meanwhile, or the cipher wasn't authorized
        }
        if (wrapped == null) {
            biometricKeyStore.deleteKey()
            return false
        }
        store.save(metadata.withBiometric(BiometricWrap(alias, wrapped)))
        _biometricEnabled.value = true
        true
    }

    override suspend fun disableBiometric() = mutex.withLock {
        turnOffBiometric()
    }

    override suspend fun startBiometricUnlock(): BiometricUnlockStart = mutex.withLock {
        awaitLoaded()
        val wrap = store.load()?.biometric ?: return BiometricUnlockStart.NotEnabled
        try {
            val cipher = withContext(cryptoDispatcher) { biometricKeyStore.decryptCipher(wrap.wrappedVek.nonce) }
            BiometricUnlockStart.Ready(cipher)
        } catch (_: BiometricKeyInvalidatedException) {
            // Enrollment changed or the key is gone: this wrap can never be opened again.
            turnOffBiometric()
            BiometricUnlockStart.KeyInvalidated
        } catch (_: GeneralSecurityException) {
            BiometricUnlockStart.Unavailable
        }
    }

    override suspend fun completeBiometricUnlock(authorizedCipher: Cipher): Boolean = mutex.withLock {
        if (awaitLoaded() == VaultState.Unlocked) return true
        val wrap = store.load()?.biometric ?: return false
        val vek = withContext(cryptoDispatcher) {
            BiometricVekWrapping.unwrap(authorizedCipher, wrap.wrappedVek, wrap.keyAlias)
        } ?: return false
        // A fingerprint proves the owner is here: clear any password lockout.
        unlockThrottle.recordSuccess()
        openSession(vek)
        true
    }

    /** Caller must hold [mutex]. */
    private suspend fun turnOffBiometric() {
        store.load()?.takeIf { it.biometricEnabled }?.let { store.save(it.withBiometric(null)) }
        withContext(cryptoDispatcher) { biometricKeyStore.deleteKey() }
        _biometricEnabled.value = false
    }

    private fun openSession(vek: ByteArray) {
        session.open(vek)
        _state.value = VaultState.Unlocked
    }

    /** Waits for the start-up check of stored data, so no call acts on a guess. */
    private suspend fun awaitLoaded(): VaultState = _state.first { it != VaultState.Loading }
}
