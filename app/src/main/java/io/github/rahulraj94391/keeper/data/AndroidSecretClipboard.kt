package io.github.rahulraj94391.keeper.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import io.github.rahulraj94391.keeper.security.SecretClipboard

class AndroidSecretClipboard(context: Context) : SecretClipboard {
    private val manager = context.getSystemService(ClipboardManager::class.java)

    override fun put(id: String, label: String, text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            putString(EXTRA_CLIP_ID, id)
            // Android 13+ hides sensitive clips from the clipboard preview.
            if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
    }

    override fun currentClipId(): String? = manager.primaryClipDescription?.extras?.getString(EXTRA_CLIP_ID)

    override fun clear() = manager.clearPrimaryClip()

    private companion object {
        const val EXTRA_CLIP_ID = "io.github.rahulraj94391.keeper.CLIP_ID"
    }
}
