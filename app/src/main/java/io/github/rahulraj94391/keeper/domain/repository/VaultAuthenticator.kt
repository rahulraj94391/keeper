package io.github.rahulraj94391.keeper.domain.repository

import io.github.rahulraj94391.keeper.domain.model.VaultState
import kotlinx.coroutines.flow.StateFlow

/**
 * Creates, unlocks and locks the vault. Master passwords are passed as [CharArray] so the
 * caller and implementation can wipe them; implementations must not keep a reference.
 */
interface VaultAuthenticator {
    val state: StateFlow<VaultState>

    /** Creates a new vault protected by [masterPassword] and leaves it unlocked. */
    suspend fun createVault(masterPassword: CharArray)

    /** Returns true and unlocks the vault when [masterPassword] is correct. */
    suspend fun unlock(masterPassword: CharArray): Boolean

    fun lock()
}
