package io.github.rahulraj94391.keeper.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rahulraj94391.keeper.BuildConfig
import io.github.rahulraj94391.keeper.R
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.presentation.common.BiometricPromptText
import io.github.rahulraj94391.keeper.presentation.common.MasterPasswordField
import io.github.rahulraj94391.keeper.presentation.common.rememberBiometricPrompt
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState = viewModel.uiState
    val showPrompt = rememberBiometricPrompt(viewModel::onBiometricResult)
    val promptText = BiometricPromptText(
        title = stringResource(R.string.biometric_enable_title),
        subtitle = stringResource(R.string.biometric_enable_subtitle),
        negativeButton = stringResource(R.string.action_cancel),
    )
    LaunchedEffect(uiState.biometricRequest) {
        uiState.biometricRequest?.let { cipher ->
            viewModel.onBiometricRequestShown()
            showPrompt(promptText, cipher)
        }
    }
    // The user may have just enrolled a fingerprint in system settings.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshBiometricAvailability()
        onPauseOrDispose { }
    }

    SettingsContent(
        uiState = uiState,
        actions = SettingsActions(
            onBack = onBack,
            onBiometricToggle = viewModel::onBiometricToggle,
            onBiometricMessageShown = viewModel::onBiometricMessageShown,
            onLockNow = viewModel::lockNow,
            onExport = viewModel::onExportClick,
            onRestore = viewModel::onRestoreClick,
            onRestoreConfirmed = viewModel::onRestoreConfirmed,
            onLimitations = viewModel::onLimitationsClick,
            onDismissDialog = viewModel::dismissDialog,
            onPassphraseChange = viewModel::onPassphraseChange,
            onConfirmationChange = viewModel::onConfirmationChange,
            onSubmitExport = viewModel::submitExport,
            onSubmitRestore = viewModel::submitRestore,
            onBackupUnavailableShown = viewModel::onBackupUnavailableShown,
        ),
    )
}

private class SettingsActions(
    val onBack: () -> Unit,
    val onBiometricToggle: (Boolean) -> Unit,
    val onBiometricMessageShown: () -> Unit,
    val onLockNow: () -> Unit,
    val onExport: () -> Unit,
    val onRestore: () -> Unit,
    val onRestoreConfirmed: () -> Unit,
    val onLimitations: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onPassphraseChange: (String) -> Unit,
    val onConfirmationChange: (String) -> Unit,
    val onSubmitExport: () -> Unit,
    val onSubmitRestore: () -> Unit,
    val onBackupUnavailableShown: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(uiState: SettingsUiState, actions: SettingsActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(uiState.showBackupUnavailable) {
        if (uiState.showBackupUnavailable) {
            snackbarHostState.showSnackbar(resources.getString(R.string.settings_backup_unavailable))
            actions.onBackupUnavailableShown()
        }
    }
    LaunchedEffect(uiState.biometricMessage) {
        val message = uiState.biometricMessage ?: return@LaunchedEffect
        actions.onBiometricMessageShown()
        snackbarHostState.showSnackbar(
            resources.getString(
                when (message) {
                    BiometricSettingsMessage.Enabled -> R.string.biometric_enabled_message
                    BiometricSettingsMessage.EnableFailed -> R.string.biometric_enable_failed
                    BiometricSettingsMessage.Lockout -> R.string.biometric_lockout
                }
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(R.string.settings_section_security)
            BiometricRow(uiState, actions.onBiometricToggle)
            SettingsRow(R.string.settings_lock_now, R.string.settings_lock_now_summary, actions.onLockNow)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(R.string.settings_section_backup)
            SettingsRow(R.string.settings_export, R.string.settings_export_summary, actions.onExport)
            SettingsRow(R.string.settings_restore, R.string.settings_restore_summary, actions.onRestore)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(R.string.settings_section_about)
            SettingsRow(R.string.settings_limitations, R.string.settings_limitations_summary, actions.onLimitations)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(BuildConfig.VERSION_NAME) },
            )
        }
    }

    when (uiState.dialog) {
        SettingsDialog.ExportPassphrase -> ExportPassphraseDialog(uiState.backupForm, actions)
        SettingsDialog.RestoreConfirm -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = actions.onRestoreConfirmed) {
                    Text(stringResource(R.string.backup_restore_continue), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { CancelButton(actions.onDismissDialog) },
        )
        SettingsDialog.RestorePassphrase -> RestorePassphraseDialog(uiState.backupForm, actions)
        SettingsDialog.Limitations -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text(stringResource(R.string.settings_limitations)) },
            text = {
                Text(
                    stringResource(R.string.limitations_body),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onDismissDialog) { Text(stringResource(R.string.action_ok)) }
            },
        )
        null -> Unit
    }
}

@Composable
private fun ExportPassphraseDialog(form: BackupPassphraseForm, actions: SettingsActions) {
    val minLength = SettingsViewModel.MIN_PASSPHRASE_LENGTH
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(stringResource(R.string.backup_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_export_body))
                MasterPasswordField(
                    value = form.passphrase,
                    onValueChange = actions.onPassphraseChange,
                    label = stringResource(R.string.backup_passphrase),
                    errorText = passphraseErrorText(form.passphraseError, minLength),
                    imeAction = ImeAction.Next,
                )
                MasterPasswordField(
                    value = form.confirmation,
                    onValueChange = actions.onConfirmationChange,
                    label = stringResource(R.string.backup_confirm_passphrase),
                    errorText = passphraseErrorText(form.confirmationError, minLength),
                    onImeAction = actions.onSubmitExport,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSubmitExport) { Text(stringResource(R.string.backup_export_confirm)) }
        },
        dismissButton = { CancelButton(actions.onDismissDialog) },
    )
}

@Composable
private fun RestorePassphraseDialog(form: BackupPassphraseForm, actions: SettingsActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_restore_body))
                MasterPasswordField(
                    value = form.passphrase,
                    onValueChange = actions.onPassphraseChange,
                    label = stringResource(R.string.backup_passphrase),
                    errorText = passphraseErrorText(form.passphraseError, 0),
                    onImeAction = actions.onSubmitRestore,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSubmitRestore) { Text(stringResource(R.string.backup_restore_button)) }
        },
        dismissButton = { CancelButton(actions.onDismissDialog) },
    )
}

@Composable
private fun passphraseErrorText(error: PassphraseError?, minLength: Int): String? = when (error) {
    PassphraseError.Required -> stringResource(R.string.backup_error_required)
    PassphraseError.TooShort -> pluralStringResource(R.plurals.setup_error_too_short, minLength, minLength)
    PassphraseError.Mismatch -> stringResource(R.string.backup_error_mismatch)
    null -> null
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.action_cancel)) }
}

@Composable
private fun SectionHeader(textRes: Int) {
    Text(
        stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun BiometricRow(uiState: SettingsUiState, onToggle: (Boolean) -> Unit) {
    // Turning it off must always be possible, even if the sensor became unavailable.
    val enabled = uiState.biometricEnabled || uiState.biometricAvailability == BiometricAvailability.Available
    val summary = when {
        uiState.biometricEnabled -> R.string.settings_biometric_summary
        else -> when (uiState.biometricAvailability) {
            BiometricAvailability.Available -> R.string.settings_biometric_summary
            BiometricAvailability.NotEnrolled -> R.string.settings_biometric_not_enrolled
            BiometricAvailability.NoHardware -> R.string.settings_biometric_no_hardware
            BiometricAvailability.Unavailable -> R.string.settings_biometric_unavailable
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_biometric)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = uiState.biometricEnabled, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = uiState.biometricEnabled,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onToggle,
        ),
    )
}

@Composable
private fun SettingsRow(titleRes: Int, summaryRes: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(stringResource(summaryRes)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    KeeperTheme(dynamicColor = false) {
        SettingsContent(
            uiState = SettingsUiState(),
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
