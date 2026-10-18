# Product Requirements Document: Offline Password Vault for Android

**Working name:** Offline Vault  
**Document version:** 1.0  
**Status:** Ready for implementation planning  
**Intended audience:** Coding agent / Android engineer  
**Product goal:** A learning-focused Android app for storing login credentials securely and entirely offline.

---

## 1. Executive summary

Build a local-first Android password manager in Kotlin. Users can store website/app names, usernames, passwords, URLs, and optional notes in an encrypted local vault. The vault is unlocked primarily with a biometric prompt and always supports a master-password fallback. Users can export and restore a vault using a separately chosen backup passphrase and an encrypted file.

The app must not require an account, server, network connection, analytics SDK, or cloud service. Sensitive credential fields must be encrypted before they are written to persistent storage. The app is a learning project, not a security-audited commercial password manager; implementation notes and limitations must be documented honestly.

**Core security invariant:** The app must never persist the vault encryption key (VEK), master password, plaintext credentials, or decrypted vault payload to disk. Persistent credential records and key-wrapping values must be authenticated ciphertexts. The VEK exists in memory only while the vault is unlocked, with best-effort clearing when the session ends.

## 2. Product goals

1. Store and retrieve credentials offline.
2. Use a master password to establish a strong, portable unlock path.
3. Offer optional biometric-first unlock backed by Android Keystore cryptographic authorization, not merely a UI gate.
4. Support encrypted file backup and restore that work across devices.
5. Provide a codebase that is easy to learn from, test, and extend.
6. Make security behavior explicit, documented, and covered by tests.

## 3. Non-goals for version 1

- Cloud sync, accounts, remote APIs, telemetry, ads, or analytics.
- Browser extensions or Android Autofill Framework integration.
- Sharing credentials between users/devices directly.
- Password breach monitoring or checking credentials against online services.
- Importing from commercial password managers.
- Secure erase guarantees on flash storage; deletion means deleting logical records and keys, not guaranteeing physical overwrite.
- Claiming resistance to a fully compromised/rooted OS, malware running inside the app process, malicious keyboards, screen recording by a privileged attacker, or physical attacks against an already-unlocked device.
- Production security certification or independent audit in this project phase.

## 4. Target platform and implementation constraints

- Language: Kotlin.
- UI: Jetpack Compose and Material components.
- Architecture: MVVM with use cases/repositories and a separate security core.
- Persistence: Room.
- Async work: Coroutines and Flow.
- Biometric UI: AndroidX Biometric (`BiometricPrompt`).
- Device-bound key protection: Android Keystore.
- Password-based key derivation: Argon2id v1.3 using a maintained, reviewed Android-compatible library. Do not implement cryptographic primitives manually.
- Symmetric encryption: AES-256-GCM via platform cryptography APIs where supported.
- Minimum SDK starting assumption: API 26 or later. Confirm final minSdk and dependency compatibility when scaffolding the repo; document any deviation.
- Use current stable, mutually compatible Android Gradle Plugin, Kotlin, Compose BOM, Room, and AndroidX Biometric versions when implementation begins. Pin versions; do not use dynamic dependency versions.
- No network functionality. Do not request `android.permission.INTERNET` unless a documented build/runtime requirement proves it is necessary (it should not be necessary for the product).
- Use Android's Storage Access Framework for user-initiated backup export/import.

## 5. User stories

### Setup and unlock
- As a new user, I can create a vault with a master password.
- As a user, I can unlock the vault with the master password if biometrics are unavailable or fail.
- As a user, I can opt into biometric unlock after establishing the master password.
- As a user, I can disable biometric unlock without losing access to the vault.
- As a user, I can lock the vault and have it automatically lock after the app is backgrounded or the device screen is turned off.

### Credential management
- As a user, I can add, view, edit, search, and delete login records.
- Each record can contain a title/service name, username, password, optional website/app URL, optional notes, and optional category/favorite metadata.
- Passwords are masked by default and can be explicitly revealed while the vault is unlocked.
- I can copy a username or password without permanently replacing clipboard content later.
- I can generate a strong random password offline.

### Backup and restore
- As a user, I can export an encrypted vault file to a location I choose.
- The export is protected by a backup passphrase that is independent of the master password and device biometrics.
- As a user, I can restore that file on another installation/device by entering the backup passphrase.
- A failed or malformed restore must not modify or partially overwrite the current vault.

## 6. Scope and functional requirements

Priority meanings: **P0** = required for usable MVP; **P1** = required for a complete first release but can follow the security foundation; **P2** = future enhancement.

### 6.1 Vault setup — P0

- Show an initial setup flow when no vault exists.
- Require a master password/passphrase. The initial UX should encourage a long, unique passphrase and provide confirmation on setup.
- Do not store the raw master password or a standalone reusable plaintext password verifier.
- Generate a cryptographically secure random 32-byte VEK for the vault.
- Generate a unique random 16-byte Argon2id salt.
- Derive a 32-byte key-encryption key (KEK) from the master password and salt; use it to wrap/encrypt the VEK with AES-256-GCM.
- Persist the salt, KDF identifier/version/parameters, wrapping algorithm/version, nonce, and wrapped VEK.
- Make vault initialization atomic: interrupted setup should leave either a complete valid vault or no vault, not partially initialized key material.

### 6.2 Master-password unlock — P0

- Derive the KEK using the stored salt and persisted KDF parameters.
- Attempt authenticated decryption of the wrapped VEK.
- If authentication/decryption succeeds and the VEK has expected length, initialize an in-memory unlocked session.
- On failure, keep the vault locked and show a generic, non-sensitive error such as “Could not unlock vault. Check your password or vault data.” Do not reveal cryptographic exception details.
- Password encoding/normalization must be consistent at creation and unlock. Document the chosen policy; prefer a deterministic Unicode normalization policy plus UTF-8 conversion, applied identically both times.
- Validate stored KDF parameters before executing them. Reject unknown algorithms, unsupported versions, or resource-exhausting values safely.

### 6.3 Biometric unlock — P0 after master-password path works

- Biometric unlock is optional and configured only after successful master-password unlock.
- Use AndroidX BiometricPrompt with a `CryptoObject` wrapping an initialized cipher so successful strong-biometric authentication authorizes the relevant cryptographic operation.
- Generate a separate AES key in Android Keystore configured for authenticated use and encryption/decryption of the biometric-wrapped VEK.
- Use a fresh AES-GCM nonce for the biometric wrapping operation and persist only the nonce and encrypted VEK outside Keystore. Keystore key material must not be exportable into application storage.
- Prefer `BIOMETRIC_STRONG` for this implementation. The master-password fallback must be an explicit in-app alternative and must not depend on device PIN/pattern/password.
- Handle no enrolled biometrics, hardware unavailable, user cancellation, lockout, temporary errors, key invalidation, and authentication errors without crashing or leaving the vault unlocked.
- If biometric key access becomes invalid (for example, biometric enrollment changes), preserve the master-password path, disable or mark biometric unlock unavailable, and allow the user to re-enroll after master-password unlock.
- Do not use biometric recognition result alone as an authorization flag; the authorization must be tied to a Keystore-backed cryptographic operation.
- On biometric failure/cancel, remain locked unless a valid unlock path succeeds.

### 6.4 Credential records — P0

- Support create, read, update, delete, and list.
- Credential payload fields: title/service name, username, password, URL (optional), notes (optional), category/favorite (optional), and payload schema version.
- Sensitive fields must not be stored as plaintext columns. The easiest MVP is to serialize the full credential payload and encrypt it as a single authenticated payload per record.
- Store only a random record ID, encrypted payload, nonce, and minimal required version/metadata outside ciphertext. Do not persist plaintext titles, usernames, passwords, URLs, or notes.
- For list/search, decrypt records only after the vault is unlocked. It is acceptable for the MVP to decrypt a bounded set of records into memory for search; do not add plaintext search indexes.
- Authenticate relevant record context as AES-GCM AAD (at minimum record ID and payload schema version) so ciphertext cannot be moved between records without detection.
- Use random, non-guessable record IDs (e.g. UUIDs) rather than sequential IDs where practical.
- Deleting a record deletes its Room row. Do not promise physical erasure from flash storage, database journals, or backups.

### 6.5 Password generator — P1

- Generate passwords using a cryptographically secure random source (e.g. `SecureRandom`), never `Random` or time-seeded pseudo-random values.
- Support configurable length (suggested initial range 16–64), and selected character groups (lowercase, uppercase, digits, symbols).
- Ensure selected groups are represented when generating a password, without introducing modulo bias or predictable substitutions.
- Generator must work offline and must not log generated values.

### 6.6 Locking and sensitive UI — P0

- Keep the VEK only in an in-memory session object while unlocked.
- Default behavior: obscure sensitive app content immediately when the app stops being visible; lock the vault when it has been in the background for 30 seconds, and lock immediately when the device screen turns off. The implementation may lock sooner, but must not extend this window without an explicit user setting.
- Ensure lifecycle handling does not incorrectly lock the vault just because the BiometricPrompt system UI is active. Model authentication state explicitly and test this behavior.
- On lock, clear the active vault session and best-effort clear mutable secret buffers. Document that JVM/runtime copies and immutable objects cannot be guaranteed erased.
- Prevent sensitive content from appearing in the recent-apps snapshot. Apply `FLAG_SECURE` on screens that display credentials as a baseline security measure; document its limitations and any UX trade-off.
- Mask passwords by default. Password reveal must be a temporary, explicit user action.
- Clipboard clearing is best effort: clear the copied secret after 30 seconds only if the clipboard still contains the exact item copied by this app. Do not overwrite content copied by another app after the user copies a secret. Respect platform clipboard behavior and document that clipboard protections vary by Android version.
- Do not log credential values, master/backup passphrases, derived keys, VEKs, plaintext payloads, crypto input/output, or sensitive exception content. Avoid logging sensitive object `toString()` output.

### 6.7 Encrypted export — P0

- Export is an explicit user action through `ACTION_CREATE_DOCUMENT` / an equivalent Storage Access Framework contract.
- Prompt for a separate backup passphrase and confirmation before exporting. Explain that losing it means the backup cannot be restored.
- Serialize a versioned backup payload containing the credentials and only required logical metadata; do not include the device's Keystore key, biometric-wrapping key, or raw VEK.
- Encrypt the complete payload with AES-256-GCM using a backup key derived from the backup passphrase via Argon2id with a new random salt. Use a fresh nonce for the encryption operation and authenticate relevant format/version headers as AAD.
- Backup envelope must contain a magic/header, format version, KDF identifier/version/parameters, salt, cipher identifier/version, nonce, and ciphertext/tag. Use a simple documented format (e.g. versioned binary envelope or strict JSON envelope with Base64 fields); choose one and add test vectors/fixtures.
- Write only ciphertext to the selected destination. Do not create plaintext temporary files. Do not use the service/credential name in the backup filename by default.
- Validate maximum payload/file sizes and KDF parameter bounds before allocating large buffers or running Argon2id.
- Export must be all-or-fail. If serialization/encryption/document writing fails, show a safe error and do not claim the backup succeeded.

### 6.8 Encrypted restore — P0

- Import is an explicit user action through `ACTION_OPEN_DOCUMENT` / an equivalent SAF contract.
- Parse the untrusted header defensively; enforce supported versions, maximum file size, bounded KDF memory/iteration/parallelism, expected salt/nonce sizes, and ciphertext limits before deriving keys.
- Derive a key from the backup passphrase and authenticate/decrypt the payload. A wrong passphrase, damaged file, or tampered tag must fail without modifying the current vault.
- Strictly validate the entire decrypted payload (schema version, record count, field size limits, IDs, and required fields) before commit.
- For restore, import logical credentials into a newly generated VEK and re-encrypt records locally. Create the master-password wrapping for the new VEK; offer biometric enrollment afterward. Do not import or reuse the original device's biometric Keystore configuration.
- Import into an empty/new vault first. If a vault already exists, require an explicit, clearly worded replace/merge decision. Version 1 may support **replace only** if safer/simpler; if replace is chosen, require confirmation and use a database transaction so failure does not leave partial data.
- Do not persist decrypted backup payloads to disk. Best-effort clear plaintext buffers after completion/failure.

### 6.9 Android backup configuration — P0

- Automatic Android cloud backup and device-transfer backup must not silently copy the vault database, key metadata, or sensitive settings. Configure `android:allowBackup` and explicit backup/data-extraction rules as appropriate for supported Android versions.
- Prefer an explicit deny/exclusion strategy for the vault DB and all related sensitive files/preferences. Do not assume one XML configuration applies identically to every Android release.
- Add a documented test/check procedure for backup exclusions. The only supported portable backup mechanism in v1 is the app's explicit, passphrase-encrypted export.

### 6.10 Offline behavior — P0

- App must work in airplane mode and have no network dependency.
- Do not add analytics, telemetry, crash-upload SDKs, remote config, ads, or network permission without a separately approved requirement.
- Dependencies should be reviewed for unexpected network behavior. Build-time dependency resolution is not a runtime network feature.

## 7. Security architecture and cryptographic invariants

### 7.1 Key hierarchy

1. **VEK:** Random 32-byte key generated once per vault. Encrypts every credential payload with AES-256-GCM.
2. **Master KEK:** 32-byte output from Argon2id, derived from the user's master passphrase, stored salt, and persisted parameters. Encrypts/wraps the VEK with AES-256-GCM.
3. **Biometric wrapping key:** Random AES key generated by Android Keystore and configured to require strong biometric authentication for use. It encrypts/wraps the VEK via a BiometricPrompt-authorized `Cipher` operation.
4. **Backup key:** 32-byte Argon2id output derived from a distinct backup passphrase and a fresh backup salt. Encrypts the exported backup payload with AES-256-GCM.

Do not reuse a key across these distinct purposes. Do not derive the VEK directly from the master password; random VEK separation enables password changes without re-encrypting every record and allows independent unlock paths.

### 7.2 Argon2id requirements

- Use Argon2id version 1.3, from a maintained implementation with an explicit license and supported Android ABIs/runtime verified by the coding agent.
- Initial benchmarking profile to evaluate: 64 MiB memory, 3 iterations, 1 lane, 32-byte output. This is a starting profile, not a universal guarantee. Benchmark low/mid/high target devices and adjust only with written rationale; keep acceptable unlock latency while maximizing reasonable memory/time cost.
- Salt: at least 16 random bytes, independently generated for master-password wrapping and each backup export.
- Persist KDF name, version, memory cost, iteration count, parallelism, output length, and salt with every wrapped-key/backup envelope.
- Enforce supported min/max values before any KDF runs against imported files or parsed metadata, to prevent memory/CPU denial of service.
- Benchmarking must run off the main thread and must not freeze the UI.
- Password conversion/normalization must be consistent. Do not silently trim spaces. Clearly specify whether the app normalizes Unicode and use the exact same function for create, unlock, and backup restore.

### 7.3 AES-GCM requirements

- Use AES-256-GCM with a 96-bit (12-byte) nonce and a full 128-bit authentication tag, unless a documented platform/library constraint requires otherwise.
- Generate a fresh unpredictable nonce for every encryption under the same key. Nonce reuse with the same key is forbidden. Persist each nonce beside its ciphertext.
- Use AES-GCM AAD to bind ciphertext to context such as format version, record ID, and schema version.
- Treat authentication/tag failure as a hard failure. Do not return partial plaintext.
- Use `SecureRandom` or platform-approved cryptographically secure randomness for salts, nonces, VEKs, record IDs, and password generation.
- Do not implement AES, GCM, Argon2id, random number generation, or constant-time tag validation manually.

### 7.4 Session handling

- A `VaultSession`-style component owns the in-memory VEK only while unlocked.
- Repository APIs that expose credentials must require an unlocked session or be reachable only via use cases that first verify session state.
- Avoid global/singleton storage of raw keys except the dedicated session owner; avoid passing key bytes through UI state, Compose state, SavedStateHandle, intents, logs, analytics, or navigation arguments.
- Do not save decrypted credentials in ViewModel saved state or persist them through `rememberSaveable`.
- On lock, clear session references and best-effort wipe mutable arrays. State the JVM memory-clearing limitation in developer documentation.

### 7.5 Threat model

Protect against:
- Someone copying app-private database/files while the vault is locked.
- Someone obtaining an encrypted backup file without its backup passphrase.
- Accidental plaintext persistence in logs, crash messages, screenshots, recent-app previews, or Android Auto Backup.
- Tampering with credential records or backup ciphertext.
- Incorrect/wrong passphrases and corrupted metadata.

Out of scope / cannot guarantee:
- Malware or an attacker with code execution inside the unlocked app process.
- Compromised OS/root with ability to inspect process memory or invoke the app's unlocked operations.
- Keylogging/malicious keyboards, shoulder surfing, or privileged screen capture.
- Brute-force resistance when a user chooses a very weak master/backup passphrase; Argon2id raises cost per guess but cannot make a weak password strong.
- Guaranteed physical erasure of data from flash storage or previously created backups.

## 8. Data model (proposed)

### VaultMetadataEntity

- `id` — constant/singleton ID.
- `schemaVersion` — integer.
- `kdfAlgorithm` — identifier (Argon2id).
- `kdfVersion` — implementation/spec version identifier.
- `kdfMemoryKiB`, `kdfIterations`, `kdfParallelism`, `kdfOutputBytes` — bounded integers.
- `masterSalt` — bytes.
- `masterWrappedVekNonce` — bytes.
- `masterWrappedVekCiphertext` — bytes (contains authenticated tag as defined by selected format).
- `biometricEnabled` — boolean (not secret).
- `biometricWrappedVekNonce` — nullable bytes.
- `biometricWrappedVekCiphertext` — nullable bytes.
- `biometricKeystoreAlias` — nullable non-secret alias string.

Never store plaintext VEK or passphrases in this entity. Store no secret in DataStore/SharedPreferences.

### CredentialEntity

- `id` — random stable ID.
- `payloadSchemaVersion` — integer.
- `nonce` — bytes.
- `ciphertext` — bytes.

The serialized and encrypted payload contains title, username, password, URL, notes, category, favorite flag, and optional timestamps. Avoid storing these values in plaintext columns. Any future query/index columns require a separate privacy review.

The exact schema can evolve, but migrations must preserve ciphertext and key metadata correctly. Never attempt a destructive schema migration on a user's only vault without a safe backup/rollback strategy.

## 9. UX and screens

1. **Welcome/setup:** Explain local-only storage and the importance of the master password. Set and confirm master password.
2. **Unlock:** Biometric prompt as primary action when enabled; visible “Use master password” alternative. If biometric key is unavailable/invalidated, guide to fallback rather than dead-ending.
3. **Vault list:** Search input, credential list, add button. Avoid showing usernames/passwords in list rows by default; service title may be decrypted in memory only.
4. **Credential details:** Masked password, reveal/hide action, copy username/password, edit, delete. Keep sensitive details off previews.
5. **Add/edit credential:** Title and password required; username, URL, notes optional. Validate size limits and show clear errors.
6. **Password generator:** Length and character-group options; copy/use action.
7. **Settings:** Enable/disable biometrics, lock behavior, export backup, restore backup, app info/security limitations.
8. **Backup passphrase dialogs:** Clearly distinguish backup passphrase from master password. Warn that forgotten backup passphrase means the backup cannot be decrypted.
9. **Restore confirmation:** State whether restore will replace an existing vault. No destructive action without confirmation.

UI accessibility: accessible labels, sufficient contrast, keyboard support, and sensible screen-reader descriptions. Don't reveal secrets in accessibility labels for masked fields.

## 10. Proposed package structure

```text
com.example.offlinevault
├── app/
├── presentation/
│   ├── setup/
│   ├── unlock/
│   ├── vault/
│   ├── credential/
│   ├── generator/
│   └── settings/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── security/
│   ├── KeyDerivation
│   ├── AeadCipher
│   ├── VaultKeyManager
│   ├── BiometricUnlockManager
│   ├── VaultSession
│   └── BackupCrypto
├── data/
│   ├── local/
│   ├── repository/
│   └── backup/
└── di/
```

A single app module is sufficient for v1. Keep security classes independent from Compose and Room where practical, and inject interfaces so tests can use deterministic fixtures/fakes. Do not use fake crypto in production source sets.

## 11. Testing requirements

### Unit tests — P0

- Argon2id integration: same password/salt/parameters yields same derived key; changed password or salt produces a different result. Use published/test-vector cases where the library provides them.
- AES-GCM encrypt/decrypt round trip.
- AES-GCM tampered ciphertext, tag, nonce, or AAD fails closed.
- Key wrapping: correct master password unwraps the VEK; incorrect password does not.
- Password encoding/normalization is identical across create/unlock.
- Record encode/decode and schema validation.
- Backup encode/decode round trip; wrong backup passphrase and tampered file fail.
- Malformed headers, unsupported versions, invalid lengths, oversized payloads, and out-of-range KDF values are rejected before expensive operations.
- Generator tests: output length and selected group constraints; randomness implementation uses `SecureRandom`.
- Restore validation fails without changing existing database state.

### Room/repository tests — P0

- CRUD operations persist ciphertext, not plaintext sensitive fields.
- Searching works only in unlocked state and returns expected records.
- No record is returned when locked.
- Database transaction prevents partial restore.
- Migrations retain the ability to unlock existing test vaults.

### Android integration tests — P0

- Biometric enrollment and successful `CryptoObject` operation on a supported real device.
- User cancels biometric prompt: app remains locked and master-password fallback works.
- No biometric hardware/enrollment: master-password flow works.
- Invalidated/deleted Keystore key: fallback still opens vault and re-enrollment can occur.
- Activity/process recreation: app relocks and does not restore the VEK through saved state.
- App background/screen-off locking and sensitive recent-app snapshot protection.
- SAF export then import on a clean installation/device/emulator.
- Automatic backup exclusion configuration is reviewed and tested on supported API levels.

### Manual security checklist — P0

- Inspect Room DB and app files with test data: no recognizable plaintext credential, master password, backup passphrase, or VEK appears.
- Inspect logs/logcat during success and failure paths for secrets.
- Verify malformed backup cannot crash the app or trigger unbounded memory/CPU use.
- Verify no network permission/traffic is required for normal operation.
- Verify plaintext backup is never written to a temporary file.

## 12. Non-functional requirements

- **Offline:** Full functionality requires no network connection.
- **Reliability:** Failed encryption/decryption/import must fail closed; no corrupted partial records should be exposed.
- **Responsiveness:** KDF work is off the main thread. Benchmark unlock on representative low-, mid-, and high-range Android devices; target roughly 0.5–1.5 seconds for the baseline profile on a representative mid-range device, then document measured results and tune responsibly. Do not reduce KDF costs without rationale.
- **Resource safety:** Enforce bounded credential counts, field sizes, backup file size, and imported KDF parameters. Initial suggested limits: up to 10,000 records and a 50 MiB backup file; document and test chosen limits.
- **Accessibility:** UI supports TalkBack and system font scaling as reasonably practical.
- **Maintainability:** Crypto operations have narrow interfaces, written invariants, tests, and versioned serialized formats.
- **Privacy:** No telemetry or credential-derived analytics.

## 13. Agent implementation workflow and guardrails

The coding agent should work incrementally, not generate the entire app in one unreviewed pass.

1. **Inspect the repository first.** Summarize existing modules, build setup, conventions, and tests. If no project exists, scaffold one using the constraints above.
2. **Write a short design note before implementation.** Include chosen Argon2id library, license, platform/API compatibility, AES-GCM envelope format, nonce strategy, Keystore configuration, backup format, and limitations. Do not invent a library or claim it is maintained without checking evidence.
3. **Implement P0 in small phases:** (a) project skeleton and domain model; (b) cryptographic primitives and unit tests; (c) master-password key wrapping and vault session; (d) encrypted Room repository; (e) UI CRUD; (f) biometric wrapping; (g) encrypted backup/restore; (h) hardening and lifecycle tests.
4. **Do not write custom cryptography.** If a required implementation is uncertain, stop and document the decision rather than silently substituting an insecure algorithm.
5. **Do not log secrets**, including in debug builds. Test fixtures must use fake credentials.
6. **Do not weaken requirements to make tests pass.** Explain any unavoidable platform limitation and offer a reviewed alternative.
7. **After each phase:** run relevant unit/instrumented tests, build the app, record commands and results, and list any tests that could not be run. Never claim checks passed unless they actually ran.
8. **Add architecture/security docs:** document key hierarchy, backup format, KDF params/versioning, threat model, lifecycle behavior, and recovery limitations.
9. **Do not add cloud features or optional integrations** outside this PRD without approval.
10. **Finish with a handoff report:** completed scope, module/file map, crypto/library choices and versions, test commands/results, known limitations, and recommended next steps.

## 14. Definition of done

The v1 MVP is done when all of the following are true:

- [ ] User can create a vault with a master password and later unlock it offline.
- [ ] A random VEK encrypts all credential payloads; the VEK is never persisted plaintext.
- [ ] Password-derived key wraps the VEK using Argon2id and AES-GCM; KDF params are versioned and validated.
- [ ] Biometric unlock uses Keystore-backed cryptographic authorization via BiometricPrompt `CryptoObject`, with a functioning master-password fallback.
- [ ] Biometric unavailability/key invalidation does not permanently lock the user out while the master password remains known.
- [ ] Credential CRUD, search, mask/reveal, clipboard handling, and secure password generation work.
- [ ] Room stores encrypted credential payloads; no sensitive credential fields appear as plaintext columns.
- [ ] Encrypted backup can be exported with a distinct passphrase and restored on a clean installation/device.
- [ ] Wrong passphrase, ciphertext tampering, malformed files, and unsupported versions fail safely.
- [ ] Restore is transactional and never leaves a partial vault.
- [ ] App background/screen-off locking, recent-task screenshot protection, and best-effort secret cleanup are implemented and tested.
- [ ] Automatic Android backup/device transfer is configured not to silently include vault data or keys.
- [ ] No runtime network permission, cloud feature, analytics, or telemetry is added.
- [ ] Unit tests and feasible Android integration tests pass; skipped tests are explicitly documented.
- [ ] Security architecture, threat model, dependencies, backup format, KDF parameters, limitations, and test results are documented.

## 15. Risks and design notes

- A weak master password remains vulnerable to offline guessing if an attacker obtains the wrapped VEK. Argon2id increases per-guess cost but cannot replace a strong passphrase.
- Keystore hardware backing differs by device. The app should rely on the Keystore API's protections without claiming all keys are hardware-backed. If reporting key security level, treat it as informational only for this project.
- JVM-managed secret values cannot be guaranteed erased from memory. Use mutable arrays when practical, limit copies/lifetime, and avoid claims of perfect zeroization.
- A custom encrypted backup format is security-sensitive. Keep it small, versioned, strictly parsed, fuzz/test malformed input, and never silently fall back to plaintext.
- Android automatic backup behavior varies by API level and device implementation. Include explicit rules and test supported versions; never assume a single configuration covers every transfer behavior.
- This learning project should not be marketed as an independently audited or production-proven password manager.

## 16. Reference documentation

The coding agent must verify these references at implementation time and consult current versions of the relevant APIs/specifications:

- Android Keystore system: https://developer.android.com/privacy-and-security/keystore
- Android cryptography guidance: https://developer.android.com/privacy-and-security/cryptography
- AndroidX Biometric / BiometricPrompt: https://developer.android.com/identity/sign-in/biometric-auth
- `BiometricPrompt.CryptoObject` API: https://developer.android.com/reference/androidx/biometric/BiometricPrompt.CryptoObject
- Android backup security recommendations: https://developer.android.com/privacy-and-security/risks/backup-best-practices
- Android Auto Backup configuration: https://developer.android.com/identity/data/autobackup
- Argon2 specification, RFC 9106: https://datatracker.ietf.org/doc/html/rfc9106
- OWASP MASVS storage requirements: https://mas.owasp.org/MASVS/05-MASVS-STORAGE/

---

**End of PRD**
