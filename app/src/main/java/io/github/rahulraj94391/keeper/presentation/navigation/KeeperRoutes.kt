package io.github.rahulraj94391.keeper.presentation.navigation

import kotlinx.serialization.Serializable

// Routes carry only record ids. Credential values never travel through navigation arguments.

@Serializable
data object VaultListRoute

@Serializable
data class CredentialDetailRoute(val id: String)

/** [id] is null when adding a new credential. */
@Serializable
data class CredentialEditRoute(val id: String? = null)
