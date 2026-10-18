package io.github.rahulraj94391.keeper.data.repository

import io.github.rahulraj94391.keeper.domain.model.CredentialLimits
import io.github.rahulraj94391.keeper.testing.testCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialPayloadCodecTest {

    @Test
    fun roundTripKeepsEveryField() {
        val credential = testCredential("1", title = "Ünïcödé ✓", url = "https://example.com", favorite = true)
            .copy(notes = "line one\nline two", password = " spaces kept ")

        assertEquals(credential, CredentialPayloadCodec.decode("1", CredentialPayloadCodec.encode(credential)))
    }

    @Test
    fun idComesFromTheRecordNotThePayload() {
        val bytes = CredentialPayloadCodec.encode(testCredential("1"))

        assertEquals("other", CredentialPayloadCodec.decode("other", bytes)?.id)
    }

    @Test
    fun malformedPayloadsAreRejected() {
        val bad = listOf(
            "not json",
            "{}",
            """{"title":"t","username":"","password":"p","url":"","notes":""}""", // missing field
            """{"title":"t","username":"","password":"p","url":"","notes":"","favorite":false,"extra":1}""",
            """{"title":" ","username":"","password":"p","url":"","notes":"","favorite":false}""",
            """{"title":"t","username":"","password":"","url":"","notes":"","favorite":false}""",
        )
        bad.forEach { assertNull(it, CredentialPayloadCodec.decode("1", it.toByteArray())) }
    }

    @Test
    fun overLimitFieldsAreRejected() {
        val tooLong = testCredential("1").copy(notes = "n".repeat(CredentialLimits.NOTES + 1))

        assertNull(CredentialPayloadCodec.decode("1", CredentialPayloadCodec.encode(tooLong)))
    }

    @Test
    fun associatedDataDependsOnIdAndSchema() {
        val base = CredentialPayloadCodec.associatedData("a", 1)

        assertFalse(base.contentEquals(CredentialPayloadCodec.associatedData("b", 1)))
        assertFalse(base.contentEquals(CredentialPayloadCodec.associatedData("a", 2)))
    }
}
