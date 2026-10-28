package dev.pcoffline.meadow.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import dev.pcoffline.meadow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** The weather over the garden. */
enum class Weather {
    CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, STORM;

    companion object {
        /** From a WMO weather code, as Open-Meteo gives it; null for one it doesn't know. */
        fun fromWmo(code: Int): Weather? = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> null
        }
    }
}

/** The last forecast read, and when. Where it was read for is never kept. */
@Serializable
data class WeatherReading(val weather: Weather, val atMillis: Long)

/** Open-Meteo: free, needs no account or key, and keeps no record of who asked. */
object OpenMeteo {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Coordinates are rounded to a tenth of a degree (about 11 km) before they leave the phone:
     * plenty for the weather, and too rough to tell where someone lives.
     */
    fun round(degrees: Double): Double = (degrees * 10).roundToInt() / 10.0

    fun url(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.1f&longitude=%.1f&current=weather_code", round(latitude), round(longitude))

    /** The weather now, or null when the answer doesn't say. */
    fun parse(body: String): Weather? = runCatching {
        Weather.fromWmo(json.parseToJsonElement(body).jsonObject.getValue("current").jsonObject.getValue("weather_code").jsonPrimitive.int)
    }.getOrNull()
}

/**
 * The weather in the sky, for people who turn it on. Their rough location is read on the phone,
 * rounded, and sent only to Open-Meteo; Meadow's own servers never see it, and it isn't kept.
 */
class WeatherRepository(
    private val context: Context,
    private val store: LocalStore,
    private val locate: suspend () -> Location? = { lastLocation(context) },
    private val fetch: suspend (String) -> String? = { httpGet(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val refreshing = Mutex()

    /** The last reading, while the weather is on. */
    val reading: StateFlow<WeatherReading?> get() = store.weather

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The weather to draw: none when it's off, the location permission is gone, or the reading is old. */
    fun current(reading: WeatherReading?, on: Boolean, at: Long = now()): Weather? =
        reading?.takeIf { on && at - it.atMillis in 0..SHOW_FOR_MS }?.weather

    fun turnOn() {
        store.setWeatherOn(true)
    }

    /** Off, and the last reading is forgotten with it. */
    fun turnOff() {
        store.setWeatherOn(false)
    }

    /**
     * Reads the weather again if it's on and the last reading is over half an hour old. Losing
     * the permission turns it off. Anything that goes wrong keeps the last reading.
     */
    suspend fun refreshIfStale(): Unit = refreshing.withLock {
        if (!store.weatherOn.value) return@withLock
        if (!hasPermission()) return@withLock turnOff()
        val last = store.weather.value
        if (last != null && now() - last.atMillis in 0 until REFRESH_AFTER_MS) return@withLock
        val location = runCatching { locate() }.getOrNull() ?: return@withLock
        val body = runCatching { fetch(OpenMeteo.url(location.latitude, location.longitude)) }.getOrNull() ?: return@withLock
        val weather = OpenMeteo.parse(body) ?: return@withLock
        // Turned off while the forecast was on its way: it isn't kept.
        if (store.weatherOn.value) store.setWeather(WeatherReading(weather, now()))
    }

    companion object {
        const val REFRESH_AFTER_MS = 30 * 60_000L
        const val SHOW_FOR_MS = 3 * 60 * 60_000L
        private const val LOCATE_TIMEOUT_MS = 10_000L
        private const val RECENT_ENOUGH_MS = 6 * 60 * 60_000L

        /** The newest rough fix the phone already has, or a fresh one from the network if none is recent. */
        // Lint can't follow the check on the first line.
        @SuppressLint("MissingPermission")
        private suspend fun lastLocation(context: Context): Location? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
            val manager = context.getSystemService(LocationManager::class.java) ?: return null
            if (!LocationManagerCompat.isLocationEnabled(manager)) return null
            // Before Android 12, rough location can't read GPS at all, so each provider is asked on its own.
            val known = manager.getProviders(true).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
            if (known != null && System.currentTimeMillis() - known.time < RECENT_ENOUGH_MS) return known
            if (!manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return known
            return withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    try {
                        LocationManagerCompat.getCurrentLocation(manager, LocationManager.NETWORK_PROVIDER, cancel, ContextCompat.getMainExecutor(context)) {
                            if (cont.isActive) cont.resume(it)
                        }
                    } catch (_: SecurityException) {
                        cont.resume(null)
                    }
                }
            } ?: known
        }

        private suspend fun httpGet(url: String): String? = withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Meadow/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                connection.disconnect()
            }
        }
    }
}
