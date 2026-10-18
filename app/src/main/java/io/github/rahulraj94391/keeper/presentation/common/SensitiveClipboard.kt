package io.github.rahulraj94391.keeper.presentation.common

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry

/**
 * Builds a clipboard entry for a secret. On Android 13+ the sensitive flag hides the value
 * from the system's clipboard preview. Auto-clearing after 30 seconds comes in a later phase.
 */
fun sensitiveClipEntry(label: String, value: String): ClipEntry {
    val clip = ClipData.newPlainText(label, value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    return ClipEntry(clip)
}

/** Android 13+ shows its own confirmation when something is copied, so the app only needs one below that. */
val systemShowsCopyConfirmation: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
