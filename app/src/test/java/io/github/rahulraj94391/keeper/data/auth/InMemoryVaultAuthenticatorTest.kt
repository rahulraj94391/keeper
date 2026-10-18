package io.github.rahulraj94391.keeper.data.auth

import io.github.rahulraj94391.keeper.domain.model.VaultState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryVaultAuthenticatorTest {

    private val authenticator = InMemoryVaultAuthenticator()

    @Test
    fun startsWithNoVault() {
        assertEquals(VaultState.NoVault, authenticator.state.value)
    }

    @Test
    fun createVault_unlocksAndLockLocks() = runTest {
        authenticator.createVault("fake master password".toCharArray())
        assertEquals(VaultState.Unlocked, authenticator.state.value)

        authenticator.lock()
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun unlock_acceptsOnlyTheCorrectPassword() = runTest {
        authenticator.createVault("fake master password".toCharArray())
        authenticator.lock()

        assertFalse(authenticator.unlock("fake master passworD".toCharArray()))
        assertFalse(authenticator.unlock("fake master password ".toCharArray()))
        assertEquals(VaultState.Locked, authenticator.state.value)

        assertTrue(authenticator.unlock("fake master password".toCharArray()))
        assertEquals(VaultState.Unlocked, authenticator.state.value)
    }

    @Test
    fun unlock_withNoVault_fails() = runTest {
        assertFalse(authenticator.unlock("anything".toCharArray()))
        assertEquals(VaultState.NoVault, authenticator.state.value)
    }
}
