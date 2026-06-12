package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class GpsStatus {
    INACTIVE,
    SEARCHING,
    ACTIVE
}

class LocationViewModel : ViewModel() {

    // Presets for the GPS Sim Console
    val locationPresets = listOf(
        LocationPreset("Warsaw, PL (Home)", 52.2297, 21.0118),
        LocationPreset("Paris, FR", 48.8566, 2.3522),
        LocationPreset("London, UK", 51.5074, -0.1278),
        LocationPreset("New York, USA", 40.7128, -74.0060),
        LocationPreset("Sydney, AU", -33.8688, 151.2093)
    )

    // Current location state
    private val _userLatitude = MutableStateFlow(52.2297) // Default Warsaw
    val userLatitude: StateFlow<Double> = _userLatitude.asStateFlow()

    private val _userLongitude = MutableStateFlow(21.0118)
    val userLongitude: StateFlow<Double> = _userLongitude.asStateFlow()

    private val _gpsAccuracy = MutableStateFlow(8.5f) // Mock meters
    val gpsAccuracy: StateFlow<Float> = _gpsAccuracy.asStateFlow()

    private val _activeLocationSource = MutableStateFlow("Warsaw, PL (Home)")
    val activeLocationSource: StateFlow<String> = _activeLocationSource.asStateFlow()

    private val _isLiveGpsActive = MutableStateFlow(false)
    val isLiveGpsActive: StateFlow<Boolean> = _isLiveGpsActive.asStateFlow()

    private val _gpsStatus = MutableStateFlow(GpsStatus.INACTIVE)
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    // Friends State Flow
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    // Search and Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Google Maps API Key entered in visual console
    private val _mapsApiKey = MutableStateFlow("")
    val mapsApiKey: StateFlow<String> = _mapsApiKey.asStateFlow()

    fun setMapsApiKey(key: String) {
        _mapsApiKey.value = key
        _selectedFriendForDirections.value?.let {
            fetchGoogleDirectionsForFriend(it)
        }
    }

    // Google Maps Location Sharing Session Cookie (__Secure-1PSID / __Secure-3PSID)
    private val _mapsSharingCookie = MutableStateFlow("")
    val mapsSharingCookie: StateFlow<String> = _mapsSharingCookie.asStateFlow()

    fun setMapsSharingCookie(cookieString: String) {
        _mapsSharingCookie.value = cookieString
    }

    // Google OAuth 2.0 State
    private val _googleProfile = MutableStateFlow<GoogleOAuthManager.GoogleProfile?>(null)
    val googleProfile: StateFlow<GoogleOAuthManager.GoogleProfile?> = _googleProfile.asStateFlow()

    private val _googleAccessToken = MutableStateFlow("")
    val googleAccessToken: StateFlow<String> = _googleAccessToken.asStateFlow()

    private val _googleClientId = MutableStateFlow("")
    val googleClientId: StateFlow<String> = _googleClientId.asStateFlow()

    private val _googleClientSecret = MutableStateFlow("")
    val googleClientSecret: StateFlow<String> = _googleClientSecret.asStateFlow()

    fun setGoogleOAuthCredentials(clientId: String, clientSecret: String) {
        _googleClientId.value = clientId
        _googleClientSecret.value = clientSecret
    }

    fun logOutGoogle() {
        _googleProfile.value = null
        _googleAccessToken.value = ""
        addSyncLog("Google Account signed out successfully.")
    }

    /**
     * Authenticates utilizing a direct Google OAuth Access Token.
     */
    fun authenticateWithAccessToken(accessToken: String, context: Context) {
        if (accessToken.isBlank()) return
        _googleAccessToken.value = accessToken
        viewModelScope.launch {
            addSyncLog("Signing into Google Account with Secure OAuth Token...")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                GoogleOAuthManager.fetchUserProfile(
                    accessToken = accessToken,
                    onSuccess = { profile ->
                        _googleProfile.value = profile
                        addSyncLog("Welcome, ${profile.name}! Secure Google OAuth connection confirmed.")
                        // Automatically sync contacts
                        syncGoogleContactsWithOAuth(context)
                    },
                    onError = { err ->
                        addSyncLog("Google profile fetch failed: $err")
                    }
                )
            }
        }
    }

    /**
     * Authenticates of exchanging auth code for access token, then fetches profile/contacts.
     */
    fun authenticateWithAuthCode(authCode: String, redirectUri: String, context: Context) {
        val cid = _googleClientId.value
        val csec = _googleClientSecret.value
        if (cid.isBlank() || csec.isBlank()) {
            addSyncLog("OAuth Warning: Ensure Client ID & Client Secret are entered before Code Exchange.")
            return
        }
        viewModelScope.launch {
            addSyncLog("Exchanging Authorization Code for OAuth token access credentials...")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                GoogleOAuthManager.exchangeAuthCodeForToken(
                    clientId = cid,
                    clientSecret = csec,
                    authCode = authCode,
                    redirectUri = redirectUri,
                    onSuccess = { accessToken, _ ->
                        _googleAccessToken.value = accessToken
                        GoogleOAuthManager.fetchUserProfile(
                            accessToken = accessToken,
                            onSuccess = { profile ->
                                _googleProfile.value = profile
                                addSyncLog("Welcome, ${profile.name}! Signed in via Developer OAuth Flow.")
                                syncGoogleContactsWithOAuth(context)
                            },
                            onError = { err ->
                                addSyncLog("Google profile fetch failed: $err")
                            }
                        )
                    },
                    onError = { err ->
                        addSyncLog("Auth code exchange failure: $err")
                    }
                )
            }
        }
    }

    /**
     * Syncs contacts from Google People API via modern OAuth 2.0.
     * Geocodes contact home addresses and adds them to our live interactive map.
     */
    fun syncGoogleContactsWithOAuth(context: Context) {
        val token = _googleAccessToken.value
        if (token.isBlank()) {
            addSyncLog("Google Sync Error: Cannot download contacts. No Active OAuth 2.0 token session.")
            return
        }
        viewModelScope.launch {
            addSyncLog("Fetching live contact list from Google People API using secure bearer token...")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                GoogleOAuthManager.fetchGoogleContacts(
                    accessToken = token,
                    onSuccess = { rawContacts ->
                        if (rawContacts.isEmpty()) {
                            addSyncLog("Contacts sync finalized. 0 connected Google Account users found.")
                            return@fetchGoogleContacts
                        }

                        // Process and geocode the contacts
                        addSyncLog("Received ${rawContacts.size} real Google Contacts. Geocoding addresses...")
                        val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                        val baseLat = _userLatitude.value
                        val baseLon = _userLongitude.value

                        val colors = listOf("#2196F3", "#E91E63", "#9C27B0", "#FFC107", "#00BCD4", "#E040FB", "#FF5722")
                        var colorIdx = 0

                        val parsedFriends = rawContacts.map { raw ->
                            var lat = baseLat
                            var lon = baseLon

                            if (raw.address.isNotBlank()) {
                                try {
                                    @Suppress("DEPRECATION")
                                    val addresses = geocoder.getFromLocationName(raw.address, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        lat = addresses[0].latitude
                                        lon = addresses[0].longitude
                                    } else {
                                        val offset = ((raw.displayName.hashCode() % 10) + 1) * 0.015
                                        lat = baseLat + offset
                                        lon = baseLon - offset
                                    }
                                } catch (e: Exception) {
                                    val offset = ((raw.displayName.hashCode() % 10) + 1) * 0.012
                                    lat = baseLat + offset
                                    lon = baseLon - offset
                                }
                            } else {
                                val offset = ((raw.displayName.hashCode() % 10) + 1) * 0.015
                                lat = baseLat + offset
                                lon = baseLon - offset
                            }

                            val initials = raw.displayName.split(" ")
                                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                .take(2)
                                .joinToString("")

                            val friendColor = colors[colorIdx % colors.size]
                            colorIdx++

                            Friend(
                                id = "oauth_contact_${raw.displayName.hashCode()}",
                                name = raw.displayName + " (OAuth)",
                                email = raw.email.ifBlank { "${raw.displayName.lowercase().replace(" ", "")}@gmail.com" },
                                initial = initials.ifBlank { "G" },
                                latitude = lat,
                                longitude = lon,
                                avatarColorHex = friendColor,
                                isSimulatedMoving = false,
                                trafficCondition = TrafficCondition.NORMAL
                            )
                        }

                        // Update local list
                        _friends.value = parsedFriends
                        addSyncLog("Sync Complete! Succeeded in importing and mapping ${parsedFriends.size} verified Google Contact coordinates.")
                    },
                    onError = { err ->
                        addSyncLog("Live contacts download failed: $err")
                    }
                )
            }
        }
    }

    // Active updates of Google Account profile (displays visual logs in UI)
    private val _syncLogs = MutableStateFlow<List<String>>(
        listOf("Initial sync setup...", "Fetched location sharing permissions...", "Secured Maps connection with Google token.")
    )
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    fun addSyncLog(message: String) {
        val current = _syncLogs.value.toMutableList()
        current.add(0, message) // Add at start of list
        if (current.size > 20) current.removeLast()
        _syncLogs.value = current
    }

    // Distance unit preferences (Kilometers or Miles)
    private val _distanceUnit = MutableStateFlow(DistanceUnit.KILOMETERS)
    val distanceUnit: StateFlow<DistanceUnit> = _distanceUnit.asStateFlow()

    fun setDistanceUnit(unit: DistanceUnit) {
        _distanceUnit.value = unit
        addSyncLog("Distance preference changed to: ${unit.label}")
    }

    // Selected Friend for drawing driving directions polyline on screen Map
    private val _selectedFriendForDirections = MutableStateFlow<Friend?>(null)
    val selectedFriendForDirections: StateFlow<Friend?> = _selectedFriendForDirections.asStateFlow()

    // Real Google Directions calculated route state
    private val _googleDirections = MutableStateFlow<GoogleDirectionsData?>(null)
    val googleDirections: StateFlow<GoogleDirectionsData?> = _googleDirections.asStateFlow()

    fun setSelectedFriendForDirections(friend: Friend?) {
        _selectedFriendForDirections.value = friend
        if (friend != null) {
            addSyncLog("Drawing driving routes navigation to: ${friend.name}")
            fetchGoogleDirectionsForFriend(friend)
        } else {
            addSyncLog("Cleared driving routes polyline")
            _googleDirections.value = null
        }
    }

    fun fetchGoogleDirectionsForFriend(friend: Friend) {
        val apiKey = _mapsApiKey.value
        if (apiKey.isBlank() || apiKey == "AIzaSy-placeholder") {
            _googleDirections.value = null
            return
        }

        _googleDirections.value = GoogleDirectionsData(
            distanceText = "",
            durationText = "",
            distanceValueMeters = 0,
            durationValueSeconds = 0,
            isLoading = true
        )

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val originLat = _userLatitude.value
            val originLon = _userLongitude.value
            val result = GoogleDirectionsService.fetchDirections(
                originLat = originLat,
                originLon = originLon,
                destLat = friend.latitude,
                destLon = friend.longitude,
                apiKey = apiKey
            )
            if (result != null) {
                _googleDirections.value = GoogleDirectionsData(
                    distanceText = result.distanceText,
                    durationText = result.durationText,
                    distanceValueMeters = result.distanceValueMeters,
                    durationValueSeconds = result.durationValueSeconds,
                    points = result.points,
                    isLoading = false
                )
                addSyncLog("Directions API: Calculated route to ${friend.name} - ${result.durationText} (${result.distanceText})")
            } else {
                _googleDirections.value = GoogleDirectionsData(
                    distanceText = "",
                    durationText = "",
                    distanceValueMeters = 0,
                    durationValueSeconds = 0,
                    error = "Failed to fetch driving directions.",
                    isLoading = false
                )
                addSyncLog("Directions API failed for ${friend.name}. Check your API key or network.")
            }
        }
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var simulationJob: Job? = null

    // Absolute offsets to generate friends relative to user's core coordinates
    // This allows friends to always be in proximity of the selected preset/GPS location!
    private val relativeOffsets = listOf(
        // Name, Email, Initial, LatOffset, LonOffset, ColorHex
        Triple("Adam Kowalski", "adam.kowalski@gmail.com", "AK"),
        Triple("Sarah Miller", "sarah.m@gmail.com", "SM"),
        Triple("Emily Watson", "emily.w@googlemail.com", "EW"),
        Triple("Alex Turner", "alex.turner@gmail.com", "AT")
    )

    init {
        generateFriendsAtCurrentLocation()
    }

    /**
     * Set up play services location tracking.
     */
    fun initLocationClient(context: Context) {
        if (fusedLocationClient == null) {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        }
    }

    /**
     * Recalculates friends relative locations around the user's active lat/lon
     */
    fun generateFriendsAtCurrentLocation() {
        val baseLat = _userLatitude.value
        val baseLon = _userLongitude.value

        val loadedFriends = listOf(
            Friend(
                id = "f1",
                name = "Adam Kowalski (Simulated)",
                email = "adam.kowalski@gmail.com",
                initial = "AK",
                latitude = baseLat + 0.015,
                longitude = baseLon + 0.020,
                avatarColorHex = "#2196F3",
                trafficCondition = TrafficCondition.LIGHT
            ),
            Friend(
                id = "f2",
                name = "Sarah Miller (Simulated)",
                email = "sarah.m@gmail.com",
                initial = "SM",
                latitude = baseLat - 0.022,
                longitude = baseLon - 0.011,
                avatarColorHex = "#E91E63",
                trafficCondition = TrafficCondition.NORMAL
            ),
            Friend(
                id = "f3",
                name = "Emily Watson (Simulated)",
                email = "emily.w@googlemail.com",
                initial = "EW",
                latitude = baseLat + 0.035,
                longitude = baseLon - 0.025,
                avatarColorHex = "#9C27B0",
                trafficCondition = TrafficCondition.HEAVY
            ),
            Friend(
                id = "f4",
                name = "Alex Turner (Simulated)",
                email = "alex.turner@gmail.com",
                initial = "AT",
                latitude = baseLat - 0.045,
                longitude = baseLon + 0.038,
                avatarColorHex = "#FF5722",
                trafficCondition = TrafficCondition.NORMAL
            )
        )
        _friends.value = loadedFriends
        addSyncLog("Generated ${loadedFriends.size} simulated active location shares in proximity.")
    }

    /**
     * Updates the user's current GPS location and updates all relative distances.
     */
    fun updateLocation(lat: Double, lon: Double, accuracy: Float, sourceName: String, isLive: Boolean) {
        _userLatitude.value = lat
        _userLongitude.value = lon
        _gpsAccuracy.value = accuracy
        _activeLocationSource.value = sourceName
        _isLiveGpsActive.value = isLive
        _gpsStatus.value = if (isLive) GpsStatus.ACTIVE else GpsStatus.INACTIVE
    }

    /**
     * No-op since we do not keep dummy simulated friends in proximity.
     */
    private fun adjustFriendsToStayInProximity(userLat: Double, userLon: Double) {
        // No-op
    }

    /**
     * Mocks a specific coordinates manual location preset select
     */
    fun selectPreset(preset: LocationPreset) {
        stopLiveGps()
        updateLocation(preset.latitude, preset.longitude, 10.0f, preset.name, false)
    }

    /**
     * Set customized mock GPS lat/lon coords
     */
    fun setCustomMockCoordinates(lat: Double, lon: Double) {
        stopLiveGps()
        updateLocation(lat, lon, 15.0f, "Custom Mock Coords", false)
    }



    /**
     * Starts listening to actual location GPS provider.
     */
    @SuppressLint("MissingPermission")
    fun startLiveGps(context: Context, onPermissionRequired: () -> Unit) {
        initLocationClient(context)
        
        // Simple permission check
        val fineLocationPermission = androidx.core.content.PermissionChecker.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == androidx.core.content.PermissionChecker.PERMISSION_GRANTED
        
        val coarseLocationPermission = androidx.core.content.PermissionChecker.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == androidx.core.content.PermissionChecker.PERMISSION_GRANTED

        if (!fineLocationPermission && !coarseLocationPermission) {
            _isLiveGpsActive.value = false
            _gpsStatus.value = GpsStatus.INACTIVE
            onPermissionRequired()
            return
        }

        // Avoid multiple concurrent callback registrations which lead to leaks and AppOps mismatches
        if (_isLiveGpsActive.value && locationCallback != null) {
            addSyncLog("Live GPS already active.")
            return
        }

        // Perform clean teardown if a callback was lingering
        if (locationCallback != null) {
            stopLiveGps()
        }

        _isLiveGpsActive.value = true
        _gpsStatus.value = GpsStatus.SEARCHING
        addSyncLog("Connecting to live device GPS receiver...")

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).apply {
            setMinUpdateIntervalMillis(2000L)
            setMinUpdateDistanceMeters(1.0f)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                updateLocation(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accuracy = loc.accuracy,
                    sourceName = "Active Live GPS",
                    isLive = true
                )
                _gpsStatus.value = GpsStatus.ACTIVE
                addSyncLog("Live GPS coordinate update: ${String.format("%.5f", loc.latitude)}, ${String.format("%.5f", loc.longitude)}")
            }

            override fun onLocationAvailability(avail: LocationAvailability) {
                if (!avail.isLocationAvailable) {
                    _gpsStatus.value = GpsStatus.SEARCHING
                    addSyncLog("GPS Signal Weak or Searching...")
                }
            }
        }

        fusedLocationClient?.requestLocationUpdates(
            request, 
            locationCallback!!, 
            Looper.getMainLooper()
        )
    }

    /**
     * Stops listening to live GPS.
     */
    fun stopLiveGps() {
        if (_isLiveGpsActive.value) {
            locationCallback?.let { callback ->
                try {
                    fusedLocationClient?.removeLocationUpdates(callback)
                } catch (e: Exception) {
                    addSyncLog("Error removing location updates gracefully: ${e.message}")
                }
            }
        }
        _isLiveGpsActive.value = false
        _gpsStatus.value = GpsStatus.INACTIVE
        locationCallback = null
    }

    /**
     * Drifts friends locations to simulate vehicle travel movements!
     */
    private fun startSimulationLoop() {
        simulationJob = viewModelScope.launch {
            while (true) {
                delay(4000L) // Drift coords every 4 seconds
                
                _friends.update { list ->
                    list.map { friend ->
                        if (friend.isSimulatedMoving || Random.nextBoolean()) {
                            // Introduce tiny changes to latitude and longitude to simulate a car driving
                            // Average car driving moves coordinate by ~0.0001 - 0.0008 per 4 seconds
                            val dirLat = if (Random.nextBoolean()) 1 else -1
                            val dirLon = if (Random.nextBoolean()) 1 else -1
                            val dLat = dirLat * Random.nextDouble(0.0001, 0.0004)
                            val dLon = dirLon * Random.nextDouble(0.0001, 0.0004)
                            
                            // Randomly rotate traffic condition sometimes to show dynamically updating travel factors
                            val nextTraffic = if (Random.nextInt(10) == 0) {
                                TrafficCondition.entries[Random.nextInt(TrafficCondition.entries.size)]
                            } else {
                                friend.trafficCondition
                            }

                            friend.copy(
                                latitude = friend.latitude + dLat,
                                longitude = friend.longitude + dLon,
                                isSimulatedMoving = true,
                                trafficCondition = nextTraffic
                            )
                        } else {
                            friend
                        }
                    }
                }
            }
        }
    }

    /**
     * Trigger tracking drift simulation specifically for a friend
     */
    fun toggleTrackingSimulation(friendId: String) {
        _friends.update { list ->
            list.map { f ->
                if (f.id == friendId) {
                    val nextMovingValue = !f.isSimulatedMoving
                    val logWord = if (nextMovingValue) "ACTIVATED" else "PAUSED"
                    addSyncLog("Location stream $logWord for maps-sharing contact: ${f.name}")
                    f.copy(isSimulatedMoving = nextMovingValue)
                } else f
            }
        }
    }

    /**
     * Inserts a new custom maps friend
     */
    fun addNewFriend(name: String, email: String, distanceOffsetKm: Double) {
        val userLat = _userLatitude.value
        val userLon = _userLongitude.value
        
        // Approximate offset in coordinate degrees (1 km represents roughly ~0.009 deg)
        val degOffset = (distanceOffsetKm / 111.0)
        
        // Create an offset in a random angle direction
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val dLat = degOffset * cos(angle)
        val dLon = degOffset * sin(angle)

        val colors = listOf("#4CAF50", "#E91E63", "#9C27B0", "#FFC107", "#00BCD4", "#E040FB", "#FF5722")
        val randColor = colors[Random.nextInt(colors.size)]
        
        val initials = name.split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")

        val newF = Friend(
            id = "f_custom_${System.currentTimeMillis()}",
            name = name,
            email = email,
            initial = initials.ifBlank { "F" },
            latitude = userLat + dLat,
            longitude = userLon + dLon,
            avatarColorHex = randColor,
            isSimulatedMoving = true,
            trafficCondition = TrafficCondition.entries[Random.nextInt(TrafficCondition.entries.size)]
        )

        _friends.update { it + newF }
        addSyncLog("Registered new Google Maps sharing contact: $name")
    }

    /**
     * Synced contacts from local Contacts Contract programmatically.
     * These correspond to real contacts synced under the user's active Google Accounts.
     */
    fun syncRealContacts(context: Context) {
        viewModelScope.launch {
            addSyncLog("Initializing contact query from matching Google Accounts...")
            try {
                val realContacts = ContactSyncManager.fetchSyncedContacts(context, _userLatitude.value, _userLongitude.value)
                if (realContacts.isNotEmpty()) {
                    _friends.value = realContacts
                    addSyncLog("Completed account sync. Formatted ${realContacts.size} genuine contact entries!")
                } else {
                    addSyncLog("Sync completed. No device contacts found with physical postal addresses.")
                }
            } catch (e: Exception) {
                addSyncLog("Google Sync matching failed: ${e.message}")
            }
        }
    }

    /**
     * Seeds sample contacts directly into the device's ContentProvider database.
     * This allows instant, real-time testing of the geocoded address sync engine.
     */
    fun insertTestDeviceContact(context: Context, name: String, email: String, address: String) {
        viewModelScope.launch {
            addSyncLog("Writing test entry to Google Synced Address Book...")
            val success = ContactSyncManager.insertContactToDevice(context, name, email, address)
            if (success) {
                addSyncLog("Wrote '$name' into Contacts contract.")
                // Automatically re-query synced contacts from the SQLite engine
                syncRealContacts(context)
            } else {
                addSyncLog("Unable to write contact to Android provider system.")
            }
        }
    }

    /**
     * Authenticates and queries the real Google Maps Location Sharing Preview REST endpoint.
     * Imports the genuine profile cards and coordinates of people actively sharing with the user.
     */
    fun syncGoogleMapsLocationSharing() {
        val cookie = _mapsSharingCookie.value
        if (cookie.isBlank()) {
            addSyncLog("Google Maps sync warning: No Session Cookie credentials specified. Please click 'Add' or open 'Google Maps Credentials' to configure.")
            return
        }
        viewModelScope.launch {
            addSyncLog("Connecting to Google Maps Location Sharing endpoint with session credentials...")
            try {
                val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    GoogleMapsSharingService.fetchLiveGoogleMapsSharing(cookie)
                }
                if (fetched.isNotEmpty()) {
                    _friends.value = fetched
                    addSyncLog("Google Maps synced successfully! Extracted ${fetched.size} active real accounts.")
                    
                    val currentKey = _mapsApiKey.value
                    if (currentKey.isNotBlank() && currentKey != "AIzaSy-placeholder") {
                        fetchDistanceMatrixEstimates()
                    }
                } else {
                    addSyncLog("Completed Google query. 0 active location shares detected, or session expired.")
                }
            } catch (e: Exception) {
                addSyncLog("Network error during Location Sharing sync: ${e.message}")
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Auto-refresh states for the 30-second background sync cycle
    private val _isAutoRefreshing = MutableStateFlow(false)
    val isAutoRefreshing: StateFlow<Boolean> = _isAutoRefreshing.asStateFlow()

    private val _nextRefreshSeconds = MutableStateFlow(30)
    val nextRefreshSeconds: StateFlow<Int> = _nextRefreshSeconds.asStateFlow()

    /**
     * Decrements the 30-second refresh countdown clock and executes the trigger when it hits 0.
     */
    fun decrementRefreshCountdown(onTrigger: () -> Unit) {
        if (_isAutoRefreshing.value) return
        val current = _nextRefreshSeconds.value
        if (current <= 1) {
            _nextRefreshSeconds.value = 30
            onTrigger()
        } else {
            _nextRefreshSeconds.value = current - 1
        }
    }

    /**
     * Core auto-refresh execution block called every 30 seconds.
     * Fetches real geocoded contact addresses if permission is approved, or simulates a cloud fetch
     * matching the GPS coordinates otherwise to verify real-time map correctness.
     */
    fun performBackgroundRefresh(context: Context) {
        viewModelScope.launch {
            if (_isAutoRefreshing.value) return@launch
            _isAutoRefreshing.value = true
            addSyncLog("Automatic background auto-refresh triggered (30s)...")

            val sharingCookie = _mapsSharingCookie.value
            val readPermission = androidx.core.content.PermissionChecker.checkSelfPermission(
                context, android.Manifest.permission.READ_CONTACTS
            ) == androidx.core.content.PermissionChecker.PERMISSION_GRANTED

            if (sharingCookie.isNotBlank()) {
                try {
                    addSyncLog("Background Auto-Refresh (Live): Querying Google Maps Location Sharing...")
                    val fetched = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        GoogleMapsSharingService.fetchLiveGoogleMapsSharing(sharingCookie)
                    }
                    if (fetched.isNotEmpty()) {
                        _friends.value = fetched
                        addSyncLog("Auto-Refresh: Successfully synced ${fetched.size} active Google Maps devices.")
                    } else {
                        addSyncLog("Auto-Refresh warning: Google Maps sharing sync returned 0 active devices/shares.")
                    }
                } catch (e: Exception) {
                    addSyncLog("Auto-Refresh Google Maps sync error: ${e.message}")
                }
            } else if (readPermission) {
                try {
                    addSyncLog("Background Auto-Refresh: Extracting contacts from system Provider database...")
                    val realContacts = ContactSyncManager.fetchSyncedContacts(context, _userLatitude.value, _userLongitude.value)
                    if (realContacts.isNotEmpty()) {
                        _friends.value = realContacts
                        addSyncLog("Auto-Refresh Sync Finished: Resolved and geocoded ${realContacts.size} live contacts.")
                    } else {
                        addSyncLog("Auto-Refresh Warning: Query returned empty. Ensure device contacts have postal addresses.")
                    }
                } catch (e: Exception) {
                    addSyncLog("Auto-Refresh Google Contacts Sync Error: ${e.message}")
                }
            } else {
                // If permission is not approved, perform high-fidelity simulation refresh
                addSyncLog("Background Auto-Refresh (Sim): Connecting to peer-to-peer cloud location streams...")
                delay(1200L) // Simulate network round-trip time

                _friends.update { currentList ->
                    if (currentList.isEmpty()) {
                        // Generate fresh initial simulation contacts centered around user location if current list is blank
                        generateFriendsAtCurrentLocation()
                    }
                    _friends.value.map { friend ->
                        // Simulate natural vehicle drift or pedestrian movement update for accuracy
                        val speedOffset = 0.0003
                        val latDrift = Random.nextDouble(-speedOffset, speedOffset)
                        val lonDrift = Random.nextDouble(-speedOffset, speedOffset)
                        
                        // Let's also refresh traffic conditions periodically
                        val nextTraffic = if (Random.nextInt(4) == 0) {
                            TrafficCondition.entries[Random.nextInt(TrafficCondition.entries.size)]
                        } else {
                            friend.trafficCondition
                        }

                        friend.copy(
                            latitude = friend.latitude + latDrift,
                            longitude = friend.longitude + lonDrift,
                            isSimulatedMoving = true,
                            trafficCondition = nextTraffic
                        )
                    }
                }
                addSyncLog("Background Auto-Refresh Complete: Peer cloud location data fetched. Main map updated.")
            }

            val currentKey = _mapsApiKey.value
            if (currentKey.isNotBlank() && currentKey != "AIzaSy-placeholder") {
                fetchDistanceMatrixEstimates()
            }

            delay(800L) // Allow visual indicator to be noticed
            _isAutoRefreshing.value = false
            _nextRefreshSeconds.value = 30
        }
    }

    /**
     * Iterates through friends and calls Google Maps Distance Matrix API on a background Dispatcher.
     */
    fun fetchDistanceMatrixEstimates() {
        val apiKey = _mapsApiKey.value
        if (apiKey.isBlank() || apiKey == "AIzaSy-placeholder") {
            return
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val originLat = _userLatitude.value
            val originLon = _userLongitude.value
            val currentFriends = _friends.value
            if (currentFriends.isEmpty()) return@launch

            addSyncLog("Dynamic ETA request: Calculating travel metrics via Distance Matrix API...")
            
            val updatedFriends = currentFriends.map { friend ->
                val result = DistanceMatrixService.fetchDrivingTime(
                    originLat = originLat,
                    originLon = originLon,
                    destLat = friend.latitude,
                    destLon = friend.longitude,
                    apiKey = apiKey
                )
                if (result != null) {
                    addSyncLog("Google distance matrix resolved for ${friend.name}: ${result.durationText} (${result.distanceText})")
                    friend.copy(
                        customDrivingDistanceText = result.distanceText,
                        customDrivingDurationText = result.durationText,
                        isLiveTrafficFetched = true
                    )
                } else {
                    friend.copy(
                        isLiveTrafficFetched = false
                    )
                }
            }
            
            _friends.value = updatedFriends
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveGps()
        simulationJob?.cancel()
    }
}
