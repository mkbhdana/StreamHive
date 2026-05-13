package com.mkbhdana.streamhive.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    var imageCacheSize by remember { mutableLongStateOf(0L) }
    var catalogCacheSize by remember { mutableLongStateOf(0L) }
    var isCalculatingCache by remember { mutableStateOf(true) }

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showClearPlaybackDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    
    // Backup state
    var exportIncludeMetadata by remember { mutableStateOf(true) }
    var exportIncludeTmdbKey by remember { mutableStateOf(true) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.exportSettings(it, exportIncludeTmdbKey, exportIncludeMetadata) { success, msg ->
                val toastMsg = if (success) "Settings exported successfully" else "Export failed: $msg"
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        uri?.let {
            isImporting = true
            viewModel.importSettings(it) { success, msg ->
                isImporting = false
                val toastMsg = if (success) "Settings imported successfully" else "Import failed: $msg"
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) String.format("%.2f MB", mb) else String.format("%.2f KB", kb)
    }

    fun reloadCacheSizes() {
        isCalculatingCache = true
        viewModel.calculateCacheSizes { images, db ->
            imageCacheSize = images
            catalogCacheSize = db
            isCalculatingCache = false
        }
    }

    LaunchedEffect(Unit) {
        reloadCacheSizes()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage & Cache") },
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
                SettingsSectionHeader(Icons.Default.Storage, "Cache Management")
            }

            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearPlaybackDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Clear Playback Cache/Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Deletes progress, history, Continue Watching, and playback selections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearDataDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Clear Cache & Data", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Deletes images, metadata, and history", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isCalculatingCache) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(formatSize(imageCacheSize + catalogCacheSize), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showResetSettingsDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Reset All Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                            Text("Restores default app preferences", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                SettingsSectionHeader(Icons.Default.CloudSync, "Backup & Restore")
            }

            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExportDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Upload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Export Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Backup settings, history, and mappings to a JSON file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isImporting) { importLauncher.launch(arrayOf("application/json")) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Import Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Restore settings and history from a backup file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear Cache & Data?") },
            text = { Text("This will delete all cached posters, TMDB metadata, and playback history.\n\nYour Auth Token, TMDB API Key, and App Settings will NOT be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataDialog = false
                    viewModel.clearCacheAndData {
                        reloadCacheSizes()
                        Toast.makeText(context, "Cache and data cleared", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Clear", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearPlaybackDialog) {
        AlertDialog(
            onDismissRequest = { showClearPlaybackDialog = false },
            title = { Text("Clear Playback Cache/Data?") },
            text = { Text("This will remove playback progress, playback history, Continue Watching items, and saved player engine/decoder selections for played files.\n\nCatalog metadata, posters, folder mappings, API keys, and app settings will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearPlaybackDialog = false
                    viewModel.clearPlaybackCacheAndData {
                        Toast.makeText(context, "Playback cache and data cleared", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Clear", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPlaybackDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            title = { Text("Reset All Settings?") },
            text = { Text("This will restore all player, subtitle, and gesture preferences to their defaults. Your Auth Token and TMDB API Key will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetSettingsDialog = false
                    viewModel.resetPreferences { Toast.makeText(context, "Settings Reset", Toast.LENGTH_SHORT).show() }
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Options") },
            text = {
                Column {
                    Text("Select what to include in your backup:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { exportIncludeTmdbKey = !exportIncludeTmdbKey }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = exportIncludeTmdbKey, onCheckedChange = { exportIncludeTmdbKey = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("TMDB API Key", style = MaterialTheme.typography.bodyMedium)
                            Text("Include your API key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { exportIncludeMetadata = !exportIncludeMetadata }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = exportIncludeMetadata, onCheckedChange = { exportIncludeMetadata = it })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("TMDB Metadata", style = MaterialTheme.typography.bodyMedium)
                            Text("Include manual metadata fixes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    exportLauncher.launch("streamhive_settings.json")
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }
}
