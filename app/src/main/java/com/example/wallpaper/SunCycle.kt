package com.example.wallpaper

import android.content.Context
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise / sunset math (NOAA solar approximation, accurate to a few
 * minutes) plus the day-phase & weather-condition model used by the dynamic
 * atmospheric background and live wallpaper.
 */
object SunCycle {

    /** Persisted by WeatherService after every successful fetch. */
    const val PREFS = "weather_prefs"
    const val KEY_LAST_LAT = "last_lat"
    const val KEY_LAST_LON = "last_lon"
    const val KEY_CACHE_CODE = "cache_code"

    const val WALLPAPER_PREFS = "sun_wallpaper"
    const val KEY_WALLPAPER_ENABLED = "enabled"
    const val KEY_SURFACE_MODE = "surface_mode"

    enum class AppSurfaceMode {
        /** Matte Notion-style workspace backdrop: 0 FPS idle GPU draw. */
        STATIC_NOTION,
        /** Low-power atmospheric sky on Home (static unless raining/snowing). */
        DYNAMIC_LOW_POWER,
        /** Calm static aurora gradient mesh: 0 FPS idle GPU draw. */
        CLASSIC_AURORA
    }

    data class Daylight(
        val sunriseMillis: Long,
        val sunsetMillis: Long
    )

    enum class Phase { NIGHT, DAWN, MORNING, MIDDAY, AFTERNOON, DUSK }

    enum class AtmosphereWeather {
        CLEAR,
        PARTLY_CLOUDY,
        CLOUDY,
        FOG,
        RAIN,
        SNOW,
        STORM
    }

    fun weatherFromCode(code: Int?): AtmosphereWeather = when (code) {
        null, 0, 1 -> AtmosphereWeather.CLEAR
        2 -> AtmosphereWeather.PARTLY_CLOUDY
        3 -> AtmosphereWeather.CLOUDY
        45, 48 -> AtmosphereWeather.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> AtmosphereWeather.RAIN
        71, 73, 75, 77, 85, 86 -> AtmosphereWeather.SNOW
        95, 96, 99 -> AtmosphereWeather.STORM
        else -> AtmosphereWeather.PARTLY_CLOUDY
    }

    fun getSurfaceMode(context: Context): AppSurfaceMode {
        return try {
            val prefs = context.getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_SURFACE_MODE, null)
            if (raw != null) {
                runCatching { AppSurfaceMode.valueOf(raw) }.getOrDefault(AppSurfaceMode.STATIC_NOTION)
            } else {
                // Migrate legacy toggle only if user explicitly disabled or switched;
                // default to STATIC_NOTION to prevent 60-120fps shader battery drain.
                AppSurfaceMode.STATIC_NOTION
            }
        } catch (_: Exception) {
            AppSurfaceMode.STATIC_NOTION
        }
    }

    fun setSurfaceMode(context: Context, mode: AppSurfaceMode) {
        try {
            context.getSharedPreferences(WALLPAPER_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SURFACE_MODE, mode.name)
                .putBoolean(KEY_WALLPAPER_ENABLED, mode == AppSurfaceMode.DYNAMIC_LOW_POWER)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun isDynamicAtmosphereEnabled(context: Context): Boolean {
        return getSurfaceMode(context) == AppSurfaceMode.DYNAMIC_LOW_POWER
    }

    fun lastWeatherCode(context: Context): Int? {
        return try {
            val code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_CACHE_CODE, -1)
            code.takeIf { it >= 0 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true when the upper sky rendered for (phase, weather, systemDark)
     * is dark enough that header text drawn directly on the sky should use
     * luminous light ink instead of dark ink.
     */
    fun isDarkHeaderBackdrop(
        phase: Phase,
        weather: AtmosphereWeather,
        systemDark: Boolean
    ): Boolean {
        if (systemDark) return true
        return when {
            weather == AtmosphereWeather.FOG -> false
            phase == Phase.NIGHT || weather == AtmosphereWeather.STORM -> true
            weather == AtmosphereWeather.CLOUDY || weather == AtmosphereWeather.SNOW -> false
            else -> true
        }
    }

    fun daylightFor(nowMillis: Long, lat: Double?, lon: Double?): Daylight {
        if (lat == null || lon == null || lat < -89.0 || lat > 89.0 || lon < -179.0 || lon > 179.0) {
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
        val gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1 + (cal.get(Calendar.HOUR_OF_DAY) - 12) / 24.0)

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
        val ha = when {
            cosHa >= 1.0 -> 0.0
            cosHa <= -1.0 -> Math.PI
            else -> acos(cosHa)
        }
        val haDeg = Math.toDegrees(ha)

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
            val localMinutes = (minutes + offsetHours * 60.0).coerceIn(0.0, 24.0 * 60.0 - 1.0)
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
     * (at night, tracks an analogous nocturnal arc from sunset to sunrise).
     */
    fun sunProgress(nowMillis: Long, daylight: Daylight): Float {
        val span = (daylight.sunsetMillis - daylight.sunriseMillis).coerceAtLeast(1)
        return ((nowMillis - daylight.sunriseMillis).toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * 0f..1f nocturnal arc for the moon between sunset and next sunrise.
     */
    fun moonProgress(nowMillis: Long, daylight: Daylight): Float {
        val dayMs = 86_400_000L
        return when {
            nowMillis >= daylight.sunsetMillis -> {
                val nextSunrise = daylight.sunriseMillis + dayMs
                val nightSpan = (nextSunrise - daylight.sunsetMillis).coerceAtLeast(1L)
                ((nowMillis - daylight.sunsetMillis).toFloat() / nightSpan).coerceIn(0f, 1f)
            }
            nowMillis <= daylight.sunriseMillis -> {
                val prevSunset = daylight.sunsetMillis - dayMs
                val nightSpan = (daylight.sunriseMillis - prevSunset).coerceAtLeast(1L)
                ((nowMillis - prevSunset).toFloat() / nightSpan).coerceIn(0f, 1f)
            }
            else -> 0.5f
        }
    }

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
    fun lastCoordinates(context: Context): Pair<Double, Double>? {
        return try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lat = prefs.getFloat(KEY_LAST_LAT, Float.NaN)
            val lon = prefs.getFloat(KEY_LAST_LON, Float.NaN)
            if (lat.isNaN() || lon.isNaN()) null else lat.toDouble() to lon.toDouble()
        } catch (_: Exception) {
            null
        }
    }
}
