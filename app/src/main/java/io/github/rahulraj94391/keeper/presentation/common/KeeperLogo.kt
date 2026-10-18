package io.github.rahulraj94391.keeper.presentation.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.rahulraj94391.keeper.R

/** The shield-and-keyhole mark, reusing the themed-icon drawable. Decorative only. */
@Composable
fun KeeperLogo(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_launcher_monochrome),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(120.dp),
    )
}
