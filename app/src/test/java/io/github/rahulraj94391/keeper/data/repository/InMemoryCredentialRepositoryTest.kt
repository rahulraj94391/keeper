package io.github.rahulraj94391.keeper.data.repository

import io.github.rahulraj94391.keeper.testing.testCredential
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryCredentialRepositoryTest {

    @Test
    fun observeAll_listsFavoritesFirstThenTitlesIgnoringCase() = runTest {
        val repository = InMemoryCredentialRepository(
            listOf(
                testCredential("1", title = "zeta"),
                testCredential("2", title = "Alpha"),
                testCredential("3", title = "middle", favorite = true),
            )
        )

        val titles = repository.observeAll().first().map { it.title }

        assertEquals(listOf("middle", "Alpha", "zeta"), titles)
    }

    @Test
    fun save_replacesCredentialWithSameId() = runTest {
        val repository = InMemoryCredentialRepository(listOf(testCredential("1", title = "Old")))

        repository.save(testCredential("1", title = "New"))

        assertEquals("New", repository.get("1")?.title)
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun delete_makesObservedCredentialNull() = runTest {
        val repository = InMemoryCredentialRepository(listOf(testCredential("1")))

        repository.delete("1")

        assertNull(repository.observe("1").first())
    }
}
