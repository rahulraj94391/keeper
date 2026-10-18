package io.github.rahulraj94391.keeper.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardGuardTest {

    private class FakeClipboard : SecretClipboard {
        var text: String? = null
        var id: String? = null
        var sensitive = false

        override fun put(id: String, label: String, text: String, sensitive: Boolean) {
            this.id = id
            this.text = text
            this.sensitive = sensitive
        }

        override fun currentClipId(): String? = id

        override fun clear() {
            id = null
            text = null
        }

        /** Another app copies something. */
        fun otherAppCopies(value: String) {
            id = null
            text = value
        }
    }

    private val clipboard = FakeClipboard()

    private fun TestScope.guard() = ClipboardGuard(clipboard, this, now = { currentTime })

    private fun TestScope.advanceSeconds(seconds: Long) {
        advanceTimeBy(seconds * 1_000)
        runCurrent()
    }

    @Test
    fun clearsOwnClipAfterThirtySeconds() = runTest {
        guard().copy("Password", "fake-secret", sensitive = true)
        assertTrue(clipboard.sensitive)

        advanceSeconds(29)
        assertEquals("fake-secret", clipboard.text)
        advanceSeconds(1)
        assertNull(clipboard.text)
    }

    @Test
    fun neverTouchesWhatAnotherAppCopied() = runTest {
        guard().copy("Password", "fake-secret", sensitive = true)

        advanceSeconds(10)
        clipboard.otherAppCopies("user's own text")
        advanceSeconds(60)

        assertEquals("user's own text", clipboard.text)
    }

    @Test
    fun inBackground_waitsForFocusThenClears() = runTest {
        val guard = guard()
        guard.copy("Password", "fake-secret", sensitive = true)
        guard.onWindowFocusChanged(false)

        advanceSeconds(45)
        assertEquals("fake-secret", clipboard.text) // can't read the clipboard without focus

        guard.onWindowFocusChanged(true)
        assertNull(clipboard.text)
    }

    @Test
    fun regainingFocusEarly_doesNotClearBeforeThirtySeconds() = runTest {
        val guard = guard()
        guard.copy("Password", "fake-secret", sensitive = true)
        guard.onWindowFocusChanged(false)
        advanceSeconds(5)

        guard.onWindowFocusChanged(true)
        assertEquals("fake-secret", clipboard.text)

        advanceSeconds(25)
        assertNull(clipboard.text)
    }

    @Test
    fun newCopyRestartsTheTimer() = runTest {
        val guard = guard()
        guard.copy("Username", "fake-user", sensitive = false)
        advanceSeconds(20)
        guard.copy("Password", "fake-secret", sensitive = true)

        advanceSeconds(20)
        assertEquals("fake-secret", clipboard.text)
        advanceSeconds(10)
        assertNull(clipboard.text)
    }
}
