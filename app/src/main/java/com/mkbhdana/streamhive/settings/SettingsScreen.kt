package com.mkbhdana.streamhive.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state = viewModel.uiState
    val availableFolders by viewModel.availableFolders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            // ──── Player Settings ────
            item { SettingsSectionHeader(Icons.Default.PlayCircle, "Player") }

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
                        viewModel::setMapDv7ToHevc
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    SettingsSwitchItem(
                        "Tunnelled Playback",
                        "Experimental hardware path; applied only with HW decoder",
                        Icons.Default.Speed,
                        state.tunneledPlaybackEnabled,
                        viewModel::setTunneledPlaybackEnabled
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

            // ──── Server Settings ────
            item { SettingsSectionHeader(Icons.Default.Dns, "Server") }

            item {
                SettingsCard {
                    SettingsSwitchItem(
                        "Keep Server Running",
                        "Keep proxy server active for external players",
                        Icons.Default.CloudQueue,
                        state.keepServerRunning,
                        viewModel::setKeepServerRunning
                    )
                }
            }

            // ──── Gesture Settings ────
            item { SettingsSectionHeader(Icons.Default.TouchApp, "Gestures") }

            item {
                SettingsCard {
                    SettingsSwitchItem("Volume Gesture", "Swipe right side to adjust volume", Icons.Default.VolumeUp, state.gestureVolumeEnabled, viewModel::setGestureVolumeEnabled)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem("Brightness Gesture", "Swipe left side to adjust brightness", Icons.Default.LightMode, state.gestureBrightnessEnabled, viewModel::setGestureBrightnessEnabled)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem("Seek Gesture", "Swipe horizontally to seek", Icons.Default.FastForward, state.gestureSeekEnabled, viewModel::setGestureSeekEnabled)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem("Double Tap Seek", "Double tap sides to skip", Icons.Default.FastForward, state.gestureDoubleTapEnabled, viewModel::setGestureDoubleTapEnabled)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Tap Seek Duration
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FastForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Tap Seek Duration", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("${state.tapSeekDuration} seconds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Slider(
                            value = state.tapSeekDuration.toFloat(),
                            onValueChange = { viewModel.setTapSeekDuration(it.toInt()) },
                            valueRange = 10f..60f, steps = 4, // 10, 20, 30, 40, 50, 60
                            modifier = Modifier.padding(start = 40.dp)
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem("Pinch to Zoom", "Pinch gesture to zoom video", Icons.Default.ZoomIn, state.gestureZoomEnabled, viewModel::setGestureZoomEnabled)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Gesture Sensitivity", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    when { state.gestureSensitivity <= 0.7f -> "Low"; state.gestureSensitivity <= 1.3f -> "Medium"; else -> "High" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Slider(value = state.gestureSensitivity, onValueChange = viewModel::setGestureSensitivity, valueRange = 0.5f..2.0f, steps = 5, modifier = Modifier.padding(start = 40.dp))
                    }
                }
            }

            // ──── Subtitle Settings ────
            item { SettingsSectionHeader(Icons.Default.Subtitles, "Subtitles") }

            item {
                SettingsCard {
                    val supportedLanguages = listOf(
                        "eng" to "English", "kor" to "Korean", "jpn" to "Japanese",
                        "mal" to "Malayalam", "tam" to "Tamil", "hin" to "Hindi",
                        "spa" to "Spanish", "fra" to "French", "deu" to "German",
                        "por" to "Portuguese", "ita" to "Italian", "rus" to "Russian",
                        "ara" to "Arabic", "zho" to "Chinese", "tha" to "Thai"
                    )

                    // Preferred Audio Language
                    var expandedAudioLang by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandedAudioLang = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Preferred Audio Language", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (state.preferredAudioLanguage == "original") "Original"
                                else supportedLanguages.find { it.first == state.preferredAudioLanguage }?.second ?: state.preferredAudioLanguage,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            DropdownMenu(expanded = expandedAudioLang, onDismissRequest = { expandedAudioLang = false }) {
                                DropdownMenuItem(
                                    text = { Text("Original") },
                                    onClick = { viewModel.setPreferredAudioLanguage("original"); expandedAudioLang = false }
                                )
                                supportedLanguages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { viewModel.setPreferredAudioLanguage(code); expandedAudioLang = false }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Preferred Subtitle Language
                    var expandedSubLang by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandedSubLang = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Subtitles, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Preferred Subtitle Language", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (state.preferredSubtitleLanguage == "none") "None"
                                else supportedLanguages.find { it.first == state.preferredSubtitleLanguage }?.second ?: state.preferredSubtitleLanguage,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            DropdownMenu(expanded = expandedSubLang, onDismissRequest = { expandedSubLang = false }) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = { viewModel.setPreferredSubtitleLanguage("none"); expandedSubLang = false }
                                )
                                supportedLanguages.forEach { (code, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { viewModel.setPreferredSubtitleLanguage(code); expandedSubLang = false }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Exclude Languages (no subtitles for these audio languages)
                    var showExcludeDialog by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showExcludeDialog = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exclude Languages", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            val excludeCount = state.subtitleExcludeLanguages.size
                            Text(
                                if (excludeCount == 0) "None — subtitles auto-select for all"
                                else "$excludeCount excluded",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (showExcludeDialog) {
                        var tempExcluded by remember { mutableStateOf(state.subtitleExcludeLanguages) }
                        AlertDialog(
                            onDismissRequest = { showExcludeDialog = false },
                            title = { Text("Exclude Subtitle for Audio") },
                            text = {
                                Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                                    Text("Subtitles will NOT auto-select when audio is in these languages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(12.dp))
                                    supportedLanguages.forEach { (code, name) ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                tempExcluded = if (tempExcluded.contains(code)) tempExcluded - code else tempExcluded + code
                                            }.padding(vertical = 8.dp)
                                        ) {
                                            Checkbox(
                                                checked = tempExcluded.contains(code),
                                                onCheckedChange = { checked ->
                                                    tempExcluded = if (checked) tempExcluded + code else tempExcluded - code
                                                }
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(name)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.setSubtitleExcludeLanguages(tempExcluded)
                                    showExcludeDialog = false
                                }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExcludeDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Font size
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatSize, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Font Size", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("${state.subtitleFontSize}sp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Slider(
                            value = state.subtitleFontSize.toFloat(),
                            onValueChange = { viewModel.setSubtitleFontSize(it.toInt()) },
                            valueRange = 10f..48f, steps = 18,
                            modifier = Modifier.padding(start = 40.dp)
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Subtitle Color — FIXED: use Color(Int) to avoid crash
                    var showColorPicker by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showColorPicker = !showColorPicker }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Subtitle Color", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(Color(state.subtitleColor.toInt()))
                        )
                    }

                    if (showColorPicker) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val colors = listOf(
                                0xFFFFFFFF to "White", 0xFFFFFF00 to "Yellow",
                                0xFF00FF00 to "Green", 0xFF00FFFF to "Cyan",
                                0xFFFF6600 to "Orange", 0xFFFF0000 to "Red"
                            )
                            colors.forEach { (color, _) ->
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .background(Color(color.toInt()))
                                        .clickable { viewModel.setSubtitleColor(color); showColorPicker = false }
                                )
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Background opacity
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Opacity, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Background Opacity", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("${(state.subtitleBgOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Slider(value = state.subtitleBgOpacity, onValueChange = viewModel::setSubtitleBgOpacity, valueRange = 0f..1f, modifier = Modifier.padding(start = 40.dp))
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Subtitle position
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerticalAlignBottom, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Subtitle Position", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("${state.subtitlePosition}% from top", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Slider(
                            value = state.subtitlePosition.toFloat(),
                            onValueChange = { viewModel.setSubtitlePosition(it.toInt()) },
                            valueRange = 50f..100f, steps = 9,
                            modifier = Modifier.padding(start = 40.dp)
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Edge Type
                    var expandedEdgeType by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandedEdgeType = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TextFields, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Edge Type", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(state.subtitleEdgeType.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            DropdownMenu(expanded = expandedEdgeType, onDismissRequest = { expandedEdgeType = false }) {
                                listOf("none", "outline", "depressed", "shadow", "raised").forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.replaceFirstChar { it.uppercase() }) },
                                        onClick = { viewModel.setSubtitleEdgeType(type); expandedEdgeType = false }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Edge Size
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FormatLineSpacing, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Edge Size", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(if (state.subtitleEdgeSize == 0) "Normal" else "${state.subtitleEdgeSize}px", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Slider(
                            value = state.subtitleEdgeSize.toFloat(),
                            onValueChange = { viewModel.setSubtitleEdgeSize(it.toInt()) },
                            valueRange = 0f..20f, steps = 19,
                            modifier = Modifier.padding(start = 40.dp)
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Outline Color
                    var showOutlineColorPicker by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showOutlineColorPicker = !showOutlineColorPicker }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BorderColor, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Outline Color", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(Color(state.subtitleOutlineColor.toInt()))
                        )
                    }

                    if (showOutlineColorPicker) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val colors = listOf(
                                0xFF000000 to "Black", 0xFFFFFFFF to "White", 0xFFFFFF00 to "Yellow",
                                0xFF00FF00 to "Green", 0xFF00FFFF to "Cyan",
                                0xFFFF6600 to "Orange", 0xFFFF0000 to "Red"
                            )
                            colors.forEach { (color, _) ->
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .background(Color(color.toInt()))
                                        .clickable { viewModel.setSubtitleOutlineColor(color); showOutlineColorPicker = false }
                                )
                            }
                        }
                    }
                }
            }

            // ──── TMDB Settings ────
            item { SettingsSectionHeader(Icons.Default.Movie, "TMDB Metadata") }

            item {
                SettingsCard {
                    // API Key (secured)
                    var apiKeyText by remember { mutableStateOf(state.tmdbApiKey) }
                    LaunchedEffect(state.tmdbApiKey) {
                        if (apiKeyText != state.tmdbApiKey) {
                            apiKeyText = state.tmdbApiKey
                        }
                    }
                    var isKeyVisible by remember { mutableStateOf(false) }
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(16.dp))
                            Text("TMDB API Key", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { apiKeyText = it },
                            placeholder = { Text("Enter your TMDB API key") },
                            modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (isKeyVisible)
                                androidx.compose.ui.text.input.VisualTransformation.None
                            else
                                androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                Row {
                                    // Toggle visibility
                                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                        Icon(
                                            if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            if (isKeyVisible) "Hide" else "Show"
                                        )
                                    }
                                    // Copy to clipboard
                                    if (apiKeyText.isNotEmpty()) {
                                        IconButton(onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(apiKeyText))
                                        }) {
                                            Icon(Icons.Default.ContentCopy, "Copy")
                                        }
                                    }
                                    // Save
                                    IconButton(onClick = { viewModel.setTmdbApiKey(apiKeyText) }) {
                                        Icon(Icons.Default.Check, "Save")
                                    }
                                }
                            }
                        )
                        if (state.tmdbApiKey.isNotEmpty()) {
                            Text(
                                "✓ API key configured",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 40.dp, top = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    // Unified catalog folder management
                    CatalogFoldersSection(
                        viewModel = viewModel,
                        movieFolders = state.tmdbMovieFolders,
                        tvFolders = state.tmdbTvFolders,
                        animeFolders = state.tmdbAnimeFolders,
                        recentFolders = state.tmdbRecentFolders,
                        allFolders = availableFolders,
                        onAddFolder = { folderId, type ->
                            when (type) {
                                "movie" -> viewModel.addMovieFolder(folderId)
                                "tv" -> viewModel.addTvFolder(folderId)
                                "anime_movie" -> viewModel.addAnimeFolder(folderId)
                                "anime_series" -> viewModel.addAnimeFolder(folderId)
                            }
                        },
                        onRemoveFolder = { folderId ->
                            viewModel.removeMovieFolder(folderId)
                            viewModel.removeTvFolder(folderId)
                            viewModel.removeAnimeFolder(folderId)
                            // Optionally, if removing a folder, we could also remove it from recents, but the logic handles it by only showing marked items.
                        },
                        onToggleRecent = { folderId ->
                            viewModel.toggleRecentFolder(folderId)
                        }
                    )
                }
            }

            // ──── Data Management ────
            item { SettingsSectionHeader(Icons.Default.Storage, "Data Management") }

            item {
                SettingsCard {
                    val context = LocalContext.current
                    var showExportDialog by remember { mutableStateOf(false) }
                    var exportIncludeApiKey by remember { mutableStateOf(true) }
                    var exportIncludeMetadata by remember { mutableStateOf(true) }

                    val exportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        if (uri != null) {
                            viewModel.exportSettings(uri, exportIncludeApiKey, exportIncludeMetadata)
                            Toast.makeText(context, "Settings exported successfully", Toast.LENGTH_SHORT).show()
                        }
                    }

                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            viewModel.importSettings(uri) { success, errorMsg ->
                                val msg = if (success) {
                                    "Settings imported successfully"
                                } else {
                                    errorMsg ?: "Failed to import settings"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showExportDialog = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Export Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Backup preferences, API key & metadata", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { importLauncher.launch(arrayOf("application/json")) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Import Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Restore preferences & metadata from file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Export options dialog
                    if (showExportDialog) {
                        AlertDialog(
                            onDismissRequest = { showExportDialog = false },
                            icon = { Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary) },
                            title = { Text("Export Options", fontWeight = FontWeight.Bold) },
                            text = {
                                Column {
                                    Text(
                                        "Choose what to include in the export file:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { exportIncludeApiKey = !exportIncludeApiKey }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = exportIncludeApiKey,
                                            onCheckedChange = { exportIncludeApiKey = it }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("TMDB API Key", style = MaterialTheme.typography.bodyMedium)
                                            Text("Include your API key in the backup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { exportIncludeMetadata = !exportIncludeMetadata }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = exportIncludeMetadata,
                                            onCheckedChange = { exportIncludeMetadata = it }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("TMDB Metadata", style = MaterialTheme.typography.bodyMedium)
                                            Text("Include manually edited metadata fixes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showExportDialog = false
                                    exportLauncher.launch("streamhive_settings.json")
                                }) {
                                    Text("Export")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showExportDialog = false }) {
                                    Text("Cancel")
                                }
                            },
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // ──── About ────
            item { SettingsSectionHeader(Icons.Default.Info, "About") }

            item {
                SettingsCard {
                    val context = LocalContext.current
                    val versionName = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
                        } catch (e: Exception) {
                            "Unknown"
                        }
                    }
                    Column(Modifier.padding(24.dp)) {
                        Text("StreamHive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("v$versionName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("Stream videos from Google Drive with advanced playback features.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        AboutInfoRow("Developer", "mkbhdana")
                        Spacer(Modifier.height(8.dp))
                        AboutInfoRow("Powered by", "ExoPlayer · MPV · TMDB")
                        Spacer(Modifier.height(8.dp))
                        AboutInfoRow("License", "Apache 2.0")
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ──── Unified Catalog Folder Management ────

@Composable
private fun CatalogFoldersSection(
    viewModel: SettingsViewModel,
    movieFolders: Set<String>,
    tvFolders: Set<String>,
    animeFolders: Set<String>,
    recentFolders: Set<String>,
    allFolders: List<MediaFileEntity>,
    onAddFolder: (folderId: String, type: String) -> Unit,
    onRemoveFolder: (folderId: String) -> Unit,
    onToggleRecent: (folderId: String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    
    data class MappedFolder(val id: String, val name: String, val type: String)
    
    // Use ordered folder IDs from ViewModel for display sequence
    val orderedIds = viewModel.getOrderedFolderIds()
    
    val mappedList = remember(movieFolders, tvFolders, animeFolders, allFolders, orderedIds) {
        val folderTypeMap = mutableMapOf<String, String>()
        movieFolders.forEach { folderTypeMap[it] = "movie" }
        tvFolders.forEach { folderTypeMap[it] = "tv" }
        animeFolders.forEach { folderTypeMap[it] = "anime" }
        
        orderedIds.mapNotNull { id ->
            val type = folderTypeMap[id] ?: return@mapNotNull null
            val name = allFolders.find { it.id == id }?.name ?: id.take(20) + "..."
            MappedFolder(id, name, type)
        }
    }

    Column(Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Catalog Folders", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (mappedList.isEmpty()) "No folders added" else "${mappedList.size} folder(s) • tap to view",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle Folders",
                modifier = Modifier.padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Show mapped folders with reorder, type badge, recent star, and remove
        androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                mappedList.forEachIndexed { index, folder ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move up/down
                Column {
                    IconButton(
                        onClick = { viewModel.moveFolderUp(folder.id) },
                        enabled = index > 0,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp, "Move up",
                            tint = if (index > 0) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.moveFolderDown(folder.id) },
                        enabled = index < mappedList.size - 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown, "Move down",
                            tint = if (index < mappedList.size - 1) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    folder.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                val (badgeText, badgeColor) = when (folder.type) {
                    "movie" -> "Movie" to Color(0xFFE91E63)
                    "tv" -> "Series" to Color(0xFF2196F3)
                    "anime" -> "Anime" to Color(0xFF9C27B0)
                    else -> "Other" to Color.Gray
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                val isRecent = folder.id in recentFolders
                IconButton(onClick = { onToggleRecent(folder.id) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (isRecent) Icons.Default.Star else Icons.Default.StarBorder,
                        "Toggle Recently Added",
                        tint = if (isRecent) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = { onRemoveFolder(folder.id) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                }
            }
        }
        }
        }
    }

    // Folder picker with drive browser
    if (showPicker) {
        val alreadyMapped = (movieFolders + tvFolders + animeFolders)
        CatalogFolderBrowserDialog(
            viewModel = viewModel,
            alreadyMapped = alreadyMapped,
            onSelect = { folderId, type ->
                onAddFolder(folderId, type)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogFolderBrowserDialog(
    viewModel: SettingsViewModel,
    alreadyMapped: Set<String>,
    onSelect: (folderId: String, type: String) -> Unit,
    onDismiss: () -> Unit
) {
    val browserState = viewModel.folderBrowserState
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf("movie") }

    // Init browser when dialog opens
    LaunchedEffect(Unit) { viewModel.initFolderBrowser() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (browserState.selectedDriveId != null) {
                    IconButton(
                        onClick = { viewModel.browserGoBack(); selectedFolderId = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text("Add Catalog Folder", modifier = Modifier.weight(1f))
            }
        },
        text = {
            Column {
                // Breadcrumb
                if (browserState.selectedDriveId != null) {
                    val driveName = browserState.drives.find { it.id == browserState.selectedDriveId }?.name ?: "Drive"
                    val pathParts = listOf(driveName) + browserState.folderStack.map { it.second }
                    Text(
                        pathParts.joinToString(" › "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Type selector
                Text("Content Type:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedType == "movie",
                            onClick = { selectedType = "movie" },
                            label = { Text("Movies", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = selectedType == "tv",
                            onClick = { selectedType = "tv" },
                            label = { Text("Series", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedType == "anime_movie",
                            onClick = { selectedType = "anime_movie" },
                            label = { Text("Anime Movies", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = selectedType == "anime_series",
                            onClick = { selectedType = "anime_series" },
                            label = { Text("Anime Series", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Loading
                if (browserState.isLoading) {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (browserState.selectedDriveId == null) {
                    // Drive list
                    Text("Select a drive:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(browserState.drives) { drive ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.browserSelectDrive(drive.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CloudQueue, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(drive.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (browserState.drives.isEmpty()) {
                        Text("No drives found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    // Folder list inside selected drive
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(browserState.currentFolders) { folder ->
                            val isMapped = folder.id in alreadyMapped
                            val isSelected = selectedFolderId == folder.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        if (!isMapped) selectedFolderId = folder.id
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder, null,
                                    tint = if (isMapped) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isMapped) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isMapped) {
                                    Text("Added", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    // Navigate into folder button
                                    IconButton(
                                        onClick = { viewModel.browserOpenFolder(folder.id, folder.name); selectedFolderId = null },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronRight, "Open", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (browserState.currentFolders.isEmpty()) {
                        Text("No subfolders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        },
        confirmButton = {
            val currentDirId = viewModel.browserCurrentFolderId()
            Button(
                onClick = {
                    if (selectedFolderId != null) {
                        onSelect(selectedFolderId!!, selectedType)
                    } else if (currentDirId != null) {
                        onSelect(currentDirId, selectedType)
                    }
                },
                enabled = selectedFolderId != null || currentDirId != null
            ) {
                Text(if (selectedFolderId != null) "Add Selected" else "Add Current Dir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ──── Reusable Components ────

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsSwitchItem(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDropdownItem(
    title: String, subtitle: String, expanded: Boolean, onToggle: () -> Unit, icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onToggle) { content() }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
