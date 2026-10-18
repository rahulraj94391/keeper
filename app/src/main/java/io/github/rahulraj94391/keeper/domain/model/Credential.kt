package io.github.rahulraj94391.keeper.domain.model

/**
 * A decrypted login record. Instances only exist in memory while the vault is unlocked.
 */
data class Credential(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val favorite: Boolean = false,
) {
    // Never expose secret fields through logs or string templates.
    override fun toString(): String = "Credential(id=$id)"
}

/** How credentials are listed: favorites first, then by title ignoring case. */
val CredentialDisplayOrder: Comparator<Credential> =
    compareByDescending<Credential> { it.favorite }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }

/** Maximum field lengths, enforced before a credential is saved. */
object CredentialLimits {
    const val TITLE = 200
    const val USERNAME = 500
    const val PASSWORD = 1_000
    const val URL = 2_000
    const val NOTES = 10_000
}
