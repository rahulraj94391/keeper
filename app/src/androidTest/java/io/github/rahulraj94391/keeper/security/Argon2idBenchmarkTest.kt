package io.github.rahulraj94391.keeper.security

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Times Argon2id with the default parameters on a device (docs/design.md section 1.4).
 * Logs only timings; the password and salt are fixed fake values.
 */
@RunWith(AndroidJUnit4::class)
class Argon2idBenchmarkTest {

    @Test
    fun defaultParametersTiming() {
        val kdf = Argon2idKeyDerivation()
        val password = "fake benchmark password".toByteArray()
        val salt = ByteArray(16)

        kdf.deriveKey(password, salt, Argon2Params.DEFAULT) // warm-up, not counted
        val millis = List(RUNS) {
            val start = System.nanoTime()
            kdf.deriveKey(password, salt, Argon2Params.DEFAULT)
            (System.nanoTime() - start) / 1_000_000
        }.sorted()

        Log.i(TAG, "device=${Build.MODEL} abi=${Build.SUPPORTED_ABIS.first()} runs=$millis median=${millis[RUNS / 2]}ms")
    }

    private companion object {
        const val TAG = "Argon2idBenchmark"
        const val RUNS = 5
    }
}
