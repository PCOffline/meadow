package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Offline, a write Firestore has kept for later doesn't hold up whoever made it. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class OfflineWriteTest {
    @After
    fun backOnline() = Connectivity.set(true)

    @Test
    fun `offline, a write isn't waited for`() = runTest {
        Connectivity.set(false)
        TaskCompletionSource<Void>().task.awaitWrite()
        assertEquals(0L, currentTime)
    }

    @Test
    fun `online, a write the server never confirms is waited for, but not forever`() = runTest {
        TaskCompletionSource<Void>().task.awaitWrite()
        assertEquals(WRITE_TIMEOUT_MS, currentTime)
    }
}
