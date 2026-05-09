package com.mkbhdana.streamhive.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.data.db.MediaFileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TmdbSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TMDB Metadata") },
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
                    var isApiKeyVisible by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        OutlinedTextField(
                            value = state.tmdbApiKey,
                            onValueChange = viewModel::setTmdbApiKey,
                            label = { Text("TMDB API Key (v3 auth)") },
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle visibility"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    CatalogFoldersSection(
                        viewModel = viewModel,
                        movieFolders = state.tmdbMovieFolders,
                        tvFolders = state.tmdbTvFolders,
                        animeFolders = state.tmdbAnimeFolders,
                        recentFolders = state.tmdbRecentFolders,
                        allFolders = viewModel.availableFolders.collectAsState().value,
                        onAddFolder = viewModel::addTmdbFolder,
                        onRemoveFolder = viewModel::removeTmdbFolder,
                        onToggleRecent = viewModel::toggleRecentFolder
                    )
                }
            }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text("Add Catalog Folder", modifier = Modifier.weight(1f))
            }
        },
        text = {
            Column {
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

                if (browserState.isLoading) {
                    Box(
                        Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (browserState.selectedDriveId == null) {
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
