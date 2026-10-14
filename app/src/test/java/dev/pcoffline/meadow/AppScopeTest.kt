package dev.pcoffline.meadow

import android.app.Application
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** Background work that fails must be logged, not take the whole app (and its limits) down. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AppScopeTest {
    @Test
    fun `a failure in the app's scope is logged instead of crashing the app`() = runBlocking {
        val uncaught = mutableListOf<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> synchronized(uncaught) { uncaught += e } }
        val failure = IllegalStateException("sync broke")
        try {
            AppGraph.scope.launch { throw failure }.join()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertTrue(ShadowLog.getLogsForTag("Meadow").any { it.type == Log.ERROR && it.throwable === failure })
        // The scope keeps working for everything else.
        var ran = false
        AppGraph.scope.launch { ran = true }.join()
        assertTrue(ran)
    }
}
