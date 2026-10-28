package dev.pcoffline.meadow.data

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The weather in the sky: off unless turned on, rough location only, and nothing kept of where. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WeatherTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var store: LocalStore
    private var clock = 1_000_000_000L
    private val asked = mutableListOf<String>()
    private var answer: String? = """{"current":{"time":"2026-10-28T09:00","interval":900,"weather_code":61}}"""
    private var located = 0

    private val here = Location("network").apply {
        latitude = 51.507_351
        longitude = -0.127_758
    }

    private fun repository(location: Location? = here) = WeatherRepository(
        app, store,
        locate = { located++; location },
        fetch = { asked += it; answer },
        now = { clock },
    )

    @Before
    fun clean() {
        app.getSharedPreferences("meadow_store", Context.MODE_PRIVATE).edit().clear().commit()
        store = LocalStore(app)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    @Test
    fun `every WMO code reads as the sky it describes`() {
        assertEquals(Weather.CLEAR, Weather.fromWmo(0))
        assertEquals(Weather.CLEAR, Weather.fromWmo(1))
        assertEquals(Weather.PARTLY_CLOUDY, Weather.fromWmo(2))
        assertEquals(Weather.CLOUDY, Weather.fromWmo(3))
        assertEquals(Weather.FOG, Weather.fromWmo(45))
        assertEquals(Weather.FOG, Weather.fromWmo(48))
        listOf(51, 55, 61, 65, 67, 80, 82).forEach { assertEquals("code $it", Weather.RAIN, Weather.fromWmo(it)) }
        listOf(71, 75, 77, 85, 86).forEach { assertEquals("code $it", Weather.SNOW, Weather.fromWmo(it)) }
        listOf(95, 96, 99).forEach { assertEquals("code $it", Weather.STORM, Weather.fromWmo(it)) }
        assertNull(Weather.fromWmo(4))
        assertNull(Weather.fromWmo(100))
    }

    @Test
    fun `only a rounded area leaves the phone, about 11 km across`() {
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=51.5&longitude=-0.1&current=weather_code",
            OpenMeteo.url(51.507_351, -0.127_758),
        )
        assertEquals(-33.9, OpenMeteo.round(-33.868_820), 0.0)
        assertEquals(0.0, OpenMeteo.round(0.04), 0.0)
    }

    @Test
    fun `an answer without a known weather code is ignored`() {
        assertEquals(Weather.STORM, OpenMeteo.parse("""{"current":{"weather_code":95}}"""))
        assertNull(OpenMeteo.parse("""{"current":{"weather_code":4}}"""))
        assertNull(OpenMeteo.parse("""{"current":{}}"""))
        assertNull(OpenMeteo.parse("""{"error":true,"reason":"Latitude must be in range"}"""))
        assertNull(OpenMeteo.parse("not json"))
    }

    @Test
    fun `while it's off, the location isn't read and nothing is asked`() = runBlocking {
        repository().refreshIfStale()
        assertEquals(0, located)
        assertTrue(asked.isEmpty())
        assertNull(store.weather.value)
    }

    @Test
    fun `turned on, it reads the weather, and keeps when but not where`() = runBlocking {
        val weather = repository()
        weather.turnOn()
        weather.refreshIfStale()
        assertEquals(listOf(OpenMeteo.url(here.latitude, here.longitude)), asked)
        assertEquals(WeatherReading(Weather.RAIN, clock), store.weather.value)
        val kept = app.getSharedPreferences("meadow_store", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse("the location was kept: $kept", "51." in kept || "-0.1" in kept)
        assertEquals(Weather.RAIN, weather.current(store.weather.value, on = true, at = clock))
    }

    @Test
    fun `it's read again only after half an hour, and shown for three`() = runBlocking {
        val weather = repository()
        weather.turnOn()
        weather.refreshIfStale()
        clock += WeatherRepository.REFRESH_AFTER_MS - 1
        weather.refreshIfStale()
        assertEquals(1, asked.size)
        clock += 1
        answer = """{"current":{"weather_code":71}}"""
        weather.refreshIfStale()
        assertEquals(2, asked.size)
        assertEquals(Weather.SNOW, store.weather.value?.weather)

        val reading = store.weather.value
        assertEquals(Weather.SNOW, weather.current(reading, on = true, at = clock + WeatherRepository.SHOW_FOR_MS))
        assertNull(weather.current(reading, on = true, at = clock + WeatherRepository.SHOW_FOR_MS + 1))
        assertNull(weather.current(reading, on = false, at = clock))
        // A clock turned back doesn't keep an old sky up forever.
        assertNull(weather.current(reading, on = true, at = clock - 1))
    }

    @Test
    fun `no location, no answer or a failed request keeps the last weather`() = runBlocking {
        repository().apply { turnOn(); refreshIfStale() }
        val last = store.weather.value
        clock += WeatherRepository.REFRESH_AFTER_MS
        repository(location = null).refreshIfStale()
        answer = null
        repository().refreshIfStale()
        WeatherRepository(app, store, locate = { here }, fetch = { throw java.io.IOException("offline") }, now = { clock }).refreshIfStale()
        WeatherRepository(app, store, locate = { throw SecurityException("revoked") }, fetch = { answer }, now = { clock }).refreshIfStale()
        assertEquals(last, store.weather.value)
    }

    @Test
    fun `turning it off forgets the last weather`() = runBlocking {
        val weather = repository()
        weather.turnOn()
        weather.refreshIfStale()
        weather.turnOff()
        assertFalse(store.weatherOn.value)
        assertNull(store.weather.value)
        assertNull(LocalStore(app).weather.value)
    }

    @Test
    fun `turned off while the forecast is on its way, it isn't kept`() = runBlocking {
        lateinit var weather: WeatherRepository
        weather = WeatherRepository(app, store, locate = { here }, fetch = { weather.turnOff(); answer }, now = { clock })
        weather.turnOn()
        weather.refreshIfStale()
        assertNull(store.weather.value)
    }

    @Test
    fun `taking the location permission away turns it off`() = runBlocking {
        val weather = repository()
        weather.turnOn()
        weather.refreshIfStale()
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        clock += WeatherRepository.REFRESH_AFTER_MS
        weather.refreshIfStale()
        assertFalse(weather.hasPermission())
        assertFalse(store.weatherOn.value)
        assertNull(store.weather.value)
        assertEquals(1, located)
    }

    @Test
    fun `it survives a restart, and a wipe turns it off`() {
        repository().apply { turnOn() }
        store.setWeather(WeatherReading(Weather.FOG, clock))
        val restarted = LocalStore(app)
        assertTrue(restarted.weatherOn.value)
        assertEquals(WeatherReading(Weather.FOG, clock), restarted.weather.value)
        restarted.clearAll()
        assertFalse(restarted.weatherOn.value)
        assertNull(restarted.weather.value)
        assertFalse(LocalStore(app).weatherOn.value)
    }
}
