package io.github.rahulraj94391.keeper.presentation.credential

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.rahulraj94391.keeper.R
import io.github.rahulraj94391.keeper.security.CharacterGroup
import io.github.rahulraj94391.keeper.security.PasswordGenerator
import kotlin.math.roundToInt

class PasswordGeneratorActions(
    val onLengthChange: (Int) -> Unit,
    val onGroupChange: (CharacterGroup, Boolean) -> Unit,
    val onRegenerate: () -> Unit,
    val onUse: () -> Unit,
    val onDismiss: () -> Unit,
    val onCopy: (String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorSheet(state: GeneratorUiState, actions: PasswordGeneratorActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PasswordGeneratorContent(state, actions)
    }
}

@Composable
private fun PasswordGeneratorContent(state: GeneratorUiState, actions: PasswordGeneratorActions) {
    val passwordLabel = stringResource(R.string.field_password)
    val range = PasswordGenerator.LENGTH_RANGE

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(stringResource(R.string.generator_title), style = MaterialTheme.typography.titleLarge)

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    state.password,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
                IconButton(onClick = actions.onRegenerate) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.generator_regenerate))
                }
                IconButton(onClick = { actions.onCopy(state.password) }) {
                    Icon(
                        painterResource(R.drawable.ic_content_copy),
                        stringResource(R.string.action_copy, passwordLabel),
                    )
                }
            }
        }

        Text(stringResource(R.string.generator_length, state.options.length), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.options.length.toFloat(),
            onValueChange = { actions.onLengthChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )

        val groups = state.options.groups
        GroupSwitch(R.string.generator_lowercase, CharacterGroup.Lowercase, groups, actions.onGroupChange)
        GroupSwitch(R.string.generator_uppercase, CharacterGroup.Uppercase, groups, actions.onGroupChange)
        GroupSwitch(R.string.generator_digits, CharacterGroup.Digits, groups, actions.onGroupChange)
        GroupSwitch(R.string.generator_symbols, CharacterGroup.Symbols, groups, actions.onGroupChange)

        Button(onClick = actions.onUse, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.generator_use))
        }
    }
}

@Composable
private fun GroupSwitch(
    labelRes: Int,
    group: CharacterGroup,
    selected: Set<CharacterGroup>,
    onChange: (CharacterGroup, Boolean) -> Unit,
) {
    val checked = group in selected
    // The last remaining group can't be switched off.
    val enabled = !(checked && selected.size == 1)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch) { onChange(group, it) },
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
