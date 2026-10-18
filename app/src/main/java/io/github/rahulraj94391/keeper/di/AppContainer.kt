package io.github.rahulraj94391.keeper.di

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.rahulraj94391.keeper.KeeperApplication
import io.github.rahulraj94391.keeper.data.auth.InMemoryVaultAuthenticator
import io.github.rahulraj94391.keeper.data.repository.InMemoryCredentialRepository
import io.github.rahulraj94391.keeper.data.repository.SampleCredentials
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator

/** Manual dependency container; one instance lives in [KeeperApplication]. */
class AppContainer {
    val vaultAuthenticator: VaultAuthenticator = InMemoryVaultAuthenticator()

    val credentialRepository: CredentialRepository =
        InMemoryCredentialRepository(SampleCredentials.all)
}

/** Looks up the app container from inside a `viewModelFactory { initializer { ... } }` block. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as KeeperApplication).container
