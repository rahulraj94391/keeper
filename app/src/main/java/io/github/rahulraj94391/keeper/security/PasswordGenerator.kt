package io.github.rahulraj94391.keeper.security

import java.security.SecureRandom

enum class CharacterGroup(val characters: String) {
    Lowercase("abcdefghijklmnopqrstuvwxyz"),
    Uppercase("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    Digits("0123456789"),
    Symbols("!@#$%^&*()-_=+[]{};:,.?/~"),
}

data class PasswordOptions(
    val length: Int = PasswordGenerator.DEFAULT_LENGTH,
    val groups: Set<CharacterGroup> = CharacterGroup.entries.toSet(),
) {
    init {
        require(length in PasswordGenerator.LENGTH_RANGE) { "Length must be in ${PasswordGenerator.LENGTH_RANGE}" }
        require(groups.isNotEmpty()) { "At least one character group is required" }
    }
}

/**
 * Generates passwords offline (PRD 6.5). Only [SecureRandom] is accepted, and
 * [SecureRandom.nextInt] with a bound is unbiased, so every character is equally likely.
 * Generated values are never logged.
 */
class PasswordGenerator(private val random: SecureRandom = SecureRandom()) {

    fun generate(options: PasswordOptions): String {
        val groups = options.groups.sortedBy { it.ordinal }
        val pool = groups.joinToString("") { it.characters }
        val chars = CharArray(options.length)
        // One character from each selected group guarantees every group appears...
        groups.forEachIndexed { i, group -> chars[i] = group.characters.randomChar() }
        // ...and the rest come from the combined pool.
        for (i in groups.size until chars.size) chars[i] = pool.randomChar()
        // Shuffle so the guaranteed characters don't sit at predictable positions.
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            chars[i] = chars[j].also { chars[j] = chars[i] }
        }
        return String(chars).also { chars.fill('\u0000') }
    }

    private fun String.randomChar(): Char = this[random.nextInt(length)]

    companion object {
        const val DEFAULT_LENGTH = 20
        val LENGTH_RANGE = 16..64
    }
}
