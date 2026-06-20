package com.mkbhdana.streamhive.tv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.mkbhdana.streamhive.data.db.MediaFileEntity
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.data.db.TmdbMetadataEntity
import com.mkbhdana.streamhive.tv.components.TvContinueCard
import com.mkbhdana.streamhive.tv.components.TvExpandingCard
import com.mkbhdana.streamhive.tv.components.backdropModel
import com.mkbhdana.streamhive.tv.components.driveThumbnailUrl
import com.mkbhdana.streamhive.tv.components.posterModel
import com.mkbhdana.streamhive.tv.components.tmdbImageUrl
import com.mkbhdana.streamhive.tv.theme.TmdbAccentColor
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor
import com.mkbhdana.streamhive.tv.theme.TvTextSecondaryColor

/** Snapshot of what the hero banner should display for the focused item. */
data class TvHeroState(
    val title: String,
    val line: String?,
    val rating: String?,
    val overview: String?,
    val backdrop: String?
)

fun heroStateFor(file: MediaFileEntity?, metadata: TmdbMetadataEntity?): TvHeroState {
    val title = metadata?.title ?: cleanTitle(file?.name ?: "")
    val typeLabel = when (metadata?.mediaType) {
        "tv" -> "Series"
        "movie" -> "Movie"
        else -> if (file?.isFolder == true) "Series" else "Video"
    }
    val year = metadata?.year ?: file?.createdTime?.take(4) ?: file?.modifiedTime?.take(4)
    val line = listOfNotNull(typeLabel, year).joinToString("  •  ")
    val rating = metadata?.rating?.takeIf { it > 0f }?.let { "%.1f".format(it) }
    return TvHeroState(title, line, rating, metadata?.overview, backdropModel(metadata, file))
}

internal fun cleanTitle(value: String): String = value
    .substringBeforeLast('.')
    .replace(Regex("""[\[(].*?[\])]"""), " ")
    .replace(Regex("""[._-]+"""), " ")
    .replace(Regex("""\s+"""), " ")
    .trim()

/**
 * Premium hero banner: a backdrop anchored to the right that fades into the
 * black background on the left, with the focused item's details bottom-left.
 */
/**
 * Fixed hero backdrop layer: a crossfading image anchored to the right that
 * blends into the background on the left, top and bottom. Drawn behind the
 * (also fixed) text block and the top of the scrolling rows.
 */
@Composable
fun TvHeroBackdropLayer(backdrop: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clipToBounds()) {
        Crossfade(targetState = backdrop, animationSpec = tween(450), label = "heroBackdrop") { model ->
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopEnd
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0.0f to TvBackgroundColor,
                    0.22f to TvBackgroundColor.copy(alpha = 0.86f),
                    0.46f to TvBackgroundColor.copy(alpha = 0.5f),
                    0.76f to TvBackgroundColor.copy(alpha = 0.14f),
                    1.0f to Color.Transparent
                )
            )
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    0.72f to TvBackgroundColor.copy(alpha = 0.55f),
                    0.9f to TvBackgroundColor.copy(alpha = 0.9f),
                    1.0f to TvBackgroundColor
                )
            )
        )
    }
}

/** Fixed hero text block (title, meta, IMDb chip, overview). */
@Composable
fun TvHeroTextBlock(state: TvHeroState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        state.line?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f))
        }
        state.rating?.let {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "TMDB rating",
                    tint = TmdbAccentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(it, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        state.overview?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TvSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = RowLeadInset, top = 14.dp, bottom = 8.dp)
    )
}

/** Shared start inset for row titles AND cards so the focused card lines up under the title. */
val RowLeadInset = 56.dp

/**
 * Pins the focused card to a fixed [leadDp] position from the row start — so it sits
 * directly under the row title — and glides the rest of the row past it in a single
 * eased scroll (NuvioTV-style). Landing the card at the inset (instead of flush at
 * x=0) leaves room for the previous card to peek on the left.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberStartAlignedBringIntoView(leadDp: Dp = RowLeadInset): BringIntoViewSpec {
    val leadPx = with(LocalDensity.current) { leadDp.toPx() }
    return remember(leadPx) {
        object : BringIntoViewSpec {
            // A critically-damped spring carries velocity across rapid d-pad presses
            // (a tween restarts each press, which reads as jitter).
            override val scrollAnimationSpec: AnimationSpec<Float> =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                offset - leadPx
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvPosterRow(
    title: String,
    files: List<MediaFileEntity>,
    tmdbMetadata: Map<String, TmdbMetadataEntity>,
    mediaType: String,
    onOpenInfo: (String, String) -> Unit,
    onFocusItem: (TvHeroState) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    if (files.isEmpty()) return
    val ownReq = remember { FocusRequester() }
    val firstReq = firstItemFocusRequester ?: ownReq
    // Trailing space so the last cards can still scroll fully to the lead slot.
    val endPad = LocalConfiguration.current.screenWidthDp.dp
    val bringIntoView = rememberStartAlignedBringIntoView()
    Column {
        TvSectionLabel(title)
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
            LazyRow(
                // Entering the row (vertical nav) always lands on the first card.
                modifier = Modifier.focusGroup().focusProperties { enter = { firstReq } },
                contentPadding = PaddingValues(start = RowLeadInset, end = endPad),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(files, key = { _, it -> it.id }) { index, file ->
                    val metadata = tmdbMetadata[file.id]
                    TvExpandingCard(
                        title = metadata?.title ?: cleanTitle(file.name),
                        posterUrl = posterModel(metadata, file),
                        backdropUrl = backdropModel(metadata, file),
                        onClick = { onOpenInfo(file.id, metadata?.mediaType ?: mediaType) },
                        onFocused = { onFocusItem(heroStateFor(file, metadata)) },
                        focusRequester = if (index == 0) firstReq else null,
                        expandDelayMs = 5000
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TvContinueRow(
    items: List<PlaybackHistoryEntity>,
    tmdbMetadata: Map<String, TmdbMetadataEntity>,
    onPlay: (PlaybackHistoryEntity) -> Unit,
    onFocusItem: (TvHeroState) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    onPlayFromStart: (PlaybackHistoryEntity) -> Unit = {},
    onRemove: (String) -> Unit = {}
) {
    if (items.isEmpty()) return
    var longPressItem by remember { mutableStateOf<PlaybackHistoryEntity?>(null) }
    val ownReq = remember { FocusRequester() }
    val firstReq = firstItemFocusRequester ?: ownReq
    val endPad = LocalConfiguration.current.screenWidthDp.dp
    val bringIntoView = rememberStartAlignedBringIntoView()

    Column {
        TvSectionLabel("Continue Watching")
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoView) {
        LazyRow(
            // Entering the row (vertical nav) always lands on the first card.
            modifier = Modifier.focusGroup().focusProperties { enter = { firstReq } },
            contentPadding = PaddingValues(start = RowLeadInset, end = endPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items, key = { _, it -> it.fileId }) { index, item ->
                val metadata = tmdbMetadata[item.fileId]
                val imageUrl = backdropModel(metadata, null)
                    ?: tmdbImageUrl(item.posterPath, "w780")
                    ?: driveThumbnailUrl(item.thumbnailUrl, 780)
                TvContinueCard(
                    title = metadata?.title ?: cleanTitle(item.fileName),
                    subtitle = "${(item.progressPercent * 100).toInt().coerceIn(0, 100)}% watched",
                    imageUrl = imageUrl,
                    progress = item.progressPercent,
                    timeLeftLabel = remainingLabel(item),
                    onClick = { onPlay(item) },
                    onLongClick = { longPressItem = item },
                    onFocused = {
                        onFocusItem(
                            TvHeroState(
                                title = metadata?.title ?: cleanTitle(item.fileName),
                                line = "Continue Watching",
                                rating = metadata?.rating?.takeIf { it > 0f }?.let { "%.1f".format(it) },
                                overview = metadata?.overview,
                                backdrop = imageUrl
                            )
                        )
                    },
                    focusRequester = if (index == 0) firstReq else null
                )
            }
        }
        }
    }

    longPressItem?.let { item ->
        val metadata = tmdbMetadata[item.fileId]
        TvContinueOptionsDialog(
            title = metadata?.title ?: cleanTitle(item.fileName),
            onPlayFromStart = { longPressItem = null; onPlayFromStart(item) },
            onRemove = { longPressItem = null; onRemove(item.fileId) },
            onDismiss = { longPressItem = null }
        )
    }
}

@Composable
private fun TvContinueOptionsDialog(
    title: String,
    onPlayFromStart: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = TvSurfaceColor) {
            Column(modifier = Modifier.width(380.dp).padding(20.dp)) {
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
                TvContinueActionRow(Icons.Default.Replay, "Play from start", firstFocus, onPlayFromStart)
                Spacer(Modifier.height(6.dp))
                TvContinueActionRow(Icons.Default.Delete, "Remove from history", null, onRemove)
            }
        }
    }
}

@Composable
private fun TvContinueActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.06f))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Color.Black else Color.White)
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (focused) Color.Black else Color.White, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun remainingLabel(item: PlaybackHistoryEntity): String? {
    val remaining = item.duration - item.lastPosition
    if (item.duration <= 0L || remaining <= 0L) return null
    val totalMin = (remaining / 60_000L).toInt()
    return when {
        totalMin <= 0 -> "<1m Left"
        totalMin < 60 -> "${totalMin}m Left"
        else -> "${totalMin / 60}h ${totalMin % 60}m Left"
    }
}

@Composable
fun TvEmptyHomeMessage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Add a TMDB key and catalog folders in Settings, or start playing a video.",
            style = MaterialTheme.typography.titleMedium,
            color = TvTextSecondaryColor
        )
    }
}

/** Shimmer skeleton shown while the home content loads (like the mobile home). */
@Composable
fun TvHomeSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "tvHomeShimmer")
    val translate by transition.animateFloat(
        initialValue = -700f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerTranslate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.05f)),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 0f)
    )

    Column(modifier = modifier.fillMaxSize().background(TvBackgroundColor)) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.45f), contentAlignment = Alignment.BottomStart) {
            Column(modifier = Modifier.padding(start = 40.dp, bottom = 18.dp)) {
                ShimmerBox(Modifier.width(360.dp).height(34.dp), brush)
                Spacer(Modifier.height(12.dp))
                ShimmerBox(Modifier.width(180.dp).height(16.dp), brush)
                Spacer(Modifier.height(10.dp))
                ShimmerBox(Modifier.width(480.dp).height(14.dp), brush)
                Spacer(Modifier.height(6.dp))
                ShimmerBox(Modifier.width(420.dp).height(14.dp), brush)
            }
        }
        repeat(2) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                ShimmerBox(Modifier.padding(start = 40.dp).width(200.dp).height(22.dp), brush)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(start = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(7) { ShimmerBox(Modifier.width(106.dp).height(158.dp), brush) }
                }
            }
        }
    }
}

@Composable
private fun ShimmerBox(modifier: Modifier, brush: Brush) {
    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(brush))
}
