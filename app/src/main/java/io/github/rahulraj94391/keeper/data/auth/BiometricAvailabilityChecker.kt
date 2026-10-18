package io.github.rahulraj94391.keeper.data.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability

/** Asks the system whether strong (Class 3) biometrics can be used right now. */
class BiometricAvailabilityChecker(context: Context) {
    private val manager = BiometricManager.from(context.applicationContext)

    fun check(): BiometricAvailability = when (manager.canAuthenticate(BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NoHardware
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
        else -> BiometricAvailability.Unavailable
    }
}
