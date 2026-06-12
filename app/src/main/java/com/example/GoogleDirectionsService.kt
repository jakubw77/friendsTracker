package com.example

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GoogleDirectionsService {
    private const val TAG = "GoogleDirectionsService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class DirectionsResult(
        val distanceText: String,
        val durationText: String,
        val distanceValueMeters: Int,
        val durationValueSeconds: Int,
        val points: String? = null
    )

    /**
     * Fetches driving distance, estimated time, and route polyline using Google Maps Directions API.
     * Returns null if API key is invalid, request fails, or no route can be found.
     */
    fun fetchDirections(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        apiKey: String
    ): DirectionsResult? {
        if (apiKey.isBlank() || apiKey == "AIzaSy-placeholder") {
            Log.d(TAG, "Directions API Key is blank or placeholder. Skipping live fetch.")
            return null
        }

        val url = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=$originLat,$originLon" +
                "&destination=$destLat,$destLon" +
                "&mode=driving" +
                "&key=$apiKey"

        Log.d(TAG, "Requesting Google Directions: $url")
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
                Log.d(TAG, "Response from Google Directions: $bodyString")
                
                val json = JSONObject(bodyString)
                val status = json.optString("status", "")
                if (status != "OK") {
                    Log.e(TAG, "Google Maps Directions API Status error: $status")
                    return null
                }

                val routes = json.optJSONArray("routes") ?: return null
                if (routes.length() == 0) return null
                
                val route = routes.getJSONObject(0)
                val legs = route.optJSONArray("legs") ?: return null
                if (legs.length() == 0) return null

                val leg = legs.getJSONObject(0)
                val distanceJson = leg.optJSONObject("distance") ?: return null
                val durationJson = leg.optJSONObject("duration") ?: return null

                val distText = distanceJson.optString("text", "")
                val distValue = distanceJson.optInt("value", 0)
                val durText = durationJson.optString("text", "")
                val durValue = durationJson.optInt("value", 0)

                val overviewPolyline = route.optJSONObject("overview_polyline")
                val points = if (overviewPolyline != null && overviewPolyline.has("points")) overviewPolyline.getString("points") else null

                return DirectionsResult(
                    distanceText = distText,
                    durationText = durText,
                    distanceValueMeters = distValue,
                    durationValueSeconds = durValue,
                    points = points
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network connection error calling Google Directions: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Google Directions JSON: ${e.message}", e)
        }

        return null
    }
}
