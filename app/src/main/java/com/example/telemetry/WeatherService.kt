package com.example.telemetry

import android.Manifest
import android.content.Context
import android.content.Intent
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

data class RealWeatherData(
    val temperature: Int = 22,
    val weatherCode: Int = 0,
    val conditionText: String = "Despejado",
    val locationName: String = "Clima Local",
    val isRealGps: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

class WeatherService(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val _weatherState = MutableStateFlow(RealWeatherData())
    val weatherState: StateFlow<RealWeatherData> = _weatherState.asStateFlow()

    suspend fun refreshWeather(): RealWeatherData = withContext(Dispatchers.IO) {
        val location = getBestLastLocation()
        val lat = location?.latitude ?: 40.4168 // Default fallback (e.g. Madrid)
        val lon = location?.longitude ?: -3.7038
        val isGps = location != null

        var cityName = "Clima Local"
        if (location != null) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    cityName = addresses[0].locality ?: addresses[0].subAdminArea ?: addresses[0].adminArea ?: "Mi Ciudad"
                }
            } catch (_: Exception) {}
        }

        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string()
                if (!bodyStr.isNullOrBlank()) {
                    val json = JSONObject(bodyStr)
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").toInt()
                    val code = current.getInt("weather_code")
                    val condition = weatherCodeToSpanish(code)

                    val result = RealWeatherData(
                        temperature = temp,
                        weatherCode = code,
                        conditionText = condition,
                        locationName = cityName,
                        isRealGps = isGps,
                        lastUpdated = System.currentTimeMillis()
                    )
                    _weatherState.value = result
                    return@withContext result
                }
            }
        } catch (_: Exception) {}

        val fallback = _weatherState.value.copy(
            locationName = if (isGps) cityName else _weatherState.value.locationName,
            isRealGps = isGps,
            lastUpdated = System.currentTimeMillis()
        )
        _weatherState.value = fallback
        return@withContext fallback
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
            0 -> "Cielo despejado"
            1, 2, 3 -> "Parcialmente nublado"
            45, 48 -> "Niebla"
            51, 53, 55 -> "Llovizna leve"
            61, 63, 65 -> "Lluvia"
            71, 73, 75 -> "Nieve"
            80, 81, 82 -> "Chubascos"
            95, 96, 99 -> "Tormenta eléctrica"
            else -> "Templado"
        }
    }

    fun openSystemWeatherApp() {
        val weather = _weatherState.value
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com/search?q=clima+${Uri.encode(weather.locationName)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir la app de clima", Toast.LENGTH_SHORT).show()
        }
    }
}
