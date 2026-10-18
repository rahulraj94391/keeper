package io.github.rahulraj94391.keeper.presentation.common

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.rahulraj94391.keeper.KeeperApplication
import javax.crypto.Cipher

sealed interface BiometricPromptResult {
    /** The prompt succeeded; [cipher] is now authorized for one operation. */
    class Success(val cipher: Cipher) : BiometricPromptResult

    /** The user backed out; not an error. */
    data object Cancelled : BiometricPromptResult

    /** Too many failed attempts; biometrics are locked for a while. */
    data object Lockout : BiometricPromptResult

    data object Error : BiometricPromptResult
}

class BiometricPromptText(val title: String, val subtitle: String, val negativeButton: String)

/**
 * Returns a function that shows the system BiometricPrompt for [cipher] as a CryptoObject.
 * Only strong (Class 3) biometrics are allowed; device PIN/pattern is never offered (PRD 6.3).
 */
@Composable
fun rememberBiometricPrompt(onResult: (BiometricPromptResult) -> Unit): (BiometricPromptText, Cipher) -> Unit {
    val activity = LocalContext.current.findFragmentActivity()
    val autoLock = (activity.application as KeeperApplication).container.autoLock
    // Every result ends the prompt, so auto-lock goes back to normal before the caller reacts.
    val currentOnResult by rememberUpdatedState<(BiometricPromptResult) -> Unit> { result ->
        autoLock.onSystemAuthFinished()
        onResult(result)
    }

    val prompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher
                    currentOnResult(if (cipher != null) BiometricPromptResult.Success(cipher) else BiometricPromptResult.Error)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    currentOnResult(
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> BiometricPromptResult.Cancelled
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricPromptResult.Lockout
                            else -> BiometricPromptResult.Error
                        }
                    )
                }
                // onAuthenticationFailed (a finger that didn't match) is handled by the prompt itself.
            },
        )
    }

    return remember(prompt) {
        { text, cipher ->
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(text.title)
                .setSubtitle(text.subtitle)
                .setNegativeButtonText(text.negativeButton)
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build()
            autoLock.onSystemAuthStarted()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> error("BiometricPrompt needs a FragmentActivity")
}
