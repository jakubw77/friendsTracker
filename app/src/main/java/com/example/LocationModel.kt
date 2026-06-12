package com.example

import kotlin.math.*

data class Friend(
    val id: String,
    val name: String,
    val email: String,
    val initial: String,
    val latitude: Double,
    val longitude: Double,
    val avatarColorHex: String,
    val isSimulatedMoving: Boolean = false,
    val trafficCondition: TrafficCondition = TrafficCondition.NORMAL,
    val customDrivingDistanceText: String? = null,
    val customDrivingDurationText: String? = null,
    val isLiveTrafficFetched: Boolean = false,
    val avatarUrl: String? = null
)

enum class DistanceUnit(val label: String, val suffix: String, val toUnitMultiplier: Double) {
    KILOMETERS("Kilometers", "km", 1.0),
    MILES("Miles", "mi", 0.621371)
}

enum class TrafficCondition(val label: String, val textHexClass: String, val speedFactor: Double) {
    LIGHT("Light Traffic", "#4CAF50", 1.15),
    NORMAL("Normal Traffic", "#FF9800", 1.0),
    HEAVY("Heavy Traffic", "#F44336", 0.65)
}

data class LocationPreset(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

object MapCalculations {
    // Earth radius in kilometers
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculates the great-circle distance between two GPS coordinates using the Haversine formula.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Friends aren't birds; they don't fly straight. Roads are winding.
     * We convert crow-flies straight-line distance into actual driving distance.
     */
    fun getDrivingDistanceKm(directDistanceKm: Double): Double {
        // Typical winding coefficient (circuity factor) is 1.25 to 1.35
        return directDistanceKm * 1.32
    }

    /**
     * Compute driving time based on distance and traffic conditions.
     * Returns the duration in minutes.
     */
    fun calculateDrivingTimeMinutes(drivingDistanceKm: Double, traffic: TrafficCondition): Int {
        if (drivingDistanceKm < 0.05) return 0 // Right next to each other
        
        // Base Speed depending on trip distance
        val baseSpeedKmph = when {
            drivingDistanceKm < 5.0 -> 35.0 // City speeds
            drivingDistanceKm < 50.0 -> 65.0 // Suburban / Arterial
            else -> 98.0 // Highway speeds
        }
        
        val actualSpeedKmph = baseSpeedKmph * traffic.speedFactor
        val timeHours = drivingDistanceKm / actualSpeedKmph
        val totalMinutes = (timeHours * 60).roundToInt()
        
        // Ensure at least 1 minute if distance exists
        return max(1, totalMinutes)
    }

    /**
     * Format minutes into readable driving time (e.g. "2h 15m" or "45 mins")
     */
    fun formatDuration(totalMinutes: Int): String {
        if (totalMinutes == 0) return "Arrived"
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "$mins mins"
        }
    }

    /**
     * Converts distance in Kilometers to the target DistanceUnit
     */
    fun convertKmToUnit(km: Double, unit: DistanceUnit): Double {
        return km * unit.toUnitMultiplier
    }
}

data class GoogleDirectionsData(
    val distanceText: String,
    val durationText: String,
    val distanceValueMeters: Int,
    val durationValueSeconds: Int,
    val points: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false
)
