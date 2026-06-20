package com.mkbhdana.streamhive.tv.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mkbhdana.streamhive.tv.theme.TvWhite
import kotlinx.coroutines.delay

private val CardHeight: Dp = 158.dp
private val PortraitWidth: Dp = 106.dp   // 2:3 of CardHeight
private val LandscapeWidth: Dp = 282.dp  // ~16:9 of CardHeight

// Compact "Continue Watching" card (NuvioTV-sized, smaller than the catalog cards).
private val ContinueWidth: Dp = 208.dp
private val ContinueHeight: Dp = 117.dp  // ~16:9

/**
 * NuvioTV-style catalog card: a compact portrait poster that, after a short
 * focus delay, expands to a landscape still. The hero banner reflects the
 * focused item, so no title is drawn under the card.
 */
@Composable
fun TvExpandingCard(
    title: String,
    posterUrl: String?,
    backdropUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    expandDelayMs: Long = 700,
    titleOverlay: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(focused) {
        if (focused) {
            delay(expandDelayMs)
            expanded = true
        } else {
            expanded = false
        }
    }

    val width by animateDpAsState(
        targetValue = if (expanded) LandscapeWidth else PortraitWidth,
        animationSpec = tween(220),
        label = "cardWidth"
    )
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .width(width)
            .height(CardHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .then(if (focused) Modifier.border(3.dp, TvWhite, shape) else Modifier)
    ) {
        Crossfade(targetState = expanded, animationSpec = tween(220), label = "cardImage") { exp ->
            val model = if (exp) (backdropUrl ?: posterUrl) else (posterUrl ?: backdropUrl)
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Title overlay on the expanded (landscape) card.
        if (titleOverlay && expanded) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))
                )
            )
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            )
        }
    }
}

/**
 * Landscape "Continue Watching" card. On focus it scales up, draws a white
 * border, and overlays the title bottom-left (NuvioTV style). A remaining-time
 * badge sits top-right and a progress bar runs along the bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvContinueCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    progress: Float,
    timeLeftLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    var centerDownAt by remember { mutableStateOf(0L) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "continueScale")
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .width(ContinueWidth)
            .scale(scale)
            .height(ContinueHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            // Detect long-press on RELEASE and consume that key so it doesn't also
            // "click" the first item of the dialog that just opened.
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.Menu -> if (event.type == KeyEventType.KeyDown) { onLongClick(); true } else false
                    Key.DirectionCenter, Key.Enter -> when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (centerDownAt == 0L) centerDownAt = System.currentTimeMillis()
                            false
                        }
                        KeyEventType.KeyUp -> {
                            val held = System.currentTimeMillis() - centerDownAt
                            centerDownAt = 0L
                            if (held > 450L) { onLongClick(); true } else false
                        }
                        else -> false
                    }
                    else -> false
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .then(if (focused) Modifier.border(3.dp, TvWhite, shape) else Modifier)
    ) {
        if (imageUrl != null) {
            AsyncImage(imageUrl, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.VideoFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Bottom scrim for legible title overlay.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f))
            )
        )
        if (timeLeftLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(timeLeftLabel, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
            color = TvWhite,
            trackColor = Color.Black.copy(alpha = 0.5f)
        )
    }
}

/** Simple portrait poster (search results / static grids). */
@Composable
fun TvPosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 130.dp,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .then(if (focused) Modifier.border(3.dp, TvWhite, shape) else Modifier)
    ) {
        if (posterUrl != null) {
            AsyncImage(posterUrl, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(Icons.Default.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Landscape video card for the library. */
@Composable
fun TvWideCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = LandscapeWidth,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CardHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .then(if (focused) Modifier.border(3.dp, TvWhite, shape) else Modifier)
        ) {
            if (imageUrl != null) {
                AsyncImage(imageUrl, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.Default.VideoFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (focused) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, "Play", tint = TvWhite, modifier = Modifier.size(40.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) TvWhite else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Folder card for the library browse view. */
@Composable
fun TvFolderCard(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .then(if (focused) Modifier.border(3.dp, TvWhite, shape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Folder,
                null,
                tint = if (focused) TvWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) TvWhite else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
