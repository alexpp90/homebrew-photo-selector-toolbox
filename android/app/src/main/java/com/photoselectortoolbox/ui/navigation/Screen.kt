package com.photoselectortoolbox.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val icon: ImageVector,
    val label: String,
) {
    data object PhotoSelector : Screen(
        route = "selector",
        icon = Icons.Default.PhotoCamera,
        label = "Selector",
    )

    data object Statistics : Screen(
        route = "statistics",
        icon = Icons.Default.BarChart,
        label = "Statistics",
    )

    data object DuplicateFinder : Screen(
        route = "duplicates",
        icon = Icons.Default.ContentCopy,
        label = "Duplicates",
    )

    data object Settings : Screen(
        route = "settings",
        icon = Icons.Default.Settings,
        label = "Settings",
    )

    /** Phone-mode: simplified TikTok-style experience. Not shown in nav bars. */
    data object PhoneMode : Screen(
        route = "phone_mode",
        icon = Icons.Default.PhotoCamera,
        label = "Phone Mode",
    )

    companion object {
        /** Screens shown in the desktop/tablet navigation bars. */
        val all: List<Screen> = listOf(PhotoSelector, Statistics, DuplicateFinder, Settings)
    }
}
