package io.github.rahulraj94391.keeper.domain.repository

import io.github.rahulraj94391.keeper.domain.model.VaultState
import kotlinx.coroutines.flow.StateFlow
import javax.crypto.Cipher
import kotlin.time.Duration

/**
 * Creates, unlocks and locks the vault. Master passwords are passed as [CharArray] so the
 * caller and implementation can wipe them; implementations must not keep a reference.
 */
interface VaultAuthenticator {
    val state: StateFlow<VaultState>

    /** Creates a new vault protected by [masterPassword] and leaves it unlocked. */
    suspend fun createVault(masterPassword: CharArray)

    /**
     * Unlocks the vault when [masterPassword] is correct. Wrong attempts are rate-limited: during
     * a wait the password isn't checked at all (see UnlockThrottle).
     */
    suspend fun unlock(masterPassword: CharArray): UnlockResult

    /** Time left before the master password may be tried again, or null if it may be now. */
    fun passwordLockoutRemaining(): Duration?

    fun lock()

    // Biometric unlock (PRD 6.3). Each operation is split in two: "start" returns a Keystore
    // cipher to hand to BiometricPrompt as a CryptoObject, and "complete" uses the cipher the
    // prompt authorized. A successful prompt alone never unlocks anything.

    /** Whether biometric unlock is turned on. Not secret. */
    val biometricEnabled: StateFlow<Boolean>

    /** Needs an unlocked vault. Creates a new Keystore key and returns its encrypt cipher. */
    suspend fun startBiometricEnrollment(): Cipher

    /** Wraps the vault key with the authorized cipher. Returns false if that failed. */
    suspend fun completeBiometricEnrollment(authorizedCipher: Cipher): Boolean

    /** Turns biometric unlock off and deletes its Keystore key. The master password is unaffected. */
    suspend fun disableBiometric()

    suspend fun startBiometricUnlock(): BiometricUnlockStart

    /** Unwraps the vault key with the authorized cipher and unlocks. Returns false on failure. */
    suspend fun completeBiometricUnlock(authorizedCipher: Cipher): Boolean
}

sealed interface UnlockResult {
    data object Success : UnlockResult

    /**
     * Wrong password. [attemptsBeforeDelay] is set when a wait is close (a warning);
     * [lockout] is set when this attempt started a wait.
     */
    data class WrongPassword(val attemptsBeforeDelay: Int?, val lockout: Duration?) : UnlockResult

    /** Too many wrong attempts; the password wasn't checked. */
    data class LockedOut(val remaining: Duration) : UnlockResult
}

sealed interface BiometricUnlockStart {
    class Ready(val cipher: Cipher) : BiometricUnlockStart

    /** Biometric unlock is off. */
    data object NotEnabled : BiometricUnlockStart

    /**
     * The Keystore key is gone or was invalidated (for example, enrollments changed), so
     * biometric unlock has been turned off. The master password still works (PRD 6.3).
     */
    data object KeyInvalidated : BiometricUnlockStart

    /** The Keystore couldn't prepare a cipher for another reason. Nothing was changed. */
    data object Unavailable : BiometricUnlockStart
}
