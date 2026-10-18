package io.github.rahulraj94391.keeper.presentation.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.rahulraj94391.keeper.R
import io.github.rahulraj94391.keeper.ui.theme.KeeperTheme

@Composable
fun VaultListScreen(
    onAddClick: () -> Unit,
    onCredentialClick: (id: String) -> Unit,
    onLockClick: () -> Unit,
    viewModel: VaultListViewModel = viewModel(factory = VaultListViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VaultListContent(
        uiState = uiState,
        query = viewModel.query,
        onQueryChange = viewModel::onQueryChange,
        onAddClick = onAddClick,
        onCredentialClick = onCredentialClick,
        onLockClick = onLockClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultListContent(
    uiState: VaultListUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onCredentialClick: (id: String) -> Unit,
    onLockClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onLockClick) {
                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.vault_lock))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.vault_add_credential)) },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                uiState.isLoading -> CenteredBox { CircularProgressIndicator() }
                uiState.vaultIsEmpty -> EmptyMessage(
                    title = stringResource(R.string.vault_empty_title),
                    body = stringResource(R.string.vault_empty_body),
                )
                uiState.items.isEmpty() -> EmptyMessage(
                    title = stringResource(R.string.vault_no_results, query.trim()),
                    body = null,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp), // keep last row clear of the FAB
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        CredentialRow(item = item, onClick = { onCredentialClick(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.vault_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.vault_search_clear))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun CredentialRow(item: VaultListItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { InitialAvatar(item.title) },
        trailingContent = if (item.favorite) {
            {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(R.string.vault_favorite),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun InitialAvatar(title: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyMessage(title: String, body: String?) {
    CenteredBox {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Preview(showBackground = true)
@Composable
private fun VaultListPreview() {
    KeeperTheme(dynamicColor = false) {
        VaultListContent(
            uiState = VaultListUiState(
                isLoading = false,
                items = listOf(
                    VaultListItem("1", "Example Mail", favorite = true),
                    VaultListItem("2", "Demo Bank", favorite = false),
                ),
            ),
            query = "",
            onQueryChange = {},
            onAddClick = {},
            onCredentialClick = {},
            onLockClick = {},
        )
    }
}
