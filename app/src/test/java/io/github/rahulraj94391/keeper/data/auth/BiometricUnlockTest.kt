package io.github.rahulraj94391.keeper.data.auth

import io.github.rahulraj94391.keeper.data.vault.InMemoryVaultMetadataStore
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.domain.repository.BiometricUnlockStart
import io.github.rahulraj94391.keeper.domain.repository.UnlockResult
import io.github.rahulraj94391.keeper.security.VaultSession
import io.github.rahulraj94391.keeper.testing.FakeBiometricKeyStore
import io.github.rahulraj94391.keeper.testing.testAuthenticator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class BiometricUnlockTest {

    private val store = InMemoryVaultMetadataStore()
    private val session = VaultSession()
    private val keyStore = FakeBiometricKeyStore()
    private val authenticator = testAuthenticator(store, session, keyStore)
    private val password = "fake master password"

    private suspend fun createAndEnable(): ByteArray {
        authenticator.createVault(password.toCharArray())
        val vek = session.withKey { it.copyOf() }
        assertTrue(authenticator.completeBiometricEnrollment(authenticator.startBiometricEnrollment()))
        return vek
    }

    @Test
    fun enrollment_needsAnUnlockedVault() = runTest {
        authenticator.createVault(password.toCharArray())
        authenticator.lock()

        assertTrue(runCatching { authenticator.startBiometricEnrollment() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun enable_storesWrapAndSetsFlag() = runTest {
        createAndEnable()

        assertTrue(authenticator.biometricEnabled.value)
        assertNotNull(store.load()?.biometric)
        assertEquals(keyStore.keyAlias, store.load()?.biometric?.keyAlias)
    }

    @Test
    fun biometricUnlock_opensTheSameVaultKey() = runTest {
        val vek = createAndEnable()
        authenticator.lock()

        val start = authenticator.startBiometricUnlock()
        assertTrue(start is BiometricUnlockStart.Ready)
        assertTrue(authenticator.completeBiometricUnlock((start as BiometricUnlockStart.Ready).cipher))

        assertEquals(VaultState.Unlocked, authenticator.state.value)
        assertTrue(session.withKey { it.contentEquals(vek) })
    }

    @Test
    fun cipherFromAnotherKey_doesNotUnlock() = runTest {
        createAndEnable()
        authenticator.lock()
        val nonce = store.load()!!.biometric!!.wrappedVek.nonce
        val strangerKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val wrongCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, strangerKey, GCMParameterSpec(128, nonce))
        }

        assertFalse(authenticator.completeBiometricUnlock(wrongCipher))
        assertEquals(VaultState.Locked, authenticator.state.value)
        assertFalse(session.isOpen)
    }

    @Test
    fun invalidatedKey_turnsBiometricsOffAndPasswordStillWorks() = runTest {
        createAndEnable()
        authenticator.lock()
        keyStore.invalidate()

        assertEquals(BiometricUnlockStart.KeyInvalidated, authenticator.startBiometricUnlock())
        assertFalse(authenticator.biometricEnabled.value)
        assertNull(store.load()?.biometric)
        assertEquals(UnlockResult.Success, authenticator.unlock(password.toCharArray()))
    }

    @Test
    fun disable_removesWrapAndKey() = runTest {
        createAndEnable()

        authenticator.disableBiometric()

        assertFalse(authenticator.biometricEnabled.value)
        assertNull(store.load()?.biometric)
        assertNull(keyStore.key)
        authenticator.lock()
        assertEquals(BiometricUnlockStart.NotEnabled, authenticator.startBiometricUnlock())
    }

    @Test
    fun enabledFlag_isRestoredAfterRestart() = runTest {
        createAndEnable()

        val restarted = testAuthenticator(store, VaultSession(), keyStore)

        assertTrue(restarted.biometricEnabled.value)
        assertEquals(VaultState.Locked, restarted.state.value)
    }

    @Test
    fun failedEnrollment_leavesBiometricsOffAndDeletesKey() = runTest {
        authenticator.createVault(password.toCharArray())
        val cipher = authenticator.startBiometricEnrollment()
        authenticator.lock() // vault locked before the prompt finished

        assertFalse(authenticator.completeBiometricEnrollment(cipher))
        assertFalse(authenticator.biometricEnabled.value)
        assertNull(keyStore.key)
    }
}
