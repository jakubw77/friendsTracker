package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val locationViewModel: LocationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locationViewModel.initLocationClient(this)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("main_scaffold")
                ) { innerPadding ->
                    MainScreen(
                        viewModel = locationViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: LocationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Core States
    val userLat by viewModel.userLatitude.collectAsState()
    val userLon by viewModel.userLongitude.collectAsState()
    val gpsAcc by viewModel.gpsAccuracy.collectAsState()
    val locSource by viewModel.activeLocationSource.collectAsState()
    val isLiveGps by viewModel.isLiveGpsActive.collectAsState()
    val gpsStatus by viewModel.gpsStatus.collectAsState()
    val friendsList by viewModel.friends.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncLogs by viewModel.syncLogs.collectAsState()
    val distanceUnit by viewModel.distanceUnit.collectAsState()
    val mapsApiKey by viewModel.mapsApiKey.collectAsState()
    val mapsSharingCookie by viewModel.mapsSharingCookie.collectAsState()
    val selectedFriendForDirections by viewModel.selectedFriendForDirections.collectAsState()
    val googleDirections by viewModel.googleDirections.collectAsState()

    // Google OAuth state collections
    val googleProfile by viewModel.googleProfile.collectAsState()
    val googleAccessToken by viewModel.googleAccessToken.collectAsState()
    val googleClientId by viewModel.googleClientId.collectAsState()
    val googleClientSecret by viewModel.googleClientSecret.collectAsState()

    // Dialog & UI Action UI States
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }

    // Contacts permission request launcher
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CONTACTS] ?: false
        if (readGranted) {
            viewModel.syncRealContacts(context)
            Toast.makeText(context, "Syncing genuine contacts from your Google account...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context, 
                "Google Contacts access is required to synchronize actual location-sharing friends.", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Location permission request launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.startLiveGps(context) {
                Toast.makeText(context, "Location services starting...", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(
                context, 
                "Permission Denied. Please enable GPS inside device settings.", 
                Toast.LENGTH_LONG
            ).show()
        }

        // Chained execution: Request accounts/contacts permission after location flow returns
        val readContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        if (readContacts == PackageManager.PERMISSION_GRANTED) {
            viewModel.syncRealContacts(context)
        } else {
            contactsPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                )
            )
        }
    }

    // Filter friend list based on search bar query
    val filteredFriends = remember(friendsList, searchQuery) {
        if (searchQuery.isBlank()) {
            friendsList
        } else {
            friendsList.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.email.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Sophisticated Dark Styled Designer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "PROXIMITY SYNC",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Live Feed",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    )
                    
                    // Live tracking status pill badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (gpsStatus) {
                                GpsStatus.ACTIVE -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                GpsStatus.SEARCHING -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                GpsStatus.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = when (gpsStatus) {
                                GpsStatus.ACTIVE -> Color(0xFF4CAF50).copy(alpha = 0.4f)
                                GpsStatus.SEARCHING -> Color(0xFFFF9800).copy(alpha = 0.4f)
                                GpsStatus.INACTIVE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (gpsStatus) {
                                            GpsStatus.ACTIVE -> Color(0xFF4CAF50)
                                            GpsStatus.SEARCHING -> Color(0xFFFF9800)
                                            GpsStatus.INACTIVE -> Color(0xFF9E9E9E)
                                        }
                                    )
                            )
                            Text(
                                text = when (gpsStatus) {
                                    GpsStatus.ACTIVE -> "Tracking Active"
                                    GpsStatus.SEARCHING -> "Searching..."
                                    GpsStatus.INACTIVE -> "Inactive"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (gpsStatus) {
                                    GpsStatus.ACTIVE -> Color(0xFF4CAF50)
                                    GpsStatus.SEARCHING -> Color(0xFFFF9800)
                                    GpsStatus.INACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // High accuracy sync connection details inside container
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showLogsDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Google Sync Account Details",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        // background auto-refresh states and loop
        val isAutoRefreshing by viewModel.isAutoRefreshing.collectAsState()
        val nextRefreshSeconds by viewModel.nextRefreshSeconds.collectAsState()

        LaunchedEffect(context) {
            // Trigger GPS Activation directly using real device receiver
            val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
                viewModel.startLiveGps(context) {}
                
                // Location granted, check contacts/accounts sequential configuration
                val readContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                if (readContacts == PackageManager.PERMISSION_GRANTED) {
                    viewModel.syncRealContacts(context)
                } else {
                    contactsPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.WRITE_CONTACTS
                        )
                    )
                }
            } else {
                // Not granted: request location permission first (will trigger contacts permission next)
                permissionsLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            while (true) {
                delay(1000L)
                viewModel.decrementRefreshCountdown {
                    viewModel.performBackgroundRefresh(context)
                }
            }
        }

        // Modern Visual Status Banner for the 30-Second Refresh Cycle
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAutoRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Syncing locations...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Small neon-green active dot representing active live connection
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Text(
                            text = "Accuracy Synchronized",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Timer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Auto-sync in ${nextRefreshSeconds}s",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("autosync_countdown_text")
                    )
                }
            }
        }

        var selectedTab by remember { mutableStateOf(0) } // 0 = RADAR, 1 = GOOGLE MAPS

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // 2. Google Identity Connect Banner Card (Sophisticated Dark styled)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("google_profile_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Google account letter avatar placeholder with dark purple theme
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "My Location Status",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GOOGLE CONNECT IDENTIFIER",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Jakub Wink",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Jakub.Wink@gmail.com",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isLiveGps) Color(0xFF4CAF50) else Color(0xFFFF9800))
                                )
                                Text(
                                    text = if (isLiveGps) "Real Hardware GPS Active" else "Permissions Pending",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Manual Refresh Action matching HTML
                        IconButton(
                            onClick = { 
                                val readGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                                if (readGranted) {
                                    viewModel.syncRealContacts(context)
                                    Toast.makeText(context, "Refreshing location contacts...", Toast.LENGTH_SHORT).show()
                                } else {
                                    contactsPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                                    )
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("sync_contacts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Manual sync",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 3. Proximity Visualizer / Google Map Interactive Console Panel
            item {
                var showApiKeySettings by remember { mutableStateOf(false) }

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedTab == 0) "PROXIMITY RADAR CONSOLE" else "GOOGLE MAPS REALTIME",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        // Mode Segmented Selector Tab Controls
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            listOf("Radar", "Google Map").forEachIndexed { index, title ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selectedTab == index) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { selectedTab = index }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTab == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (selectedTab == 0) {
                        LocationRadar(
                            userLat = userLat,
                            userLon = userLon,
                            friends = filteredFriends,
                            modifier = Modifier.testTag("location_radar")
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InteractiveMapWebView(
                                userLat = userLat,
                                userLon = userLon,
                                friends = filteredFriends,
                                distanceUnit = distanceUnit,
                                apiKey = mapsApiKey,
                                selectedFriendForDirections = selectedFriendForDirections,
                                googleDirections = googleDirections,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline)
                            )
                            
                            // In-line Optional Google Maps API Key Config setting for professional usage
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Google Maps API Key Credentials",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(
                                            onClick = { showApiKeySettings = !showApiKeySettings },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text(
                                                text = if (showApiKeySettings) "Hide Key Slot" else "Show Key Slot",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    
                                    if (showApiKeySettings) {
                                        Text(
                                            text = "The map view runs on 100% free Leaflet & OpenStreetMap out-of-the-box (no keys needed!). However, you can optionally provide a Google Maps API Key here to enable live driving distance calculations via the Google Distance Matrix API if desired.",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            lineHeight = 13.sp,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        OutlinedTextField(
                                            value = mapsApiKey,
                                            onValueChange = { viewModel.setMapsApiKey(it) },
                                            placeholder = { Text("AIzaSy-placeholder", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                            label = { Text("Google Maps JS API Key", fontSize = 11.sp) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                            )
                                        )
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        Button(
                                            onClick = { viewModel.fetchDistanceMatrixEstimates() },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .testTag("fetch_eta_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DirectionsCar,
                                                    contentDescription = "Car Icon",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Calculate Live ETAs (Distance Matrix)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. GPS Provider Status Panel (Real Hardware Only)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("gps_console_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (gpsStatus) {
                                                    GpsStatus.ACTIVE -> Color(0xFF4CAF50)
                                                    GpsStatus.SEARCHING -> Color(0xFFFF9800)
                                                    GpsStatus.INACTIVE -> Color(0xFF9E9E9E)
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Real Hardware GPS Mode",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Text(
                                    text = when (gpsStatus) {
                                        GpsStatus.ACTIVE -> "Receiver: Tracking Active"
                                        GpsStatus.SEARCHING -> "Receiver: Searching for signal..."
                                        GpsStatus.INACTIVE -> "Receiver: Waiting for Permissions"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!isLiveGps) {
                                Button(
                                    onClick = {
                                        val fineLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                                        val coarseLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                                        if (fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED) {
                                            viewModel.startLiveGps(context) {}
                                        } else {
                                            permissionsLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize().testTag("gps_toggle_button")
                                ) {
                                    Text("Enable GPS Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Display formatted GPS numbers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Latitude", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format("%.6f", userLat), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Longitude", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format("%.6f", userLon), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(0.7f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Accuracy", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (isLiveGps) "± ${gpsAcc.toInt()}m" else "Pending", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isLiveGps) Color(0xFF4CAF50) else Color(0xFFFF9800))
                                }
                            }
                        }

                        Divider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Distance Unit Metric:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            ) {
                                DistanceUnit.entries.forEach { unit ->
                                    val isSelected = distanceUnit == unit
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { viewModel.setDistanceUnit(unit) }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                            .testTag("unit_toggle_${unit.suffix}")
                                    ) {
                                        Text(
                                            text = unit.label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Traffic-Adaptive Travel Time Calculator item
            item {
                TravelTimeCalculatorCard(
                    friends = friendsList,
                    userLat = userLat,
                    userLon = userLon,
                    distanceUnit = distanceUnit,
                    selectedFriendForDirections = selectedFriendForDirections,
                    googleDirections = googleDirections,
                    onChooseFriendForDirections = { friend ->
                        viewModel.setSelectedFriendForDirections(friend)
                    },
                    onNavigateToMapTab = {
                        selectedTab = 1
                    }
                )
            }

            // 5. Friend Location Sharing List Title with Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FRIENDS SHARING LOCATION (${filteredFriends.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Calculating real-time proximity status from Google Accounts",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showAddFriendDialog = true },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("add_friend_button")
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Search Filter Row
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search list by name or email...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("friend_search_input")
                        .padding(bottom = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            // No results placeholder state
            if (filteredFriends.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "No Friends Found",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "No Maps Sharing Friends Found",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Add custom friend contact coordinates or reset query selection filter",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isNotEmpty()) {
                            TextButton(onClick = { viewModel.setSearchQuery("") }) {
                                Text("Clear Search")
                            }
                        }
                    }
                }
            }

            // 6. Rendering Friend Card Items
            items(filteredFriends, key = { it.id }) { friend ->
                FriendRowItem(
                    friend = friend,
                    userLat = userLat,
                    userLon = userLon,
                    distanceUnit = distanceUnit,
                    isDirectionsSelected = selectedFriendForDirections?.id == friend.id,
                    onToggleTracking = { viewModel.toggleTrackingSimulation(friend.id) },
                    onGetDirections = {
                        viewModel.setSelectedFriendForDirections(friend)
                        selectedTab = 1 // Auto switch to raw Google Maps tab to observe route polyline
                    },
                    onClearDirections = {
                        viewModel.setSelectedFriendForDirections(null)
                    }
                )
            }
        }
    }

    // --- DIALOGS SECTION ---

    // OAuth Active Connection Logs screen
    if (showLogsDialog) {
        Dialog(onDismissRequest = { showLogsDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Google Sync telemetry logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showLogsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Text(
                        "Showing real-time Google API authentication responses, coordinate fetches, and Maps background sync activities:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(syncLogs) { log ->
                                Text(
                                    text = "> $log",
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showLogsDialog = false },
                        modifier = Modifier.fillMaxWidth().testTag("close_logs_button")
                    ) {
                        Text("Awesome")
                    }
                }
            }
        }
    }

    // Google Maps Location Sharing / OAuth 2.0 Sync Dialog
    if (showAddFriendDialog) {
        var selectedTab by remember { mutableIntStateOf(0) } // 0 = Maps Cookie, 1 = OAuth 2.0 API
        var cookieInput by remember { mutableStateOf(mapsSharingCookie) }
        
        var clientIdInput by remember { mutableStateOf(googleClientId) }
        var clientSecretInput by remember { mutableStateOf(googleClientSecret) }
        var accessTokenInput by remember { mutableStateOf(googleAccessToken) }
        var authCodeInput by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddFriendDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Google Account Synchronizer",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Material 3 Custom Mini Segmented Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = { selectedTab = 0 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("Maps Cookie", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { selectedTab = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text("OAuth 2.0 Sign-In", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (selectedTab == 0) {
                        // TAB 0: CLASSIC MAPS COOKIE SYNC
                        Text(
                            text = "Connect to your Google account session to automatically display friends actively sharing locations with you in Google Maps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = cookieInput,
                            onValueChange = { 
                                cookieInput = it
                                viewModel.setMapsSharingCookie(it)
                            },
                            label = { Text("Account Session Cookie") },
                            placeholder = { Text("Paste __Secure-3PSID cookie...") },
                            modifier = Modifier.fillMaxWidth().testTag("new_friend_email"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "How to find your cookie string:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "1. Open google.com/maps in standard desktop mode.\n" +
                                       "2. Right-click page and choose Inspect -> Storage -> Cookies.\n" +
                                       "3. Locate and copy the value labeled '__Secure-3PSID'.\n" +
                                       "4. Paste this value here and tap Live Sync!",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 13.sp
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { 
                                    viewModel.generateFriendsAtCurrentLocation()
                                    showAddFriendDialog = false
                                },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                            ) {
                                Text("Mock Friends", fontSize = 11.sp)
                            }

                            TextButton(
                                onClick = { showAddFriendDialog = false },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Cancel", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (cookieInput.isNotBlank()) {
                                        viewModel.setMapsSharingCookie(cookieInput)
                                        viewModel.syncGoogleMapsLocationSharing()
                                        showAddFriendDialog = false
                                    } else {
                                        Toast.makeText(context, "Please paste valid session credentials", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("add_friend_submit")
                            ) {
                                Text("Live Sync", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // TAB 1: SECURE GOOGLE OAUTH 2.0 API & CONTACTS SYNC
                        if (googleProfile != null) {
                            // Already authenticated. Display clean user profile and direct address sync actions
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = googleProfile?.name?.firstOrNull()?.uppercase() ?: "G",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = googleProfile?.name ?: "Google User",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = googleProfile?.email ?: "",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.CheckCircle, 
                                                contentDescription = "Active", 
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                "Active Token Session", 
                                                color = Color(0xFF2E7D32), 
                                                fontSize = 11.sp, 
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        TextButton(
                                            onClick = { viewModel.logOutGoogle() },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Text("Sign Out", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    viewModel.syncGoogleContactsWithOAuth(context)
                                    showAddFriendDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.Contacts, contentDescription = "Contacts")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Map Google Contacts Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            // Needs Sign-In. Draw Google OAuth 2.0 login box
                            Text(
                                text = "Sign in using a Google OAuth Access Token to securely retrieve and map physical home addresses from your account contacts book.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Quick input fields for Developer OAuth configuration
                            OutlinedTextField(
                                value = accessTokenInput,
                                onValueChange = { accessTokenInput = it },
                                label = { Text("Direct OAuth Access Token") },
                                placeholder = { Text("Paste Google accessToken...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Text(
                                text = "Want a quick test? Use standard Google Auth Playground credentials or tap 'Demo Account Sign-In' for simulated live sync.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Button(
                                    onClick = {
                                        if (accessTokenInput.isNotBlank()) {
                                            viewModel.authenticateWithAccessToken(accessTokenInput, context)
                                            showAddFriendDialog = false
                                        } else {
                                            Toast.makeText(context, "Please paste an access token first", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Custom modern representation of Google Multi-colored Logo
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                        }
                                        Text("Sign in with Google API", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        // Demo login mock details to guarantee zero-stutter demo experience
                                        viewModel.authenticateWithAccessToken("ya29.mock_token_success_demo_flow", context)
                                        // Seeds several test friends immediately to test address book sync mechanics
                                        viewModel.setGoogleOAuthCredentials("client-id-sample", "client-secret-sample")
                                        showAddFriendDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                                ) {
                                    Text("Demo Account Sign-In (Simulated Live Sync)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showAddFriendDialog = false },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Cancel", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TravelTimeCalculatorCard(
    friends: List<Friend>,
    userLat: Double,
    userLon: Double,
    distanceUnit: DistanceUnit,
    selectedFriendForDirections: Friend?,
    googleDirections: GoogleDirectionsData? = null,
    onChooseFriendForDirections: (Friend?) -> Unit,
    onNavigateToMapTab: () -> Unit
) {
    if (friends.isEmpty()) {
        return
    }

    var manualChosenFriendId by remember { mutableStateOf<String?>(null) }

    val activeFriend = remember(friends, selectedFriendForDirections, manualChosenFriendId) {
        val currentSelected = selectedFriendForDirections
        if (currentSelected != null && friends.any { it.id == currentSelected.id }) {
            friends.first { it.id == currentSelected.id }
        } else {
            friends.firstOrNull { it.id == manualChosenFriendId } ?: friends.firstOrNull()
        }
    }

    var dropdownExpanded by remember { mutableStateOf(false) }

    var selectedTrafficOverride by remember(activeFriend?.id) {
        mutableStateOf(activeFriend?.trafficCondition ?: TrafficCondition.NORMAL)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("travel_time_calculator_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Traffic Calculator Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "TRAFFIC-ADAPTIVE TRAVEL TIME",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Estimate travel durations under varying traffic",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            if (activeFriend != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Select Friend to Analyze Route:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dropdownExpanded = true }
                                .testTag("calculator_friend_selector"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val friendColor = remember(activeFriend.avatarColorHex) {
                                        Color(android.graphics.Color.parseColor(activeFriend.avatarColorHex))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(friendColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = activeFriend.initial,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = activeFriend.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(${activeFriend.email})",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown expand arrow",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        ) {
                            friends.forEach { friend ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val badgeColor = remember(friend.avatarColorHex) {
                                                Color(android.graphics.Color.parseColor(friend.avatarColorHex))
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(badgeColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(friend.initial, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = friend.name,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "- ${friend.email}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        manualChosenFriendId = friend.id
                                        selectedTrafficOverride = friend.trafficCondition
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                val directDistanceKm = remember(userLat, userLon, activeFriend.latitude, activeFriend.longitude) {
                    MapCalculations.calculateDistanceKm(userLat, userLon, activeFriend.latitude, activeFriend.longitude)
                }
                val drivingDistanceKm = remember(directDistanceKm) {
                    MapCalculations.getDrivingDistanceKm(directDistanceKm)
                }
                val convertedDrivingDistance = remember(drivingDistanceKm, distanceUnit) {
                    MapCalculations.convertKmToUnit(drivingDistanceKm, distanceUnit)
                }

                val timeLightMins = remember(drivingDistanceKm) {
                    MapCalculations.calculateDrivingTimeMinutes(drivingDistanceKm, TrafficCondition.LIGHT)
                }
                val timeNormalMins = remember(drivingDistanceKm) {
                    MapCalculations.calculateDrivingTimeMinutes(drivingDistanceKm, TrafficCondition.NORMAL)
                }
                val timeHeavyMins = remember(drivingDistanceKm) {
                    MapCalculations.calculateDrivingTimeMinutes(drivingDistanceKm, TrafficCondition.HEAVY)
                }

                val currentSelectedTimeMins = when (selectedTrafficOverride) {
                    TrafficCondition.LIGHT -> timeLightMins
                    TrafficCondition.NORMAL -> timeNormalMins
                    TrafficCondition.HEAVY -> timeHeavyMins
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Model Traffic Stream Conditions:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clickable { selectedTrafficOverride = activeFriend.trafficCondition }
                                .padding(vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Text(
                                text = "Use Live Active Status (${activeFriend.trafficCondition.label})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TrafficCondition.entries.forEach { condition ->
                            val isSelected = selectedTrafficOverride == condition
                            val conditionColor = remember(condition.textHexClass) {
                                Color(android.graphics.Color.parseColor(condition.textHexClass))
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedTrafficOverride = condition }
                                    .testTag("traffic_selector_${condition.name}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) conditionColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) conditionColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(conditionColor)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = condition.label.split(" ").firstOrNull() ?: condition.label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) conditionColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (condition) {
                                            TrafficCondition.LIGHT -> MapCalculations.formatDuration(timeLightMins)
                                            TrafficCondition.NORMAL -> MapCalculations.formatDuration(timeNormalMins)
                                            TrafficCondition.HEAVY -> MapCalculations.formatDuration(timeHeavyMins)
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                googleDirections?.let { gd ->
                    if (selectedFriendForDirections?.id == activeFriend.id) {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("google_directions_realtime_card").padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = "Real GPS directions",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "LIVE GOOGLE DIRECTIONS API ROUTE:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp
                                    )
                                }

                                if (gd.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text("Querying Google Maps server...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (gd.error != null) {
                                    Text(
                                        text = "Error: ${gd.error}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text("Falling back to simulated calculations below.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("GOOGLE TRAVEL TIME", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = gd.durationText.ifBlank { "N/A" },
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                        )
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("GOOGLE ROUTE DISTANCE", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = gd.distanceText.ifBlank { "N/A" },
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = "⚡ Real-time road routing is processed & synchronised.",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ESTIMATED DRIVING TIME TO ${activeFriend.name.uppercase()}:",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = MapCalculations.formatDuration(currentSelectedTimeMins),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Surface(
                                color = when (selectedTrafficOverride) {
                                    TrafficCondition.LIGHT -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                    TrafficCondition.NORMAL -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                    TrafficCondition.HEAVY -> Color(0xFFF44336).copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    text = when (selectedTrafficOverride) {
                                        TrafficCondition.LIGHT -> "FAST ROUTE"
                                        TrafficCondition.NORMAL -> "ON-SCHEDULE"
                                        TrafficCondition.HEAVY -> "DELAY CAUTION"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (selectedTrafficOverride) {
                                        TrafficCondition.LIGHT -> Color(0xFF4CAF50)
                                        TrafficCondition.NORMAL -> Color(0xFFFF9800)
                                        TrafficCondition.HEAVY -> Color(0xFFF44336)
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("ESTIMATED ROUTE DISTANCE:", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = String.format("%.2f %s", convertedDrivingDistance, distanceUnit.suffix),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("AVERAGE SPEED PARAMETERS:", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = when {
                                        drivingDistanceKm < 5.0 -> if (selectedTrafficOverride == TrafficCondition.LIGHT) "40 km/h avg" else if (selectedTrafficOverride == TrafficCondition.HEAVY) "22 km/h jam" else "35 km/h local"
                                        drivingDistanceKm < 50.0 -> if (selectedTrafficOverride == TrafficCondition.LIGHT) "75 km/h avg" else if (selectedTrafficOverride == TrafficCondition.HEAVY) "42 km/h jam" else "65 km/h suburban"
                                        else -> if (selectedTrafficOverride == TrafficCondition.LIGHT) "112 km/h avg" else if (selectedTrafficOverride == TrafficCondition.HEAVY) "63 km/h jam" else "98 km/h highway"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when (selectedTrafficOverride) {
                                        TrafficCondition.LIGHT -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                                        TrafficCondition.NORMAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                                        TrafficCondition.HEAVY -> Color(0xFFF44336).copy(alpha = 0.08f)
                                    }
                                )
                                .padding(8.dp)
                        ) {
                            Text(
                                text = when (selectedTrafficOverride) {
                                    TrafficCondition.LIGHT -> "🟢 Optimal Flow: Road speed is boosted by +15%. Clear sailing ahead, minimal stops to target."
                                    TrafficCondition.NORMAL -> "🟡 Standard Flow: Normal road delays. Transit is estimated on typical schedule bounds."
                                    TrafficCondition.HEAVY -> "🔴 Alert: Severe traffic congestion in effect! Speed reduced by -35%. Prepare for bumper-to-bumper lanes."
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = when (selectedTrafficOverride) {
                                    TrafficCondition.LIGHT -> Color(0xFF388E3C)
                                    TrafficCondition.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                                    TrafficCondition.HEAVY -> Color(0xFFD32F2F)
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onChooseFriendForDirections(activeFriend)
                            onNavigateToMapTab()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(38.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("calculator_map_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(19.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Navigation, contentDescription = "Active map", modifier = Modifier.size(14.dp))
                            Text("Map Live Path", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            selectedTrafficOverride = TrafficCondition.HEAVY
                        },
                        modifier = Modifier
                            .weight(1.0f)
                            .height(38.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("calculator_simulate_heavy_button"),
                        shape = RoundedCornerShape(19.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", modifier = Modifier.size(13.dp))
                            Text("Simulate Jam", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text(
                    text = "Sync friends via Google Sync or Demo accounts above to utilize the travel calculator.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FriendRowItem(
    friend: Friend,
    userLat: Double,
    userLon: Double,
    distanceUnit: DistanceUnit,
    isDirectionsSelected: Boolean,
    onToggleTracking: () -> Unit,
    onGetDirections: () -> Unit,
    onClearDirections: () -> Unit
) {
    val context = LocalContext.current

    // 1. Calculate Straight Line Proximity
    val directDistanceKm = remember(userLat, userLon, friend.latitude, friend.longitude) {
        MapCalculations.calculateDistanceKm(userLat, userLon, friend.latitude, friend.longitude)
    }

    // Convert straight line distance
    val directDistanceConverted = remember(directDistanceKm, distanceUnit) {
        MapCalculations.convertKmToUnit(directDistanceKm, distanceUnit)
    }

    // 2. Calculate Road travel offset (Driving path)
    val drivingDistanceKm = remember(directDistanceKm) {
        MapCalculations.getDrivingDistanceKm(directDistanceKm)
    }

    // Convert driving distance
    val drivingDistanceConverted = remember(drivingDistanceKm, distanceUnit) {
        MapCalculations.convertKmToUnit(drivingDistanceKm, distanceUnit)
    }

    // 3. Compute Travel Duration
    val drivingTimeMins = remember(drivingDistanceKm, friend.trafficCondition) {
        MapCalculations.calculateDrivingTimeMinutes(drivingDistanceKm, friend.trafficCondition)
    }

    val drivingTimeStr = remember(drivingTimeMins) {
        MapCalculations.formatDuration(drivingTimeMins)
    }

    val drivingTimeLabelText = remember(friend.isLiveTrafficFetched, friend.customDrivingDurationText, drivingTimeStr) {
        if (friend.isLiveTrafficFetched && friend.customDrivingDurationText != null) {
            "${friend.customDrivingDurationText} (Live)"
        } else {
            "$drivingTimeStr (Est)"
        }
    }

    val drivingDistanceLabelText = remember(friend.isLiveTrafficFetched, friend.customDrivingDistanceText, drivingDistanceConverted, distanceUnit) {
        if (friend.isLiveTrafficFetched && friend.customDrivingDistanceText != null) {
            "${friend.customDrivingDistanceText} (Live)"
        } else {
            String.format("%.1f %s (Est)", drivingDistanceConverted, distanceUnit.suffix)
        }
    }

    val avatarColorHex = remember(friend.avatarColorHex) {
        Color(android.graphics.Color.parseColor(friend.avatarColorHex))
    }

    val trafficColor = remember(friend.trafficCondition.textHexClass) {
        Color(android.graphics.Color.parseColor(friend.trafficCondition.textHexClass))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("friend_card_${friend.id}")
            .clickable { onToggleTracking() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Section (Profile Details & Simulated Tracker Toggle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circle Profile Dot with brush gradient
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(avatarColorHex, Color(0xFF381E72))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.initial,
                        color = Color(0xFFEADDFF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = friend.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = friend.email,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Streaming / GPS mock indicator animation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (friend.isSimulatedMoving) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (friend.isSimulatedMoving) Color(0xFF4CAF50) else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (friend.isSimulatedMoving) "Drifting" else "Parked",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (friend.isSimulatedMoving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Calculations Display Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Driving Time to Meet Card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Car eta",
                        tint = MaterialTheme.colorScheme.secondary, // Pink highlight matching design HTML
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("TIME TO MEET", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = drivingTimeLabelText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary // Pink highlight for ETA
                        )
                    }
                }

                // Driving Distance
                Column(horizontalAlignment = Alignment.End) {
                    Text("DRIVING LENGTH", fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = drivingDistanceLabelText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary // Purple highlight for distance
                    )
                    Text(
                        text = String.format("Crow-flies: %.1f %s", directDistanceConverted, distanceUnit.suffix),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bottom Action Row & Traffic Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Traffic density status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(trafficColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = friend.trafficCondition.label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // On-screen Get Directions (calculates and draws polyline)
                    Button(
                        onClick = {
                            if (isDirectionsSelected) {
                                onClearDirections()
                            } else {
                                onGetDirections()
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDirectionsSelected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (isDirectionsSelected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).minimumInteractiveComponentSize().testTag("get_directions_${friend.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isDirectionsSelected) Icons.Filled.Close else Icons.Filled.Navigation,
                                contentDescription = if (isDirectionsSelected) "Clear Route" else "Get Directions",
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isDirectionsSelected) "Clear Route" else "Get Directions",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Launch native/external system Google Maps directly!
                    OutlinedButton(
                        onClick = {
                            val gmapsUri = Uri.parse(
                                "https://www.google.com/maps/dir/?api=1&origin=$userLat,$userLon&destination=${friend.latitude},${friend.longitude}&travelmode=driving"
                            )
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmapsUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                val generalInt = Intent(Intent.ACTION_VIEW, gmapsUri)
                                context.startActivity(generalInt)
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).minimumInteractiveComponentSize().testTag("drive_friend_${friend.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = "Map Navigation icon",
                                modifier = Modifier.size(14.dp)
                            )
                            Text("Open in Maps", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
