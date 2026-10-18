package io.github.rahulraj94391.keeper.presentation.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordStrengthTest {

    private fun strength(password: String) = PasswordStrength.of(password, minLength = 12)

    @Test
    fun belowMinimumIsWeak() {
        assertEquals(PasswordStrength.Weak, strength("Sh0rt!pass"))
    }

    @Test
    fun longerAndMoreVariedRanksHigher() {
        assertEquals(PasswordStrength.Fair, strength("abcdefghijkl"))
        assertEquals(PasswordStrength.Strong, strength("abcdefghijklmnop"))
        assertEquals(PasswordStrength.VeryStrong, strength("correct horse battery staple"))
    }
}
