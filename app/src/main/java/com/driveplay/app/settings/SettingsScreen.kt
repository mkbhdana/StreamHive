package com.driveplay.app.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.driveplay.app.data.db.MediaFileEntity
import com.driveplay.app.player.mpv.PlayerEngine

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
                    SettingsSwitchItem("Double Tap Seek", "Double tap sides to skip 10s", Icons.Default.Replay10, state.gestureDoubleTapEnabled, viewModel::setGestureDoubleTapEnabled)
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
                }
            }

            // ──── TMDB Settings ────
            item { SettingsSectionHeader(Icons.Default.Movie, "TMDB Metadata") }

            item {
                SettingsCard {
                    // API Key
                    var apiKeyText by remember { mutableStateOf(state.tmdbApiKey) }
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
                            trailingIcon = {
                                IconButton(onClick = { viewModel.setTmdbApiKey(apiKeyText) }) {
                                    Icon(Icons.Default.Check, "Save")
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

                    // Folder mappings
                    TmdbFolderSection(
                        title = "Movie Folders",
                        icon = Icons.Default.Movie,
                        mappedFolderIds = state.tmdbMovieFolders,
                        allFolders = availableFolders,
                        onAddFolder = viewModel::addMovieFolder,
                        onRemoveFolder = viewModel::removeMovieFolder
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    TmdbFolderSection(
                        title = "TV Show Folders",
                        icon = Icons.Default.Tv,
                        mappedFolderIds = state.tmdbTvFolders,
                        allFolders = availableFolders,
                        onAddFolder = viewModel::addTvFolder,
                        onRemoveFolder = viewModel::removeTvFolder
                    )

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    TmdbFolderSection(
                        title = "Anime Folders",
                        icon = Icons.Default.Animation,
                        mappedFolderIds = state.tmdbAnimeFolders,
                        allFolders = availableFolders,
                        onAddFolder = viewModel::addAnimeFolder,
                        onRemoveFolder = viewModel::removeAnimeFolder
                    )
                }
            }

            // ──── About ────
            item { SettingsSectionHeader(Icons.Default.Info, "About") }

            item {
                SettingsCard {
                    Column(Modifier.padding(24.dp)) {
                        Text("DrivePlay", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("v2.0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ──── TMDB Folder Picker Section ────

@Composable
private fun TmdbFolderSection(
    title: String,
    icon: ImageVector,
    mappedFolderIds: Set<String>,
    allFolders: List<MediaFileEntity>,
    onAddFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val mappedFolders = allFolders.filter { it.id in mappedFolderIds }

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (mappedFolderIds.isEmpty()) "No folders mapped" else "${mappedFolderIds.size} folder(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Add, "Add folder", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Show mapped folders with remove button
        mappedFolders.forEach { folder ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    folder.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { onRemoveFolder(folder.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Show IDs for mapped folders not in the DB yet
        (mappedFolderIds - allFolders.map { it.id }.toSet()).forEach { folderId ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(folderId.take(20) + "...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemoveFolder(folderId) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    // Folder picker dialog
    if (showPicker) {
        FolderPickerDialog(
            folders = allFolders.filter { it.id !in mappedFolderIds },
            onSelect = { folderId ->
                onAddFolder(folderId)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun FolderPickerDialog(
    folders: List<MediaFileEntity>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = if (searchQuery.isBlank()) folders
    else folders.filter { it.name.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search folders...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    Text(
                        if (folders.isEmpty()) "No folders cached. Browse some folders in the Folders tab first."
                        else "No matching folders",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.id }) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelect(folder.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(folder.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
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
