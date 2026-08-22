package com.example.sailingapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.coroutines.resume

class SailingRepository(private val context: Context) {

    private fun JSONArray.getDoubleOrNull(index: Int): Double? = if (isNull(index)) null else optDouble(index)
    private fun JSONArray.getIntOrNull(index: Int): Int? = if (isNull(index)) null else optInt(index)

    private val prefs = context.getSharedPreferences("bentumetru_prefs", Context.MODE_PRIVATE)
    private val FAVORITES_KEY = "favorite_locations"

    fun getFavorites(): List<LocationItem> {
        val jsonString = prefs.getString(FAVORITES_KEY, null) ?: return emptyList()
        val list = mutableListOf<LocationItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LocationItem(
                        name = obj.getString("name"),
                        region = obj.getString("region"),
                        country = obj.getString("country"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveFavorites(favorites: List<LocationItem>) {
        val jsonArray = JSONArray()
        favorites.forEach { fav ->
            val obj = JSONObject().apply {
                put("name", fav.name)
                put("region", fav.region)
                put("country", fav.country)
                put("latitude", fav.latitude)
                put("longitude", fav.longitude)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(FAVORITES_KEY, jsonArray.toString()).apply()
    }
    private fun readUrlWithTimeout(urlStr: String): String {
        val connection = URL(urlStr).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchMeteoData(lat: Double, lon: Double): List<ForecastItem>? = withContext(Dispatchers.IO) {
        val resultList = mutableListOf<ForecastItem>()
        try {
            val windUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=temperature_2m,wind_speed_10m,wind_gusts_10m,wind_direction_10m,precipitation_probability,weathercode&wind_speed_unit=kn&timezone=auto"
            val waveUrl = "https://marine-api.open-meteo.com/v1/marine?latitude=$lat&longitude=$lon&hourly=wave_height,wave_period&timezone=auto"

            val windResponse = readUrlWithTimeout(windUrl)
            val waveResponse = readUrlWithTimeout(waveUrl)

            val windJsonRoot = JSONObject(windResponse)
            val waveJsonRoot = JSONObject(waveResponse)
            val offsetSeconds = windJsonRoot.getInt("utc_offset_seconds")

            val windJson = windJsonRoot.getJSONObject("hourly")
            val waveJson = waveJsonRoot.getJSONObject("hourly")

            val timeArray = windJson.getJSONArray("time")
            val tempArray = windJson.getJSONArray("temperature_2m")
            val windSpeedArray = windJson.getJSONArray("wind_speed_10m")
            val windGustsArray = windJson.getJSONArray("wind_gusts_10m")
            val windDirArray = windJson.getJSONArray("wind_direction_10m")
            val rainArray = windJson.getJSONArray("precipitation_probability")
            val weathercodeArray = windJson.getJSONArray("weathercode")
            val waveArray = waveJson.getJSONArray("wave_height")
            val wavePeriodArray = waveJson.getJSONArray("wave_period")

            val nowUtc = java.time.Instant.now()
            val zoneOffset = java.time.ZoneOffset.ofTotalSeconds(offsetSeconds)
            val targetDateTime = java.time.LocalDateTime.ofInstant(nowUtc, zoneOffset)
            val searchFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00")

            var startIndex = 0
            for (i in 0 until timeArray.length()) {
                val apiTime = LocalDateTime.parse(timeArray.getString(i))
                if (!apiTime.isBefore(targetDateTime)) {
                    startIndex = i
                    break
                }
            }

            if (startIndex >= timeArray.length()) return@withContext null
            val maxHours = minOf(72, timeArray.length() - startIndex)

            for (i in startIndex until startIndex + maxHours) {
                val fullTimeString = timeArray.getString(i)
                val currentDateTime = LocalDateTime.parse(fullTimeString)
                val dayName = currentDateTime.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
                val dayNumber = currentDateTime.dayOfMonth.toString().padStart(2, '0')
                val monthName = currentDateTime.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
                val displayDate = "$dayName $dayNumber $monthName"
                val time = fullTimeString.substring(11, 16)

                val temperature = tempArray.getDoubleOrNull(i)
                val windSpeed = windSpeedArray.getDoubleOrNull(i)
                val windGust = windGustsArray.getDoubleOrNull(i)
                val wave = waveArray.getDoubleOrNull(i)
                if (temperature == null || windSpeed == null || windGust == null) continue

                val wavePeriod = wavePeriodArray.getDoubleOrNull(i)
                val windDirDegrees = windDirArray.getIntOrNull(i) ?: 0
                val rainProb = rainArray.getIntOrNull(i) ?: 0
                val weatherCode = weathercodeArray.getIntOrNull(i)
                val isThunderstorm = weatherCode != null && weatherCode in THUNDERSTORM_CODES

                val windDirStr = getWindDirection(windDirDegrees)
                val flagResult: Pair<FlagColor?, String?> = if (wave != null) {
                    getSailingFlag(
                        windSpeed, windGust, wave, wavePeriod, rainProb, isThunderstorm,
                        SailingProfile.CROCIERA.thresholds
                    )
                } else {
                    null to null
                }
                val (flag, vetoReason) = flagResult
                resultList.add(ForecastItem(
                    displayDate, time, windSpeed, windGust, windDirStr, windDirDegrees,
                    wave, wavePeriod, rainProb, temperature, flag, vetoReason, isThunderstorm
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
        resultList
    }

    suspend fun searchLocation(query: String): List<LocationItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LocationItem>()
        try {
            val safeQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$safeQuery&count=5&language=it&format=json"
            val response = readUrlWithTimeout(url)
            val jsonObject = JSONObject(response)

            if (jsonObject.has("results")) {
                val resultsArray = jsonObject.getJSONArray("results")
                for (i in 0 until resultsArray.length()) {
                    val item = resultsArray.getJSONObject(i)
                    results.add(LocationItem(
                        item.getString("name"),
                        item.optString("admin1", ""),
                        item.optString("country", ""),
                        item.getDouble("latitude"),
                        item.getDouble("longitude")
                    ))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        results
    }

    suspend fun getCurrentLocation(): LocationItem? {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        val location = getFreshLocation(fusedLocationClient)
            ?: getLastKnownLocation(fusedLocationClient)
            ?: return null

        return withContext(Dispatchers.IO) {
            var name = "Posizione Attuale"
            var region = "GPS"
            var country = ""
            try {
                val geocoder = android.location.Geocoder(context, Locale.ITALIAN)
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    name = address.locality ?: address.subAdminArea ?: "In mare / Zona non mappata"
                    region = address.adminArea ?: ""
                    country = address.countryName ?: ""
                } else {
                    name = "Lat: ${String.format(Locale.US, "%.4f", location.latitude)}°, Lon: ${String.format(Locale.US, "%.4f", location.longitude)}°"
                    region = "Coordinate"
                }
            } catch (e: Exception) {
                name = "Lat: ${String.format(Locale.US, "%.4f", location.latitude)}°, Lon: ${String.format(Locale.US, "%.4f", location.longitude)}°"
                region = "Coordinate"
            }
            LocationItem(name, region, country, location.latitude, location.longitude)
        }
    }

    private suspend fun getFreshLocation(client: FusedLocationProviderClient): android.location.Location? {
        val cancellationTokenSource = CancellationTokenSource()
        return suspendCancellableCoroutine<android.location.Location?> { continuation ->
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { loc -> continuation.resume(loc) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }

    private suspend fun getLastKnownLocation(client: FusedLocationProviderClient): android.location.Location? {
        return suspendCancellableCoroutine<android.location.Location?> { continuation ->
            client.lastLocation.addOnSuccessListener { loc -> continuation.resume(loc) }
                .addOnFailureListener { continuation.resume(null) }
        }
    }
}