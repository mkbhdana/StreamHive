package com.mkbhdana.streamhive.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Fallback dark scheme — used on devices below Android 12 (API 31)
 * that don't support Material You dynamic colors.
 */
private val FallbackDarkColorScheme = darkColorScheme(
    primary = Purple60,
    onPrimary = TextOnPrimary,
    primaryContainer = PurpleDark,
    onPrimaryContainer = Purple80,
    secondary = Blue60,
    onSecondary = TextOnPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Blue80,
    tertiary = AccentCyan,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TextTertiary,
    error = AccentRed,
    onError = TextOnPrimary
)

private val FallbackLightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = TextOnPrimary,
    primaryContainer = Purple80,
    onPrimaryContainer = PurpleDark,
    secondary = Blue40,
    onSecondary = TextOnPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    error = AccentRed,
    onError = TextOnPrimary
)

@Composable
fun StreamHiveTheme(
    isTv: Boolean = false,
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        // Material You dynamic colors (Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }

    val typography = if (isTv) TvTypography else AppTypography

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
