package io.github.rahulraj94391.keeper.debug

import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.security.PasswordGenerator

/** Release builds ship no sample data; the Settings action is hidden. */
object SampleCredentials {
    const val AVAILABLE = false
    const val COUNT = 0

    @Suppress("UNUSED_PARAMETER")
    fun all(generator: PasswordGenerator = PasswordGenerator()): List<Credential> = emptyList()
}
