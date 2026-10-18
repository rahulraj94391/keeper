package io.github.rahulraj94391.keeper.debug

import io.github.rahulraj94391.keeper.presentation.credential.CredentialEditViewModel
import io.github.rahulraj94391.keeper.presentation.credential.CredentialForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the debug source set (unit tests use the debug variant). */
class SampleCredentialsTest {

    private val samples = SampleCredentials.all()

    @Test
    fun fortyUniqueSamples() {
        assertEquals(40, samples.size)
        assertEquals(40, samples.map { it.id }.toSet().size)
        assertEquals(40, samples.map { it.title }.toSet().size)
    }

    @Test
    fun everySamplePassesTheSameValidationAsTheForm() {
        samples.forEach {
            val errors = CredentialEditViewModel.validate(
                CredentialForm(it.title, it.username, it.password, it.url, it.notes, it.favorite)
            )
            assertTrue("${it.title} is invalid: $errors", errors.isEmpty)
        }
    }

    @Test
    fun onlyReservedExampleDomainsAreUsed() {
        samples.flatMap { listOf(it.username, it.url) }.filter { "@" in it || it.startsWith("http") }.forEach {
            assertTrue(it, Regex("example\\.(com|org|net)").containsMatchIn(it))
        }
    }
}
