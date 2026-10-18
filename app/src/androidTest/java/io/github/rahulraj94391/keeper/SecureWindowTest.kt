package io.github.rahulraj94391.keeper

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The main window blocks screenshots and recent-apps thumbnails (PRD 6.6). */
@RunWith(AndroidJUnit4::class)
class SecureWindowTest {

    @Test
    fun mainWindowHasFlagSecure() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        }
    }
}
