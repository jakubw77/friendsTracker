package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LocationRadar(
    userLat: Double,
    userLon: Double,
    friends: List<Friend>,
    modifier: Modifier = Modifier,
    onFriendClick: (Friend) -> Unit = {}
) {
    // Rotating sweep animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepAngle"
    )

    // Glowing user rings animation
    val userPulseRadius by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "UserPulse"
    )

    val colorScheme = MaterialTheme.colorScheme
    val radarBackground = colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val gridColor = colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val sweepColor = colorScheme.primary.copy(alpha = 0.25f)
    val primaryColor = colorScheme.primary
    val listColors = friends.map { Color(android.graphics.Color.parseColor(it.avatarColorHex)) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(CircleShape)
            .background(radarBackground)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Radar Canvas
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.width.coerceAtMost(size.height) / 2

            // 1. Draw concentric grid circles representing distance markers (10km, 30km, 60km)
            val ringRadii = listOf(outerRadius * 0.33f, outerRadius * 0.66f, outerRadius)
            val pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)

            ringRadii.forEachIndexed { index, radius ->
                drawCircle(
                    color = gridColor,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5f, pathEffect = if (index < 2) pathEffect else null)
                )
            }

            // Draw Crosshairs (horizontal and vertical cardinal axes)
            drawLine(
                color = gridColor,
                start = Offset(center.x - outerRadius, center.y),
                end = Offset(center.x + outerRadius, center.y),
                strokeWidth = 1f,
                pathEffect = pathEffect
            )
            drawLine(
                color = gridColor,
                start = Offset(center.x, center.y - outerRadius),
                end = Offset(center.x, center.y + outerRadius),
                strokeWidth = 1f,
                pathEffect = pathEffect
            )

            // 2. Draw active rotating radar sweep line & slice
            val sweepRadians = Math.toRadians(sweepAngle.toDouble())
            val sweepTarget = Offset(
                (center.x + outerRadius * cos(sweepRadians)).toFloat(),
                (center.y + outerRadius * sin(sweepRadians)).toFloat()
            )

            // Radar sweep gradient slice
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(Color.Transparent, sweepColor, Color.Transparent),
                    center = center
                ),
                startAngle = sweepAngle - 30f,
                sweepAngle = 30f,
                useCenter = true
            )
            
            // Sweep edge line
            drawLine(
                color = primaryColor.copy(alpha = 0.4f),
                start = center,
                end = sweepTarget,
                strokeWidth = 2f
            )

            // 3. Draw friends on the radar relative to user
            // To fit them nicely on the radar, let's normalize their delta coords
            // We use standard coordinate delta scaled to fit within the outermost circle (60km representation)
            // Let's determine scale dynamically so all friends fit beautifully
            var maxDelta = 0.001 // Prevent divide by zero
            friends.forEach { friend ->
                val dLat = Math.abs(friend.latitude - userLat)
                val dLon = Math.abs(friend.longitude - userLon)
                if (dLat > maxDelta) maxDelta = dLat
                if (dLon > maxDelta) maxDelta = dLon
            }
            
            // Ensure friendly map padding factor
            val maxDeltaDegree = maxDelta * 1.25

            friends.forEach { friend ->
                // Calculate pixel offset
                // dLon -> x offset, dLat -> y offset (y increases down on screen, so subtract dLat)
                val dx = (friend.longitude - userLon) / maxDeltaDegree * outerRadius
                val dy = -(friend.latitude - userLat) / maxDeltaDegree * outerRadius
                
                val friendPos = Offset(
                    (center.x + dx).toFloat(),
                    (center.y + dy).toFloat()
                )

                // Draw Friend position ping pulse
                val colorHex = Color(android.graphics.Color.parseColor(friend.avatarColorHex))
                drawCircle(
                    color = colorHex.copy(alpha = 0.18f),
                    radius = 20f,
                    center = friendPos
                )
                drawCircle(
                    color = colorHex,
                    radius = 6.5f,
                    center = friendPos
                )
            }

            // 4. Center Marker (The User - "My Location")
            // Glowing pulsing ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = userPulseRadius,
                center = center
            )
            drawCircle(
                color = primaryColor,
                radius = 7.5f,
                center = center,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = Color.White,
                radius = 3.5f,
                center = center
            )
        }

        // Add Floating Label Indicators for Radar Range
        Box(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                "N",
                color = colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                "Range: ~50km",
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
