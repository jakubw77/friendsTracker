package com.example

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveMapWebView(
    userLat: Double,
    userLon: Double,
    friends: List<Friend>,
    distanceUnit: DistanceUnit,
    apiKey: String,
    selectedFriendForDirections: Friend?,
    googleDirections: GoogleDirectionsData? = null,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Convert friend list to JSON string for Javascript communication
    val friendsJson = remember(friends, distanceUnit, userLat, userLon) {
        friends.joinToString(prefix = "[", postfix = "]") { friend ->
            val directDistanceKm = MapCalculations.calculateDistanceKm(userLat, userLon, friend.latitude, friend.longitude)
            val convertedDistance = MapCalculations.convertKmToUnit(directDistanceKm, distanceUnit)
            val distanceStr = String.format("%.1f %s", convertedDistance, distanceUnit.suffix)
            
            val drivingTimeMins = MapCalculations.calculateDrivingTimeMinutes(
                MapCalculations.getDrivingDistanceKm(directDistanceKm),
                friend.trafficCondition
            )
            val defaultDurationStr = MapCalculations.formatDuration(drivingTimeMins)
            
            val finalDistanceText = friend.customDrivingDistanceText ?: distanceStr
            val finalDurationText = if (friend.isLiveTrafficFetched && friend.customDrivingDurationText != null) {
                "${friend.customDrivingDurationText} (Live)"
            } else {
                "$defaultDurationStr (Est)"
            }
            
            """{"id":"${friend.id}","latitude":${friend.latitude},"longitude":${friend.longitude},"name":"${friend.name.replace("\"", "\\\"")}","email":"${friend.email.replace("\"", "\\\"")}","initial":"${friend.initial.replace("\"", "\\\"")}","avatarColorHex":"${friend.avatarColorHex}","distanceFormatted":"$finalDistanceText","durationFormatted":"$finalDurationText","isLive":${friend.isLiveTrafficFetched},"avatarUrl":"${friend.avatarUrl ?: ""}"}"""
        }
    }

    // Full custom HTML template integrating Leaflet and OpenStreetMap (100% Free, NO API key required)
    val htmlContent = remember(userLat, userLon) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="initial-scale=1.0, user-scalable=no, width=device-width" />
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>
            html, body, #map {
              height: 100%;
              width: 100%;
              margin: 0;
              padding: 0;
              background-color: #14111f;
            }
            /* Dark Mode filter to style free OpenStreetMap tiles into a cyberpunk dark purple scheme matching original app theme */
            .leaflet-tile-container img {
              filter: invert(100%) hue-rotate(180deg) brightness(95%) contrast(90%);
            }
            .custom-friend-marker {
              background: transparent !important;
              border: none !important;
              display: flex !important;
              align-items: center !important;
              justify-content: center !important;
            }
          </style>
          <script>
            let map;
            let userMarker;
            let markers = {};
            let routeLine = null;

            function initMap() {
              const initialCoords = [$userLat, $userLon];
              
              map = L.map('map', {
                zoomControl: false,
                attributionControl: false
              }).setView(initialCoords, 12);

              // Use standard OpenStreetMap tiles
              L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19
              }).addTo(map);

              // Standard blue pulsing style circle marker representing User Origin Coordinates
              userMarker = L.circleMarker(initialCoords, {
                color: '#FFFFFF',
                fillColor: '#65558F',
                fillOpacity: 1,
                radius: 8,
                weight: 2
              }).addTo(map);

              userMarker.bindPopup("<b>My GPS Origin Coordinates</b>");

              // Load active location coordinates
              updateFriendMarkers($friendsJson);
            }

            function decodePolyline(encoded) {
              if (!encoded) return [];
              var points = [];
              var index = 0, len = encoded.length;
              var lat = 0, lng = 0;
              while (index < len) {
                var b, shift = 0, result = 0;
                do {
                  b = encoded.charCodeAt(index++) - 63;
                  result |= (b & 0x1f) << shift;
                  shift += 5;
                } while (b >= 0x20);
                var dlat = ((result & 1) ? ~(result >> 1) : (result >> 1));
                lat += dlat;
                shift = 0;
                result = 0;
                do {
                  b = encoded.charCodeAt(index++) - 63;
                  result |= (b & 0x1f) << shift;
                  shift += 5;
                } while (b >= 0x20);
                var dlng = ((result & 1) ? ~(result >> 1) : (result >> 1));
                lng += dlng;
                points.push([lat / 1e5, lng / 1e5]);
              }
              return points;
            }

            function drawRoute(userLat, userLon, destLat, destLon, googlePoints) {
              clearRoute();
              
              if (googlePoints) {
                try {
                  const latLngs = decodePolyline(googlePoints);
                  if (latLngs && latLngs.length > 0) {
                    routeLine = L.polyline(latLngs, {
                      color: '#4CAF50',
                      weight: 6,
                      opacity: 0.9
                    }).addTo(map);
                    
                    map.fitBounds(routeLine.getBounds(), { padding: [40, 40] });
                    return;
                  }
                } catch(err) {
                  console.error("Decode polyline failed", err);
                }
              }

              // Use OSRM (Open Source Routing Machine) API to calculate the real road route sequence
              const url = "https://router.project-osrm.org/route/v1/driving/" + userLon + "," + userLat + ";" + destLon + "," + destLat + "?geometries=geojson";
              
              fetch(url)
                .then(response => response.json())
                .then(data => {
                  if (data && data.routes && data.routes.length > 0) {
                    const route = data.routes[0];
                    const coordinates = route.geometry.coordinates;
                    const latLngs = coordinates.map(coord => [coord[1], coord[0]]);
                    
                    routeLine = L.polyline(latLngs, {
                      color: '#D0BCFF',
                      weight: 5,
                      opacity: 0.85
                    }).addTo(map);
                    
                    map.fitBounds(routeLine.getBounds(), { padding: [40, 40] });
                  } else {
                    fallbackRoute(userLat, userLon, destLat, destLon);
                  }
                })
                .catch(err => {
                  console.error("OSRM route lookup failed, drawing fallback line:", err);
                  fallbackRoute(userLat, userLon, destLat, destLon);
                });
            }

            function fallbackRoute(userLat, userLon, destLat, destLon) {
              routeLine = L.polyline([[userLat, userLon], [destLat, destLon]], {
                color: '#D0BCFF',
                dashArray: '5, 10',
                weight: 4,
                opacity: 0.85
              }).addTo(map);
              
              const bounds = L.latLngBounds([[userLat, userLon], [destLat, destLon]]);
              map.fitBounds(bounds, { padding: [40, 40] });
            }

            function clearRoute() {
              if (routeLine) {
                map.removeLayer(routeLine);
                routeLine = null;
              }
            }

            function updateLocations(userLat, userLon, friends) {
              if (map) {
                const userPos = [userLat, userLon];
                if (userMarker) {
                  userMarker.setLatLng(userPos);
                }
                if (friends) {
                  updateFriendMarkers(friends);
                }
              }
            }

            function panToUser() {
              if (map && userMarker) {
                map.setView(userMarker.getLatLng(), 13);
              }
            }

            function fitAllMarkers() {
              if (!map) return;
              const group = [];
              if (userMarker) {
                group.push(userMarker.getLatLng());
              }
              for (let id in markers) {
                group.push(markers[id].getLatLng());
              }
              if (group.length > 0) {
                const bounds = L.latLngBounds(group);
                map.fitBounds(bounds, { padding: [40, 40] });
              } else {
                panToUser();
              }
            }

            function updateFriendMarkers(friends) {
              if (!map) return;
              
              const currentIds = new Set();
              
              friends.forEach(friend => {
                currentIds.add(friend.id);
                const position = [friend.latitude, friend.longitude];
                
                const labelText = friend.initial + " • " + (friend.durationFormatted || "");
                const colorHex = friend.avatarColorHex || '#E91E63';

                // Display custom styled SVG/HTML marker centering accurately
                const iconHtml = '<div style="transform: translate(-50%, -50%); display: flex; align-items: center; justify-content: center; background-color: ' + colorHex + '; border: 2px solid #FFFFFF; border-radius: 14px; color: #FFFFFF; font-family: system-ui, sans-serif; font-weight: bold; font-size: 11px; white-space: nowrap; padding: 4px 10px; box-shadow: 0 2px 5px rgba(0,0,0,0.4);">' + labelText + '</div>';

                const customIcon = L.divIcon({
                  html: iconHtml,
                  className: 'custom-friend-marker',
                  iconSize: [1, 1],
                  iconAnchor: [0, 0]
                });

                const infoContent = '<div style="color: #2D2440; padding: 4px; font-family: system-ui, sans-serif; min-width: 140px;">' +
                  '<h4 style="margin: 0 0 4px 0; font-size: 13px; font-weight: bold; color: #1F1A2D;">' + friend.name + '</h4>' +
                  '<p style="margin: 0; font-size: 11px; color: #6E687A; word-break: break-all;">' + friend.email + '</p>' +
                  '<p style="margin: 4px 0 0 0; font-size: 11px; font-weight: bold; color: ' + colorHex + ';">Distance: ' + friend.distanceFormatted + '</p>' +
                  '<p style="margin: 2px 0 0 0; font-size: 11px; font-weight: bold; color: #6750A4;">Driving ETA: ' + friend.durationFormatted + '</p>' +
                  '</div>';

                if (markers[friend.id]) {
                  markers[friend.id].setLatLng(position);
                  markers[friend.id].setIcon(customIcon);
                  markers[friend.id].setPopupContent(infoContent);
                } else {
                  const marker = L.marker(position, { icon: customIcon }).addTo(map);
                  marker.bindPopup(infoContent);
                  markers[friend.id] = marker;
                }
              });

              // Clean up any removed contact markers from list
              for (let id in markers) {
                if (!currentIds.has(id)) {
                  map.removeLayer(markers[id]);
                  delete markers[id];
                }
              }
            }
          </script>
        </head>
        <body onload="initMap()">
          <div id="map"></div>
        </body>
        </html>
        """.trimIndent()
    }

    // Push coordinates updates into Javascript instantly when locations change
    LaunchedEffect(webViewInstance, userLat, userLon, friendsJson) {
        webViewInstance?.evaluateJavascript(
            "updateLocations($userLat, $userLon, $friendsJson);", null
        )
    }

    // Reactively draw or clear directions routing polyline instantly on selection change
    LaunchedEffect(webViewInstance, selectedFriendForDirections, googleDirections, userLat, userLon) {
        webViewInstance?.let { webView ->
            if (selectedFriendForDirections != null) {
                val encodedPointsRaw = googleDirections?.points ?: ""
                val encodedPointsEscaped = encodedPointsRaw.replace("\\", "\\\\").replace("'", "\\'")
                webView.evaluateJavascript(
                    "drawRoute($userLat, $userLon, ${selectedFriendForDirections.latitude}, ${selectedFriendForDirections.longitude}, '$encodedPointsEscaped');", null
                )
            } else {
                webView.evaluateJavascript(
                    "clearRoute();", null
                )
            }
        }
    }

    Box(modifier = modifier.background(Color(0xFF14111F))) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    loadDataWithBaseURL("https://maps.googleapis.com", htmlContent, "text/html", "UTF-8", null)
                    webViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                // Ensures updates if HTML loads at slightly different timings
                webViewInstance = webView
            }
        )

        // Maps HUD controls overlayed beautifully at the bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    webViewInstance?.evaluateJavascript("panToUser();", null)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center User", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Center Me", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    webViewInstance?.evaluateJavascript("fitAllMarkers();", null)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Icon(Icons.Default.Cached, contentDescription = "Fit All", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Fit Bounds", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
