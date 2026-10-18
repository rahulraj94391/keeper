package io.github.rahulraj94391.keeper.testing

import io.github.rahulraj94391.keeper.data.auth.LocalVaultAuthenticator
import io.github.rahulraj94391.keeper.data.vault.InMemoryVaultMetadataStore
import io.github.rahulraj94391.keeper.data.vault.VaultMetadataStore
import io.github.rahulraj94391.keeper.security.Argon2Params
import io.github.rahulraj94391.keeper.security.VaultKeyManager
import io.github.rahulraj94391.keeper.security.UnlockThrottle
import io.github.rahulraj94391.keeper.security.VaultSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Smallest Argon2id cost Keeper accepts, so tests run fast. Production uses the defaults. */
val FAST_KDF_PARAMS = Argon2Params(memoryKiB = 19_456, iterations = 2)

fun testKeyManager() = VaultKeyManager(kdfParams = FAST_KDF_PARAMS)

/** The real authenticator, running everything immediately on the calling thread. */
fun testAuthenticator(
    store: VaultMetadataStore = InMemoryVaultMetadataStore(),
    session: VaultSession = VaultSession(),
    biometricKeyStore: FakeBiometricKeyStore = FakeBiometricKeyStore(),
    throttle: UnlockThrottle = testThrottle(),
) = LocalVaultAuthenticator(
    store = store,
    keyManager = testKeyManager(),
    session = session,
    biometricKeyStore = biometricKeyStore,
    unlockThrottle = throttle,
    appScope = CoroutineScope(Dispatchers.Unconfined),
    cryptoDispatcher = Dispatchers.Unconfined,
)
