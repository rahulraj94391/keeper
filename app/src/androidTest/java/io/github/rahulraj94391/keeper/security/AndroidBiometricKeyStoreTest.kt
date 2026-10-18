package io.github.rahulraj94391.keeper.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rahulraj94391.keeper.data.auth.BiometricAvailabilityChecker
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.IllegalBlockSizeException

/**
 * Real Android Keystore. Needs a device with a strong biometric enrolled; skipped otherwise.
 * It can't pass a BiometricPrompt, so it checks that the key refuses to work without one.
 */
@RunWith(AndroidJUnit4::class)
class AndroidBiometricKeyStoreTest {

    private val keyStore = AndroidBiometricKeyStore("keeper_test_biometric_key")

    @After
    fun cleanUp() = keyStore.deleteKey()

    private fun assumeBiometricsEnrolled() {
        val availability = BiometricAvailabilityChecker(ApplicationProvider.getApplicationContext()).check()
        assumeTrue("needs an enrolled strong biometric", availability == BiometricAvailability.Available)
    }

    @Test
    fun keyCannotEncryptWithoutAuthentication() {
        assumeBiometricsEnrolled()
        val cipher = keyStore.newEncryptCipher()

        // Using the cipher without a successful BiometricPrompt must fail.
        assertThrows(IllegalBlockSizeException::class.java) { cipher.doFinal(ByteArray(32)) }
    }

    @Test
    fun deletedKey_reportsInvalidated() {
        assumeBiometricsEnrolled()
        val iv = keyStore.newEncryptCipher().iv
        keyStore.deleteKey()

        assertThrows(BiometricKeyInvalidatedException::class.java) { keyStore.decryptCipher(iv) }
    }

    @Test
    fun keystoreNonceIs12Bytes() {
        assumeBiometricsEnrolled()

        assertEquals(12, keyStore.newEncryptCipher().iv.size)
    }
}
