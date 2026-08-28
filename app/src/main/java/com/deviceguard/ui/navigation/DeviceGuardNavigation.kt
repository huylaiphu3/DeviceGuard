package com.deviceguard.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.deviceguard.ui.screen.AppsScreen
import com.deviceguard.ui.screen.DashboardScreen
import com.deviceguard.ui.screen.RatDetectorScreen
import com.deviceguard.ui.screen.RecoveryScreen
import com.deviceguard.ui.screen.SettingsScreen
import com.deviceguard.ui.screen.UsageScreen
import com.deviceguard.ui.viewmodel.AppsViewModel
import com.deviceguard.ui.viewmodel.DeviceGuardViewModels
import com.deviceguard.ui.viewmodel.OverviewViewModel
import com.deviceguard.ui.viewmodel.RatViewModel
import com.deviceguard.ui.viewmodel.RecoveryViewModel
import com.deviceguard.ui.viewmodel.SettingsViewModel

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", "Tổng quan", Icons.Default.Dashboard),
    USAGE("usage", "Sử dụng", Icons.Default.Timeline),
    APPS("apps", "Ứng dụng", Icons.Default.Apps),
    THREATS("threats", "Rà soát", Icons.Default.Security),
    RECOVERY("recovery", "Phục hồi", Icons.Default.Restore),
    SETTINGS("settings", "Cài đặt", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceGuardNavigation() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        topBar = {
            val title = Destination.entries
                .firstOrNull { destination ->
                    currentDestination?.hierarchy?.any { it.route == destination.route } == true
                }?.label ?: "DeviceGuard"
            TopAppBar(title = { Text("DeviceGuard — $title") })
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.DASHBOARD.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.DASHBOARD.route) {
                DashboardScreen(
                    viewModel<OverviewViewModel>(factory = DeviceGuardViewModels.Factory)
                )
            }
            composable(Destination.USAGE.route) {
                UsageScreen(
                    viewModel<OverviewViewModel>(factory = DeviceGuardViewModels.Factory)
                )
            }
            composable(Destination.APPS.route) {
                AppsScreen(viewModel<AppsViewModel>(factory = DeviceGuardViewModels.Factory))
            }
            composable(Destination.THREATS.route) {
                RatDetectorScreen(
                    viewModel<RatViewModel>(factory = DeviceGuardViewModels.Factory)
                )
            }
            composable(Destination.RECOVERY.route) {
                RecoveryScreen(
                    viewModel<RecoveryViewModel>(factory = DeviceGuardViewModels.Factory)
                )
            }
            composable(Destination.SETTINGS.route) {
                SettingsScreen(
                    viewModel<SettingsViewModel>(factory = DeviceGuardViewModels.Factory)
                )
            }
        }
    }
}
