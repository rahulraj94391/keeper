package io.github.rahulraj94391.keeper.data.repository

import io.github.rahulraj94391.keeper.domain.model.Credential

/** Fake records so the UI has something to show before real storage exists. Not real accounts. */
object SampleCredentials {
    val all = listOf(
        Credential(
            id = "sample-mail",
            title = "Example Mail",
            username = "alex@example.com",
            password = "correct-horse-battery-staple",
            url = "https://mail.example.com",
            notes = "Recovery codes are in the desk drawer.",
            favorite = true,
        ),
        Credential(
            id = "sample-bank",
            title = "Demo Bank",
            username = "alex.demo",
            password = "Tr0ub4dor&3-sample",
            url = "https://bank.example.org",
            notes = "",
        ),
        Credential(
            id = "sample-games",
            title = "Game Store",
            username = "alex_plays",
            password = "not-a-real-password-42",
            url = "",
            notes = "",
        ),
    )
}
