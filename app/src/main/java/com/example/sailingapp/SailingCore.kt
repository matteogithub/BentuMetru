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
    val vetoReason: String? = null,
    val isThunderstorm: Boolean = false
)

data class LocationItem(
    val name: String, val region: String, val country: String,
    val latitude: Double, val longitude: Double
) {
    val displayName: String get() =
        if (region.isNotEmpty()) "$name ($region), $country" else "$name, $country"
}

private const val LOCATION_MATCH_EPSILON = 0.0001

fun LocationItem.isSameSpot(other: LocationItem): Boolean =
    Math.abs(latitude - other.latitude) < LOCATION_MATCH_EPSILON &&
        Math.abs(longitude - other.longitude) < LOCATION_MATCH_EPSILON

data class SailingThresholds(
    val windMin: Double,
    val windVeto: Double,
    val gustVeto: Double,
    val waveMax: Double,
    val steepMax: Double,
    val idealLow: Double,
    val idealHigh: Double,
    val comfortMax: Double
)

enum class SailingProfile(
    val label: String,
    val description: String,
    val thresholds: SailingThresholds
) {
    PRUDENTE(
        "🟦 Prudente",
        "Per famiglie, neofiti o uscite rilassanti. Soglie molto cautelative.",
        SailingThresholds(4.0, 14.0, 16.0, 0.8, 0.06, 6.0, 12.0, 16.0)
    ),
    CROCIERA(
        "🟩 Crociera",
        "Per velisti medi e imbarcazioni da diporto. Equilibrio tra sicurezza e divertimento.",
        SailingThresholds(3.5, 18.0, 20.0, 1.5, 0.08, 6.0, 16.0, 20.0)
    ),
    SPORTIVO(
        "🟥 Sportivo",
        "Per esperti e barche performanti. Soglie alte per vento forte e condizioni impegnative.",
        SailingThresholds(3.0, 22.0, 28.0, 2.0, 0.12, 5.0, 20.0, 28.0)
    );

    companion object {
        fun fromLabel(label: String): SailingProfile {
            return entries.find { it.label == label } ?: CROCIERA
        }
    }
}

val THUNDERSTORM_CODES = setOf(95, 96, 99)
const val DEFAULT_COASTAL_PERIOD = 4.0

fun getWindDirection(degrees: Int): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW", "N")
    val index = Math.round((degrees.toDouble() % 360) / 45).toInt()
    return directions[index]
}

fun windComfort(windKnots: Double, windGusts: Double, t: SailingThresholds): Double {
    val effectiveWind = maxOf(windKnots, windGusts)
    return when {
        effectiveWind < t.windMin -> (effectiveWind / t.windMin) * 40.0
        effectiveWind < t.idealLow -> 40.0 + (effectiveWind - t.windMin) / (t.idealLow - t.windMin) * 60.0
        effectiveWind <= t.idealHigh -> 100.0
        effectiveWind <= t.comfortMax -> 100.0 - (effectiveWind - t.idealHigh) / (t.comfortMax - t.idealHigh) * 100.0
        else -> 0.0
    }
}

fun waveComfort(heightM: Double, periodS: Double?, t: SailingThresholds): Double {
    val effectivePeriod = periodS?.takeIf { it > 0 } ?: DEFAULT_COASTAL_PERIOD
    val steepness = heightM / (effectivePeriod * effectivePeriod)
    return when {
        steepness <= 0.02 -> 100.0
        steepness <= 0.06 -> 100.0 - (steepness - 0.02) / 0.04 * 60.0
        steepness <= t.steepMax -> 40.0 - (steepness - 0.06) / (t.steepMax - 0.06) * 40.0
        else -> 0.0
    }
}

fun getSailingFlag(
    windKnots: Double,
    windGusts: Double,
    waveHeightM: Double,
    wavePeriodS: Double?,
    rainProb: Int,
    isThunderstorm: Boolean,
    t: SailingThresholds = SailingProfile.CROCIERA.thresholds
): Pair<FlagColor, String?> {
    if (windKnots < t.windMin)
        return FlagColor.RED to "Bonaccia (< ${t.windMin} nodi)"
    if (windKnots > t.windVeto)
        return FlagColor.RED to "Vento forte (> ${t.windVeto.toInt()} nodi)"
    if (windGusts > t.gustVeto)
        return FlagColor.RED to "Raffica pericolosa (> ${t.gustVeto.toInt()} nodi)"
    if (waveHeightM > t.waveMax)
        return FlagColor.RED to "Onda enorme (> ${t.waveMax} m)"

    val effectivePeriod = wavePeriodS?.takeIf { it > 0 } ?: DEFAULT_COASTAL_PERIOD
    val steepness = waveHeightM / (effectivePeriod * effectivePeriod)
    if (steepness > t.steepMax)
        return FlagColor.RED to "Onda troppo ripida"

    if (isThunderstorm)
        return FlagColor.RED to "Temporale"

    val cWind = windComfort(windKnots, windGusts, t)
    val cWave = waveComfort(waveHeightM, wavePeriodS, t)
    val baseScore = (cWind / 100.0) * (cWave / 100.0) * 100.0
    val finalScore = baseScore * (1.0 - 0.3 * (rainProb / 100.0))

    val flag = when {
        finalScore >= 80.0 -> FlagColor.GREEN
        finalScore >= 60.0 -> FlagColor.YELLOW
        finalScore >= 40.0 -> FlagColor.ORANGE
        else -> FlagColor.RED
    }

    return flag to null
}
