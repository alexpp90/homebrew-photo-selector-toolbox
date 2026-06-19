package com.photoselectortoolbox.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.photoselectortoolbox.ui.duplicates.DuplicatesScreen
import com.photoselectortoolbox.ui.phonemode.PhoneModeScreen
import com.photoselectortoolbox.ui.selector.SelectorScreen
import com.photoselectortoolbox.ui.settings.SettingsScreen
import com.photoselectortoolbox.ui.statistics.StatisticsScreen
import com.photoselectortoolbox.ui.theme.Indigo500
import com.photoselectortoolbox.ui.theme.Zinc400
import com.photoselectortoolbox.ui.theme.Zinc800
import com.photoselectortoolbox.ui.theme.Zinc900
import com.photoselectortoolbox.ui.theme.Zinc950

@Composable
fun PhotoSelectorApp(windowSizeClass: WindowSizeClass) {
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val isLargeScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    // On compact devices, always use phone mode. No toggle.
    // On large devices, default to desktop mode, with toggle option.
    var forcePhoneMode by remember { mutableStateOf(false) }

    val usePhoneMode = isCompact || (isLargeScreen && forcePhoneMode)

    if (usePhoneMode) {
        // Phone mode: full-screen simplified experience
        Box(modifier = Modifier.fillMaxSize()) {
            PhoneModeScreen()

            // Mode toggle icon (only on large screens)
            if (isLargeScreen) {
                ModeToggleButton(
                    isPhoneMode = true,
                    onClick = { forcePhoneMode = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                )
            }
        }
    } else {
        // Desktop/tablet mode: full-featured with navigation
        DesktopModeApp(
            windowSizeClass = windowSizeClass,
            showModeToggle = isLargeScreen,
            onSwitchToPhoneMode = { forcePhoneMode = true },
        )
    }
}

@Composable
private fun DesktopModeApp(
    windowSizeClass: WindowSizeClass,
    showModeToggle: Boolean,
    onSwitchToPhoneMode: () -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val navigateToScreen: (Screen) -> Unit = { screen ->
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (useNavigationRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = Zinc950,
                contentColor = Zinc400,
            ) {
                Screen.all.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == screen.route
                    } == true

                    NavigationRailItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                            )
                        },
                        label = { Text(text = screen.label) },
                        selected = selected,
                        onClick = { navigateToScreen(screen) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Indigo500,
                            selectedTextColor = Indigo500,
                            unselectedIconColor = Zinc400,
                            unselectedTextColor = Zinc400,
                            indicatorColor = Zinc800,
                        ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            ) {
                AppNavHost(navController = navController, windowSizeClass = windowSizeClass)

                // Mode toggle
                if (showModeToggle) {
                    ModeToggleButton(
                        isPhoneMode = false,
                        onClick = onSwitchToPhoneMode,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp),
                    )
                }
            }
        }
    } else {
        Scaffold(
            containerColor = Zinc900,
            bottomBar = {
                NavigationBar(
                    containerColor = Zinc950,
                    contentColor = Zinc400,
                    tonalElevation = 0.dp,
                ) {
                    Screen.all.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label,
                                )
                            },
                            label = { Text(text = screen.label) },
                            selected = selected,
                            onClick = { navigateToScreen(screen) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Indigo500,
                                selectedTextColor = Indigo500,
                                unselectedIconColor = Zinc400,
                                unselectedTextColor = Zinc400,
                                indicatorColor = Zinc800,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                AppNavHost(navController = navController, windowSizeClass = windowSizeClass)
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.PhotoSelector.route,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Screen.PhotoSelector.route) {
            SelectorScreen(windowSizeClass = windowSizeClass)
        }
        composable(Screen.Statistics.route) {
            StatisticsScreen(windowSizeClass = windowSizeClass)
        }
        composable(Screen.DuplicateFinder.route) {
            DuplicatesScreen(windowSizeClass = windowSizeClass)
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

/**
 * Small floating button to switch between phone and desktop modes.
 * Only shown on large-screen Android devices.
 */
@Composable
private fun ModeToggleButton(
    isPhoneMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Zinc800.copy(alpha = 0.8f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPhoneMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
            contentDescription = if (isPhoneMode) "Switch to desktop mode" else "Switch to phone mode",
            tint = Zinc400,
            modifier = Modifier.size(18.dp),
        )
    }
}
