package com.example.wallpaper

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise / sunset math (NOAA solar approximation, accurate to a few
 * minutes) plus the day-phase model the dynamic sun wallpaper renders.
 *
 * Location comes from the weather layer (selected city or last GPS fix),
 * persisted in the weather prefs; a sane equinox fallback (6:30 / 18:30)
 * keeps the wallpaper alive with no location at all.
 */
object SunCycle {

    /** Persisted by WeatherService after every successful fetch. */
    const val PREFS = "weather_prefs"
    const val KEY_LAST_LAT = "last_lat"
    const val KEY_LAST_LON = "last_lon"

    data class Daylight(
        val sunriseMillis: Long,
        val sunsetMillis: Long
    )

    fun daylightFor(nowMillis: Long, lat: Double?, lon: Double?): Daylight {
        if (lat == null || lon == null || lat < -89.0 || lat > 89.0 || lon < -179.0 || lon > 179.0) {
            // No location: fixed 6:30 / 18:30 local schedule.
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val sunrise = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 18)
            cal.set(Calendar.MINUTE, 30)
            return Daylight(sunrise, cal.timeInMillis)
        }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        // Fractional year (gamma), radians.
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + (cal.get(Calendar.HOUR_OF_DAY) - 12) / 24.0)

        // Equation of time (minutes) and solar declination (radians).
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma)
            )
        val decl = 0.006918 -
            0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(lat)
        val zenith = Math.toRadians(90.833)

        val cosHa = (cos(zenith) / (cos(latRad) * cos(decl))) - (tan(latRad) * tan(decl))
        val ha = if (cosHa >= 1.0) {
            // Polar night: no sunrise today.
            0.0
        } else if (cosHa <= -1.0) {
            // Midnight sun: no sunset today.
            Math.PI
        } else {
            acos(cosHa)
        }
        val haDeg = Math.toDegrees(ha)

        // Solar noon / sunrise / sunset in UTC minutes.
        val sunriseUtcMin = 720 - 4 * (lon + haDeg) - eqTime
        val sunsetUtcMin = 720 - 4 * (lon - haDeg) - eqTime

        val tz = TimeZone.getDefault()
        val offsetHours = tz.getOffset(nowMillis) / 3_600_000.0

        fun utcMinutesToLocalMillis(minutes: Double): Long {
            val cal2 = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            var localMinutes = minutes + offsetHours * 60.0
            // Keep inside today (wrap polar edge cases into the day).
            localMinutes = localMinutes.coerceIn(0.0, 24.0 * 60.0 - 1.0)
            cal2.add(Calendar.MINUTE, localMinutes.toInt())
            return cal2.timeInMillis
        }

        return Daylight(
            sunriseMillis = utcMinutesToLocalMillis(sunriseUtcMin),
            sunsetMillis = utcMinutesToLocalMillis(sunsetUtcMin)
        )
    }

    /**
     * 0f..1f position of the sun across the sky between sunrise and sunset
     * (before sunrise / after sunset it clamps to the ends).
     */
    fun sunProgress(nowMillis: Long, daylight: Daylight): Float {
        val span = (daylight.sunsetMillis - daylight.sunriseMillis).coerceAtLeast(1)
        return ((nowMillis - daylight.sunriseMillis).toFloat() / span).coerceIn(0f, 1f)
    }

    enum class Phase { NIGHT, DAWN, MORNING, MIDDAY, AFTERNOON, DUSK }

    fun phaseAt(nowMillis: Long, daylight: Daylight): Phase {
        val sunrise = daylight.sunriseMillis
        val sunset = daylight.sunsetMillis
        val span = (sunset - sunrise).coerceAtLeast(1)
        return when {
            nowMillis < sunrise - 40 * 60_000 || nowMillis > sunset + 40 * 60_000 -> Phase.NIGHT
            nowMillis < sunrise + span * 0.10 -> Phase.DAWN
            nowMillis < sunrise + span * 0.35 -> Phase.MORNING
            nowMillis < sunrise + span * 0.65 -> Phase.MIDDAY
            nowMillis < sunrise + span * 0.90 -> Phase.AFTERNOON
            else -> Phase.DUSK
        }
    }

    /** Reads the last known coordinates the weather layer persisted. */
    fun lastCoordinates(context: android.content.Context): Pair<Double, Double>? {
        return try {
            val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            val lat = prefs.getFloat(KEY_LAST_LAT, Float.NaN)
            val lon = prefs.getFloat(KEY_LAST_LON, Float.NaN)
            if (lat.isNaN() || lon.isNaN()) null else lat.toDouble() to lon.toDouble()
        } catch (_: Exception) {
            null
        }
    }
}
