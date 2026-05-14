package com.mkbhdana.streamhive.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesturesSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestures & Haptics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsCard {
                    SettingsSwitchItem(
                        "Haptic Feedback",
                        "Vibrate on slider and button interactions",
                        Icons.Default.Vibration,
                        state.hapticFeedbackEnabled,
                        viewModel::setHapticFeedbackEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )
                    
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    
                    SettingsSwitchItem(
                        "Volume Gestures",
                        "Swipe vertically on the right side",
                        Icons.AutoMirrored.Filled.VolumeUp,
                        state.gestureVolumeEnabled,
                        viewModel::setGestureVolumeEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )
                    
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    
                    SettingsSwitchItem(
                        "Brightness Gestures",
                        "Swipe vertically on the left side",
                        Icons.Default.BrightnessMedium,
                        state.gestureBrightnessEnabled,
                        viewModel::setGestureBrightnessEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )
                    
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    
                    SettingsSwitchItem(
                        "Seek Gestures",
                        "Swipe horizontally to seek",
                        Icons.Default.FastForward,
                        state.gestureSeekEnabled,
                        viewModel::setGestureSeekEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )
                    
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    
                    SettingsSwitchItem(
                        "Pinch to Zoom",
                        "Pinch to change resize mode",
                        Icons.Default.ZoomIn,
                        state.gestureZoomEnabled,
                        viewModel::setGestureZoomEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )
                    
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    
                    SettingsSwitchItem(
                        "Long Press 2x Speed",
                        "Hold the right side to temporarily play at 2x",
                        Icons.Default.Speed,
                        state.gestureSpeedPressEnabled,
                        viewModel::setGestureSpeedPressEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )
                    
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    
                    SettingsSwitchItem(
                        "Double Tap to Seek",
                        "Double tap edges to seek forward/backward",
                        Icons.Default.TouchApp,
                        state.gestureDoubleTapEnabled,
                        viewModel::setGestureDoubleTapEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )
                    
                    if (state.gestureDoubleTapEnabled) {
                        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Double Tap Duration", style = MaterialTheme.typography.bodyMedium)
                                Text("${state.tapSeekDuration}s", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            HapticSlider(
                                value = state.tapSeekDuration.toFloat(),
                                onValueChange = { viewModel.setTapSeekDuration(it.roundToInt()) },
                                valueRange = 10f..60f,
                                steps = 4, // 10, 20, 30, 40, 50, 60
                                modifier = Modifier.padding(horizontal = 8.dp),
                                hapticsEnabled = state.hapticFeedbackEnabled
                            )
                        }
                    }
                    
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Gesture Sensitivity", style = MaterialTheme.typography.bodyMedium)
                            Text(String.format("%.1fx", state.gestureSensitivity), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        HapticSlider(
                            value = state.gestureSensitivity,
                            onValueChange = viewModel::setGestureSensitivity,
                            valueRange = 0.5f..2.0f,
                            steps = 14, // 0.1 increments
                            modifier = Modifier.padding(horizontal = 8.dp),
                            hapticsEnabled = state.hapticFeedbackEnabled
                        )
                    }
                }
            }
        }
    }
}
