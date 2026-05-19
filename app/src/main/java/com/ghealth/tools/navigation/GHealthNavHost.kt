package com.ghealth.tools.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ghealth.tools.feature.connection.ConnectionScreen
import com.ghealth.tools.feature.connection.TestConfigDialog
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
    val demoState by demoViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDestination?.let { dest ->
                    TopLevelRoute.entries.find { it.route == dest.route }?.label
                        ?: if (dest.route == "device_info") "设备信息" else "GHealth Tools"
                } ?: "GHealth Tools") },
                actions = {
                    if (isTopLevelRoute) {
                        val isRecording = demoState.isRecording
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (isRecording) Color(0xFFFF1744) else Color(0xFF666666),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRecording) "录制中" else "未录制",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isRecording) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { demoViewModel.toggleRecording() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = if (isRecording) "停止录制" else "开始录制",
                                tint = if (isRecording) Color(0xFFFF1744) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
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

    if (demoState.showRestartConfigDialog) {
        TestConfigDialog(
            deviceName = "已连接设备",
            onConfirm = { config -> demoViewModel.confirmRestartRecording(config) },
            onDismiss = { demoViewModel.cancelRestartRecording() },
            defaultName = demoState.testerName,
            defaultScenario = demoState.lastTestScenario,
            defaultRound = demoState.testRound
        )
    }
}
