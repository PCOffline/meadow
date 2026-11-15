package dev.pcoffline.meadow.data

import android.content.Context
import dev.pcoffline.meadow.R
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * When the sun rises and sets on a day, in minutes after local midnight. The theme's Clock look
 * and the sky follow it: night is from sunset to sunrise. [polar] is set on a day the sun doesn't
 * rise, or doesn't set, far in the north or south.
 */
data class SunTimes(val riseMinute: Int, val setMinute: Int, val polar: Polar? = null) {
    enum class Polar { DAY, NIGHT }

    /** Whether it is night at [minute] after midnight. */
    fun isNight(minute: Int): Boolean = when (polar) {
        Polar.DAY -> false
        Polar.NIGHT -> true
        null -> if (riseMinute <= setMinute) minute < riseMinute || minute >= setMinute else minute in setMinute until riseMinute
    }

    /** How long the sun is up, in minutes. */
    val dayLength: Int get() = when (polar) {
        Polar.DAY -> DAY
        Polar.NIGHT -> 0
        null -> (setMinute - riseMinute).mod(DAY)
    }

    /** How far through the day (0 at sunrise, 1 at sunset) or the night (0 at sunset, 1 at sunrise) [minute] is. */
    fun progress(minute: Int): Float = when {
        polar != null -> minute / DAY.toFloat()
        !isNight(minute) -> (minute - riseMinute).mod(DAY) / dayLength.toFloat()
        else -> (minute - setMinute).mod(DAY) / (DAY - dayLength).toFloat()
    }

    companion object {
        const val DAY = 24 * 60

        /** When Meadow doesn't know where the phone is: 05:00 to 19:00. */
        val DEFAULT = SunTimes(5 * 60, 19 * 60)

        /**
         * Sunrise and sunset on [date] at [latitude] and [longitude], in [zone]'s local time. It
         * uses NOAA's solar equations, which are right to a minute or two away from the poles.
         */
        fun at(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes {
            val gamma = 2 * PI / 365 * (date.dayOfYear - 1 + 0.5)
            val equation = 229.18 * (
                0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
                )
            val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) +
                0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
            val lat = Math.toRadians(latitude)
            // The sun's centre 0.833 degrees below the horizon: its top edge, bent by the air.
            val cosHour = cos(Math.toRadians(90.833)) / (cos(lat) * cos(declination)) - tan(lat) * tan(declination)
            if (cosHour > 1) return SunTimes(0, 0, Polar.NIGHT)
            if (cosHour < -1) return SunTimes(0, DAY, Polar.DAY)
            val hourAngle = Math.toDegrees(acos(cosHour))
            val offset = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 60
            val rise = 720 - 4 * (longitude + hourAngle) - equation + offset
            val set = 720 - 4 * (longitude - hourAngle) - equation + offset
            return SunTimes(Math.round(rise).toInt().mod(DAY), Math.round(set).toInt().mod(DAY))
        }

        /**
         * Today's sun times: at [place] when Meadow knows where the phone is, else at the main
         * place of the phone's time zone, else [DEFAULT].
         */
        fun today(context: Context, place: Place?, zone: ZoneId = ZoneId.systemDefault(), date: LocalDate = LocalDate.now(zone)): SunTimes {
            val where = place ?: ZonePlaces.of(context, zone.id) ?: return DEFAULT
            return at(date, where.latitude, where.longitude, zone)
        }
    }
}

/** A rough place on Earth, in degrees. */
data class Place(val latitude: Double, val longitude: Double)

/**
 * The main place of each time zone, from the public-domain tz database's zone.tab and
 * zone1970.tab, kept in res/raw/zone_places.tsv.
 */
object ZonePlaces {
    @Volatile private var places: Map<String, Place>? = null

    /**
     * The main place of [zone]. A phone can name its zone by an older name ("Asia/Calcutta" for
     * "Asia/Kolkata"): then it is the place of the zone that Android says is the same one.
     */
    fun of(context: Context, zone: String): Place? {
        val all = load(context)
        all[zone]?.let { return it }
        return renamed.getOrPut(zone) {
            val same = canonical(zone) ?: return@getOrPut NONE
            all[same] ?: all.entries.firstOrNull { canonical(it.key) == same }?.value ?: NONE
        }.takeIf { it !== NONE }
    }

    /** Zones looked up by another name, so the table is searched once for each. */
    private val renamed = java.util.concurrent.ConcurrentHashMap<String, Place>()
    private val NONE = Place(Double.NaN, Double.NaN)

    private fun canonical(zone: String): String? =
        runCatching { android.icu.util.TimeZone.getCanonicalID(zone) }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun load(context: Context): Map<String, Place> = places ?: runCatching {
        context.resources.openRawResource(R.raw.zone_places).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val (name, lat, lon) = line.split('\t').takeIf { it.size == 3 } ?: return@mapNotNull null
                name to Place(lat.toDouble(), lon.toDouble())
            }.toMap()
        }
    }.getOrDefault(emptyMap()).also { places = it }
}
