package com.mkbhdana.streamhive.tv.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mkbhdana.streamhive.ui.theme.AppTypography

/**
 * Theme wrapper for the Android TV surface: a premium black/grey scheme with
 * white highlights ([TvColorScheme]). Independent of the mobile theme, so the
 * mobile look is unaffected.
 */
@Composable
fun TvStreamHiveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        typography = AppTypography,
        content = content
    )
}

/** 10-foot UI constants. */
object TvDimens {
    /** Overscan-safe margin used at the edges of full-screen TV content. */
    val Overscan: Dp = 40.dp
    /** Collapsed width of the navigation drawer (icons only). */
    val DrawerCollapsed: Dp = 60.dp
    /** Expanded width of the navigation drawer (icons + labels). */
    val DrawerExpanded: Dp = 184.dp
}
