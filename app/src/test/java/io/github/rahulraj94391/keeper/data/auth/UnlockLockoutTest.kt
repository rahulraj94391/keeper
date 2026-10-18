package io.github.rahulraj94391.keeper.data.auth

import io.github.rahulraj94391.keeper.data.vault.InMemoryVaultMetadataStore
import io.github.rahulraj94391.keeper.domain.model.VaultState
import io.github.rahulraj94391.keeper.domain.repository.BiometricUnlockStart
import io.github.rahulraj94391.keeper.domain.repository.UnlockResult
import io.github.rahulraj94391.keeper.security.VaultSession
import io.github.rahulraj94391.keeper.testing.FakeBiometricKeyStore
import io.github.rahulraj94391.keeper.testing.FakeThrottleClock
import io.github.rahulraj94391.keeper.testing.testAuthenticator
import io.github.rahulraj94391.keeper.testing.testThrottle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class UnlockLockoutTest {

    private val password = "fake master password"
    private val clock = FakeThrottleClock()
    private val keyStore = FakeBiometricKeyStore()
    private val authenticator = testAuthenticator(InMemoryVaultMetadataStore(), VaultSession(), keyStore, testThrottle(clock))

    private suspend fun lockedVault(withBiometrics: Boolean = false) {
        authenticator.createVault(password.toCharArray())
        if (withBiometrics) authenticator.completeBiometricEnrollment(authenticator.startBiometricEnrollment())
        authenticator.lock()
    }

    private suspend fun wrong() = authenticator.unlock("wrong fake password".toCharArray())

    @Test
    fun warnings_thenAWaitOnTheFifthWrongPassword() = runTest {
        lockedVault()

        assertEquals(UnlockResult.WrongPassword(null, null), wrong())
        assertEquals(UnlockResult.WrongPassword(null, null), wrong())
        assertEquals(UnlockResult.WrongPassword(2, null), wrong())
        assertEquals(UnlockResult.WrongPassword(1, null), wrong())
        assertEquals(UnlockResult.WrongPassword(null, 30.seconds), wrong())
        assertEquals(30.seconds, authenticator.passwordLockoutRemaining())
    }

    @Test
    fun duringAWait_evenTheRightPasswordIsRefused() = runTest {
        lockedVault()
        repeat(5) { wrong() }
        clock.advance(10_000)

        assertEquals(UnlockResult.LockedOut(20.seconds), authenticator.unlock(password.toCharArray()))
        assertEquals(VaultState.Locked, authenticator.state.value)
    }

    @Test
    fun afterTheWait_theRightPasswordWorksAndResetsTheCount() = runTest {
        lockedVault()
        repeat(5) { wrong() }
        clock.advance(30_000)

        assertEquals(UnlockResult.Success, authenticator.unlock(password.toCharArray()))
        assertNull(authenticator.passwordLockoutRemaining())

        authenticator.lock()
        assertEquals(UnlockResult.WrongPassword(null, null), wrong()) // counting from 1 again
    }

    @Test
    fun aWrongAttemptAfterAWait_doublesIt() = runTest {
        lockedVault()
        repeat(5) { wrong() }
        clock.advance(30_000)

        assertEquals(UnlockResult.WrongPassword(null, 1.minutes), wrong())
    }

    @Test
    fun fingerprint_stillUnlocksDuringAWaitAndResetsTheCount() = runTest {
        lockedVault(withBiometrics = true)
        repeat(5) { wrong() }

        val start = authenticator.startBiometricUnlock()
        assertTrue(start is BiometricUnlockStart.Ready)
        assertTrue(authenticator.completeBiometricUnlock((start as BiometricUnlockStart.Ready).cipher))

        assertEquals(VaultState.Unlocked, authenticator.state.value)
        assertNull(authenticator.passwordLockoutRemaining())
    }
}
