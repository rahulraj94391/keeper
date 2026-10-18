package io.github.rahulraj94391.keeper.presentation.setup

/**
 * A rough guide to nudge users toward longer passphrases. It is not an entropy estimate:
 * it cannot spot dictionary words or reused passwords.
 */
enum class PasswordStrength {
    Weak, Fair, Strong, VeryStrong;

    companion object {
        fun of(password: String, minLength: Int): PasswordStrength {
            val length = password.codePointCount(0, password.length)
            if (length < minLength) return Weak
            val classes = listOf(
                password.any { it.isLowerCase() },
                password.any { it.isUpperCase() },
                password.any { it.isDigit() },
                password.any { !it.isLetterOrDigit() },
            ).count { it }
            // Length matters most; each extra character class is worth about two characters.
            val score = length + 2 * (classes - 1)
            return when {
                score < minLength + 4 -> Fair
                score < minLength + 8 -> Strong
                else -> VeryStrong
            }
        }
    }
}
