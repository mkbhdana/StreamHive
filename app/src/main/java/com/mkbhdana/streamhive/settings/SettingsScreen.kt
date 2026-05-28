package com.mkbhdana.streamhive.settings

import android.widget.Toast
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel
) {
    val state = viewModel.uiState
    val screenContext = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsCard {
                    SettingsMenuRow(
                        title = "Player Settings",
                        subtitle = "Engine, Decoder, Resize Mode",
                        icon = Icons.Default.SmartDisplay,
                        onClick = { onNavigate(Routes.SETTINGS_PLAYER) }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsMenuRow(
                        title = "Gestures & Haptics",
                        subtitle = "Volume, Brightness, Speed Hold",
                        icon = Icons.Default.TouchApp,
                        onClick = { onNavigate(Routes.SETTINGS_GESTURES) }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsMenuRow(
                        title = "Audio & Subtitles",
                        subtitle = "Languages, LibASS, Appearance",
                        icon = Icons.Default.Subtitles,
                        onClick = { onNavigate(Routes.SETTINGS_SUBTITLES) }
                    )
                }
            }

            item {
                SettingsCard {
                    SettingsMenuRow(
                        title = "TMDB Metadata",
                        subtitle = "API Key, Catalog Folders",
                        icon = Icons.Default.Movie,
                        onClick = { onNavigate(Routes.SETTINGS_TMDB) }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsMenuRow(
                        title = "Storage & Cache",
                        subtitle = "Manage cached data and backups",
                        icon = Icons.Default.Storage,
                        onClick = { onNavigate(Routes.SETTINGS_STORAGE) }
                    )
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !state.isCheckingForUpdate) {
                                    viewModel.checkForUpdates { message ->
                                        Toast.makeText(screenContext, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isCheckingForUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Check for Updates", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    if (state.isCheckingForUpdate) "Checking latest release..."
                                    else "Last checked: ${formatUpdateCheckTimestamp(state.lastUpdateCheckAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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

            item { SettingsSectionHeader(Icons.Default.AccountCircle, "Account") }
            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLogout)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Log Out", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row {
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatUpdateCheckTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    return java.text.SimpleDateFormat(
        "dd MMM yyyy, hh:mm a",
        java.util.Locale.getDefault()
    ).format(java.util.Date(timestamp))
}
