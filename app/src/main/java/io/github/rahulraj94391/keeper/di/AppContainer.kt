package io.github.rahulraj94391.keeper.di

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.rahulraj94391.keeper.KeeperApplication
import io.github.rahulraj94391.keeper.data.auth.MasterPasswordVaultAuthenticator
import io.github.rahulraj94391.keeper.data.repository.InMemoryCredentialRepository
import io.github.rahulraj94391.keeper.data.repository.SampleCredentials
import io.github.rahulraj94391.keeper.data.vault.InMemoryVaultMetadataStore
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.security.VaultKeyManager
import io.github.rahulraj94391.keeper.security.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; one instance lives in [KeeperApplication]. */
class AppContainer {
    /** Lives as long as the process; for work that must not be cancelled with a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Sole owner of the in-memory vault key. */
    val vaultSession = VaultSession()

    val vaultAuthenticator: VaultAuthenticator = MasterPasswordVaultAuthenticator(
        store = InMemoryVaultMetadataStore(),
        keyManager = VaultKeyManager(),
        session = vaultSession,
        appScope = appScope,
    )

    val credentialRepository: CredentialRepository =
        InMemoryCredentialRepository(SampleCredentials.all)
}

/** Looks up the app container from inside a `viewModelFactory { initializer { ... } }` block. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as KeeperApplication).container
