package com.example

import android.content.Context
import android.util.Log
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object GoogleOAuthManager {
    private const val TAG = "GoogleOAuthManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Google profile details fetched after successful OAuth 2.0 sign-in.
     */
    data class GoogleProfile(
        val name: String,
        val email: String,
        val pictureUrl: String?
    )

    /**
     * Exchange Google OAuth 2.0 Authorization Code for Access Token & Refresh Token.
     */
    fun exchangeAuthCodeForToken(
        clientId: String,
        clientSecret: String,
        authCode: String,
        redirectUri: String,
        onSuccess: (accessToken: String, refreshToken: String?) -> Unit,
        onError: (String) -> Unit
    ) {
        val formBody = FormBody.Builder()
            .add("code", authCode)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(formBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val accessToken = json.getString("access_token")
                    val refreshToken = if (json.has("refresh_token")) json.getString("refresh_token") else null
                    onSuccess(accessToken, refreshToken)
                } else {
                    Log.e(TAG, "Token exchange failed: $bodyStr")
                    val errorMsg = JSONObject(bodyStr).optString("error_description", "Unknown OAuth failure")
                    onError(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout or network error in Token exchange", e)
            onError(e.message ?: "Network error exchange")
        }
    }

    /**
     * Refreshes an expired Google Access Token using a Refresh Token.
     */
    fun refreshAccessToken(
        clientId: String,
        clientSecret: String,
        refreshToken: String,
        onSuccess: (accessToken: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val formBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(formBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val accessToken = json.getString("access_token")
                    onSuccess(accessToken)
                } else {
                    val errorMsg = JSONObject(bodyStr).optString("error_description", "Failed to refresh token")
                    onError(errorMsg)
                }
            }
        } catch (e: Exception) {
            onError(e.message ?: "Network error on refresh")
        }
    }

    /**
     * Fetches user profile info (name, email, avatar image URL) using active access token.
     */
    fun fetchUserProfile(
        accessToken: String,
        onSuccess: (GoogleProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo")
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(bodyStr)
                    val name = json.optString("name", "Google User")
                    val email = json.optString("email", "")
                    val picture = if (json.has("picture")) json.getString("picture") else null
                    onSuccess(GoogleProfile(name, email, picture))
                } else {
                    onError("HTTP ${response.code} fetching profile")
                }
            }
        } catch (e: Exception) {
            onError(e.message ?: "Network error fetching profile")
        }
    }

    /**
     * Fetches genuine contact list and addresses from Google People API.
     */
    fun fetchGoogleContacts(
        accessToken: String,
        onSuccess: (List<FriendRawData>) -> Unit,
        onError: (String) -> Unit
    ) {
        // Query people/me/connections requesting name, emailAddresses, and postal addresses
        val url = "https://people.googleapis.com/v1/people/me/connections?personFields=names,emailAddresses,addresses&pageSize=100"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    onError("Google API Error ${response.code}: $bodyStr")
                    return
                }

                val json = JSONObject(bodyStr)
                val connections = json.optJSONArray("connections")
                if (connections == null || connections.length() == 0) {
                    onSuccess(emptyList())
                    return
                }

                val rawFriends = mutableListOf<FriendRawData>()
                for (i in 0 until connections.length()) {
                    val person = connections.getJSONObject(i)

                    // Extract Display Name
                    val names = person.optJSONArray("names")
                    val name = if (names != null && names.length() > 0) {
                        names.getJSONObject(0).optString("displayName", "Unnamed Contact")
                    } else "Unnamed Contact"

                    // Extract Email Address
                    val emails = person.optJSONArray("emailAddresses")
                    val email = if (emails != null && emails.length() > 0) {
                        emails.getJSONObject(0).optString("value", "")
                    } else ""

                    // Extract Postal/Home Addresses
                    val addresses = person.optJSONArray("addresses")
                    val postalAddress = if (addresses != null && addresses.length() > 0) {
                        addresses.getJSONObject(0).optString("formattedValue", "")
                    } else ""

                    if (name.isNotBlank()) {
                        rawFriends.add(FriendRawData(name, email, postalAddress))
                    }
                }
                onSuccess(rawFriends)
            }
        } catch (e: Exception) {
            onError(e.message ?: "Network failure during contacts query")
        }
    }

    data class FriendRawData(
        val displayName: String,
        val email: String,
        val address: String
    )
}
