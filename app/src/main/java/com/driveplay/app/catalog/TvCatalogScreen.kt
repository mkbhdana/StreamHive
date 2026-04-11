package com.driveplay.app.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.driveplay.app.ui.components.LoadingIndicator
import com.driveplay.app.ui.components.TvMediaCard
import com.driveplay.app.ui.theme.*

@Composable
fun TvCatalogScreen(
    onPlayFile: (fileId: String, fileName: String) -> Unit,
    onLogout: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    BackHandler(enabled = uiState.folderStack.isNotEmpty()) {
        viewModel.navigateBack()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TV Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(DarkSurface, TvBackground)
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Purple60,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "DrivePlay",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    // Drive name
                    if (uiState.selectedDrive != null) {
                        Spacer(modifier = Modifier.width(24.dp))
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.selectedDrive!!.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = AccentCyan
                        )
                    }
                }

                // Breadcrumb on TV
                if (uiState.folderStack.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        uiState.folderStack.takeLast(2).forEach { folder ->
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Content
            when {
                uiState.isLoading && uiState.files.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(message = "Loading your library...")
                    }
                }
                uiState.files.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "No video files found",
                                color = TextSecondary,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Press BACK to go up",
                                color = TextTertiary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                else -> {
                    // Group files: folders first, then videos
                    val folders = uiState.files.filter { it.isFolder }
                    val videos = uiState.files.filter { !it.isFolder }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                        contentPadding = PaddingValues(bottom = 48.dp)
                    ) {
                        // Folders row
                        if (folders.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(start = 48.dp, top = 16.dp)) {
                                    Text(
                                        text = "Folders",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(end = 48.dp)
                                    ) {
                                        items(folders, key = { it.id }) { file ->
                                            TvMediaCard(
                                                file = file,
                                                onClick = {
                                                    viewModel.openFolder(file.id, file.name)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Videos row
                        if (videos.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(start = 48.dp, top = 24.dp)) {
                                    Text(
                                        text = "Videos",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(end = 48.dp)
                                    ) {
                                        items(videos, key = { it.id }) { file ->
                                            TvMediaCard(
                                                file = file,
                                                onClick = {
                                                    onPlayFile(file.id, file.name)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Drive selector rows if at root
                        if (uiState.folderStack.isEmpty() && uiState.sharedDrives.size > 1) {
                            item {
                                Column(modifier = Modifier.padding(start = 48.dp, top = 32.dp)) {
                                    Text(
                                        text = "Switch Drive",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(end = 48.dp)
                                    ) {
                                        items(
                                            uiState.sharedDrives.filter { it != uiState.selectedDrive },
                                            key = { it.id }
                                        ) { drive ->
                                            Card(
                                                modifier = Modifier
                                                    .width(260.dp)
                                                    .focusable(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = DarkSurfaceCard
                                                ),
                                                onClick = { viewModel.selectDrive(drive) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Default.CloudQueue,
                                                        contentDescription = null,
                                                        tint = Blue60,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Text(
                                                        text = drive.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = TextPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loading overlay
        if (uiState.isLoading && uiState.files.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Purple60,
                trackColor = TvBackground
            )
        }

        // Error
        if (uiState.error != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(48.dp)
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = AccentRed.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, null, tint = AccentRed)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = uiState.error ?: "",
                        color = AccentRed,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
