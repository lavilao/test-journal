package com.example.telemetry

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A city the user picked manually for the weather card.
 */
data class WeatherCity(
    val name: String,
    val admin: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double
)

data class RealWeatherData(
    /** Temperature in degrees Celsius. Null when no real data could be fetched. */
    val temperature: Int? = null,
    val weatherCode: Int? = null,
    val conditionText: String? = null,
    val locationName: String? = null,
    val isRealGps: Boolean = false,
    val lastUpdated: Long? = null
)

/** One of today's buckets: "Mañana" (6-12), "Tarde" (12-18), "Noche" (18-24). */
data class ForecastBucket(
    val period: String,
    val temperature: Int,
    val condition: String,
    val rainProbable: Boolean
)

/** A day of the weekly outlook. */
data class DailyForecast(
    val dateEpochMs: Long,
    val minTemp: Int,
    val maxTemp: Int,
    val condition: String,
    val rainProbable: Boolean
)

/** Today + week forecast, cached on disk so it survives offline starts. */
data class ForecastData(
    val buckets: List<ForecastBucket> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
    val locationName: String? = null,
    val updatedAt: Long? = null
)

/**
 * Real weather powered by the free Open-Meteo API.
 *
 * Two honest sources of location:
 *  1. The city the user picked manually (persisted; works without any
 *     location permission at all).
 *  2. GPS last-known-location, if the user granted it.
 *
 * If neither is available, [weatherState] holds null data and the UI shows a
 * "choose your city" affordance instead of inventing a temperature.
 */
class WeatherService(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    private val _weatherState = MutableStateFlow(loadCachedWeather())
    val weatherState: StateFlow<RealWeatherData> = _weatherState.asStateFlow()

    private val _forecastState = MutableStateFlow(loadCachedForecast())
    val forecastState: StateFlow<ForecastData> = _forecastState.asStateFlow()

    private val _selectedCity = MutableStateFlow(loadSavedCity())
    val selectedCity: StateFlow<WeatherCity?> = _selectedCity.asStateFlow()

    private fun loadSavedCity(): WeatherCity? {
        val name = prefs.getString(KEY_CITY_NAME, null) ?: return null
        val lat = prefs.getFloat(KEY_CITY_LAT, Float.NaN)
        val lon = prefs.getFloat(KEY_CITY_LON, Float.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        return WeatherCity(
            name = name,
            admin = prefs.getString(KEY_CITY_ADMIN, null),
            country = prefs.getString(KEY_CITY_COUNTRY, null),
            latitude = lat.toDouble(),
            longitude = lon.toDouble()
        )
    }

    fun setSelectedCity(city: WeatherCity) {
        prefs.edit()
            .putString(KEY_CITY_NAME, city.name)
            .putString(KEY_CITY_ADMIN, city.admin)
            .putString(KEY_CITY_COUNTRY, city.country)
            .putFloat(KEY_CITY_LAT, city.latitude.toFloat())
            .putFloat(KEY_CITY_LON, city.longitude.toFloat())
            .apply()
        _selectedCity.value = city
    }

    /** Minutes since the last successful (or cached) weather update. */
    fun cachedAgeMinutes(): Int {
        val ts = _weatherState.value.lastUpdated ?: return Int.MAX_VALUE
        return ((System.currentTimeMillis() - ts) / 60_000L).toInt()
    }

    /**
     * Loads the last successful weather reading from disk so the UI can show
     * real data immediately on cold start (even offline). This is what stops
     * the app from asking for a city again on every launch.
     */
    private fun loadCachedWeather(): RealWeatherData {
        val temp = prefs.getInt(KEY_CACHE_TEMP, Int.MIN_VALUE)
        if (temp == Int.MIN_VALUE) return RealWeatherData()
        return RealWeatherData(
            temperature = temp,
            weatherCode = prefs.getInt(KEY_CACHE_CODE, -1).takeIf { it >= 0 },
            conditionText = prefs.getString(KEY_CACHE_CONDITION, null),
            locationName = prefs.getString(KEY_CACHE_LOCATION, null),
            isRealGps = prefs.getBoolean(KEY_CACHE_IS_GPS, false),
            lastUpdated = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L).takeIf { it > 0L }
        )
    }

    private fun persistWeatherToCache(data: RealWeatherData) {
        val temp = data.temperature ?: run {
            prefs.edit().remove(KEY_CACHE_TEMP).apply()
            return
        }
        prefs.edit()
            .putInt(KEY_CACHE_TEMP, temp)
            .putInt(KEY_CACHE_CODE, data.weatherCode ?: -1)
            .putString(KEY_CACHE_CONDITION, data.conditionText)
            .putString(KEY_CACHE_LOCATION, data.locationName)
            .putBoolean(KEY_CACHE_IS_GPS, data.isRealGps)
            .putLong(KEY_CACHE_TIMESTAMP, data.lastUpdated ?: System.currentTimeMillis())
            .apply()
        // Keep the home-screen widget in sync with the freshest real reading.
        try {
            com.example.widget.AtAGlanceWidgetProvider.refreshAll(context)
        } catch (_: Exception) {
            // Best-effort; the widget also self-updates on its 30-min tick.
        }
    }

    fun clearSelectedCity() {
        prefs.edit().remove(KEY_CITY_NAME).remove(KEY_CITY_LAT).remove(KEY_CITY_LON).apply()
        _selectedCity.value = null
    }

    // ------------------------------------------------------------------
    // Today + week forecast (cached: morning / afternoon / night + 7 days)
    // ------------------------------------------------------------------

    private fun loadCachedForecast(): ForecastData {
        val json = prefs.getString(KEY_FORECAST_JSON, null) ?: return ForecastData()
        return try {
            val root = JSONObject(json)
            ForecastData(
                buckets = parseBuckets(root),
                daily = parseDaily(root),
                locationName = root.optString("location"),
                updatedAt = root.optLong("fetched_at", 0L).takeIf { it > 0 }
            )
        } catch (_: Exception) {
            ForecastData()
        }
    }

    private fun persistForecast(json: JSONObject, locationName: String) {
        json.put("location", locationName)
        json.put("fetched_at", System.currentTimeMillis())
        prefs.edit().putString(KEY_FORECAST_JSON, json.toString()).apply()
    }

    private fun parseBuckets(root: JSONObject): List<ForecastBucket> {
        val out = mutableListOf<ForecastBucket>()
        val hourly = root.optJSONObject("hourly") ?: return out
        val times = hourly.optJSONArray("time") ?: return out
        val temps = hourly.optJSONArray("temperature_2m") ?: return out
        val codes = hourly.optJSONArray("weather_code") ?: return out
        val today = java.time.LocalDate.now()
        val windows = listOf(
            "Mañana" to 6..11,
            "Tarde" to 12..17,
            "Noche" to 18..23
        )
        windows.forEach { (label, hours) ->
            var acc = 0.0; var n = 0
            var worstCode = 0
            for (i in 0 until times.length()) {
                val stamp = times.optString(i)
                val hour = try {
                    java.time.LocalDateTime.parse(stamp)
                } catch (_: Exception) {
                    continue
                }
                if (hour.toLocalDate() != today) continue
                if (hour.hour !in hours) continue
                val t = temps.optDouble(i, Double.NaN)
                if (!t.isNaN()) { acc += t; n++ }
                val c = codes.optInt(i, 0)
                if (c > worstCode) worstCode = c
            }
            if (n > 0) {
                out.add(
                    ForecastBucket(
                        period = label,
                        temperature = (acc / n).toInt(),
                        condition = weatherCodeToSpanish(worstCode),
                        rainProbable = worstCode >= 51
                    )
                )
            }
        }
        return out
    }

    private fun parseDaily(root: JSONObject): List<DailyForecast> {
        val out = mutableListOf<DailyForecast>()
        val daily = root.optJSONObject("daily") ?: return out
        val times = daily.optJSONArray("time") ?: return out
        val maxT = daily.optJSONArray("temperature_2m_max") ?: return out
        val minT = daily.optJSONArray("temperature_2m_min") ?: return out
        val codes = daily.optJSONArray("weather_code") ?: return out
        for (i in 0 until times.length()) {
            val day = try {
                java.time.LocalDate.parse(times.optString(i))
            } catch (_: Exception) {
                continue
            }
            out.add(
                DailyForecast(
                    dateEpochMs = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    minTemp = minT.optInt(i, 0),
                    maxTemp = maxT.optInt(i, 0),
                    condition = weatherCodeToSpanish(codes.optInt(i, 0)),
                    rainProbable = codes.optInt(i, 0) >= 51
                )
            )
        }
        return out.take(7)
    }

    /**
     * Search cities by name using the Open-Meteo geocoding API (free, no key).
     */
    suspend fun searchCities(query: String, limit: Int = 8): List<WeatherCity> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${Uri.encode(query.trim())}" +
                "&count=$limit&language=${Locale.getDefault().language}&format=json"
        val results = mutableListOf<WeatherCity>()
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val json = JSONObject(bodyStr)
                        val array = json.optJSONArray("results") ?: return@use
                        for (i in 0 until array.length()) {
                            val item = array.getJSONObject(i)
                            results.add(
                                WeatherCity(
                                    name = item.optString("name", ""),
                                    admin = if (item.has("admin1")) item.getString("admin1") else null,
                                    country = if (item.has("country")) item.getString("country") else null,
                                    latitude = item.getDouble("latitude"),
                                    longitude = item.getDouble("longitude")
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        results
    }

    /**
     * Refresh weather using the selected city (if any) or GPS. Never returns
     * fabricated data: when nothing is available the state simply keeps nulls.
     *
     * The disk cache is served instantly on start; the network is only hit
     * when the cached reading is older than [STALE_AFTER_MINUTES] (or when
     * [force] is true, e.g. pull-to-refresh).
     */
    suspend fun refreshWeather(force: Boolean = false): RealWeatherData = withContext(Dispatchers.IO) {
        if (!force) {
            val ageMinutes = cachedAgeMinutes()
            val hasData = _weatherState.value.temperature != null
            if (hasData && ageMinutes < STALE_AFTER_MINUTES) {
                return@withContext _weatherState.value
            }
        }

        val city = _selectedCity.value

        val gpsLocation = if (city == null) getBestLastLocation() else null

        val lat: Double
        val lon: Double
        val locationName: String
        val isGps: Boolean

        when {
            city != null -> {
                lat = city.latitude
                lon = city.longitude
                locationName = city.name
                isGps = false
            }
            gpsLocation != null -> {
                lat = gpsLocation.latitude
                lon = gpsLocation.longitude
                locationName = resolveCityName(lat, lon)
                isGps = true
            }
            else -> {
                // No city chosen and no GPS permission/fix: stay honest.
                _weatherState.value = RealWeatherData()
                return@withContext _weatherState.value
            }
        }

        try {
            // One request for everything: current + hourly (today buckets) +
            // daily (week outlook). timezone=auto gives local hours, so the
            // morning/afternoon/night split matches the user's clock.
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&hourly=temperature_2m,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=7"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val json = JSONObject(bodyStr)
                        val current = json.getJSONObject("current")
                        val temp = current.getDouble("temperature_2m").toInt()
                        val code = current.getInt("weather_code")
                        val result = RealWeatherData(
                            temperature = temp,
                            weatherCode = code,
                            conditionText = weatherCodeToSpanish(code),
                            locationName = locationName,
                            isRealGps = isGps,
                            lastUpdated = System.currentTimeMillis()
                        )
                        persistWeatherToCache(result)
                        // Cache the today+week forecast JSON (offline reads).
                        try {
                            persistForecast(json, locationName)
                            _forecastState.value = ForecastData(
                                buckets = parseBuckets(json),
                                daily = parseDaily(json),
                                locationName = locationName,
                                updatedAt = System.currentTimeMillis()
                            )
                        } catch (_: Exception) {
                        }
                        // Last coordinates feed the dynamic sun wallpaper
                        // (SunCycle computes sunrise/sunset locally).
                        prefs.edit()
                            .putFloat("last_lat", lat.toFloat())
                            .putFloat("last_lon", lon.toFloat())
                            .apply()
                        _weatherState.value = result
                        return@withContext result
                    }
                }
            }
        } catch (_: Exception) {}

        // Request failed (offline?): keep previous values, only refresh the
        // location label if we know it. Never invent a temperature.
        val previous = _weatherState.value
        val fallback = previous.copy(
            locationName = locationName.ifBlank { previous.locationName },
            isRealGps = isGps,
            lastUpdated = System.currentTimeMillis()
        )
        _weatherState.value = fallback
        fallback
    }

    private fun resolveCityName(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea ?: "Mi Ciudad"
            } else "Mi Ciudad"
        } catch (_: Exception) {
            "Mi Ciudad"
        }
    }

    private fun getBestLastLocation(): Location? {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = lm.getProviders(true)
        var bestLocation: Location? = null

        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            } catch (_: SecurityException) {}
        }
        return bestLocation
    }

    private fun weatherCodeToSpanish(code: Int): String {
        return when (code) {
            0 -> "Despejado"
            1 -> "Mayormente despejado"
            2 -> "Parcialmente nublado"
            3 -> "Nublado"
            45, 48 -> "Niebla"
            51, 53, 55 -> "Llovizna"
            56, 57 -> "Llovizna helada"
            61, 63, 65 -> "Lluvia"
            66, 67 -> "Lluvia helada"
            71, 73, 75 -> "Nieve"
            77 -> "Granos de nieve"
            80, 81, 82 -> "Chubascos"
            85, 86 -> "Chubascos de nieve"
            95 -> "Tormenta"
            96, 99 -> "Tormenta con granizo"
            else -> "Variable"
        }
    }

    fun openSystemWeatherApp() {
        val weather = _weatherState.value
        val place = weather.locationName ?: _selectedCity.value?.name ?: ""
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com/search?q=clima+${Uri.encode(place)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir la app de clima", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val KEY_CITY_NAME = "city_name"
        private const val KEY_CITY_ADMIN = "city_admin"
        private const val KEY_CITY_COUNTRY = "city_country"
        private const val KEY_CITY_LAT = "city_lat"
        private const val KEY_CITY_LON = "city_lon"

        // Disk cache of the last successful weather reading.
        private const val KEY_CACHE_TEMP = "cache_temp"
        private const val KEY_CACHE_CODE = "cache_code"
        private const val KEY_CACHE_CONDITION = "cache_condition"
        private const val KEY_CACHE_LOCATION = "cache_location"
        private const val KEY_CACHE_IS_GPS = "cache_is_gps"
        private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"

        // A cached reading younger than this is served without touching the network.
        private const val STALE_AFTER_MINUTES = 20

        /** Raw forecast JSON (today buckets + week) for offline starts. */
        private const val KEY_FORECAST_JSON = "forecast_json"
    }
}
