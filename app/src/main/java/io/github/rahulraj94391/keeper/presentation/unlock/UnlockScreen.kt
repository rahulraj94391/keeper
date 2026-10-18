package io.github.rahulraj94391.keeper.presentation.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rahulraj94391.keeper.R
import io.github.rahulraj94391.keeper.presentation.common.BiometricPromptText
import io.github.rahulraj94391.keeper.presentation.common.KeeperLogo
import io.github.rahulraj94391.keeper.presentation.common.MasterPasswordField
import io.github.rahulraj94391.keeper.presentation.common.rememberBiometricPrompt
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme
import kotlin.time.Duration

@Composable
fun UnlockScreen(viewModel: UnlockViewModel = viewModel(factory = UnlockViewModel.Factory)) {
    val uiState = viewModel.uiState
    val showPrompt = rememberBiometricPrompt(viewModel::onBiometricResult)
    val promptText = BiometricPromptText(
        title = stringResource(R.string.biometric_unlock_title),
        subtitle = stringResource(R.string.biometric_unlock_subtitle),
        negativeButton = stringResource(R.string.biometric_use_password),
    )

    LaunchedEffect(uiState.showBiometricButton) {
        if (uiState.showBiometricButton) viewModel.autoPromptOnce()
    }
    LaunchedEffect(uiState.biometricRequest) {
        uiState.biometricRequest?.let { cipher ->
            viewModel.onBiometricRequestShown()
            showPrompt(promptText, cipher)
        }
    }

    UnlockContent(
        uiState = uiState,
        onPasswordChange = viewModel::onPasswordChange,
        onUnlock = viewModel::unlock,
        onBiometricClick = viewModel::requestBiometric,
    )
}

@Composable
private fun UnlockContent(
    uiState: UnlockUiState,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onBiometricClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // Focus the field (opening the keyboard) only when the password is the way in: biometrics
    // are off, or the user just tried a password. With biometrics on, the keyboard would cover
    // the "Unlock with biometrics" button; tapping the field still opens it.
    // The field is disabled while unlocking, which drops focus, so this also restores it after
    // a failed attempt.
    LaunchedEffect(uiState.isUnlocking, uiState.showBiometricButton, uiState.isLockedOut) {
        val passwordIsPrimary = !uiState.showBiometricButton || uiState.showError
        if (!uiState.isUnlocking && !uiState.isLockedOut && passwordIsPrimary) focusRequester.requestFocus()
    }

    Scaffold { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            KeeperLogo()
            Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.unlock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            MasterPasswordField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.setup_master_password),
                enabled = !uiState.isUnlocking && !uiState.isLockedOut,
                errorText = unlockErrorText(uiState),
                onImeAction = onUnlock,
                modifier = Modifier.focusRequester(focusRequester),
            )
            Button(
                onClick = onUnlock,
                enabled = uiState.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isUnlocking) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.unlock_button))
                }
            }
            if (uiState.showBiometricButton) {
                OutlinedButton(
                    onClick = onBiometricClick,
                    enabled = !uiState.isUnlocking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.unlock_with_biometrics))
                }
            }
            uiState.biometricMessage?.let { message ->
                Text(
                    stringResource(
                        when (message) {
                            BiometricMessage.KeyInvalidated -> R.string.biometric_invalidated
                            BiometricMessage.Lockout -> R.string.biometric_lockout
                            BiometricMessage.Failed -> R.string.biometric_failed
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun unlockErrorText(uiState: UnlockUiState): String? {
    uiState.lockoutRemaining?.let { remaining ->
        val time = formatCountdown(remaining)
        return if (uiState.showBiometricButton) {
            stringResource(R.string.unlock_locked_out_biometric, time)
        } else {
            stringResource(R.string.unlock_locked_out, time)
        }
    }
    if (!uiState.showError) return null
    return uiState.attemptsBeforeDelay?.let {
        pluralStringResource(R.plurals.unlock_attempts_warning, it, it)
    } ?: stringResource(R.string.unlock_error)
}

/** "0:30", "12:05", "2:59:59". Rounds up, so it never shows 0:00 while still waiting. */
internal fun formatCountdown(remaining: Duration): String {
    val total = (remaining.inWholeMilliseconds + 999) / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun UnlockPreview() {
    KeeperTheme(dynamicColor = false) {
        UnlockContent(
            uiState = UnlockUiState(showError = true, showBiometricButton = true),
            onPasswordChange = {},
            onUnlock = {},
            onBiometricClick = {},
        )
    }
}
