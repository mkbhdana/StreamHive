package com.mkbhdana.streamhive.settings

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
                    // API Key (secured)
                    var apiKeyText by remember { mutableStateOf(state.tmdbApiKey) }
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
                        }
                    )
                }
            }

            // ──── Data Management ────
            item { SettingsSectionHeader(Icons.Default.Storage, "Data Management") }

            item {
                SettingsCard {
                    val context = LocalContext.current
                    val exportLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        if (uri != null) {
                            viewModel.exportSettings(uri)
                            Toast.makeText(context, "Settings exported successfully", Toast.LENGTH_SHORT).show()
                        }
                    }

                    val importLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            viewModel.importSettings(uri) { success ->
                                val msg = if (success) "Settings imported successfully" else "Failed to import settings"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { exportLauncher.launch("streamhive_settings.json") }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Export Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Backup your preferences to a file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("Restore your preferences from a file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ──── About ────
            item { SettingsSectionHeader(Icons.Default.Info, "About") }

            item {
                SettingsCard {
                    Column(Modifier.padding(24.dp)) {
                        Text("StreamHive", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

// ──── Unified Catalog Folder Management ────

@Composable
private fun CatalogFoldersSection(
    viewModel: SettingsViewModel,
    movieFolders: Set<String>,
    tvFolders: Set<String>,
    animeFolders: Set<String>,
    allFolders: List<MediaFileEntity>,
    onAddFolder: (folderId: String, type: String) -> Unit,
    onRemoveFolder: (folderId: String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    
    // Build a unified list of all mapped folders with their type
    data class MappedFolder(val id: String, val name: String, val type: String)
    
    val mappedList = remember(movieFolders, tvFolders, animeFolders, allFolders) {
        val result = mutableListOf<MappedFolder>()
        movieFolders.forEach { id ->
            val name = allFolders.find { it.id == id }?.name ?: id.take(20) + "..."
            result.add(MappedFolder(id, name, "movie"))
        }
        tvFolders.forEach { id ->
            val name = allFolders.find { it.id == id }?.name ?: id.take(20) + "..."
            result.add(MappedFolder(id, name, "tv"))
        }
        animeFolders.forEach { id ->
            val name = allFolders.find { it.id == id }?.name ?: id.take(20) + "..."
            result.add(MappedFolder(id, name, "anime"))
        }
        result.distinctBy { it.id }
    }

    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Catalog Folders", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (mappedList.isEmpty()) "No folders added" else "${mappedList.size} folder(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Show mapped folders with type badge and remove button
        mappedList.forEach { folder ->
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
                Spacer(Modifier.width(6.dp))
                // Type badge
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
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { onRemoveFolder(folder.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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
