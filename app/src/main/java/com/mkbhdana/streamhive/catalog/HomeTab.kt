package com.mkbhdana.streamhive.catalog

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.util.FileUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.widget.Toast
import com.mkbhdana.streamhive.ui.components.LoadingIndicator
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.absoluteValue
import androidx.compose.foundation.lazy.LazyListState

@Composable
fun HomeTab(
    state: CatalogUiState,
    preferredEngine: PlayerEngine,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onPlayFileWithDecoder: (
        fileId: String,
        fileName: String,
        engine: PlayerEngine,
        decoderMode: String?
    ) -> Unit = { fileId, fileName, engine, _ -> onPlayFile(fileId, fileName, engine) },
    onNavigateToSettings: () -> Unit,
    onClearHistory: () -> Unit = {},
    onNavigateToInfo: (String, String) -> Unit = { _, _ -> },
    onRemoveFromContinue: (String) -> Unit = {},
    onPlayFromStart: (String, String, PlayerEngine, String?) -> Unit = onPlayFileWithDecoder,
    onNavigateToSeeAll: (String) -> Unit = {},
    homeListState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val hasContinuePlaying = state.continuePlayingItems.isNotEmpty()
    val hasAnyContent = state.homeSections.isNotEmpty() || state.homeRecentlyAdded.isNotEmpty()
    val listState = homeListState
    val heroPosterScale by animateFloatAsState(
        targetValue = 1f + when {
            listState.firstVisibleItemIndex == 0 -> (listState.firstVisibleItemScrollOffset / 900f).coerceIn(0f, 0.08f)
            listState.firstVisibleItemIndex > 0 -> 0.08f
            else -> 0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "hero_scroll_scale"
    )

    if (!state.hasTmdbSetup && !hasContinuePlaying) {
        TmdbSetupPrompt(onNavigateToSettings = onNavigateToSettings, modifier = modifier)
        return
    }

    // No connectivity and no cached content → show centered message
    if (state.isOffline && !hasAnyContent && !hasContinuePlaying) {
        NoConnectivityMessage(modifier = modifier)
        return
    }

    val showSkeleton = state.isHomeRefreshing || state.isHomeLoading ||
        (state.isLoading && !hasAnyContent)

    // Show skeleton while drives/home content load, and for explicit home refreshes.
    if (showSkeleton) {
        HomeSkeletonLoading(modifier = modifier)
        return
    }

    if (!hasAnyContent && !hasContinuePlaying && state.hasTmdbSetup) {
        TmdbSetupPrompt(onNavigateToSettings = onNavigateToSettings, modifier = modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp), // Extra padding to avoid overlay
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

        // Recently added hero
        if (state.homeRecentlyAdded.isNotEmpty()) {
            item {
                RecentlyAddedHeroSection(
                    files = state.homeRecentlyAdded,
                    tmdbMetadata = state.tmdbMetadata,
                    posterScrollScale = heroPosterScale,
                    onNavigateToInfo = onNavigateToInfo
                )
            }
        }



        if (hasContinuePlaying && state.homeSections.isNotEmpty()) {
            item {
                ContinuePlayingSection(
                    items = state.continuePlayingItems,
                    tmdbMetadata = state.tmdbMetadata,
                    engine = preferredEngine,
                    onPlayFile = onPlayFileWithDecoder,
                    onRemoveItem = onRemoveFromContinue,
                    onPlayFromStart = onPlayFromStart
                )
            }
        }

        // ──── Dynamic Catalog Sections ────
        state.homeSections.forEach { section ->
            item(key = "section_${section.folderId}") {
                val sectionIcon = if (section.typeLabel == "Series") {
                    Icons.Default.Tv
                } else {
                    Icons.Default.Movie
                }
                TmdbHorizontalSection(
                    title = "${section.folderName} - ${section.typeLabel}",
                    icon = sectionIcon,
                    files = section.items.take(10),
                    totalCount = section.items.size,
                    tmdbMetadata = state.tmdbMetadata,
                    mediaType = section.mediaType,
                    onNavigateToInfo = onNavigateToInfo,
                    onSeeAll = { onNavigateToSeeAll(section.folderId) }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    // Floating Offline Banner above the bottom navigation bar
    if (state.isOffline) {
        OfflineBanner(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyAddedHeroSection(
    files: List<MediaFileEntity>,
    tmdbMetadata: Map<String, TmdbMetadataEntity>,
    posterScrollScale: Float,
    onNavigateToInfo: (String, String) -> Unit
) {
    val heroItems = files.take(8)
    if (heroItems.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { heroItems.size })
    val overlayScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val targetHeight = (configuration.screenHeightDp * 0.62f).dp
    val heroHeight = when {
        targetHeight < 360.dp -> 360.dp
        targetHeight > 500.dp -> 500.dp
        else -> targetHeight
    }

    LaunchedEffect(heroItems.size) {
        if (heroItems.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5_500)
            val nextPage = (pagerState.currentPage + 1) % heroItems.size
            pagerState.animateScrollToPage(
                page = nextPage,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    val currentPage = pagerState.currentPage.coerceIn(0, heroItems.lastIndex)
    val currentFile = heroItems[currentPage]
    val currentMetadata = tmdbMetadata[currentFile.id]
    val currentMediaType = currentMetadata?.mediaType ?: "auto"
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .graphicsLayer { clip = true }
            .clipToBounds()
            .background(backgroundColor)
    ) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) { page ->
            val file = heroItems[page]
            val metadata = tmdbMetadata[file.id]
            val imageModel = heroImageModel(metadata, file)
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            ).absoluteValue.coerceIn(0f, 1f)
            val carouselScale = 0.96f + ((1f - pageOffset) * 0.04f)

            HeroBackdrop(
                file = file,
                metadata = metadata,
                imageModel = imageModel,
                posterScale = posterScrollScale * carouselScale
            )
        }



        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.03f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                            backgroundColor
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Crossfade(
                targetState = currentPage,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                label = "hero_copy",
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val file = heroItems[page]
                val metadata = tmdbMetadata[file.id]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = metadata?.title ?: cleanDisplayTitle(file.name),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = heroMetadataLine(metadata, file),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.82f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onNavigateToInfo(currentFile.id, currentMediaType) },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .height(54.dp)
                    .widthIn(min = 176.dp)
            ) {
                Text(
                    "View Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                heroItems.forEachIndexed { index, _ ->
                    val dotWidth by animateDpAsState(
                        targetValue = if (index == currentPage) 42.dp else 10.dp,
                        label = "hero_dot_width"
                    )
                    val dotAlpha by animateFloatAsState(
                        targetValue = if (index == currentPage) 0.95f else 0.58f,
                        label = "hero_dot_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = dotWidth, height = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = dotAlpha))
                            .clickable {
                                overlayScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBackdrop(
    file: MediaFileEntity,
    metadata: TmdbMetadataEntity?,
    imageModel: String?,
    posterScale: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = true }
            .clipToBounds()
            .background(Color.Black)
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = imageModel,
                contentDescription = metadata?.title ?: file.name,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = posterScale
                        scaleY = posterScale
                    },
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = posterScale
                        scaleY = posterScale
                    }
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                Color(0xFF171717),
                                Color.Black
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.22f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f)
                        )
                    )
                )
        )
    }
}
private fun heroMetadataLine(metadata: TmdbMetadataEntity?, file: MediaFileEntity): String {
    val mediaType = when (metadata?.mediaType) {
        "tv" -> "Series"
        "movie" -> "Movie"
        else -> if (file.isFolder) "Series" else "Video"
    }
    val year = metadata?.year ?: file.createdTime?.take(4) ?: file.modifiedTime?.take(4)
    return listOfNotNull(mediaType, year).joinToString(" • ")
}

private val tmdbImageSizeRegex = Regex("/t/p/(w\\d+|original)/")
private val driveThumbnailSizeRegex = Regex("=s\\d+(-[a-z]+)?")

private fun heroImageModel(metadata: TmdbMetadataEntity?, file: MediaFileEntity): String? {
    return highQualityTmdbImage(metadata?.backdropPath, "w1280")
        ?: highQualityTmdbImage(metadata?.posterPath, "w780")
        ?: highQualityDriveThumbnail(file.thumbnailLink)
}

private fun highQualityTmdbImage(url: String?, size: String): String? {
    if (url.isNullOrBlank()) return null
    val fullUrl = if (url.startsWith("/")) "https://image.tmdb.org/t/p/$size$url" else url
    return if (fullUrl.contains("image.tmdb.org/t/p/")) {
        fullUrl.replace(tmdbImageSizeRegex, "/t/p/$size/")
    } else {
        fullUrl
    }
}

private fun highQualityDriveThumbnail(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return url.replace(driveThumbnailSizeRegex, "=s1280")
}

// ──── Continue Playing Section ────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinuePlayingSection(
    items: List<PlaybackHistoryEntity>,
    tmdbMetadata: Map<String, TmdbMetadataEntity>,
    engine: PlayerEngine,
    onPlayFile: (String, String, PlayerEngine, String?) -> Unit,
    onRemoveItem: (String) -> Unit = {},
    onPlayFromStart: (String, String, PlayerEngine, String?) -> Unit = onPlayFile
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var longPressItem by remember { mutableStateOf<PlaybackHistoryEntity?>(null) }

    Column {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                "Continue Watching",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(82.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Spacer(Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items, key = { it.fileId }) { item ->
                ContinuePlayingCard(
                    item = item,
                    metadata = tmdbMetadata[item.fileId],
                    onClick = {
                        onPlayFile(
                            item.fileId,
                            item.fileName,
                            item.continueEngineOr(engine),
                            item.continueDecoderMode()
                        )
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longPressItem = item
                    }
                )
            }
        }
    }

    // Long-press options dialog
    if (longPressItem != null) {
        val item = longPressItem!!
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { longPressItem = null },
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    item.fileName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                longPressItem = null
                                scope.launch {
                                    onPlayFromStart(
                                        item.fileId,
                                        item.fileName,
                                        item.continueEngineOr(engine),
                                        item.continueDecoderMode()
                                    )
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Replay, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Play from start", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onRemoveItem(item.fileId)
                                Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                                longPressItem = null
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Text("Remove from history", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { longPressItem = null }) { Text("Cancel") }
            }
        )
    }
}

private fun PlaybackHistoryEntity.continueEngineOr(defaultEngine: PlayerEngine): PlayerEngine {
    val storedEngine = lastPlayerEngine?.takeIf { it.isNotBlank() } ?: return defaultEngine
    return runCatching { PlayerEngine.valueOf(storedEngine) }.getOrDefault(defaultEngine)
}

private fun PlaybackHistoryEntity.continueDecoderMode(): String? {
    return lastDecoderMode?.takeIf { it.isNotBlank() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinuePlayingCard(
    item: PlaybackHistoryEntity,
    metadata: TmdbMetadataEntity?,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val labels = remember(item.fileName) { continueMediaLabels(item.fileName) }
    val progress = item.progressPercent.coerceIn(0f, 1f)
    val progressText = "${(progress * 100).toInt().coerceIn(0, 100)}% watched"
    val imageModel = remember(metadata?.posterPath, item.posterPath, item.thumbnailUrl) {
        highQualityTmdbImage(metadata?.posterPath, "w780")
            ?: highQualityTmdbImage(item.posterPath, "w780")
            ?: highQualityDriveThumbnail(item.thumbnailUrl)
    }

    Card(
        modifier = Modifier
            .width(330.dp)
            .height(116.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.48f)),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceColorAtElevation(3.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(98.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                    .background(colorScheme.surfaceVariant.copy(alpha = 0.46f))
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = item.fileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        colorScheme.secondaryContainer,
                                        colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.VideoFile, null,
                            tint = colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    labels.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                labels.episodeLine?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurfaceVariant
                    )
                } ?: Text(
                    "${FileUtils.formatDuration(item.lastPosition)} of ${FileUtils.formatDuration(item.duration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurfaceVariant
                )
                labels.detailLine?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.76f)
                    )
                }
                Spacer(Modifier.weight(1f))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = colorScheme.primary,
                    trackColor = colorScheme.surfaceVariant.copy(alpha = 0.72f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    progressText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Continue label helpers

private data class ContinueMediaLabels(
    val title: String,
    val episodeLine: String?,
    val detailLine: String?
)

private fun continueMediaLabels(fileName: String): ContinueMediaLabels {
    val base = fileName.substringBeforeLast('.', fileName)
    val seasonEpisode = Regex("""(?i)\bS0*(\d{1,2})\s*E0*(\d{1,2})\b[ ._-]*(.*)$""").find(base)
    if (seasonEpisode != null) {
        val title = cleanDisplayTitle(base.substring(0, seasonEpisode.range.first))
        val episode = "S${seasonEpisode.groupValues[1].toInt()}E${seasonEpisode.groupValues[2].toInt()}"
        val episodeTitle = cleanDisplayTitle(seasonEpisode.groupValues[3]).takeIf { it.isNotBlank() }
        return ContinueMediaLabels(
            title = title.ifBlank { cleanDisplayTitle(base) },
            episodeLine = listOfNotNull(episode, episodeTitle).joinToString(" • "),
            detailLine = episodeTitle
        )
    }

    val numericEpisode = Regex("""(?i)\b0*(\d{1,2})x0*(\d{1,2})\b[ ._-]*(.*)$""").find(base)
    if (numericEpisode != null) {
        val title = cleanDisplayTitle(base.substring(0, numericEpisode.range.first))
        val episode = "S${numericEpisode.groupValues[1].toInt()}E${numericEpisode.groupValues[2].toInt()}"
        val episodeTitle = cleanDisplayTitle(numericEpisode.groupValues[3]).takeIf { it.isNotBlank() }
        return ContinueMediaLabels(
            title = title.ifBlank { cleanDisplayTitle(base) },
            episodeLine = listOfNotNull(episode, episodeTitle).joinToString(" • "),
            detailLine = episodeTitle
        )
    }

    return ContinueMediaLabels(
        title = cleanDisplayTitle(base),
        episodeLine = null,
        detailLine = null
    )
}

private fun cleanDisplayTitle(value: String): String {
    return value
        .replace(Regex("""[\[\(].*?[\]\)]"""), " ")
        .replace(Regex("""[._-]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

// ──── Last Played Section ────

@Composable
private fun LastPlayedSection(
    items: List<PlaybackHistoryEntity>,
    engine: PlayerEngine,
    onPlayFile: (String, String, PlayerEngine) -> Unit,
    onClearHistory: () -> Unit = {}
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.History, null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Last Played",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showClearConfirm = true }) {
                Icon(
                    Icons.Default.DeleteSweep, "Clear All",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { "lp_${it.fileId}" }) { item ->
                LastPlayedCard(
                    item = item,
                    onClick = { onPlayFile(item.fileId, item.fileName, engine) }
                )
            }
        }
    }

    // Clear history confirmation dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear Watch History") },
            text = { Text("This will remove all playback history and continue playing items. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearConfirm = false
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LastPlayedCard(
    item: PlaybackHistoryEntity,
    onClick: () -> Unit
) {
    val timeAgo = remember(item.lastPlayedAt) {
        val diff = System.currentTimeMillis() - item.lastPlayedAt
        when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            diff < 604_800_000 -> "${diff / 86_400_000}d ago"
            else -> "${diff / 604_800_000}w ago"
        }
    }

    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VideoFile, null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    timeAgo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.fileName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            // Show progress if partially watched
            if (item.progressPercent > 0.01f && !item.isCompleted) {
                LinearProgressIndicator(
                    progress = { item.progressPercent.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )
            } else if (item.isCompleted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ──── TMDB Horizontal Section ────

@Composable
fun TmdbHorizontalSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    files: List<MediaFileEntity>,
    totalCount: Int,
    tmdbMetadata: Map<String, TmdbMetadataEntity>,
    mediaType: String = "auto",
    onNavigateToInfo: (String, String) -> Unit,
    onSeeAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            if (totalCount > 10) {
                TextButton(onClick = onSeeAll) {
                    Text("See All ($totalCount)")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp))
                }
            } else {
                Text(
                    "$totalCount items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(files, key = { it.id }) { file ->
                TmdbPosterCard(
                    file = file,
                    metadata = tmdbMetadata[file.id],
                    onClick = { onNavigateToInfo(file.id, mediaType) },
                    modifier = Modifier.width(130.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ──── TMDB Poster Card (compact poster with name, year, tag) ────

@Composable
fun TmdbPosterCard(
    file: MediaFileEntity,
    metadata: TmdbMetadataEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // Poster area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (metadata?.posterPath != null) {
                    AsyncImage(
                        model = metadata.posterPath,
                        contentDescription = metadata.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (file.thumbnailLink != null) {
                    AsyncImage(
                        model = file.thumbnailLink,
                        contentDescription = file.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Movie/Series tag badge
                metadata?.let { meta ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (meta.mediaType == "tv") Color(0xFF2196F3).copy(alpha = 0.9f)
                                else Color(0xFFE91E63).copy(alpha = 0.9f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (meta.mediaType == "tv") "TV" else "Movie",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Rating badge
                metadata?.rating?.let { rating ->
                    if (rating > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        rating >= 7f -> Color(0xFF4CAF50)
                                        rating >= 5f -> Color(0xFFFF9800)
                                        else -> Color(0xFFF44336)
                                    }.copy(alpha = 0.9f)
                                )
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "%.1f".format(rating),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Title + Year
            Column(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .height(48.dp)
            ) {
                Text(
                    text = metadata?.title ?: file.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                metadata?.year?.let { year ->
                    Text(
                        text = year,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ──── Empty states ────

@Composable
private fun TmdbSetupPrompt(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                Text("Set Up Your Catalog", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Add your TMDB API key and map folders to Movies or Series to see beautiful metadata here.\n\nOr start watching videos — they'll appear in Continue Playing!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Button(
                    onClick = onNavigateToSettings,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Settings")
                }
            }
        }
    }
}

@Composable
private fun NoHomeContent(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("No Folders Mapped", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Go to Settings → TMDB Metadata and add folders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = onNavigateToSettings,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Open Settings")
            }
        }
    }
}

// ──── Connectivity States ────

@Composable
fun NoConnectivityMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            )
            Text(
                "No Connectivity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "No Connectivity — showing cached content",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ──── Shimmer / Skeleton ────

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmer"
    )
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(translateAnim - 500f, translateAnim - 500f),
        end = Offset(translateAnim, translateAnim)
    )
}

@Composable
private fun ShimmerBox(modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(shimmerBrush()))
}

@Composable
fun HomeSkeletonLoading(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val heroTargetHeight = (configuration.screenHeightDp * 0.62f).dp
    val heroHeight = when {
        heroTargetHeight < 360.dp -> 360.dp
        heroTargetHeight > 500.dp -> 500.dp
        else -> heroTargetHeight
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hero section skeleton
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
            ) {
                ShimmerBox(
                    Modifier.fillMaxSize()
                )
                // Bottom content overlay skeleton
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 28.dp)
                        .padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShimmerBox(Modifier.width(220.dp).height(28.dp))
                    Spacer(Modifier.height(10.dp))
                    ShimmerBox(Modifier.width(120.dp).height(16.dp))
                    Spacer(Modifier.height(20.dp))
                    ShimmerBox(
                        Modifier
                            .width(176.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(28.dp))
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(4) {
                            ShimmerBox(
                                Modifier
                                    .size(width = 10.dp, height = 10.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }
        }
        // Continue Playing skeleton
        item {
            Column {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBox(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    ShimmerBox(Modifier.width(140.dp).height(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(10) {
                        Card(
                            modifier = Modifier.width(330.dp).height(116.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                ShimmerBox(Modifier.width(98.dp).fillMaxHeight())
                                Column(modifier = Modifier.fillMaxHeight().weight(1f).padding(12.dp)) {
                                    ShimmerBox(Modifier.fillMaxWidth(0.8f).height(18.dp))
                                    Spacer(Modifier.height(8.dp))
                                    ShimmerBox(Modifier.fillMaxWidth(0.5f).height(14.dp))
                                    Spacer(Modifier.weight(1f))
                                    ShimmerBox(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)))
                                    Spacer(Modifier.height(6.dp))
                                    ShimmerBox(Modifier.width(60.dp).height(10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        // Category skeleton x2
        items(2) {
            Column {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBox(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    ShimmerBox(Modifier.width(100.dp).height(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(10) {
                        Card(
                            modifier = Modifier.width(130.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column {
                                ShimmerBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                                Column(modifier = Modifier.padding(8.dp)) {
                                    ShimmerBox(Modifier.fillMaxWidth().height(14.dp))
                                    Spacer(Modifier.height(4.dp))
                                    ShimmerBox(Modifier.fillMaxWidth(0.5f).height(10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
