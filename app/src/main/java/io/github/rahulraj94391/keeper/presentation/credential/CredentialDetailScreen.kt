package io.github.rahulraj94391.keeper.presentation.credential

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rahulraj94391.keeper.R
import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.presentation.common.PasswordVisibilityToggle
import io.github.rahulraj94391.keeper.presentation.common.sensitiveClipEntry
import io.github.rahulraj94391.keeper.presentation.common.systemShowsCopyConfirmation
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme
import kotlinx.coroutines.launch

private const val PASSWORD_MASK = "••••••••••"

@Composable
fun CredentialDetailScreen(
    onBack: () -> Unit,
    onEdit: (id: String) -> Unit,
    viewModel: CredentialDetailViewModel = viewModel(factory = CredentialDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Leave once the record is gone, either because it was deleted here or never existed.
    LaunchedEffect(uiState) {
        if (uiState is CredentialDetailUiState.NotFound) onBack()
    }

    val credential = (uiState as? CredentialDetailUiState.Loaded)?.credential
    CredentialDetailContent(
        credential = credential,
        onBack = onBack,
        onEdit = { credential?.let { onEdit(it.id) } },
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialDetailContent(
    credential: Credential?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val resources = LocalResources.current
    val copy: (label: String, value: String) -> Unit = { label, value ->
        scope.launch {
            clipboard.setClipEntry(sensitiveClipEntry(label, value))
            if (!systemShowsCopyConfirmation) snackbarHostState.showSnackbar(resources.getString(R.string.detail_copied, label))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(credential?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (credential != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.detail_edit))
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.detail_delete))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (credential == null) return@Scaffold

        // Reset to hidden whenever a different record is shown. Deliberately not saveable.
        var passwordVisible by remember(credential.id) { mutableStateOf(false) }

        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val usernameLabel = stringResource(R.string.field_username)
            val passwordLabel = stringResource(R.string.field_password)

            if (credential.username.isNotEmpty()) {
                DetailField(label = usernameLabel, value = credential.username) {
                    CopyButton(usernameLabel) { copy(usernameLabel, credential.username) }
                }
            }
            DetailField(
                label = passwordLabel,
                value = if (passwordVisible) credential.password else PASSWORD_MASK,
                monospace = passwordVisible,
            ) {
                PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible }
                CopyButton(passwordLabel) { copy(passwordLabel, credential.password) }
            }
            if (credential.url.isNotEmpty()) {
                DetailField(label = stringResource(R.string.field_url), value = credential.url)
            }
            if (credential.notes.isNotEmpty()) {
                DetailField(label = stringResource(R.string.field_notes), value = credential.notes, singleLine = false)
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.detail_delete_title, credential.title)) },
                text = { Text(stringResource(R.string.detail_delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }) {
                        Text(stringResource(R.string.detail_delete_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    monospace: Boolean = false,
    singleLine: Boolean = true,
    actions: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions()
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun CopyButton(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_content_copy), stringResource(R.string.action_copy, label))
    }
}

@Preview(showBackground = true)
@Composable
private fun CredentialDetailPreview() {
    KeeperTheme(dynamicColor = false) {
        CredentialDetailContent(
            credential = Credential(
                id = "1",
                title = "Example Mail",
                username = "alex@example.com",
                password = "sample-password",
                url = "https://mail.example.com",
                notes = "Preview only.",
            ),
            onBack = {},
            onEdit = {},
            onDelete = {},
        )
    }
}
