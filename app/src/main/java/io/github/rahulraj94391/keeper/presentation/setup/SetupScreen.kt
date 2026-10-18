package io.github.rahulraj94391.keeper.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rahulraj94391.keeper.R
import io.github.rahulraj94391.keeper.presentation.common.KeeperLogo
import io.github.rahulraj94391.keeper.presentation.common.MasterPasswordField
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme

@Composable
fun SetupScreen(viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory)) {
    SetupContent(
        uiState = viewModel.uiState,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmationChange = viewModel::onConfirmationChange,
        onAcknowledgedChange = viewModel::onAcknowledgedChange,
        onCreate = viewModel::createVault,
    )
}

@Composable
private fun SetupContent(
    uiState: SetupUiState,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onAcknowledgedChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    val minLength = SetupViewModel.MIN_PASSWORD_LENGTH
    val editable = !uiState.isCreating

    Scaffold { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            KeeperLogo()
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.setup_intro_local),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.setup_intro_master),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            MasterPasswordField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.setup_master_password),
                enabled = editable,
                errorText = uiState.passwordError?.let { pluralStringResource(R.plurals.setup_error_too_short, minLength, minLength) },
                supportingText = pluralStringResource(R.plurals.setup_hint, minLength, minLength),
                imeAction = ImeAction.Next,
            )
            StrengthMeter(uiState.strength, visible = uiState.password.isNotEmpty())
            MasterPasswordField(
                value = uiState.confirmation,
                onValueChange = onConfirmationChange,
                label = stringResource(R.string.setup_confirm_password),
                enabled = editable,
                errorText = uiState.confirmationError?.let { stringResource(R.string.setup_error_mismatch) },
                onImeAction = onCreate,
            )

            AcknowledgeRow(
                checked = uiState.acknowledged,
                onCheckedChange = onAcknowledgedChange,
                showError = uiState.acknowledgeError,
                enabled = editable,
            )

            Button(
                onClick = onCreate,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isCreating) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.setup_create))
                }
            }
        }
    }
}

@Composable
private fun StrengthMeter(strength: PasswordStrength, visible: Boolean) {
    if (!visible) return
    val colors = MaterialTheme.colorScheme
    val (fraction, color, labelRes) = when (strength) {
        PasswordStrength.Weak -> Triple(0.25f, colors.error, R.string.strength_weak)
        PasswordStrength.Fair -> Triple(0.5f, Color(0xFFB26A00), R.string.strength_fair)
        PasswordStrength.Strong -> Triple(0.75f, Color(0xFF2E7D32), R.string.strength_strong)
        PasswordStrength.VeryStrong -> Triple(1f, Color(0xFF1B5E20), R.string.strength_very_strong)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.setup_strength, stringResource(labelRes)),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AcknowledgeRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showError: Boolean,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.setup_acknowledge), style = MaterialTheme.typography.bodyMedium)
            if (showError) {
                Text(
                    stringResource(R.string.setup_error_acknowledge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupPreview() {
    KeeperTheme(dynamicColor = false) {
        SetupContent(
            uiState = SetupUiState(password = "correct horse battery", confirmation = "correct"),
            onPasswordChange = {},
            onConfirmationChange = {},
            onAcknowledgedChange = {},
            onCreate = {},
        )
    }
}
