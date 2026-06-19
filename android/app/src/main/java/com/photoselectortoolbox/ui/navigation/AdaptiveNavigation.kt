package com.photoselectortoolbox.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.photoselectortoolbox.ui.duplicates.DuplicatesScreen
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
