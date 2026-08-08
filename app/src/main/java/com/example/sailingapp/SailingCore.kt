package com.example.sailingapp

enum class FlagColor { RED, ORANGE, YELLOW, GREEN }

data class ForecastItem(
    val date: String,
    val time: String,
    val windSpeed: Double,
    val windGust: Double,
    val windDir: String,
    val windDirDegrees: Int,
    val wave: Double,
    val wavePeriod: Double?,
    val rainProb: Int,
    val temperature: Double,
    val flagColor: FlagColor,
    val vetoReason: String? = null  // ← NUOVO CAMPO
)

data class LocationItem(
    val name: String, val region: String, val country: String,
    val latitude: Double, val longitude: Double
) {
    val displayName: String get() =
        if (region.isNotEmpty()) "$name ($region), $country" else "$name, $country"
}

// === SOGLIE DI VETO (RED automatico) ===
const val WIND_MIN_SAILABLE = 3.5
const val WIND_VETO = 20.0
const val WIND_GUST_VETO = 22.0
const val WAVE_MAX_SAFE = 2.0
const val WAVE_STEEPNESS_MAX = 0.08

// === SOGLIE PER CALCOLO COMFORT ===
const val WIND_IDEAL_LOW = 6.0
const val WIND_IDEAL_HIGH = 16.0
const val WIND_MAX_SAFE_COMFORT = 25.0
const val DEFAULT_COASTAL_PERIOD = 4.0

val THUNDERSTORM_CODES = setOf(95, 96, 99)

fun getWindDirection(degrees: Int): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
    val index = Math.round((degrees.toDouble() % 360) / 45).toInt()
    return directions[index]
}

fun windComfort(windKnots: Double, windGusts: Double): Double {
    val effectiveWind = maxOf(windKnots, windGusts)
    return when {
        effectiveWind < WIND_MIN_SAILABLE ->
            (effectiveWind / WIND_MIN_SAILABLE) * 40.0
        effectiveWind < WIND_IDEAL_LOW ->
            40.0 + (effectiveWind - WIND_MIN_SAILABLE) / (WIND_IDEAL_LOW - WIND_MIN_SAILABLE) * 60.0
        effectiveWind <= WIND_IDEAL_HIGH -> 100.0
        effectiveWind <= WIND_MAX_SAFE_COMFORT ->
            100.0 - (effectiveWind - WIND_IDEAL_HIGH) / (WIND_MAX_SAFE_COMFORT - WIND_IDEAL_HIGH) * 100.0
        else -> 0.0
    }
}

fun waveComfort(heightM: Double, periodS: Double?): Double {
    val effectivePeriod = periodS?.takeIf { it > 0 } ?: DEFAULT_COASTAL_PERIOD
    val steepness = heightM / (effectivePeriod * effectivePeriod)
    return when {
        steepness <= 0.02 -> 100.0
        steepness <= 0.06 -> 100.0 - (steepness - 0.02) / 0.04 * 60.0
        steepness <= 0.10 -> 40.0 - (steepness - 0.06) / 0.04 * 40.0
        else -> 0.0
    }
}

/**
 * Calcola il flag e il motivo del veto (se presente).
 * @return Pair(FlagColor, vetoReason) dove vetoReason è null se non c'è veto.
 */
fun getSailingFlag(
    windKnots: Double,
    windGusts: Double,
    waveHeightM: Double,
    wavePeriodS: Double?,
    rainProb: Int,
    isThunderstorm: Boolean
): Pair<FlagColor, String?> {
    // 1. Controllo veti prioritari
    if (windKnots < WIND_MIN_SAILABLE)
        return FlagColor.RED to "Bonaccia (< ${WIND_MIN_SAILABLE} nodi)"
    if (windKnots > WIND_VETO)
        return FlagColor.RED to "Vento forte (> ${WIND_VETO.toInt()} nodi)"
    if (windGusts > WIND_GUST_VETO)
        return FlagColor.RED to "Raffica pericolosa (> ${WIND_GUST_VETO.toInt()} nodi)"
    if (waveHeightM > WAVE_MAX_SAFE)
        return FlagColor.RED to "Onda enorme (> ${WAVE_MAX_SAFE} m)"

    val effectivePeriod = wavePeriodS?.takeIf { it > 0 } ?: DEFAULT_COASTAL_PERIOD
    val steepness = waveHeightM / (effectivePeriod * effectivePeriod)
    if (steepness > WAVE_STEEPNESS_MAX)
        return FlagColor.RED to "Onda troppo ripida"

    if (isThunderstorm)
        return FlagColor.RED to "Temporale"

    // 2. Calcolo comfort (nessun veto)
    val cWind = windComfort(windKnots, windGusts)
    val cWave = waveComfort(waveHeightM, wavePeriodS)
    val baseScore = (cWind / 100.0) * (cWave / 100.0) * 100.0
    val finalScore = baseScore * (1.0 - 0.3 * (rainProb / 100.0))

    val flag = when {
        finalScore >= 80.0 -> FlagColor.GREEN
        finalScore >= 60.0 -> FlagColor.YELLOW
        finalScore >= 40.0 -> FlagColor.ORANGE
        else -> FlagColor.RED
    }

    return flag to null  // Nessun veto
}