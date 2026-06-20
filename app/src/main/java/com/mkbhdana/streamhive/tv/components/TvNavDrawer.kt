package com.mkbhdana.streamhive.tv.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mkbhdana.streamhive.R
import com.mkbhdana.streamhive.tv.theme.TvBackgroundColor
import com.mkbhdana.streamhive.tv.theme.TvCardColor
import com.mkbhdana.streamhive.tv.theme.TvDimens
import com.mkbhdana.streamhive.tv.theme.TvSurfaceColor

enum class TvDestination(val icon: ImageVector, val label: String) {
    Home(Icons.Default.Home, "Home"),
    Search(Icons.Default.Search, "Search"),
    Library(Icons.Default.VideoLibrary, "Library"),
    Settings(Icons.Default.Settings, "Settings")
}

/**
 * NuvioTV-style left drawer: a slim icon rail that expands to show a profile
 * header + labels when focus enters it, overlaying the page content.
 *
 * Pressing LEFT from the leftmost content element returns focus here (via
 * [focusProperties] on the content group pointing at the drawer's restorer),
 * which fixes the "can't get back to the left panel" problem.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvNavDrawer(
    selected: TvDestination,
    onSelect: (TvDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        if (expanded) TvDimens.DrawerExpanded else TvDimens.DrawerCollapsed,
        label = "drawerWidth"
    )
    // Labels fade with their own animation (no size change), so the icons never
    // reflow/jiggle when the drawer opens or closes.
    val labelAlpha by animateFloatAsState(if (expanded) 1f else 0f, label = "drawerLabel")
    val drawerRequester = remember { FocusRequester() }

    Box(modifier = modifier.fillMaxSize()) {
        content(
            Modifier
                .padding(start = TvDimens.DrawerCollapsed)
                .focusGroup()
                .focusProperties { left = drawerRequester }
        )

        Column(
            modifier = Modifier
                .zIndex(1f)
                .fillMaxHeight()
                .width(width)
                .clipToBounds()
                .background(if (expanded) TvSurfaceColor else TvBackgroundColor.copy(alpha = 0.92f))
                .onFocusChanged { expanded = it.hasFocus }
                .focusRequester(drawerRequester)
                .focusRestorer()
                .focusGroup()
                .padding(vertical = TvDimens.Overscan, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // App header — logo fixed, name fades in.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 4.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_streamhive_logo),
                    contentDescription = "StreamHive",
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    "StreamHive",
                    modifier = Modifier.padding(start = 12.dp).alpha(labelAlpha),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            TvDestination.entries.forEach { dest ->
                TvDrawerItem(
                    destination = dest,
                    selected = dest == selected,
                    labelAlpha = labelAlpha,
                    onClick = { onSelect(dest) }
                )
            }
        }
    }
}

@Composable
private fun TvDrawerItem(
    destination: TvDestination,
    selected: Boolean,
    labelAlpha: Float,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val container = when {
        focused -> Color.White
        selected -> Color.White.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val contentColor = when {
        focused -> Color.Black
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.7f)
    }

    // The row always fills the (animating) drawer width so its width stays in sync
    // with the container; the icon sits at a fixed left offset and the label fades,
    // so nothing jumps when the drawer expands or collapses.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(9.dp))
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            tint = contentColor,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = destination.label,
            modifier = Modifier.alpha(labelAlpha),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}
