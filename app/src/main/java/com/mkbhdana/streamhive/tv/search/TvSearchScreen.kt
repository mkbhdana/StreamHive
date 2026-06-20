package com.mkbhdana.streamhive.tv.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mkbhdana.streamhive.catalog.CatalogViewModel
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.tv.components.TvBackButton
import com.mkbhdana.streamhive.tv.components.TvClearFieldButton
import com.mkbhdana.streamhive.tv.components.TvExpandingCard
import com.mkbhdana.streamhive.tv.components.TvItemInfoDialog
import com.mkbhdana.streamhive.tv.components.backdropModel
import com.mkbhdana.streamhive.tv.components.driveThumbnailUrl
import com.mkbhdana.streamhive.tv.components.longPressable
import com.mkbhdana.streamhive.tv.components.posterModel
import com.mkbhdana.streamhive.tv.theme.TvCardColor
import com.mkbhdana.streamhive.tv.theme.TvDimens
import com.mkbhdana.streamhive.tv.theme.TvWhite
import com.mkbhdana.streamhive.tv.theme.TvTextSecondaryColor as TextSecondary
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor as TvBackground
import kotlinx.coroutines.delay

/**
 * Global search backed by [CatalogViewModel]. Mirrors the mobile search: three
 * result sections (TMDB catalog matches, Folders, Files), an isolated folder
 * browser reached by opening a folder result, and a long-press info popup.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchScreen(
    viewModel: CatalogViewModel,
    onPlay: (fileId: String, fileName: String, engine: PlayerEngine, decoder: String?) -> Unit,
    onOpenInfo: (driveFileId: String, mediaType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val fieldFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    val exactFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var infoItem by remember { mutableStateOf<MediaFileEntity?>(null) }
    // The field holds local text; the search only runs when the user submits (OK/Enter).
    var queryText by remember { mutableStateOf(state.searchQuery) }
    val runSearch = { viewModel.updateSearchQuery(queryText.trim()) }

    LaunchedEffect(Unit) { viewModel.prepareGlobalSearch() }

    val tmdbResults = state.tmdbSearchResults
    val tmdbIds = tmdbResults.map { it.id }.toSet()
    val allResults = state.searchResults.values.flatten()
    val folderResults = allResults.filter { it.isFolder && it.id !in tmdbIds }
    val fileResults = allResults.filter { !it.isFolder && it.id !in tmdbIds }
    val inSearchFolder = state.searchFolderStack.isNotEmpty()

    LaunchedEffect(inSearchFolder) {
        if (!inSearchFolder) runCatching { fieldFocus.requestFocus() }
    }
    BackHandler(enabled = inSearchFolder) { viewModel.navigateBackSearchFolder() }

    Box(modifier = modifier.fillMaxSize().background(TvBackground)) {
        if (inSearchFolder) {
            TvSearchFolderBrowser(
                title = state.searchFolderStack.joinToString("  ›  ") { it.name },
                files = state.searchFolderFiles,
                loading = state.isSearchFolderLoading,
                isGridView = state.isGridView,
                onToggleView = { viewModel.toggleGridView() },
                onBack = { viewModel.navigateBackSearchFolder() },
                onOpenFolder = { f -> viewModel.openSearchFolder(f.id, f.name, f.driveId) },
                onPlay = { f -> onPlay(f.id, f.name, viewModel.getPreferredEngine(), null) },
                onLongClick = { infoItem = it }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = TvDimens.Overscan, start = TvDimens.Overscan, end = TvDimens.Overscan)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .focusRequester(fieldFocus)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionRight -> {
                                            val next = if (queryText.isNotEmpty()) clearFocus else exactFocus
                                            runCatching { next.requestFocus() }; true
                                        }
                                        Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                                        Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                                        else -> false
                                    }
                                } else false
                            },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = if (queryText.isNotEmpty()) {
                            {
                                TvClearFieldButton(
                                    focusRequester = clearFocus,
                                    onLeft = { runCatching { fieldFocus.requestFocus() } },
                                    onRight = { runCatching { exactFocus.requestFocus() } }
                                ) { queryText = ""; viewModel.updateSearchQuery(""); runCatching { fieldFocus.requestFocus() } }
                            }
                        } else null,
                        placeholder = { Text("Search movies, series, files…", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(Modifier.width(14.dp))
                    TvToggleChip(
                        "Exact", state.isExactSearch,
                        modifier = Modifier.focusRequester(exactFocus),
                        onLeft = {
                            val back = if (queryText.isNotEmpty()) clearFocus else fieldFocus
                            runCatching { back.requestFocus() }
                        }
                    ) { viewModel.toggleExactSearch() }
                }

                if (state.searchQuery.isBlank()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("Type to search your drives", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                    }
                    return@Column
                }

                val noResults = tmdbResults.isEmpty() && folderResults.isEmpty() && fileResults.isEmpty()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (tmdbResults.isNotEmpty()) {
                        item {
                            TmdbSection(
                                results = tmdbResults,
                                metadata = state.tmdbMetadata,
                                onOpenInfo = onOpenInfo
                            )
                        }
                    }
                    if (folderResults.isNotEmpty()) {
                        item {
                            ResultRow(
                                title = "Folders",
                                files = folderResults,
                                getDriveName = viewModel::getDriveName,
                                onClick = { f -> viewModel.openSearchFolder(f.id, f.name, f.driveId) },
                                onLongClick = { infoItem = it }
                            )
                        }
                    }
                    if (fileResults.isNotEmpty()) {
                        item {
                            ResultRow(
                                title = "Files",
                                files = fileResults,
                                getDriveName = viewModel::getDriveName,
                                onClick = { f -> onPlay(f.id, f.name, viewModel.getPreferredEngine(), null) },
                                onLongClick = { infoItem = it }
                            )
                        }
                    }
                    if (noResults && !state.isSearchLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                                Text("No results", color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        infoItem?.let { f ->
            TvItemInfoDialog(
                file = f,
                location = viewModel.getDriveName(f.driveId),
                onDismiss = { infoItem = null }
            )
        }
    }
}

/** TMDB "Catalog matches": portrait posters that expand to landscape (+title) on focus. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TmdbSection(
    results: List<MediaFileEntity>,
    metadata: Map<String, com.mkbhdana.streamhive.data.db.TmdbMetadataEntity>,
    onOpenInfo: (String, String) -> Unit
) {
    val first = remember { FocusRequester() }
    Column {
        SectionHeader("Catalog matches")
        LazyRow(
            modifier = Modifier.focusGroup().focusProperties { enter = { first } },
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(results, key = { "tmdb-${it.id}" }) { file ->
                val meta = metadata[file.id]
                TvExpandingCard(
                    title = meta?.title ?: file.name,
                    posterUrl = posterModel(meta, file),
                    backdropUrl = backdropModel(meta, file),
                    onClick = { onOpenInfo(file.id, meta?.mediaType ?: "auto") },
                    focusRequester = if (file == results.first()) first else null,
                    expandDelayMs = 600,
                    titleOverlay = true
                )
            }
        }
    }
}

/** A "Folders"/"Files" row of landscape cards. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ResultRow(
    title: String,
    files: List<MediaFileEntity>,
    getDriveName: (String) -> String,
    onClick: (MediaFileEntity) -> Unit,
    onLongClick: (MediaFileEntity) -> Unit
) {
    val first = remember { FocusRequester() }
    Column {
        SectionHeader(title)
        LazyRow(
            modifier = Modifier.focusGroup().focusProperties { enter = { first } },
            contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files, key = { "${title}-${it.id}" }) { file ->
                TvSearchResultCard(
                    name = file.name,
                    isFolder = file.isFolder,
                    thumbnailUrl = if (file.isFolder) null else driveThumbnailUrl(file.thumbnailLink, 480),
                    subtitle = getDriveName(file.driveId),
                    onClick = { onClick(file) },
                    onLongClick = { onLongClick(file) },
                    modifier = Modifier.width(200.dp),
                    focusRequester = if (file == files.first()) first else null
                )
            }
        }
    }
}

/** Isolated folder browser shown after opening a folder result. */
@Composable
private fun TvSearchFolderBrowser(
    title: String,
    files: List<MediaFileEntity>,
    loading: Boolean,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    onBack: () -> Unit,
    onOpenFolder: (MediaFileEntity) -> Unit,
    onPlay: (MediaFileEntity) -> Unit,
    onLongClick: (MediaFileEntity) -> Unit
) {
    val backReq = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    // Anchor focus on the (persistent) back button while a folder loads so the nav
    // drawer can't grab focus and glitch open, then move into the grid once it lays out.
    LaunchedEffect(files, loading) {
        runCatching { backReq.requestFocus() }
        if (!loading) {
            repeat(12) {
                if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(16)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TvDimens.Overscan, start = TvDimens.Overscan, end = TvDimens.Overscan)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            TvBackButton(onClick = onBack, focusRequester = backReq)
            Spacer(Modifier.width(14.dp))
            Text("Search Results", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
            Text(
                title, style = MaterialTheme.typography.titleMedium, color = TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            TvToggleChip(if (isGridView) "List" else "Grid", selected = false) { onToggleView() }
        }
        when {
            loading && files.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            files.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Folder is empty", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
            }
            isGridView -> LazyVerticalGrid(
                // Compact grid: ~5–6 small cards per row.
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItems(files, key = { it.id }) { file ->
                    TvSearchResultCard(
                        name = file.name,
                        isFolder = file.isFolder,
                        thumbnailUrl = if (file.isFolder) null else driveThumbnailUrl(file.thumbnailLink, 480),
                        subtitle = null,
                        onClick = { if (file.isFolder) onOpenFolder(file) else onPlay(file) },
                        onLongClick = { onLongClick(file) },
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = if (file == files.firstOrNull()) first else null
                    )
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(files, key = { it.id }) { file ->
                    TvSearchListRow(
                        name = file.name,
                        isFolder = file.isFolder,
                        onClick = { if (file.isFolder) onOpenFolder(file) else onPlay(file) },
                        onLongClick = { onLongClick(file) },
                        focusRequester = if (file == files.firstOrNull()) first else null
                    )
                }
            }
        }
    }
}

/** Compact list row (folder/file) for the search folder browser. */
@Composable
private fun TvSearchListRow(
    name: String,
    isFolder: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focusRequester: FocusRequester?
) {
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
            modifier = Modifier.size(22.dp)
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

/** Landscape result card (folder icon or video thumbnail) with name overlay + long-press. */
@Composable
private fun TvSearchResultCard(
    name: String,
    isFolder: Boolean,
    thumbnailUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester?
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
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
            .then(if (focused) Modifier.border(3.dp, TvWhite, shape) else Modifier)
    ) {
        if (isFolder) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.Folder, null, tint = if (focused) TvWhite else TextSecondary, modifier = Modifier.size(34.dp))
            }
        } else if (thumbnailUrl != null) {
            AsyncImage(thumbnailUrl, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.VideoFile, null, tint = TextSecondary, modifier = Modifier.size(34.dp))
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.86f))
            )
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(7.dp)) {
            Text(
                name,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvToggleChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val container = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft && onLeft != null) {
                    onLeft(); true
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
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}
