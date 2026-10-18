package io.github.rahulraj94391.keeper.testing

import io.github.rahulraj94391.keeper.domain.model.AutoLockTimeout
import io.github.rahulraj94391.keeper.domain.repository.AutoLockPreferences
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAutoLockPreferences(initial: AutoLockTimeout = AutoLockTimeout.DEFAULT) : AutoLockPreferences {
    override val timeout = MutableStateFlow(initial)

    override fun setTimeout(timeout: AutoLockTimeout) {
        this.timeout.value = timeout
    }
}
