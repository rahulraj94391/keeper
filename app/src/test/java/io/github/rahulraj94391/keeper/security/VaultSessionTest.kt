package io.github.rahulraj94391.keeper.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSessionTest {

    private val session = VaultSession()

    @Test
    fun startsClosedAndRefusesKeyAccess() {
        assertFalse(session.isOpen)
        assertThrows(VaultLockedException::class.java) { session.withKey { } }
    }

    @Test
    fun openSessionProvidesTheKey() {
        session.open(ByteArray(32) { 7 })

        assertTrue(session.isOpen)
        assertArrayEquals(ByteArray(32) { 7 }, session.withKey { it.copyOf() })
    }

    @Test
    fun closeWipesTheKey() {
        val key = ByteArray(32) { 7 }
        session.open(key)

        session.close()

        assertFalse(session.isOpen)
        assertTrue(key.all { it == 0.toByte() })
        assertThrows(VaultLockedException::class.java) { session.withKey { } }
    }

    @Test
    fun reopeningWipesThePreviousKey() {
        val first = ByteArray(32) { 1 }
        session.open(first)

        session.open(ByteArray(32) { 2 })

        assertTrue(first.all { it == 0.toByte() })
    }

    @Test
    fun wrongKeySizeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { session.open(ByteArray(16)) }
    }
}
