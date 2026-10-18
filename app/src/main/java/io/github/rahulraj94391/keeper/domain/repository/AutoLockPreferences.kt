package io.github.rahulraj94391.keeper.domain.repository

import io.github.rahulraj94391.keeper.domain.model.AutoLockTimeout
import kotlinx.coroutines.flow.StateFlow

/** The user's auto-lock choice. Not secret. */
interface AutoLockPreferences {
    val timeout: StateFlow<AutoLockTimeout>

    fun setTimeout(timeout: AutoLockTimeout)
}
