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

    private val _weatherState = MutableStateFlow(RealWeatherData())
    val weatherState: StateFlow<RealWeatherData> = _weatherState.asStateFlow()

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

    fun clearSelectedCity() {
        prefs.edit().remove(KEY_CITY_NAME).remove(KEY_CITY_LAT).remove(KEY_CITY_LON).apply()
        _selectedCity.value = null
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
     */
    suspend fun refreshWeather(): RealWeatherData = withContext(Dispatchers.IO) {
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
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code"
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
    }
}
