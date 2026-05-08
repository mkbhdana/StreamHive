package com.mkbhdana.streamhive.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.player.mpv.PlayerEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                    var engineExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Default Player",
                        subtitle = when (state.preferredEngine) {
                            PlayerEngine.EXO_PLAYER -> "ExoPlayer (Media3)"
                            PlayerEngine.MPV -> "MPV Player"
                            PlayerEngine.EXTERNAL -> "External (Open with)"
                        },
                        expanded = engineExpanded,
                        onToggle = { engineExpanded = !engineExpanded },
                        icon = Icons.Default.SmartDisplay
                    ) {
                        DropdownMenuItem(
                            text = { Text("ExoPlayer (Media3)") },
                            onClick = { viewModel.setPreferredEngine(PlayerEngine.EXO_PLAYER); engineExpanded = false },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("MPV Player") },
                            onClick = { viewModel.setPreferredEngine(PlayerEngine.MPV); engineExpanded = false },
                            leadingIcon = { Icon(Icons.Default.Videocam, null, Modifier.size(20.dp)) },
                            enabled = state.isMpvAvailable
                        )
                        DropdownMenuItem(
                            text = { Text("External (Open with)") },
                            onClick = { viewModel.setPreferredEngine(PlayerEngine.EXTERNAL); engineExpanded = false },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null, Modifier.size(20.dp)) }
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    var decoderExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Decoder",
                        subtitle = when (state.defaultDecoder) {
                            "hw" -> "Hardware (HW)"; "sw" -> "Software (SW)"; "hw+" -> "Hardware+ (HW+)"
                            else -> state.defaultDecoder
                        },
                        expanded = decoderExpanded,
                        onToggle = { decoderExpanded = !decoderExpanded },
                        icon = Icons.Default.Memory
                    ) {
                        listOf("hw" to "Hardware (HW)", "sw" to "Software (SW)", "hw+" to "Hardware+ (HW+)").forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { viewModel.setDefaultDecoder(k); decoderExpanded = false })
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SettingsSwitchItem(
                        "Map DV7 to HEVC",
                        "Dolby Vision Profile 7 to HEVC fallback for unsupported devices",
                        Icons.Default.Memory,
                        state.mapDv7ToHevc,
                        viewModel::setMapDv7ToHevc,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SettingsSwitchItem(
                        "Tunnelled Playback",
                        "Experimental hardware path; applied only with HW decoder",
                        Icons.Default.Speed,
                        state.tunneledPlaybackEnabled,
                        viewModel::setTunneledPlaybackEnabled,
                        hapticsEnabled = state.hapticFeedbackEnabled
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    var resizeExpanded by remember { mutableStateOf(false) }
                    SettingsDropdownItem(
                        title = "Default Resize Mode",
                        subtitle = when (state.defaultResizeMode) {
                            "fit" -> "Fit"; "fill" -> "Fill"; "zoom" -> "Zoom"
                            "16:9" -> "16:9"; "4:3" -> "4:3"; else -> state.defaultResizeMode
                        },
                        expanded = resizeExpanded,
                        onToggle = { resizeExpanded = !resizeExpanded },
                        icon = Icons.Default.AspectRatio
                    ) {
                        listOf("fit" to "Fit", "fill" to "Fill", "zoom" to "Zoom", "16:9" to "16:9", "4:3" to "4:3").forEach { (k, v) ->
                            DropdownMenuItem(text = { Text(v) }, onClick = { viewModel.setDefaultResizeMode(k); resizeExpanded = false })
                        }
                    }
                }
            }
        }
    }
}
