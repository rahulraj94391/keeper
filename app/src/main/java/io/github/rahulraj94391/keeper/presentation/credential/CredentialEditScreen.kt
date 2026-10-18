package io.github.rahulraj94391.keeper.presentation.credential

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rahulraj94391.keeper.R
import io.github.rahulraj94391.keeper.presentation.common.PasswordVisibilityToggle
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme

@Composable
fun CredentialEditScreen(
    onDone: () -> Unit,
    viewModel: CredentialEditViewModel = viewModel(factory = CredentialEditViewModel.Factory),
) {
    val uiState = viewModel.uiState

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    CredentialEditContent(
        uiState = uiState,
        actions = CredentialEditActions(
            onTitleChange = viewModel::onTitleChange,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onUrlChange = viewModel::onUrlChange,
            onNotesChange = viewModel::onNotesChange,
            onFavoriteChange = viewModel::onFavoriteChange,
            onSave = viewModel::save,
            onClose = onDone,
            onOpenGenerator = viewModel::openGenerator,
        ),
        generatorActions = PasswordGeneratorActions(
            onLengthChange = viewModel::onGeneratorLengthChange,
            onGroupChange = viewModel::onGeneratorGroupChange,
            onRegenerate = viewModel::regeneratePassword,
            onUse = viewModel::useGeneratedPassword,
            onDismiss = viewModel::closeGenerator,
        ),
    )
}

private class CredentialEditActions(
    val onTitleChange: (String) -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onUrlChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onFavoriteChange: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onClose: () -> Unit,
    val onOpenGenerator: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialEditContent(
    uiState: CredentialEditUiState,
    actions: CredentialEditActions,
    generatorActions: PasswordGeneratorActions,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestClose = {
        if (uiState.hasUnsavedChanges) showDiscardDialog = true else actions.onClose()
    }
    BackHandler(enabled = uiState.hasUnsavedChanges) { showDiscardDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (uiState.isNew) R.string.edit_title_new else R.string.edit_title_existing))
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.Filled.Close, stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = actions.onSave, enabled = !uiState.isLoading) {
                        Text(stringResource(R.string.edit_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        CredentialForm(
            form = uiState.form,
            errors = uiState.errors,
            actions = actions,
            modifier = Modifier
                .padding(innerPadding)
                .imePadding(),
        )
    }

    uiState.generator?.let { PasswordGeneratorSheet(it, generatorActions) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    actions.onClose()
                }) { Text(stringResource(R.string.edit_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.edit_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun CredentialForm(
    form: CredentialForm,
    errors: CredentialFormErrors,
    actions: CredentialEditActions,
    modifier: Modifier = Modifier,
) {
    // Hidden by default; deliberately not saveable so it resets after recreation.
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val titleLabel = stringResource(R.string.field_title)
        val passwordLabel = stringResource(R.string.field_password)

        FormField(
            value = form.title,
            onValueChange = actions.onTitleChange,
            label = titleLabel,
            required = true,
            error = errors.title,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
        )
        FormField(
            value = form.username,
            onValueChange = actions.onUsernameChange,
            label = stringResource(R.string.field_username),
            error = errors.username,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        FormField(
            value = form.password,
            onValueChange = actions.onPasswordChange,
            label = passwordLabel,
            required = true,
            error = errors.password,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = if (passwordVisible) MonospaceStyle else null,
            trailingIcon = { PasswordVisibilityToggle(passwordVisible) { passwordVisible = !passwordVisible } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        TextButton(onClick = actions.onOpenGenerator) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.generator_open))
        }
        FormField(
            value = form.url,
            onValueChange = actions.onUrlChange,
            label = stringResource(R.string.field_url),
            error = errors.url,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        FormField(
            value = form.notes,
            onValueChange = actions.onNotesChange,
            label = stringResource(R.string.field_notes),
            error = errors.notes,
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.field_favorite),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = form.favorite, onCheckedChange = actions.onFavoriteChange)
        }
    }
}

private val MonospaceStyle = TextStyle(fontFamily = FontFamily.Monospace)

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: FieldError?,
    keyboardOptions: KeyboardOptions,
    required: Boolean = false,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val supportingText = when (error) {
        FieldError.Required -> stringResource(R.string.field_error_required, label)
        is FieldError.TooLong -> pluralStringResource(R.plurals.field_error_too_long, error.max, error.max)
        null -> if (required) stringResource(R.string.field_required) else null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        visualTransformation = visualTransformation,
        textStyle = textStyle?.let { MaterialTheme.typography.bodyLarge.merge(it) } ?: MaterialTheme.typography.bodyLarge,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun CredentialEditPreview() {
    KeeperTheme(dynamicColor = false) {
        CredentialEditContent(
            uiState = CredentialEditUiState(
                isNew = true,
                isLoading = false,
                form = CredentialForm(title = "Example Mail", username = "alex@example.com"),
                errors = CredentialFormErrors(password = FieldError.Required),
            ),
            actions = CredentialEditActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
            generatorActions = PasswordGeneratorActions({}, { _, _ -> }, {}, {}, {}),
        )
    }
}
