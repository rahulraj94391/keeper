package io.github.rahulraj94391.keeper.data

import android.content.Context
import androidx.core.content.edit
import io.github.rahulraj94391.keeper.domain.model.AutoLockTimeout
import io.github.rahulraj94391.keeper.domain.repository.AutoLockPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stores the auto-lock choice in SharedPreferences; excluded from backup like all app data. */
class SharedPrefsAutoLockPreferences(context: Context) : AutoLockPreferences {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _timeout = MutableStateFlow(read())
    override val timeout: StateFlow<AutoLockTimeout> = _timeout.asStateFlow()

    override fun setTimeout(timeout: AutoLockTimeout) {
        prefs.edit { putString(KEY_TIMEOUT, timeout.name) }
        _timeout.value = timeout
    }

    // An unknown or missing value falls back to the most secure default.
    private fun read(): AutoLockTimeout = prefs.getString(KEY_TIMEOUT, null)
        ?.let { name -> AutoLockTimeout.entries.firstOrNull { it.name == name } }
        ?: AutoLockTimeout.DEFAULT

    private companion object {
        const val FILE = "keeper_settings"
        const val KEY_TIMEOUT = "auto_lock_timeout"
    }
}
