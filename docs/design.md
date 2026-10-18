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
`data/auth/MasterPasswordVaultAuthenticator.kt`.

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

**Storage:** for now `InMemoryVaultMetadataStore`, so the vault still disappears when the app
process ends. The encrypted storage phase replaces it with a Room table.

### 2.3 Vault session

`VaultSession` is the only holder of the VEK while unlocked. Code reads it only inside
`withKey { }`; outside a session that call throws `VaultLockedException`. Locking calls `close()`,
which overwrites the key array. The key is never put in UI state, saved state, navigation
arguments or logs.

### 2.4 Threading

Create and unlock each run Argon2id once, on `Dispatchers.Default`, never the main thread. A
mutex makes sure repeated taps can't start two runs at once. While the app checks stored data at
start-up, the vault state is `Loading` and a blank screen is shown.
