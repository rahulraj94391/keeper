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
