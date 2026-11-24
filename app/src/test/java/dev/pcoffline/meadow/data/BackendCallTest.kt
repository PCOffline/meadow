package dev.pcoffline.meadow.data

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Calls to the backend's functions on the emulators: a function that isn't there, and a full change asked for. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class BackendCallTest : EmulatorTest() {
    @Test
    fun `a function that isn't there fails with its code and no words of the backend`() {
        person("Robin")
        val failed = assertThrows(Backend.CallFailed::class.java) { onBackend { backend.call("noSuchFunction") } }
        assertEquals("", failed.message)
        assertFalse(failed.unreachable)
        assertEquals(FirebaseFunctionsException.Code.NOT_FOUND, failed.code)
    }

    @Test
    fun `a change with hours, days' own limits and caps reaches the Bumblebee whole, even when today's use can't be read`() {
        val robin = person("Robin")
        val bee = person("Bee")
        makeBumblebee(robin, bee)
        signInAs(robin)
        val requests = RequestRepository(backend) { error("The usage log can't be read") }
        val hours = Schedule.everyDay(TimeWindow(9 * 60, 17 * 60))
        val cap = TimeCap(listOf(1, 2, 3, 4, 5), 600, 960, 30)
        onBackend {
            LimitsRepository(backend, store, requests).save(AppLimit(APP, "Pebbles", 30, 0))
            requests.create(
                RequestType.CHANGE_LIMIT, packageName = APP, appName = "Pebbles", dailyMinutes = 20,
                schedule = hours, dayMinutes = mapOf(6 to 60), caps = listOf(cap),
            )
            val asked = requests.outgoing(robin.uid, fresh = true).firstWhere { list -> list.any { it.type == RequestType.CHANGE_LIMIT } }
                .single { it.type == RequestType.CHANGE_LIMIT }
            assertEquals(hours, asked.schedule)
            assertEquals(mapOf(6 to 60), asked.dayMinutes)
            assertEquals(listOf(cap), asked.caps)
        }
    }

    @Test
    fun `signed out, there are no changes to withdraw`() {
        onBackend {
            backend.auth.signOut()
            RequestRepository(backend).withdrawChanges(APP)
        }
    }

    private companion object {
        const val APP = "com.example.pebbles"
    }
}
