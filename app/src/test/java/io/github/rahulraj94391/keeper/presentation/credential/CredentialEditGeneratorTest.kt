package io.github.rahulraj94391.keeper.presentation.credential

import io.github.rahulraj94391.keeper.data.repository.InMemoryCredentialRepository
import io.github.rahulraj94391.keeper.security.CharacterGroup
import io.github.rahulraj94391.keeper.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CredentialEditGeneratorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = CredentialEditViewModel(null, InMemoryCredentialRepository())

    @Test
    fun open_showsAGeneratedPasswordWithDefaults() {
        viewModel.openGenerator()

        val generator = checkNotNull(viewModel.uiState.generator)
        assertEquals(20, generator.password.length)
        assertEquals(CharacterGroup.entries.toSet(), generator.options.groups)
    }

    @Test
    fun changingLength_regeneratesAtNewLength() {
        viewModel.openGenerator()

        viewModel.onGeneratorLengthChange(32)

        assertEquals(32, viewModel.uiState.generator!!.password.length)
    }

    @Test
    fun lastGroupCannotBeTurnedOff() {
        viewModel.openGenerator()
        viewModel.onGeneratorGroupChange(CharacterGroup.Uppercase, false)
        viewModel.onGeneratorGroupChange(CharacterGroup.Digits, false)
        viewModel.onGeneratorGroupChange(CharacterGroup.Symbols, false)

        viewModel.onGeneratorGroupChange(CharacterGroup.Lowercase, false)

        val generator = viewModel.uiState.generator!!
        assertEquals(setOf(CharacterGroup.Lowercase), generator.options.groups)
        assertTrue(generator.password.all { it.isLowerCase() })
    }

    @Test
    fun use_fillsPasswordFieldMarksChangedAndClosesSheet() {
        viewModel.openGenerator()
        val generated = viewModel.uiState.generator!!.password

        viewModel.useGeneratedPassword()

        assertEquals(generated, viewModel.uiState.form.password)
        assertTrue(viewModel.uiState.hasUnsavedChanges)
        assertNull(viewModel.uiState.generator)
    }

    @Test
    fun optionsAreRememberedBetweenOpenings() {
        viewModel.openGenerator()
        viewModel.onGeneratorLengthChange(40)
        viewModel.closeGenerator()

        viewModel.openGenerator()

        assertEquals(40, viewModel.uiState.generator!!.options.length)
    }
}
