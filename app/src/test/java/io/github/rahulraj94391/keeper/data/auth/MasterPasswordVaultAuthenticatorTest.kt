package io.github.rahulraj94391.keeper.data.auth

import io.github.rahulraj94391.keeper.data.vault.InMemoryVaultMetadataStore
import io.github.rahulraj94391.keeper.data.vault.VaultMetadataStore
import io.github.rahulraj94391.keeper.security.VaultKeyMetadata
import io.github.rahulraj94391.keeper.testing.testKeyManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.security.VaultSession
import io.github.rahulraj94391.keeper.testing.testAuthenticator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPasswordVaultAuthenticatorTest {

    private val store = InMemoryVaultMetadataStore()
    private val session = VaultSession()
    private val password = "fake master password"

    @Test
    fun emptyStore_startsWithNoVault() {
        assertEquals(VaultState.NoVault, testAuthenticator(store, session).state.value)
    }

    @Test
    fun createVault_savesMetadataAndOpensSession() = runTest {
        val authenticator = testAuthenticator(store, session)

        authenticator.createVault(password.toCharArray())

        assertNotNull(store.load())
        assertTrue(session.isOpen)
        assertEquals(VaultState.Unlocked, authenticator.state.value)
    }

    @Test
    fun createVault_twiceIsRejected() = runTest {
        val authenticator = testAuthenticator(store, session)
        authenticator.createVault(password.toCharArray())

        val result = runCatching { authenticator.createVault("another fake one".toCharArray()) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun lock_closesSession() = runTest {
        val authenticator = testAuthenticator(store, session)
        authenticator.createVault(password.toCharArray())

        authenticator.lock()

        assertFalse(session.isOpen)
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun afterRestart_vaultIsLockedAndUnlocksWithTheRightPasswordOnly() = runTest {
        testAuthenticator(store, VaultSession()).createVault(password.toCharArray())

        // A new authenticator over the same store simulates the app starting again.
        val restarted = testAuthenticator(store, session)
        assertEquals(VaultState.Locked, restarted.state.value)

        assertFalse(restarted.unlock("wrong fake password".toCharArray()))
        assertFalse(session.isOpen)
        assertEquals(VaultState.Locked, restarted.state.value)

        assertTrue(restarted.unlock(password.toCharArray()))
        assertTrue(session.isOpen)
        assertEquals(VaultState.Unlocked, restarted.state.value)
    }

    @Test
    fun unlockedKeyMatchesTheOneCreated() = runTest {
        val first = VaultSession()
        testAuthenticator(store, first).createVault(password.toCharArray())
        val created = first.withKey { it.copyOf() }

        testAuthenticator(store, session).unlock(password.toCharArray())

        assertTrue(session.withKey { it.contentEquals(created) })
    }

    @Test
    fun unlock_withNoVault_fails() = runTest {
        val authenticator = testAuthenticator(store, session)

        assertFalse(authenticator.unlock(password.toCharArray()))
        assertNull(store.load())
        assertEquals(VaultState.NoVault, authenticator.state.value)
    }

    @Test
    fun createVault_waitsForTheStartupCheck() = runTest {
        val slowStore = object : VaultMetadataStore by store {
            val loaded = CompletableDeferred<Unit>()
            override suspend fun load(): VaultKeyMetadata? {
                loaded.await()
                return store.load()
            }
        }
        val authenticator = MasterPasswordVaultAuthenticator(
            slowStore, testKeyManager(), session, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined,
        )
        assertEquals(VaultState.Loading, authenticator.state.value)

        val creating = async { authenticator.createVault(password.toCharArray()) }
        slowStore.loaded.complete(Unit)
        creating.await()

        assertEquals(VaultState.Unlocked, authenticator.state.value)
    }
}
