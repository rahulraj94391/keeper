package io.github.rahulraj94391.keeper.testing

import io.github.rahulraj94391.keeper.domain.model.Credential

fun testCredential(
    id: String,
    title: String = "Title $id",
    url: String = "",
    favorite: Boolean = false,
) = Credential(
    id = id,
    title = title,
    username = "user-$id",
    password = "fake-password-$id",
    url = url,
    notes = "",
    favorite = favorite,
)
