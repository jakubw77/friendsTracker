package com.example

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object DistanceMatrixService {
    private const val TAG = "DistanceMatrixService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class MatrixResult(
        val distanceText: String,
        val durationText: String,
        val distanceValueMeters: Int,
        val durationValueSeconds: Int
    )

    /**
     * Fetches driving distance and estimated time between an origin and a destination coordinate.
     * Returns null if API key is invalid, request fails, or no route can be found.
     */
    fun fetchDrivingTime(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        apiKey: String
    ): MatrixResult? {
        if (apiKey.isBlank() || apiKey == "AIzaSy-placeholder") {
            Log.d(TAG, "Distance Matrix API Key is blank or placeholder. Skipping live fetch.")
            return null
        }

        val url = "https://maps.googleapis.com/maps/api/distancematrix/json" +
                "?origins=$originLat,$originLon" +
                "&destinations=$destLat,$destLon" +
                "&mode=driving" +
                "&key=$apiKey"

        Log.d(TAG, "Requesting Distance Matrix: $url")
        val request = Request.Builder()
            .url(url)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Network request failed: HTTP ${response.code}")
                    return null
                }

                val bodyString = response.body?.string() ?: return null
                Log.d(TAG, "Response from Google: $bodyString")
                
                val json = JSONObject(bodyString)
                val status = json.optString("status", "")
                if (status != "OK") {
                    Log.e(TAG, "Google Maps API Status error: $status")
                    return null
                }

                val rows = json.optJSONArray("rows") ?: return null
                if (rows.length() == 0) return null
                
                val elements = rows.getJSONObject(0).optJSONArray("elements") ?: return null
                if (elements.length() == 0) return null

                val element = elements.getJSONObject(0)
                val elementStatus = element.optString("status", "")
                if (elementStatus != "OK") {
                    Log.e(TAG, "Destination status error: $elementStatus")
                    return null
                }

                val distanceJson = element.optJSONObject("distance") ?: return null
                val durationJson = element.optJSONObject("duration") ?: return null

                val distText = distanceJson.optString("text", "")
                val distValue = distanceJson.optInt("value", 0)
                val durText = durationJson.optString("text", "")
                val durValue = durationJson.optInt("value", 0)

                return MatrixResult(
                    distanceText = distText,
                    durationText = durText,
                    distanceValueMeters = distValue,
                    durationValueSeconds = durValue
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network connection error calling Distance Matrix: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Distance Matrix JSON: ${e.message}", e)
        }

        return null
    }
}
