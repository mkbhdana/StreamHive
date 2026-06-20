package com.mkbhdana.streamhive.tv.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Premium monochrome palette for the TV UI: deep black/grey surfaces with white
 * used for highlights, focus, and selection. No coloured accents.
 */

val TvBlack = Color(0xFF080809)
val TvBackgroundColor = Color(0xFF0B0B0D)
val TvSurfaceColor = Color(0xFF141416)
val TvCardColor = Color(0xFF1B1B1F)
val TvCardElevatedColor = Color(0xFF26262B)
val TvOutlineColor = Color(0xFF2C2C32)

val TvWhite = Color(0xFFFFFFFF)
val TvTextPrimaryColor = Color(0xFFF3F3F5)
val TvTextSecondaryColor = Color(0xFF9C9CA4)
val TvTextTertiaryColor = Color(0xFF66666E)

/** TMDB brand accent (the teal/blue from the TMDB logo) used for the rating star. */
val TmdbAccentColor = Color(0xFF01B4E4)

/** Material color scheme that makes `primary` (highlight/focus) pure white. */
val TvColorScheme = darkColorScheme(
    primary = TvWhite,
    onPrimary = TvBlack,
    primaryContainer = Color(0xFFE7E7EA),
    onPrimaryContainer = TvBlack,
    secondary = Color(0xFFCFCFD6),
    onSecondary = TvBlack,
    tertiary = Color(0xFFE0E0E6),
    onTertiary = TvBlack,
    background = TvBackgroundColor,
    onBackground = TvTextPrimaryColor,
    surface = TvSurfaceColor,
    onSurface = TvTextPrimaryColor,
    surfaceVariant = TvCardColor,
    onSurfaceVariant = TvTextSecondaryColor,
    surfaceContainerHigh = TvCardElevatedColor,
    surfaceContainerHighest = TvCardElevatedColor,
    outline = TvOutlineColor,
    outlineVariant = TvOutlineColor,
    error = Color(0xFFFF6B6B),
    onError = TvBlack
)
