package com.example

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GoogleMapsSharingService {
    private const val TAG = "GoogleMapsSharingService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Calls the unofficial Google Maps preview/locationsharing/read endpoint with the user's cookie.
     * Parses and returns the list of real accounts sharing their location with the user.
     */
    fun fetchLiveGoogleMapsSharing(cookieString: String): List<Friend> {
        if (cookieString.isBlank()) {
            Log.d(TAG, "Cookie string is blank")
            return emptyList()
        }

        // Clean up cookie string. If they only paste the __Secure-1PSID/3PSID value, we construct a cookie header.
        val headerCookieValue = if (!cookieString.contains("=") && cookieString.length > 20) {
            "__Secure-1PSID=$cookieString; __Secure-3PSID=$cookieString"
        } else {
            cookieString
        }

        val url = "https://www.google.com/maps/preview/locationsharing/read?authuser=0&hl=en"
        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", headerCookieValue)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed with status code ${response.code}")
                    return emptyList()
                }

                val bodyStr = response.body?.string() ?: return emptyList()
                Log.d(TAG, "Length of raw body response: ${bodyStr.length}")
                
                // Google lists typically start with the security prefix )]}'
                val sanitized = bodyStr.trim().removePrefix(")]}'")
                
                return parseSharingResponse(sanitized)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching live maps location sharing: ${e.message}", e)
        }

        return emptyList()
    }

    /**
     * Parses the JSON payload defensively to adapt to current/historical formats.
     */
    fun parseSharingResponse(jsonText: String): List<Friend> {
        val friends = mutableListOf<Friend>()
        try {
            val root = JSONArray(jsonText)
            
            // Try to find the array of people. It is typically at root index 0 or index 1
            var peopleArray: JSONArray? = null
            for (i in 0 until root.length()) {
                val arrayMaybe = root.optJSONArray(i)
                if (arrayMaybe != null && arrayMaybe.length() > 0) {
                    // Inside array maybe, we are looking for child arrays which start with a Google ID (string)
                    // Let's inspect the first element of its child
                    val testChild = arrayMaybe.optJSONArray(0)
                    if (testChild != null && testChild.length() >= 4 && testChild.opt(0) is String) {
                        peopleArray = arrayMaybe
                        break
                    }
                }
            }

            // Fallback: If not found, use root[0][0] or inspect recursively
            if (peopleArray == null && root.length() > 0) {
                val first = root.optJSONArray(0)
                if (first != null) {
                    peopleArray = first
                }
            }

            val arrayToParse = peopleArray ?: root
            val colors = listOf("#2196F3", "#E91E63", "#9C27B0", "#FFC107", "#00BCD4", "#E040FB", "#FF5722")
            var colorIdx = 0

            for (i in 0 until arrayToParse.length()) {
                val person = arrayToParse.optJSONArray(i) ?: continue
                if (person.length() < 4) continue

                val id = person.optString(0)
                if (id.isBlank() || id == "null") continue

                // Check nested location information inside person[1]
                val locOuter = person.optJSONArray(1) ?: continue
                val displayPic = locOuter.optString(0, "")
                
                val locInner = locOuter.optJSONArray(1) ?: continue
                
                // locInner has coordinates: typically [timestamp, lat, lon, acc...] or [null, lat, lon...]
                var lat = Double.NaN
                var lon = Double.NaN
                
                // Let's inspect elements to extract coordinates defensively
                // Often: index 1 is lat, index 2 is lon. Or search for valid float numbers
                if (locInner.length() >= 3) {
                    val val1 = locInner.optDouble(1, Double.NaN)
                    val val2 = locInner.optDouble(2, Double.NaN)
                    if (!val1.isNaN() && !val2.isNaN()) {
                        lat = val1
                        lon = val2
                    }
                }
                
                // Fallback number search if indices are offset
                if (lat.isNaN() || lon.isNaN()) {
                    val numbersList = mutableListOf<Double>()
                    for (j in 0 until locInner.length()) {
                        val num = locInner.optDouble(j, Double.NaN)
                        if (!num.isNaN() && Math.abs(num) > 0.0001) {
                            numbersList.add(num)
                        }
                    }
                    if (numbersList.size >= 2) {
                        // Assume first two coordinates are lat and lon
                        lat = numbersList[0]
                        lon = numbersList[1]
                    }
                }

                if (lat.isNaN() || lon.isNaN() || lat == 0.0 || lon == 0.0) {
                    continue // Skip invalid/empty location entries
                }

                val fullName = person.optString(3).ifBlank { person.optString(2, "Real Account") }
                val initial = person.optString(4).ifBlank { 
                    fullName.split(" ")
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .take(2)
                        .joinToString("")
                }.ifBlank { "RA" }

                val email = "${fullName.lowercase().replace(" ", "")}@gmail.com"
                val friendColor = colors[colorIdx % colors.size]
                colorIdx++

                friends.add(
                    Friend(
                        id = "google_maps_$id",
                        name = fullName,
                        email = email,
                        initial = initial,
                        latitude = lat,
                        longitude = lon,
                        avatarColorHex = friendColor,
                        avatarUrl = displayPic,
                        isSimulatedMoving = false,
                        trafficCondition = TrafficCondition.NORMAL
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sharing response JSON: ${e.message}", e)
        }
        return friends
    }
}
