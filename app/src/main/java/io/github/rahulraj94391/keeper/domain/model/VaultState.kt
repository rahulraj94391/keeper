package io.github.rahulraj94391.keeper.domain.model

/** Where the vault is in its lifecycle. Drives which part of the app is reachable. */
enum class VaultState {
    /** Stored vault data hasn't been checked yet, just after app start. */
    Loading,

    /** No vault has been created yet; first-launch setup is needed. */
    NoVault,

    /** A vault exists but its key is not in memory. */
    Locked,

    /** The vault key is in memory and credentials can be read. */
    Unlocked,
}
