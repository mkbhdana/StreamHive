package com.mkbhdana.streamhive.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.catalog.CatalogViewModel
import com.mkbhdana.streamhive.data.db.PlaybackHistoryEntity
import com.mkbhdana.streamhive.player.mpv.PlayerEngine
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TMDB-style home. The hero (backdrop layer + text) is **fixed**; the rows live
 * in a separate clipped [LazyColumn]. Focusing a row scrolls it to the top
 * (`scrollToItem`), so rows above leave the view and the hero never moves — it
 * only crossfades to the focused item. (Approach from the user's reference.)
 */
@Composable
fun TvHomeScreen(
    viewModel: CatalogViewModel,
    onPlay: (fileId: String, fileName: String, engine: PlayerEngine, decoder: String?) -> Unit,
    onOpenInfo: (driveFileId: String, mediaType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val preferredEngine = viewModel.getPreferredEngine()

    var hero by remember { mutableStateOf<TvHeroState?>(null) }
    val firstCardFocus = remember { FocusRequester() }
    val refreshFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    LaunchedEffect(state.homeRecentlyAdded, state.homeSections, state.continuePlayingItems) {
        if (hero == null) {
            val seed = state.homeRecentlyAdded.firstOrNull()
                ?: state.homeSections.firstOrNull()?.items?.firstOrNull()
            if (seed != null) hero = heroStateFor(seed, state.tmdbMetadata[seed.id])
        }
    }

    val hasContent = state.continuePlayingItems.isNotEmpty() ||
        state.homeRecentlyAdded.isNotEmpty() ||
        state.homeSections.isNotEmpty()
    val showSkeleton = state.isHomeRefreshing || state.isHomeLoading || (state.isLoading && !hasContent)

    // While the skeleton shows, keep focus anchored on the always-present refresh
    // icon so the nav drawer can't grab focus and auto-expand. The moment the
    // skeleton finishes, hand focus to the first card of the first row (Continue
    // Watching when present), retrying until that lazily-composed card exists.
    LaunchedEffect(showSkeleton) {
        if (showSkeleton) {
            runCatching { refreshFocus.requestFocus() }
        } else if (hasContent) {
            runCatching { refreshFocus.requestFocus() }
            repeat(40) {
                delay(24)
                if (runCatching { firstCardFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }
    }

    fun bringRowToTop(index: Int) {
        // Pin the focused row to the top on every focus. This must fire on horizontal
        // moves too: it counteracts the LazyColumn's own focus bring-into-view, which
        // otherwise drifts and makes the row above peek in/out as you move card to card.
        scope.launch {
            delay(32)
            runCatching { listState.scrollToItem(index, 0) }
        }
    }

    val continueFirst = state.continuePlayingItems.isNotEmpty()
    val recentFirst = !continueFirst && state.homeRecentlyAdded.isNotEmpty()

    val onContinueFromStart: (PlaybackHistoryEntity) -> Unit = { item ->
        scope.launch {
            runCatching { viewModel.removeFromHistorySync(item.fileId) }
            playContinue(item, preferredEngine, onPlay)
        }
    }
    val onContinueRemove: (String) -> Unit = { id -> viewModel.removeFromHistory(id) }

    Box(modifier = modifier.fillMaxSize().background(TvBackgroundColor)) {
      when {
        showSkeleton -> TvHomeSkeleton(Modifier.fillMaxSize())
        !hasContent -> TvEmptyHomeMessage(Modifier.fillMaxSize())
        else -> {
        // Fixed backdrop layer (right ~72%, top ~55%).
        TvHeroBackdropLayer(
            backdrop = hero?.backdrop,
            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.72f).fillMaxHeight(0.55f)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed hero text (top 40%, text sits at the bottom of it).
            Box(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.40f)
            ) {
                hero?.let {
                    TvHeroTextBlock(
                        state = it,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, end = 48.dp, bottom = 10.dp).fillMaxWidth(0.5f)
                    )
                }
            }

            // Scrollable, clipped rows.
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                contentPadding = PaddingValues(top = 4.dp, bottom = screenHeight),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var idx = 0

                if (continueFirst) {
                    val i = idx++
                    item("continue") {
                        TvContinueRow(
                            items = state.continuePlayingItems,
                            tmdbMetadata = state.tmdbMetadata,
                            onPlay = { item -> playContinue(item, preferredEngine, onPlay) },
                            onFocusItem = { hero = it; bringRowToTop(i) },
                            firstItemFocusRequester = firstCardFocus,
                            onPlayFromStart = onContinueFromStart,
                            onRemove = onContinueRemove
                        )
                    }
                }

                if (state.homeRecentlyAdded.isNotEmpty()) {
                    val i = idx++
                    item("recent") {
                        TvPosterRow(
                            title = "Recently Added",
                            files = state.homeRecentlyAdded,
                            tmdbMetadata = state.tmdbMetadata,
                            mediaType = "auto",
                            onOpenInfo = onOpenInfo,
                            onFocusItem = { hero = it; bringRowToTop(i) },
                            firstItemFocusRequester = if (recentFirst) firstCardFocus else null
                        )
                    }
                }

                if (!continueFirst) {
                    val i = idx++
                    item("continue2") {
                        TvContinueRow(
                            items = state.continuePlayingItems,
                            tmdbMetadata = state.tmdbMetadata,
                            onPlay = { item -> playContinue(item, preferredEngine, onPlay) },
                            onFocusItem = { hero = it; bringRowToTop(i) },
                            onPlayFromStart = onContinueFromStart,
                            onRemove = onContinueRemove
                        )
                    }
                }

                state.homeSections.forEach { section ->
                    val i = idx++
                    item(section.folderId) {
                        TvPosterRow(
                            title = "${section.folderName} · ${section.typeLabel}",
                            files = section.items,
                            tmdbMetadata = state.tmdbMetadata,
                            mediaType = section.mediaType,
                            onOpenInfo = onOpenInfo,
                            onFocusItem = { hero = it; bringRowToTop(i) }
                        )
                    }
                }
            }
        }
        }
      }

        // Always-present refresh affordance (top-right). Doubles as a stable focus
        // anchor so the nav drawer never auto-focuses while content loads.
        TvHomeRefreshButton(
            focusRequester = refreshFocus,
            onClick = { viewModel.refreshHomeContent(false) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 28.dp)
        )
    }
}

@Composable
private fun TvHomeRefreshButton(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Black.copy(alpha = 0.45f))
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Refresh, "Refresh home", tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(22.dp))
    }
}

private fun playContinue(
    item: PlaybackHistoryEntity,
    default: PlayerEngine,
    onPlay: (String, String, PlayerEngine, String?) -> Unit
) {
    val engine = item.lastPlayerEngine
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { PlayerEngine.valueOf(it) }.getOrNull() }
        ?: default
    onPlay(item.fileId, item.fileName, engine, item.lastDecoderMode?.takeIf { it.isNotBlank() })
}
