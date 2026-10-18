package io.github.rahulraj94391.keeper.data.auth

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import io.github.rahulraj94391.keeper.security.ThrottleClock
import io.github.rahulraj94391.keeper.security.ThrottleState
import io.github.rahulraj94391.keeper.security.ThrottleStore

/** Unlock-attempt counter in SharedPreferences. Not secret; excluded from backup like all app data. */
class SharedPrefsThrottleStore(context: Context) : ThrottleStore {
    private val prefs = context.getSharedPreferences("keeper_unlock_throttle", Context.MODE_PRIVATE)

    override fun load() = ThrottleState(
        failedAttempts = prefs.getInt(FAILED, 0),
        lockoutMillis = prefs.getLong(LOCKOUT, 0),
        lockoutStartWallMillis = prefs.getLong(START_WALL, 0),
        lockoutStartElapsedMillis = prefs.getLong(START_ELAPSED, 0),
        lockoutStartBootCount = prefs.getInt(START_BOOT, -1),
    )

    override fun save(state: ThrottleState) {
        // commit = true writes synchronously, so killing the app right after a wrong attempt
        // can't make it forget the failure.
        prefs.edit(commit = true) {
            putInt(FAILED, state.failedAttempts)
            putLong(LOCKOUT, state.lockoutMillis)
            putLong(START_WALL, state.lockoutStartWallMillis)
            putLong(START_ELAPSED, state.lockoutStartElapsedMillis)
            putInt(START_BOOT, state.lockoutStartBootCount)
        }
    }

    private companion object {
        const val FAILED = "failed_attempts"
        const val LOCKOUT = "lockout_ms"
        const val START_WALL = "lockout_start_wall_ms"
        const val START_ELAPSED = "lockout_start_elapsed_ms"
        const val START_BOOT = "lockout_start_boot_count"
    }
}

class AndroidThrottleClock(context: Context) : ThrottleClock {
    private val resolver = context.applicationContext.contentResolver

    override fun wallMillis(): Long = System.currentTimeMillis()

    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)
}
