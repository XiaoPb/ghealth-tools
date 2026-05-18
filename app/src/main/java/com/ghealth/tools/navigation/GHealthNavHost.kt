package com.ghealth.tools.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ghealth.tools.feature.connection.ConnectionScreen
import com.ghealth.tools.feature.demo.DemoScreen
import com.ghealth.tools.feature.demo.DemoViewModel
import com.ghealth.tools.feature.login.LoginScreen
import com.ghealth.tools.feature.settings.DeviceInfoScreen
import com.ghealth.tools.feature.settings.SettingsScreen

enum class TopLevelRoute(val route: String, val label: String, val icon: ImageVector) {
    Connection("connection", "主界面", Icons.Default.Bluetooth),
    Demo("demo", "演示", Icons.Default.Insights),
    Settings("settings", "设置", Icons.Default.Settings),
}

@Composable
fun GHealthNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainScreen(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val demoViewModel: DemoViewModel = hiltViewModel()

    val isTopLevelRoute = currentDestination?.route in TopLevelRoute.entries.map { it.route }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDestination?.let { dest ->
                    TopLevelRoute.entries.find { it.route == dest.route }?.label
                        ?: if (dest.route == "device_info") "设备信息" else "GHealth Tools"
                } ?: "GHealth Tools") },
                actions = {
                    if (isTopLevelRoute) {
                        IconButton(onClick = onLogout) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "退出"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isTopLevelRoute) {
                NavigationBar {
                    TopLevelRoute.entries.forEach { route ->
                        NavigationBarItem(
                            icon = { Icon(route.icon, contentDescription = route.label) },
                            label = { Text(route.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == route.route } == true,
                            onClick = {
                                navController.navigate(route.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelRoute.Connection.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelRoute.Connection.route) { ConnectionScreen() }
            composable(TopLevelRoute.Demo.route) { DemoScreen(viewModel = demoViewModel) }
            composable(TopLevelRoute.Settings.route) {
                SettingsScreen(
                    onNavigateToDeviceinfo = {
                        navController.navigate("device_info")
                    }
                )
            }
            composable("device_info") {
                DeviceInfoScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
