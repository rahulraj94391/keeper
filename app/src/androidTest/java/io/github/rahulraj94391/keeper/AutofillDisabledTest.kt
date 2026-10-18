package io.github.rahulraj94391.keeper

import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the app-wide autofill opt-out (docs/design.md 5.5). */
@RunWith(AndroidJUnit4::class)
class AutofillDisabledTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun composeAutofillIsOffOnceTheAppStarts() {
        // KeeperApplication.onCreate has run before any instrumented test.
        assertFalse(ComposeUiFlags.isSemanticAutofillEnabled)
    }
}
