package io.github.rahulraj94391.keeper.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private val generator = PasswordGenerator()

    @Test
    fun producesRequestedLength() {
        for (length in listOf(16, 20, 64)) {
            assertEquals(length, generator.generate(PasswordOptions(length = length)).length)
        }
    }

    @Test
    fun containsEverySelectedGroupAndNothingElse() {
        val groups = setOf(CharacterGroup.Uppercase, CharacterGroup.Digits)
        repeat(200) {
            val password = generator.generate(PasswordOptions(length = 16, groups = groups))
            val allowed = groups.joinToString("") { it.characters }
            assertTrue(password.all { it in allowed })
            groups.forEach { group -> assertTrue(password.any { it in group.characters }) }
        }
    }

    @Test
    fun allGroupsAtMinimumLength_includesEachGroup() {
        repeat(200) {
            val password = generator.generate(PasswordOptions(length = 16))
            CharacterGroup.entries.forEach { group -> assertTrue(password.any { it in group.characters }) }
        }
    }

    @Test
    fun outputsVary() {
        val passwords = List(50) { generator.generate(PasswordOptions()) }.toSet()
        assertEquals(50, passwords.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLengthOutsideRange() {
        PasswordOptions(length = 8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNoGroups() {
        PasswordOptions(groups = emptySet())
    }
}
