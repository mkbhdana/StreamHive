package com.mkbhdana.streamhive.player.gesture

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GestureIndicatorOverlay(
    gestureState: GestureState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Volume indicator — left side (swipe is on right, indicator on opposite)
        AnimatedVisibility(
            visible = gestureState.showVolumeIndicator,
            enter = fadeIn(tween(150)) + scaleIn(tween(150)),
            exit = fadeOut(tween(300)) + scaleOut(tween(300)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp)
        ) {
            VerticalIndicator(
                icon = if (gestureState.volumePercent > 0.5f) Icons.Default.VolumeUp
                else if (gestureState.volumePercent > 0f) Icons.Default.VolumeDown
                else Icons.Default.VolumeOff,
                label = "Volume",
                percent = gestureState.volumePercent,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        // Brightness indicator — right side (swipe is on left, indicator on opposite)
        AnimatedVisibility(
            visible = gestureState.showBrightnessIndicator,
            enter = fadeIn(tween(150)) + scaleIn(tween(150)),
            exit = fadeOut(tween(300)) + scaleOut(tween(300)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp)
        ) {
            VerticalIndicator(
                icon = if (gestureState.brightnessPercent > 0.5f) Icons.Default.LightMode
                else Icons.Default.BrightnessLow,
                label = "Brightness",
                percent = gestureState.brightnessPercent,
                color = Color(0xFFFFAB40)
            )
        }

        // Seek indicator — above center play button
        AnimatedVisibility(
            visible = gestureState.showSeekIndicator,
            enter = fadeIn(tween(100)) +
                scaleIn(tween(150)) +
                slideInVertically(tween(150), initialOffsetY = { 16 }),
            exit = fadeOut(tween(220)) + scaleOut(tween(220)),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-96).dp)
        ) {
            Card(
                shape = RoundedCornerShape(50),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.78f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (gestureState.seekDeltaSeconds >= 0)
                            Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = buildString {
                            if (gestureState.seekDeltaSeconds >= 0) append("+")
                            append("${gestureState.seekDeltaSeconds}s")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Zoom indicator — center top
        AnimatedVisibility(
            visible = gestureState.showZoomIndicator,
            enter = fadeIn(tween(100)) + slideInVertically(initialOffsetY = { -20 }),
            exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { -20 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
        ) {
            Card(
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.75f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "${(gestureState.zoomLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    percent: Float,
    color: Color
) {
    // Animate the progress bar smoothly
    val animatedPercent by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 1f),
        animationSpec = tween(100),
        label = "progress"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.75f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Vertical progress bar
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(140.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedPercent)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${(animatedPercent * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
