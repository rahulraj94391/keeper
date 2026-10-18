package io.github.rahulraj94391.keeper.data.repository

import io.github.rahulraj94391.keeper.domain.model.Credential
import io.github.rahulraj94391.keeper.domain.model.CredentialLimits
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Turns a credential into the bytes that get encrypted, and back (PRD 6.4). The record id is not
 * inside the payload; it is bound to the ciphertext as associated data instead.
 */
object CredentialPayloadCodec {

    const val SCHEMA_VERSION = 1

    /** Larger than any valid payload; anything bigger is rejected before decryption. */
    const val MAX_CIPHERTEXT_BYTES = 64 * 1024

    @Serializable
    private class PayloadV1(
        val title: String,
        val username: String,
        val password: String,
        val url: String,
        val notes: String,
        val favorite: Boolean,
    ) {
        override fun toString(): String = "PayloadV1"
    }

    // Strict: unknown or missing fields mean the payload isn't one Keeper wrote.
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = true
    }

    /**
     * Limitation: JSON encoding goes through an immutable String, which can't be wiped.
     * The returned bytes can and should be wiped by the caller after encryption.
     */
    fun encode(credential: Credential): ByteArray = with(credential) {
        json.encodeToString(PayloadV1.serializer(), PayloadV1(title, username, password, url, notes, favorite))
            .toByteArray(Charsets.UTF_8)
    }

    /** Returns null for anything malformed or outside the field limits. */
    fun decode(id: String, bytes: ByteArray): Credential? {
        val payload = try {
            json.decodeFromString(PayloadV1.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val valid = payload.title.isNotBlank() && payload.title.length <= CredentialLimits.TITLE &&
            payload.username.length <= CredentialLimits.USERNAME &&
            payload.password.isNotEmpty() && payload.password.length <= CredentialLimits.PASSWORD &&
            payload.url.length <= CredentialLimits.URL &&
            payload.notes.length <= CredentialLimits.NOTES
        if (!valid) return null
        return Credential(id, payload.title, payload.username, payload.password, payload.url, payload.notes, payload.favorite)
    }

    /** Ties a ciphertext to its record, so it can't be moved to another row unnoticed. */
    fun associatedData(id: String, schemaVersion: Int): ByteArray =
        "keeper/credential/v$schemaVersion;id=$id".toByteArray(Charsets.UTF_8)
}
