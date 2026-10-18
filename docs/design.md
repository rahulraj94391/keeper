# Keeper design note

How Keeper implements the security requirements in
[offline_password_manager_prd.md](offline_password_manager_prd.md). Sections are added as each
phase lands; anything not written here yet is not implemented yet.

## 1. Cryptographic primitives

Code: `app/src/main/java/io/github/rahulraj94391/keeper/security/`.

### 1.1 Argon2id (key derivation)

**Library:** Bouncy Castle `org.bouncycastle:bcprov-jdk18on:1.86`, class
`org.bouncycastle.crypto.generators.Argon2BytesGenerator`.

Why this one (checked October 2026):

| Option | Latest release | Notes |
|---|---|---|
| **Bouncy Castle bcprov-jdk18on** | 1.86, September 2026 | Chosen. Long-running project with regular releases. Pure Java, so no native libraries per CPU architecture. Bouncy Castle Licence (MIT-style). |
| argon2kt (`com.lambdapioneer.argon2kt`) | 1.6.0, September 2024 | Native and fast, but no release in two years. MIT. |
| lazysodium-android (libsodium) | 5.2.0, May 2025 | Native via JNA; adds JNA and libsodium binaries. |

Trade-off: pure Java Argon2 is slower than native code, so the same parameters cost more unlock
time. Section 1.4 records measured times.

Release builds let R8 shrink `org.bouncycastle.**` (listed in `packageScope` in
`app/build.gradle.kts`). Keeper calls the Argon2 classes directly and never registers Bouncy
Castle as a JCA provider, so no keep rules are needed: only 3 Bouncy Castle classes remain in the
release APK, and its size dropped from 8.8 MB to 3.3 MB.

Android has its own internal copy of Bouncy Castle under `com.android.org.bouncycastle`; the app's
copy uses the normal `org.bouncycastle` package, so they don't conflict.

**Parameters** (persisted alongside every derived-key use, PRD 7.2):

| Parameter | Default | Accepted range |
|---|---|---|
| Algorithm | Argon2id | Argon2id only |
| Version | 0x13 (1.3) | 0x13 only |
| Memory | 65,536 KiB (64 MiB) | 19,456–262,144 KiB (19–256 MiB) |
| Iterations | 3 | 2–10 |
| Parallelism | 1 | 1–4 |
| Output | 32 bytes | 32 bytes only |
| Salt | 16 random bytes | 16–64 bytes |

Parameters are checked against these ranges **before** Argon2id runs, so a tampered vault or
backup file can't make the app allocate gigabytes or spin for minutes. The lower bounds follow
OWASP's minimum Argon2id recommendation (19 MiB, 2 iterations).

**Password input:** `MasterPasswordEncoding` (Unicode NFC, then UTF-8, no trimming) produces the
bytes passed to Argon2id, for both create and unlock.

### 1.2 AES-256-GCM (authenticated encryption)

Uses the platform `javax.crypto` provider, `AES/GCM/NoPadding`.

- Key: exactly 32 bytes.
- Nonce: 12 random bytes from `SecureRandom`, new for every encryption. The caller never supplies
  a nonce, so nonce reuse under one key can't happen through this API.
- Tag: 128 bits, appended to the ciphertext.
- Associated data (AAD): required on every call, chosen by the caller to bind context such as
  format version, record id and schema version (PRD 6.4, 7.3).
- Output: a `SealedBox(nonce, ciphertextWithTag)`; both parts are stored.

Any decryption problem (wrong key, changed ciphertext, tag, nonce or AAD) throws one exception
type, `DecryptionFailedException`, with no detail, and no partial plaintext is ever returned.

### 1.3 Memory hygiene

Keys and passwords are `ByteArray`/`CharArray` and are wiped with `fill(0)` once used, where the
code owns the array. JVM limits apply: the garbage collector may have moved or copied arrays,
`String`s can't be wiped, and the JCA provider keeps its own key copies. Wiping is best effort.

### 1.4 Argon2id timing

Measured with `Argon2idBenchmarkTest` (instrumented test, default parameters):

| Device | Build | Runs (ms) | Median |
|---|---|---|---|
| Emulator, Pixel 9 Pro image (arm64-v8a), Apple Silicon host | debug | 1128, 1245, 1286, 1399, 1435 | 1.29 s |
| Samsung Galaxy S21 FE (SM-G990E), Android 16 | debug | 929, 941, 961, 987, 1041 | 0.96 s |

The emulator runs on a desktop CPU, and debug builds aren't optimized by R8. PRD 12 asks for
0.5–1.5 s on a mid-range device. The emulator result is already near the top of that range, so a
pure-Java Argon2id at 64 MiB / 3 iterations will likely be slower than 1.5 s on mid-range phones.
Before tuning, measure a release build on real low-, mid- and high-range devices. If it's too
slow, the options are lowering iterations to 2 (keeping 64 MiB), or switching to a native Argon2
library; record the decision and its reasoning here either way.

Run it with:

```bash
./gradlew connectedDebugAndroidTest
adb logcat -d -s Argon2idBenchmark:I
```

## 2. Master-password key wrapping and vault session

Code: `security/VaultKeyManager.kt`, `security/VaultSession.kt`,
`data/auth/LocalVaultAuthenticator.kt`.

### 2.1 Key hierarchy (so far)

1. **Vault encryption key (VEK):** 32 random bytes from `SecureRandom`, made once per vault. It
   will encrypt every credential record (next phase).
2. **Master key-encryption key (KEK):** Argon2id(master password, 16-byte random salt, stored
   parameters). Used only to wrap and unwrap the VEK, then wiped.

The VEK is not derived from the password, so changing the master password later only re-wraps
the VEK instead of re-encrypting every record, and biometric unlock can wrap the same VEK
independently.

### 2.2 Stored metadata

`VaultKeyMetadata`, saved as one record so setup is all-or-nothing:

| Field | Value |
|---|---|
| `schemaVersion` | 1 |
| `kdfAlgorithm` | `argon2id` |
| `kdfParams` | memory, iterations, parallelism, output length, version (section 1.1) |
| `masterSalt` | 16 random bytes |
| `wrapAlgorithm` | `aes-256-gcm` |
| `masterWrappedVek` | nonce + VEK ciphertext and tag |

**Associated data for the wrap** is a canonical string of the schema version, every KDF setting
and the salt (hex), for example
`keeper/master-wrapped-vek/v1;kdf=argon2id;v=19;m=65536;t=3;p=1;len=32;salt=…`. Editing any stored
setting therefore makes unwrapping fail, rather than quietly deriving a different key.

**Unlock** rejects an unknown schema version or algorithm, validates the KDF settings against
section 1.1 before running Argon2id, then tries to decrypt. A wrong password, tampered metadata
and unsupported settings all return the same "couldn't unlock" result.

**Storage:** the single row of the Room table `vault_metadata` (section 3).

### 2.3 Vault session

`VaultSession` is the only holder of the VEK while unlocked. Code reads it only inside
`withKey { }`; outside a session that call throws `VaultLockedException`. Locking calls `close()`,
which overwrites the key array. The key is never put in UI state, saved state, navigation
arguments or logs.

### 2.4 Threading

Create and unlock each run Argon2id once, on `Dispatchers.Default`, never the main thread. A
mutex makes sure repeated taps can't start two runs at once. While the app checks stored data at
start-up, the vault state is `Loading` and a blank screen is shown. Create and unlock wait for
that check to finish rather than acting on the `Loading` state.

## 3. Encrypted storage (Room)

Code: `data/local/`, `data/vault/RoomVaultMetadataStore.kt`,
`data/repository/EncryptedCredentialRepository.kt`, `data/repository/CredentialPayloadCodec.kt`.

Database file `keeper_vault.db`, schema version 2 (version 1 plus the biometric columns, added by
a Room auto-migration that `MigrationTest` checks keeps an existing vault unlockable). Exported schemas live in `app/schemas/` and are
committed so later migrations can be written and tested. There is no destructive-migration
fallback: a failed migration must never wipe the vault. The file is excluded from Android
backup and device transfer (`data_extraction_rules.xml`).

### 3.1 Tables

| Table | Columns | Readable without the vault key |
|---|---|---|
| `vault_metadata` | one row: section 2.2 fields, plus section 4.2 biometric fields | KDF settings, salt, algorithm names, whether biometrics are on, key alias |
| `credentials` | `id`, `payload_schema_version`, `nonce`, `ciphertext` | the random record id (UUID) only |

No title, username, password, URL or notes are stored in plain columns, and there is no plaintext
search index. Search runs over records decrypted in memory while the vault is unlocked.

### 3.2 Credential records

- **Payload:** strict JSON `{title, username, password, url, notes, favorite}`, schema version 1.
  Unknown or missing fields, blank titles, empty passwords and over-limit fields are rejected
  on read.
- **Encryption:** AES-256-GCM under the VEK, fresh nonce per save.
- **Associated data:** `keeper/credential/v1;id=<record id>`, so a ciphertext copied onto another
  row (or to another schema version) fails to decrypt.
- **Size bound:** ciphertexts over 64 KiB are rejected before decryption.

### 3.3 Behavior

- **Locked:** reads return an empty list or null; saves and deletes throw `VaultLockedException`.
- **Corrupted record:** a row that fails to decrypt or validate is left out of the list instead of
  being shown partly read. The list doesn't yet tell the user that something was skipped.
- **Deleting** removes the row. SQLite may keep old pages or journal data on flash; the PRD does
  not promise physical erasure (PRD 3, 6.4).
- **Limitations:** JSON encoding goes through immutable `String`s, so decrypted text can't be
  fully wiped from memory; the plaintext byte arrays are wiped after use.

### 3.4 Tests

- JVM: codec round trip and rejection cases; repository with a fake DAO covering round trip, no
  plaintext in rows, locked behavior, moved/tampered/unsupported rows, wrong vault key.
- Instrumented (real Room, `EncryptedStorageTest`): vault survives a simulated restart and
  unlocks only with the right password; a raw dump of the tables contains no plaintext; delete
  removes the row.

## 4. Biometric unlock

Code: `security/BiometricKeyStore.kt`, `data/auth/LocalVaultAuthenticator.kt`,
`presentation/common/BiometricPromptLauncher.kt`.

**Library:** `androidx.biometric:biometric:1.1.0`, the latest stable release (1.2 and 1.4 are
still alpha). It needs a `FragmentActivity`, so `MainActivity` extends one, and Fragment is pinned
to 1.9.1 instead of the 1.2.5 that Biometric 1.1.0 asks for.

### 4.1 Keystore key

A separate AES-256-GCM key in the Android Keystore, alias `keeper_biometric_vek_wrap_v1`:

- `setUserAuthenticationRequired(true)` with `setUserAuthenticationParameters(0,
  AUTH_BIOMETRIC_STRONG)`: every single use needs a fresh Class 3 biometric. Device PIN, pattern
  or password are not accepted (PRD 6.3).
- `setInvalidatedByBiometricEnrollment(true)`: adding or removing a fingerprint or face makes the
  key permanently unusable.
- Randomized encryption (the default): the Keystore picks the GCM nonce; Keeper stores it.

The key material never leaves the Keystore. Whether it is hardware-backed depends on the device
and is not checked or claimed (PRD 15).

### 4.2 Stored fields (`vault_metadata`, schema 2)

`biometric_enabled`, `biometric_keystore_alias`, `biometric_wrapped_vek_nonce`,
`biometric_wrapped_vek_ciphertext`. Associated data for the wrap is
`keeper/biometric-wrapped-vek/v1;alias=<alias>`. A row with the flag set but any field missing is
read as "off".

### 4.3 Flows

Each flow is split in two so that only a BiometricPrompt-authorized `CryptoObject` can complete it;
a successful prompt result alone never unlocks anything.

- **Turn on** (Settings, vault unlocked): create a new Keystore key → prompt with its encrypt
  cipher → wrap the VEK with the authorized cipher → save. If wrapping fails, the key is deleted.
- **Unlock:** load the wrap → get a decrypt cipher with the stored nonce → prompt → unwrap with the
  authorized cipher → open the session. The Unlock screen prompts once automatically when
  biometrics are on, and always offers the master password.
- **Turn off:** clear the stored fields and delete the Keystore key. No prompt needed.
- **Invalidated key** (enrollments changed, or key missing): biometric unlock is turned off, the
  user sees why, and the master password still works. They can turn it on again after unlocking.
- **Prompt results:** cancel or "Use master password" shows nothing; lockout and other errors
  show a short message. A non-matching finger is handled inside the system prompt.

### 4.4 Tests

- JVM (`BiometricUnlockTest`, ViewModel tests) with a fake key store that uses a plain AES key:
  enable, unlock to the same VEK, wrong cipher, invalidation fallback, disable, restart.
- Instrumented: `MigrationTest` (v1 → v2 keeps the vault unlockable);
  `AndroidBiometricKeyStoreTest` checks the real Keystore key refuses to work without
  authentication. It is skipped on devices with no enrolled biometric.
- Real device, 18 October 2026, Samsung Galaxy S21 FE (SM-G990E), Android 16, enrolled
  fingerprint:
  - All 9 instrumented tests pass, including the 3 Keystore tests.
  - Turn on from Settings: the system prompt appeared, a fingerprint turned it on, and the
    database stored a 12-byte Keystore nonce and 48-byte wrapped key.
  - Lock, then fingerprint: the Unlock screen prompted automatically and unlocked to the same
    vault contents. The system log showed a strong-biometric success with `IsCrypto: true` and a
    keystore2 auth token for the operation.
  - Lock, then "Use master password": the vault stayed locked with no error, and the master
    password unlocked it.
- Not yet tested on a device: invalidation after adding or removing a fingerprint (covered by
  JVM tests and `deletedKey_reportsInvalidated`), and lockout after repeated failed touches.

### 4.5 Prompt and auto-lock

Auto-lock (section 5.1) must not lock the vault just because the system BiometricPrompt is in
front of the app (PRD 6.6). See section 5.1 for how that is modeled.

## 5. Hardening

### 5.1 Auto-lock

Code: `security/AutoLockController.kt`, hooks in `KeeperApplication`.

- **Background:** `ProcessLifecycleOwner` reports when no Keeper activity is visible (Home,
  recent apps, or switching apps). What happens next is the user's **Auto-lock** setting:
  - **Immediately** (default): the vault locks as soon as Keeper leaves the screen. In practice
    that's about 0.7 s later, because `ProcessLifecycleOwner` waits briefly so that rotation
    doesn't count as leaving.
  - **30 seconds** or **1 minute**: the vault locks after that delay unless Keeper comes back
    first.

  This is stricter than the PRD's 30-second default, which the PRD allows ("may lock sooner").
  The longer options exist for copy-and-paste workflows, where every switch to another app
  would otherwise need an unlock. The setting is stored in `SharedPreferences` (`keeper_settings`,
  not secret, excluded from backup); a missing or unknown value falls back to Immediately.
- **Screen off:** a runtime `ACTION_SCREEN_OFF` receiver locks immediately, whatever the setting.
- **System screens Keeper opens on purpose** (BiometricPrompt, and the file picker for backup
  export and restore) report when they open and close. While one is open, leaving the
  foreground does not lock or start the timer. If the user left during the
  prompt, the setting applies from when the prompt closes (so with Immediately, it locks then).
  Screen-off still locks during a prompt.
  On the Galaxy S21 FE the activity stays `RESUMED` behind the prompt, so the case never
  arises there; the suppression covers devices that do stop the activity.
- **Locking** replaces the navigation back stack (section 2), so vault screens and their
  ViewModels are discarded.
- **Process death** loses the in-memory vault key, so a restarted app is always locked.

### 5.2 Screenshot and recent-apps protection

`MainActivity` sets `FLAG_SECURE` on its window in `onCreate`, for every screen rather than only
credential screens. That avoids gaps during transitions or at the moment of locking, at the cost
of also blocking screenshots of the Setup and Unlock screens. Screenshots, screen recording and
the recent-apps thumbnail show a blank window.

Limitations: `FLAG_SECURE` doesn't stop a compromised device, accessibility services that read
the screen, or someone photographing it.

### 5.3 Clipboard

Code: `security/ClipboardGuard.kt`, `data/AndroidSecretClipboard.kt`.

- Every copy is tagged with a random id in the clip's extras. Passwords are also marked
  `EXTRA_IS_SENSITIVE` (Android 13+), which hides them from the system clipboard preview.
- About 30 seconds later the clip is cleared, but only if the clipboard still holds that exact
  tagged clip. Something copied by another app is never cleared.
- Android 10+ only lets an app read the clipboard while it has window focus. If Keeper is in the
  background when the 30 seconds pass, the check runs again when Keeper next gets focus. Until
  then the copied value stays on the clipboard.
- Keyboard apps with their own clipboard history (for example Samsung Keyboard or Gboard) may
  keep a copy that Keeper can't see or clear. Whether they honor the sensitive flag is up to them.

### 5.5 Autofill services

Android autofill services (Samsung Pass on Samsung phones, Google, other password managers)
could otherwise offer to fill or save Keeper's master password, a backup passphrase, or the
logins typed into the Add/Edit form. That would copy vault secrets into another app.

Keeper turns Compose's autofill integration off for the whole app:
`ComposeUiFlags.isSemanticAutofillEnabled = false` in `KeeperApplication.onCreate()`, before
the first composition.

- **Why app-wide:** Compose 1.10 has no per-field opt-out. It treats every text field as
  autofillable, and neither `semantics { contentDataType = ContentDataType.None }` nor
  `View.importantForAutofill = NO_EXCLUDE_DESCENDANTS` on the hosting view stops it. All three
  approaches were tested on the Galaxy S21 FE by counting the requests that reach the autofill
  service (`adb shell dumpsys autofill`). Only the global flag got that count to zero.
- **Effect:** no other app can fill or save anything in Keeper. Keeper itself never offered
  autofill to other apps; that's a non-goal for v1.
- **Caveat:** the flag is marked experimental. `AutofillDisabledTest` checks it is off at
  runtime. When updating Compose, check that the flag still exists, or switch to a per-field
  opt-out if one is added.
- **Not covered:** keyboard apps may still keep their own history of what was typed. Password
  fields ask keyboards not to learn, but that's up to the keyboard.

### 5.4 Tests

- JVM: `AutoLockControllerTest` (each setting, return in time, screen off, prompt suppression
  and timing, setting changes), `SettingsViewModelTest` (choosing a setting), `ClipboardGuardTest` (clears own clip, never another app's, waits for focus,
  restarts on a new copy).
- Instrumented: `SecureWindowTest` checks `FLAG_SECURE` on the main window, and
  `AutofillDisabledTest` checks Compose autofill is off.
- Real device, 18 October 2026, Galaxy S21 FE, Android 16:
  - All 10 instrumented tests pass.
  - A screenshot of Keeper is black.
  - With the 30-second setting, 10 s in the background keeps the vault unlocked and 33 s locks
    it. With the default (Immediately), 2 s away locks it.
  - A copied password could be pasted at 12 s and was gone by 39 s.
  - The activity stays `RESUMED` behind BiometricPrompt.
  - Turning the screen off locked the vault; on return the Unlock screen prompted for a
    fingerprint.
  - Autofill: before the change, opening the Unlock screen sent a request to Samsung Pass and to
    Samsung's augmented-autofill service. After it, the Unlock screen and the Add login form
    sent none.

## 6. Encrypted backup and restore

Code: `security/BackupCrypto.kt`, `data/backup/`, Settings screen and ViewModel.

### 6.1 File format (version 1)

Binary, all integers big-endian, file extension `.kbak`:

| Field | Bytes | Value |
|---|---|---|
| magic | 8 | `KEEPERBK` |
| format version | 1 | 1 |
| KDF id | 1 | 1 = Argon2id |
| KDF version | 1 | 0x13 |
| memory (KiB) | 4 | default 65,536 |
| iterations | 4 | default 3 |
| parallelism | 1 | default 1 |
| key length | 1 | 32 |
| salt length, salt | 1 + 16 | random per export |
| cipher id | 1 | 1 = AES-256-GCM |
| nonce length, nonce | 1 + 12 | random per export |
| ciphertext length | 4 | must equal the rest of the file |
| ciphertext and tag | n + 16 | |

- **Associated data:** every byte from the magic through the cipher id. Changing any header
  field makes decryption fail; GCM authenticates the nonce itself.
- **Key:** Argon2id(backup passphrase, salt, header settings). The passphrase goes through the
  same `MasterPasswordEncoding` (NFC, UTF-8, no trimming) as the master password (PRD 7.2). It
  is separate from the master password and must be at least 12 characters.
- **Payload:** strict JSON, `{"format":1,"credentials":[{id,title,username,password,url,notes,favorite}]}`.
  Unknown or missing fields are rejected.
- **Not included:** the vault key, the master password wrap, Keystore keys and biometric
  settings (PRD 6.7).
- **File name:** `keeper-backup-<date>.kbak`. No service names (PRD 6.7).
- **Fixture:** `src/test/resources/backup/v1-fixture.kbak` is a frozen version-1 file. Tests
  check it still decrypts, and that deterministic encoding reproduces it byte for byte, so any
  accidental format change fails the build. `WriteBackupFixture` explains how to regenerate it
  deliberately.

### 6.2 Reading a backup (PRD 6.8)

Checks happen in this order, and nothing in the vault changes unless all of them pass:

1. **Size:** files over 50 MiB are rejected. The file is read with a hard cap, so a huge file
   is never loaded in full.
2. **Structure:** magic, versions, algorithm ids, a 12-byte nonce, and a ciphertext length that
   exactly matches the file.
3. **KDF settings** against the section 1.1 bounds, **before** Argon2id runs.
4. **Decryption:** a wrong passphrase and a damaged or altered file give the same result.
5. **Payload:** at most 10,000 records, unique non-blank ids of at most 100 characters, and every
   field within the same limits as the Add/Edit form. One bad record rejects the whole file.

### 6.3 Export

Passphrase (twice) → system "save" picker (Storage Access Framework) → encrypt in memory →
write. Only ciphertext is written, and there's no plaintext temporary file. If writing fails,
the partly written document is deleted where the provider allows it, and the user is told
nothing was saved. Records that couldn't be decrypted (section 3.3) are not included.

### 6.4 Restore

Confirm "Replace your vault?" → system "open" picker → passphrase → read and validate (6.2) →
replace all logins in **one Room transaction**. Every record is re-encrypted under this vault's
own key before the transaction starts, so a failure at any point leaves the vault as it was.

Version 1 supports **replace only**, as the PRD allows. Restoring into an unlocked vault keeps
its master password, vault key and biometric unlock; only the logins change. On a new phone,
the user creates a vault first, then restores into it.

### 6.5 Limitations

- The backup is only as strong as its passphrase. Argon2id slows down guessing, but a short or
  reused passphrase can still be guessed offline by someone who has the file.
- While the file picker is open the vault stays unlocked, however long that takes. Screen off
  still locks.
- Keeper can't control where the user saves the file or who else can read that location.

### 6.6 Tests

- JVM (`BackupCryptoTest`, `VaultBackupServiceTest`, `SettingsViewModelTest`): round trip,
  empty vault, fresh salt and nonce, wrong passphrase, every header and body byte tampered,
  truncated, extended, not-a-backup, unknown versions, hostile KDF settings rejected before
  Argon2id, oversized file, invalid records, frozen fixture, restore into a vault with a
  different key, export while locked, write failure cleanup, and the Settings flow steps.
- Instrumented: `EncryptedStorageTest.replaceAll_swapsEveryRowInOneTransaction` against real
  Room.
- Real device, 18 October 2026, Galaxy S21 FE:
  - All 11 instrumented tests pass.
  - Exported 40 logins to Downloads (7,242 bytes) through the system picker; the vault stayed
    unlocked with auto-lock on Immediately. The file's header was as specified, and it contained
    no titles, usernames, domains or passphrases.
  - Deleted one login, then restoring with a wrong passphrase left the vault unchanged at 39.
  - Restoring with the right passphrase brought it back to 40, including the deleted login.
