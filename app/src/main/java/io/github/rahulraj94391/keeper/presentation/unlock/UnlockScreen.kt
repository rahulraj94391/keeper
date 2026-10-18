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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rahulraj94391.keeper.R
import io.github.rahulraj94391.keeper.presentation.common.KeeperLogo
import io.github.rahulraj94391.keeper.presentation.common.MasterPasswordField
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme

@Composable
fun UnlockScreen(viewModel: UnlockViewModel = viewModel(factory = UnlockViewModel.Factory)) {
    UnlockContent(
        uiState = viewModel.uiState,
        onPasswordChange = viewModel::onPasswordChange,
        onUnlock = viewModel::unlock,
    )
}

@Composable
private fun UnlockContent(
    uiState: UnlockUiState,
    onPasswordChange: (String) -> Unit,
    onUnlock: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // The field is disabled while unlocking, which drops focus; put it back after a failed
    // attempt so the user can type again straight away.
    LaunchedEffect(uiState.isUnlocking) {
        if (!uiState.isUnlocking) focusRequester.requestFocus()
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
                enabled = !uiState.isUnlocking,
                errorText = if (uiState.showError) stringResource(R.string.unlock_error) else null,
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
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UnlockPreview() {
    KeeperTheme(dynamicColor = false) {
        UnlockContent(uiState = UnlockUiState(showError = true), onPasswordChange = {}, onUnlock = {})
    }
}
