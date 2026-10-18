package io.github.rahulraj94391.keeper.testing

import io.github.rahulraj94391.keeper.data.backup.VaultBackupService
import io.github.rahulraj94391.keeper.data.repository.InMemoryCredentialRepository
import io.github.rahulraj94391.keeper.domain.model.BiometricAvailability
import io.github.rahulraj94391.keeper.domain.repository.AutoLockPreferences
import io.github.rahulraj94391.keeper.domain.repository.CredentialRepository
import io.github.rahulraj94391.keeper.domain.repository.VaultAuthenticator
import io.github.rahulraj94391.keeper.presentation.settings.SettingsViewModel
import io.github.rahulraj94391.keeper.security.BackupCrypto
import io.github.rahulraj94391.keeper.security.VaultSession
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

fun testBackupService(
    repository: CredentialRepository,
    session: VaultSession,
    files: FakeBackupFiles = FakeBackupFiles(),
) = VaultBackupService(repository, session, files, BackupCrypto(kdfParams = FAST_KDF_PARAMS), Dispatchers.Unconfined)

fun testSettingsViewModel(
    authenticator: VaultAuthenticator,
    availability: () -> BiometricAvailability = { BiometricAvailability.Available },
    preferences: AutoLockPreferences = FakeAutoLockPreferences(),
    repository: CredentialRepository = InMemoryCredentialRepository(),
    backupService: VaultBackupService = testBackupService(repository, VaultSession()),
) = SettingsViewModel(authenticator, availability, preferences, repository, backupService) { LocalDate.of(2026, 10, 18) }
