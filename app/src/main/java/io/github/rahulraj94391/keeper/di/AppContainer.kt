package io.github.rahulraj94391.keeper.di

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.rahulraj94391.keeper.KeeperApplication
import io.github.rahulraj94391.keeper.data.AndroidSecretClipboard
import io.github.rahulraj94391.keeper.data.auth.BiometricAvailabilityChecker
import io.github.rahulraj94391.keeper.data.auth.LocalVaultAuthenticator
import io.github.rahulraj94391.keeper.data.local.KeeperDatabase
import io.github.rahulraj94391.keeper.data.repository.EncryptedCredentialRepository
import io.github.rahulraj94391.keeper.data.vault.RoomVaultMetadataStore
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.security.AndroidBiometricKeyStore
import io.github.rahulraj94391.keeper.security.AutoLockController
import io.github.rahulraj94391.keeper.security.ClipboardGuard
import io.github.rahulraj94391.keeper.security.VaultKeyManager
import io.github.rahulraj94391.keeper.security.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; one instance lives in [KeeperApplication]. */
class AppContainer(context: Context) {
    /** Lives as long as the process; for work that must not be cancelled with a screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = KeeperDatabase.create(context)

    /** Sole owner of the in-memory vault key. */
    val vaultSession = VaultSession()

    val vaultAuthenticator: VaultAuthenticator = LocalVaultAuthenticator(
        store = RoomVaultMetadataStore(database.vaultMetadataDao()),
        keyManager = VaultKeyManager(),
        session = vaultSession,
        biometricKeyStore = AndroidBiometricKeyStore(),
        appScope = appScope,
    )

    val biometricAvailability = BiometricAvailabilityChecker(context)

    /** Main-thread scope for timers that react to lifecycle and UI events. */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val autoLock = AutoLockController(vaultAuthenticator, mainScope)

    val clipboardGuard = ClipboardGuard(
        clipboard = AndroidSecretClipboard(context),
        scope = mainScope,
        now = SystemClock::elapsedRealtime,
    )

    val credentialRepository: CredentialRepository =
        EncryptedCredentialRepository(database.credentialDao(), vaultSession)
}

/** Looks up the app container from inside a `viewModelFactory { initializer { ... } }` block. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as KeeperApplication).container
