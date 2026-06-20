package com.phototok.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Indigo500,
    onPrimary = Color.White,
    primaryContainer = Indigo600,
    onPrimaryContainer = Zinc50,
    secondary = Zinc600,
    onSecondary = Zinc50,
    secondaryContainer = Zinc700,
    onSecondaryContainer = Zinc200,
    tertiary = Indigo400,
    onTertiary = Color.White,
    tertiaryContainer = Indigo700,
    onTertiaryContainer = Zinc100,
    surface = Zinc900,
    onSurface = Zinc50,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc400,
    surfaceContainerLowest = Zinc950,
    surfaceContainerLow = Zinc900,
    surfaceContainer = Zinc800,
    surfaceContainerHigh = Zinc700,
    surfaceContainerHighest = Zinc600,
    outline = Zinc700,
    outlineVariant = Zinc600,
    background = Zinc900,
    onBackground = Zinc50,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Zinc100,
    inverseOnSurface = Zinc900,
    inversePrimary = Indigo700,
    scrim = Color.Black,
)

@Composable
fun PhotoTokTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Zinc950.toArgb()
            window.navigationBarColor = Zinc950.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}
