package com.photoselectortoolbox.ui.theme

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
    primary = FocusCyan,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Zinc50,
    secondary = Zinc600,
    onSecondary = Zinc50,
    secondaryContainer = StudioCard,
    onSecondaryContainer = Zinc200,
    tertiary = FocusCyanGlow,
    onTertiary = StudioCanvas,
    tertiaryContainer = Color(0xFF075985),
    onTertiaryContainer = Zinc100,
    surface = StudioCanvas,
    onSurface = Zinc50,
    surfaceVariant = StudioPanel,
    onSurfaceVariant = Zinc400,
    surfaceContainerLowest = StudioCanvas,
    surfaceContainerLow = StudioPanel,
    surfaceContainer = StudioCard,
    surfaceContainerHigh = Zinc700,
    surfaceContainerHighest = Zinc600,
    outline = StudioBorder,
    outlineVariant = Zinc600,
    background = StudioCanvas,
    onBackground = Zinc50,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Zinc100,
    inverseOnSurface = StudioCanvas,
    inversePrimary = FocusCyan,
    scrim = Color.Black,
)

@Composable
fun PhotoSelectorToolboxTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = StudioCanvas.toArgb()
            window.navigationBarColor = StudioCanvas.toArgb()
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
