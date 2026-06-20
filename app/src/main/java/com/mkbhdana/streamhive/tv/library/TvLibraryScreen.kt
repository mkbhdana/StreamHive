package com.mkbhdana.streamhive.tv.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.mkbhdana.streamhive.catalog.CatalogViewModel
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.tv.components.TvBackButton
import com.mkbhdana.streamhive.tv.components.TvClearFieldButton
import com.mkbhdana.streamhive.tv.components.TvItemInfoDialog
import com.mkbhdana.streamhive.tv.components.driveThumbnailUrl
import com.mkbhdana.streamhive.tv.components.longPressable
import com.mkbhdana.streamhive.tv.theme.TvCardColor
import com.mkbhdana.streamhive.tv.theme.TvDimens
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor
import com.mkbhdana.streamhive.tv.theme.TvWhite
import com.mkbhdana.streamhive.tv.theme.TvTextSecondaryColor as TextSecondary
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor as TvBackground
import com.mkbhdana.streamhive.util.FileUtils
import kotlinx.coroutines.delay

/**
 * Library / folder browser, backed by the shared [CatalogViewModel] folder
 * navigation. Inside a drive it offers a Refresh button, a scoped search field,
 * and a Grid/List view toggle — matching the mobile folder browser.
 */
@Composable
fun TvLibraryScreen(
    viewModel: CatalogViewModel,
    onPlay: (fileId: String, fileName: String, engine: PlayerEngine, decoder: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val preferredEngine = viewModel.getPreferredEngine()
    val firstFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Control-row focus requesters (hoisted so the focus anchor below can use them).
    val backReq = remember { FocusRequester() }
    val refreshReq = remember { FocusRequester() }
    val fieldReq = remember { FocusRequester() }
    val clearReq = remember { FocusRequester() }
    val exactReq = remember { FocusRequester() }
    val gridReq = remember { FocusRequester() }
    var infoItem by remember { mutableStateOf<MediaFileEntity?>(null) }
    // The field holds local text; the search only runs when the user submits (OK/Enter).
    var scopedText by remember { mutableStateOf("") }

    val searching = state.scopedSearchQuery.isNotBlank()
    val displayedFiles = if (searching) state.scopedSearchResults else state.files

    // Clear the scoped search (and the field) whenever the folder path changes.
    LaunchedEffect(state.selectedDrive, state.folderStack) {
        scopedText = ""
        viewModel.clearScopedSearch()
    }

    // Move focus into the content only on drive/folder navigation (keyed on the raw
    // folder files, NOT the scoped-search results), so typing in the search field
    // never yanks focus onto a result — the user moves down manually.
    LaunchedEffect(state.selectedDrive, state.folderStack, state.files, state.sharedDrives) {
        // Anchor focus on the (persistent) back button first so the nav drawer cannot
        // grab focus (and glitch open) while a folder loads, then move into the
        // first item once it lays out.
        if (state.selectedDrive != null) runCatching { backReq.requestFocus() }
        repeat(12) {
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(16)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TvBackground)
            .padding(top = TvDimens.Overscan)
    ) {
        // Breadcrumb header
        Row(
            modifier = Modifier.padding(start = TvDimens.Overscan, end = TvDimens.Overscan, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.selectedDrive != null) {
                TvBackButton(
                    onClick = {
                        if (state.folderStack.isNotEmpty()) viewModel.navigateBack()
                        else viewModel.clearSelectedDrive()
                    },
                    focusRequester = backReq
                )
                Spacer(Modifier.width(14.dp))
            }
            Text(
                text = state.selectedDrive?.name ?: "Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            state.folderStack.takeLast(2).forEach { folder ->
                Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                Text(folder.name, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            }
        }

        // Controls: grid/list toggle always; refresh + scoped search only in a folder.
        val inFolder = state.selectedDrive != null
        if (inFolder || state.sharedDrives.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = TvDimens.Overscan, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (inFolder) {
                    OutlinedTextField(
                        value = scopedText,
                        onValueChange = { scopedText = it },
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .focusRequester(fieldReq)
                            // The field consumes left/right for the cursor, so explicitly
                            // hop to the neighbouring chips; symmetric in both directions.
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                                        Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                                        Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                                        Key.DirectionRight -> {
                                            val next = if (scopedText.isNotEmpty()) clearReq else exactReq
                                            runCatching { next.requestFocus() }; true
                                        }
                                        else -> false
                                    }
                                } else false
                            },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.updateScopedSearchQuery(scopedText.trim()) }),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = if (scopedText.isNotEmpty()) {
                            {
                                TvClearFieldButton(
                                    focusRequester = clearReq,
                                    onLeft = { runCatching { fieldReq.requestFocus() } },
                                    onRight = { runCatching { exactReq.requestFocus() } }
                                ) { scopedText = ""; viewModel.clearScopedSearch(); runCatching { fieldReq.requestFocus() } }
                            }
                        } else null,
                        placeholder = { Text("Search this folder…", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    LibChip(
                        Icons.Default.Search, "Exact", selected = state.isExactSearch,
                        focusRequester = exactReq,
                        onLeft = {
                            val back = if (scopedText.isNotEmpty()) clearReq else fieldReq
                            runCatching { back.requestFocus() }
                        },
                        onRight = { runCatching { refreshReq.requestFocus() } }
                    ) { viewModel.toggleScopedExactSearch() }
                    Spacer(Modifier.width(12.dp))
                    LibChip(
                        Icons.Default.Refresh, "Refresh",
                        focusRequester = refreshReq,
                        onLeft = { runCatching { exactReq.requestFocus() } },
                        onRight = { runCatching { gridReq.requestFocus() } }
                    ) { viewModel.refresh() }
                    if (state.isRefreshing) {
                        Spacer(Modifier.width(10.dp))
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.width(20.dp).height(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                val grid = state.isGridView
                val gridOnLeft: (() -> Unit)? = if (inFolder) {
                    { runCatching { refreshReq.requestFocus() } }
                } else null
                LibChip(
                    if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    if (grid) "List" else "Grid",
                    focusRequester = gridReq,
                    onLeft = gridOnLeft
                ) { viewModel.toggleGridView() }
            }
            Spacer(Modifier.width(0.dp))
        }

        val isLoading = (state.isLoading || (searching && state.isScopedSearchLoading)) &&
            displayedFiles.isEmpty() && state.selectedDrive != null
        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.selectedDrive == null -> {
                if (state.isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        contentPadding = PaddingValues(horizontal = TvDimens.Overscan, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.sharedDrives, key = { it.id }) { drive ->
                            TvGridItemCard(
                                name = drive.name,
                                isFolder = true,
                                thumbnailUrl = null,
                                onClick = { viewModel.selectDrive(drive) },
                                onLongClick = {},
                                focusRequester = if (drive == state.sharedDrives.firstOrNull()) firstFocus else null
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        contentPadding = PaddingValues(horizontal = TvDimens.Overscan, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.sharedDrives, key = { it.id }) { drive ->
                            TvListRow(
                                name = drive.name,
                                isFolder = true,
                                onClick = { viewModel.selectDrive(drive) },
                                onLongClick = {},
                                focusRequester = if (drive == state.sharedDrives.firstOrNull()) firstFocus else null
                            )
                        }
                    }
                }
            }

            displayedFiles.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (searching) "No matches in this folder" else "No videos in this folder",
                    color = TextSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            state.isGridView -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(horizontal = TvDimens.Overscan, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(displayedFiles, key = { it.id }) { file ->
                        TvGridItemCard(
                            name = file.name,
                            isFolder = file.isFolder,
                            thumbnailUrl = if (file.isFolder) null else driveThumbnailUrl(file.thumbnailLink, 480),
                            onClick = {
                                if (file.isFolder) viewModel.openFolder(file.id, file.name)
                                else onPlay(file.id, file.name, preferredEngine, null)
                            },
                            onLongClick = { infoItem = file },
                            focusRequester = if (file == displayedFiles.firstOrNull()) firstFocus else null
                        )
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    contentPadding = PaddingValues(horizontal = TvDimens.Overscan, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedFiles, key = { it.id }) { file ->
                        TvListRow(
                            name = file.name,
                            isFolder = file.isFolder,
                            onClick = {
                                if (file.isFolder) viewModel.openFolder(file.id, file.name)
                                else onPlay(file.id, file.name, preferredEngine, null)
                            },
                            onLongClick = { infoItem = file },
                            focusRequester = if (file == displayedFiles.firstOrNull()) firstFocus else null
                        )
                    }
                }
            }
        }
    }

    infoItem?.let { item ->
        TvItemInfoDialog(file = item, onDismiss = { infoItem = null })
    }
}

@Composable
private fun LibChip(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val container = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.28f)
        else -> TvCardColor.copy(alpha = 0.7f)
    }
    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            // Explicit, symmetric left/right hops to neighbours (bypasses 2D search,
            // which otherwise exits to the drawer).
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> if (onLeft != null) { onLeft(); true } else false
                        Key.DirectionRight -> if (onRight != null) { onRight(); true } else false
                        else -> false
                    }
                } else false
            }
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.width(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TvListRow(name: String, isFolder: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else TvCardColor.copy(alpha = 0.5f))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .longPressable(onLongClick)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isFolder) Icons.Default.Folder else Icons.Default.PlayArrow,
            null,
            tint = if (focused) Color.Black else TextSecondary,
            modifier = Modifier.width(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            color = if (focused) Color.Black else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** Compact grid card (drives / folders / videos), ~5 per row. Long-press shows info. */
@Composable
private fun TvGridItemCard(
    name: String,
    isFolder: Boolean,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester?
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(TvCardColor)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .longPressable(onLongClick)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .then(if (focused) Modifier.border(3.dp, TvWhite, shape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            when {
                isFolder -> Icon(
                    Icons.Default.Folder, null,
                    tint = if (focused) TvWhite else TextSecondary,
                    modifier = Modifier.size(30.dp)
                )
                thumbnailUrl != null -> AsyncImage(thumbnailUrl, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Icon(Icons.Default.PlayArrow, null, tint = TextSecondary, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) TvWhite else Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

