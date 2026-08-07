package com.ghealth.tools.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.app.Activity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ghealth.tools.core.ui.adaptive.shouldUseLandscapeLayout
import com.ghealth.tools.feature.connection.ConnectionScreen
import com.ghealth.tools.feature.connection.ConnectionViewModel
import com.ghealth.tools.feature.connection.TestConfigDialog
import com.ghealth.tools.feature.demo.DemoScreen
import com.ghealth.tools.feature.factory.FactoryScreen
import com.ghealth.tools.feature.demo.DemoUiState
import com.ghealth.tools.feature.demo.DemoViewModel
import com.ghealth.tools.feature.login.ProjectConfigUploadScreen
import com.ghealth.tools.feature.login.ChipSelectionScreen
import com.ghealth.tools.feature.login.LoginScreen
import com.ghealth.tools.feature.login.ProjectManageScreen
import com.ghealth.tools.feature.login.ProjectEditScreen
import com.ghealth.tools.feature.login.CsvFileListScreen
import com.ghealth.tools.feature.login.ProjectSelectionScreen
import com.ghealth.tools.feature.login.ProjectCreateScreen
import com.ghealth.tools.feature.login.RegisterScreen
import com.ghealth.tools.feature.settings.DeviceInfoScreen
import com.ghealth.tools.feature.settings.FeedbackScreen
import com.ghealth.tools.feature.settings.SettingsScreen
import com.ghealth.tools.ble.connection.DeviceRole
import com.ghealth.tools.core.model.ConnectionState
import com.ghealth.tools.feature.ota.ConnectedDeviceInfo
import com.ghealth.tools.feature.ota.OtaScreen
import com.ghealth.tools.feature.ota.OtaTopBarMenu
import com.ghealth.tools.feature.ota.OtaViewModel
import com.ghealth.tools.core.ui.component.GHealthTopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

enum class TopLevelRoute(val route: String, val label: String, val icon: ImageVector) {
    Connection("connection", "主界面", Icons.Default.Bluetooth),
    Demo("demo", "演示", Icons.Default.Insights),
    Settings("settings", "设置", Icons.Default.Settings),
}

@Composable
fun GHealthNavHost() {
    val navController = rememberNavController()
    val loginViewModel: com.ghealth.tools.feature.login.LoginViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = Routes.LOGIN) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.PROJECT_SELECTION) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onOfflineMode = {
                    navController.navigate(Routes.CHIP_SELECTION)
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                }
            )
        }
        composable(Routes.CHIP_SELECTION) {
            ChipSelectionScreen(
                onChipSelected = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(Routes.PROJECT_SELECTION) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PROJECT_SELECTION) {
            val scope = rememberCoroutineScope()
            ProjectSelectionScreen(
                onProjectSelected = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.PROJECT_SELECTION) { inclusive = true }
                    }
                },
                onCreateProject = {
                    navController.navigate(Routes.PROJECT_CREATE)
                },
                onLogout = {
                    scope.launch {
                        loginViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onManageProjects = {
                    navController.navigate(Routes.PROJECT_MANAGE)
                }
            )
        }
        composable(Routes.PROJECT_CREATE) {
            ProjectCreateScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onProjectCreated = { projectId, projectName ->
                    navController.navigate(Routes.configUpload(projectId, projectName)) {
                        popUpTo(Routes.PROJECT_CREATE) { inclusive = true }
                    }
                }
            )
        }
        composable(
            Routes.CONFIG_UPLOAD,
            arguments = listOf(
                navArgument("projectId") { type = NavType.IntType },
                navArgument("projectName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getInt("projectId") ?: 0
            val projectName = backStackEntry.arguments?.getString("projectName") ?: ""
            ProjectConfigUploadScreen(
                projectId = projectId,
                projectName = projectName,
                onNavigateBack = { navController.popBackStack() },
                onUploadComplete = {
                    navController.navigate(Routes.PROJECT_SELECTION) {
                        popUpTo(Routes.CONFIG_UPLOAD) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PROJECT_MANAGE) {
            ProjectManageScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditProject = { id, _ ->
                    navController.navigate(Routes.projectEdit(id))
                },
                onViewCsvFiles = { id, name ->
                    navController.navigate(Routes.csvFileList(id, name))
                },
                onUploadProdConfig = { projectId, projectName ->
                    navController.navigate(Routes.configUpload(projectId, projectName))
                }
            )
        }
        composable(
            Routes.PROJECT_EDIT,
            arguments = listOf(
                navArgument("projectId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getInt("projectId") ?: 0
            ProjectEditScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable(
            Routes.CSV_FILE_LIST,
            arguments = listOf(
                navArgument("projectId") { type = NavType.IntType },
                navArgument("projectName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getInt("projectId") ?: 0
            val projectName = backStackEntry.arguments?.getString("projectName") ?: ""
            CsvFileListScreen(
                projectId = projectId,
                projectName = projectName,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.MAIN) {
            val outerNavController = navController
            val scope = rememberCoroutineScope()
            MainScreen(
                onLogout = {
                    scope.launch {
                        loginViewModel.logout()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onSwitchProject = {
                    outerNavController.navigate(Routes.PROJECT_SELECTION) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToProjectManage = {
                    outerNavController.navigate(Routes.PROJECT_MANAGE)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onSwitchProject: () -> Unit = {},
    onNavigateToProjectManage: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val demoViewModel: DemoViewModel = hiltViewModel()
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val connectionState by connectionViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val windowSizeClass = calculateWindowSizeClass(context as Activity)

    val isTopLevelRoute = currentDestination?.route in TopLevelRoute.entries.map { it.route }
    val isWide = windowSizeClass.shouldUseLandscapeLayout && isTopLevelRoute
    val demoState by demoViewModel.uiState.collectAsState()
    var otaViewModel by remember { mutableStateOf<OtaViewModel?>(null) }

    if (isWide) {
        WideMainLayout(navController, currentDestination, demoViewModel, demoState, connectionViewModel, connectionState, otaViewModel, { otaViewModel = it }, onLogout, onSwitchProject, onNavigateToProjectManage)
    } else {
        CompactMainLayout(navController, currentDestination, isTopLevelRoute, demoViewModel, demoState, connectionViewModel, connectionState, otaViewModel, { otaViewModel = it }, onLogout, onSwitchProject, onNavigateToProjectManage)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WideMainLayout(
    navController: NavHostController,
    currentDestination: NavDestination?,
    demoViewModel: DemoViewModel,
    demoState: DemoUiState,
    connectionViewModel: ConnectionViewModel,
    connectionState: com.ghealth.tools.feature.connection.ConnectionUiState,
    otaViewModel: OtaViewModel?,
    onOtaViewModelChange: (OtaViewModel?) -> Unit,
    onLogout: () -> Unit,
    onSwitchProject: () -> Unit,
    onNavigateToProjectManage: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            header = {
                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "退出"
                    )
                }
            }
        ) {
            TopLevelRoute.entries.forEach { route ->
                NavigationRailItem(
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
                    },
                )
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            topBar = {
                when (currentDestination?.route) {
                    Routes.Main.DEVICE_INFO -> {
                        GHealthTopAppBar(
                            title = "设备信息",
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        )
                    }
                    Routes.Main.FACTORY -> {
                        GHealthTopAppBar(
                            title = "产测",
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        )
                    }
                    Routes.Main.OTA -> {
                        GHealthTopAppBar(
                            title = "OTA固件升级",
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            },
                            actions = {
                                otaViewModel?.let { OtaTopBarMenu(it) }
                            }
                        )
                    }

                    Routes.Main.FEEDBACK -> {
                        GHealthTopAppBar(
                            title = "反馈和建议",
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        )
                    }
                    else -> {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(currentDestination?.let { dest ->
                                        TopLevelRoute.entries.find { it.route == dest.route }?.label
                                            ?: "GHealth Tools"
                                    } ?: "GHealth Tools")
                                    Text(
                                        text = "芯片: ${demoState.chipType.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            actions = {
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
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TopLevelRoute.Connection.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(TopLevelRoute.Connection.route) {
                    ConnectionScreen(
                        onFactoryTest = { navController.navigate(Routes.Main.FACTORY) },
                        onOtaUpgrade = { navController.navigate(Routes.Main.OTA) },
                    )
                }
                composable(TopLevelRoute.Demo.route) { DemoScreen(viewModel = demoViewModel) }
                composable(TopLevelRoute.Settings.route) {
                    SettingsScreen(
                        onNavigateToDeviceinfo = {
                            navController.navigate(Routes.Main.DEVICE_INFO)
                        },
                        onNavigateToFeedback = {
                            navController.navigate(Routes.Main.FEEDBACK)
                        },
                        onSwitchProject = onSwitchProject,
                        onNavigateToProjectManage = onNavigateToProjectManage
                    )
                }
                composable(Routes.Main.DEVICE_INFO) {
                    DeviceInfoScreen(
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(Routes.Main.FACTORY) {
                    FactoryScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.Main.OTA) {
                    val viewModel: OtaViewModel = hiltViewModel()
                    DisposableEffect(viewModel) {
                        onOtaViewModelChange(viewModel)
                        onDispose { onOtaViewModelChange(null) }
                    }
                    LaunchedEffect(connectionState.connectedDevices) {
                        val deviceInfos = connectionState.connectedDevices.values
                            .filter { it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED }
                            .map { device ->
                                ConnectedDeviceInfo(
                                    address = device.address,
                                    name = device.name ?: device.address,
                                    role = device.role,
                                )
                            }
                        viewModel.loadAvailableDevices(deviceInfos)
                    }
                    OtaScreen(
                        onNavigateBack = { navController.popBackStack() },
                        viewModel = viewModel,
                    )
                }
                composable(Routes.Main.FEEDBACK) {
                    FeedbackScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactMainLayout(
    navController: NavHostController,
    currentDestination: NavDestination?,
    isTopLevelRoute: Boolean,
    demoViewModel: DemoViewModel,
    demoState: DemoUiState,
    connectionViewModel: ConnectionViewModel,
    connectionState: com.ghealth.tools.feature.connection.ConnectionUiState,
    otaViewModel: OtaViewModel?,
    onOtaViewModelChange: (OtaViewModel?) -> Unit,
    onLogout: () -> Unit,
    onSwitchProject: () -> Unit,
    onNavigateToProjectManage: () -> Unit
) {
    Scaffold(
        topBar = {
            when (currentDestination?.route) {
                Routes.Main.DEVICE_INFO -> {
                    GHealthTopAppBar(
                        title = "设备信息",
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
                Routes.Main.FACTORY -> {
                    GHealthTopAppBar(
                        title = "产测",
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
                Routes.Main.OTA -> {
                    GHealthTopAppBar(
                        title = "OTA固件升级",
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            otaViewModel?.let { OtaTopBarMenu(it) }
                        }
                    )
                }

                Routes.Main.FEEDBACK -> {
                    GHealthTopAppBar(
                        title = "反馈和建议",
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
                else -> {
                    TopAppBar(
                        title = {
                            Column {
                                Text(currentDestination?.let { dest ->
                                    TopLevelRoute.entries.find { it.route == dest.route }?.label
                                        ?: "GHealth Tools"
                                } ?: "GHealth Tools")
                                Text(
                                    text = "芯片: ${demoState.chipType.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
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
                }
            }
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
            composable(TopLevelRoute.Connection.route) {
                ConnectionScreen(
                    onFactoryTest = { navController.navigate(Routes.Main.FACTORY) },
                    onOtaUpgrade = { navController.navigate(Routes.Main.OTA) },
                )
            }
            composable(TopLevelRoute.Demo.route) { DemoScreen(viewModel = demoViewModel) }
            composable(TopLevelRoute.Settings.route) {
                SettingsScreen(
                    onNavigateToDeviceinfo = {
                        navController.navigate(Routes.Main.DEVICE_INFO)
                    },
                    onNavigateToFeedback = {
                        navController.navigate(Routes.Main.FEEDBACK)
                    },
                    onSwitchProject = onSwitchProject,
                    onNavigateToProjectManage = onNavigateToProjectManage
                )
            }
            composable(Routes.Main.DEVICE_INFO) {
                DeviceInfoScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.Main.FACTORY) {
                FactoryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Routes.Main.OTA) {
                val viewModel: OtaViewModel = hiltViewModel()
                DisposableEffect(viewModel) {
                    onOtaViewModelChange(viewModel)
                    onDispose { onOtaViewModelChange(null) }
                }
                LaunchedEffect(connectionState.connectedDevices) {
                    val deviceInfos = connectionState.connectedDevices.values
                        .filter { it.role == DeviceRole.MASTER && it.state == ConnectionState.CONNECTED }
                        .map { device ->
                            ConnectedDeviceInfo(
                                address = device.address,
                                name = device.name ?: device.address,
                                role = device.role,
                            )
                        }
                    viewModel.loadAvailableDevices(deviceInfos)
                }
                OtaScreen(
                    onNavigateBack = { navController.popBackStack() },
                    viewModel = viewModel,
                )
            }
            composable(Routes.Main.FEEDBACK) {
                FeedbackScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
