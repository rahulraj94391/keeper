package io.github.rahulraj94391.keeper.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The system clipboard, reduced to what [ClipboardGuard] needs. */
interface SecretClipboard {
    /** Copies [text], tagged with [id] so Keeper can recognize its own clip later. */
    fun put(id: String, label: String, text: String, sensitive: Boolean)

    /** The tag of the current clip if Keeper put it there; null for anything else or nothing. */
    fun currentClipId(): String?

    fun clear()
}

/**
 * Best-effort clipboard clearing (PRD 6.6): about 30 seconds after a copy, the clip is removed,
 * but only if it is still exactly the one Keeper copied. Content another app copied since is
 * never touched.
 *
 * Android 10+ lets an app read the clipboard only while it has window focus. If Keeper is in
 * the background when the 30 seconds are up, the check waits until Keeper has focus again.
 */
class ClipboardGuard(
    private val clipboard: SecretClipboard,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val timeout: Duration = 30.seconds,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private class Pending(val id: String, val copiedAt: Long)

    private var pending: Pending? = null
    private var timer: Job? = null
    private var hasFocus = true

    fun copy(label: String, text: String, sensitive: Boolean) {
        val id = newId()
        clipboard.put(id, label, text, sensitive)
        pending = Pending(id, now())
        timer?.cancel()
        timer = scope.launch {
            delay(timeout)
            checkAndClear()
        }
    }

    fun onWindowFocusChanged(focused: Boolean) {
        hasFocus = focused
        if (focused) checkAndClear()
    }

    private fun checkAndClear() {
        val copied = pending ?: return
        if (now() - copied.copiedAt < timeout.inWholeMilliseconds) return
        if (!hasFocus) return // Can't read the clipboard now; try again on focus.
        if (clipboard.currentClipId() == copied.id) clipboard.clear()
        pending = null
    }
}
