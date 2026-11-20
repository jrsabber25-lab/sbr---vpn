package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.UiOneShotEvent
import com.example.ui.VpnViewModel
import com.example.ui.navigation.SbrRoute
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LegalScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileAndAuthScreen
import com.example.ui.screens.ServerSelectionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.SupportScreen
import com.example.ui.theme.SbrOrange
import com.example.ui.theme.SbrVpnTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SbrVpnApplication
        val factory = VpnViewModel.Factory(
            repository = app.container.vpnRepository,
            vpnManager = app.container.vpnManager,
            billingManager = app.container.playBillingManager
        )

        setContent {
            val viewModel: VpnViewModel = viewModel(factory = factory)
            SbrVpnApp(viewModel = viewModel)
        }
    }
}

private data class TopLevelNavItem(
    val route: SbrRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun SbrVpnApp(viewModel: VpnViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connectAfterPermissionGranted()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.error_vpn_permission_denied))
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Tunnel still runs; ongoing notification displays if granted */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiOneShotEvent.RequestVpnPermission -> vpnPermissionLauncher.launch(event.intent)
                is UiOneShotEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val navItems = remember {
        listOf(
            TopLevelNavItem(SbrRoute.Home, "Home", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
            TopLevelNavItem(SbrRoute.Servers, "Servers", Icons.Rounded.Dns, Icons.Outlined.Dns, "nav_servers"),
            TopLevelNavItem(SbrRoute.Stats, "Stats", Icons.Rounded.BarChart, Icons.Outlined.BarChart, "nav_stats"),
            TopLevelNavItem(SbrRoute.Settings, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val showTopLevelChrome = currentDest?.let { dest ->
        dest.hasRoute<SbrRoute.Home>() ||
            dest.hasRoute<SbrRoute.Servers>() ||
            dest.hasRoute<SbrRoute.Stats>() ||
            dest.hasRoute<SbrRoute.Settings>()
    } ?: false

    fun navigateTopLevel(route: SbrRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    SbrVpnTheme(darkTheme = uiState.preferences.darkMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 700.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (showTopLevelChrome && !useNavRail) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDest.hasRoute(item.route::class)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SbrOrange,
                                        selectedTextColor = SbrOrange,
                                        indicatorColor = SbrOrange.copy(alpha = 0.16f)
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showTopLevelChrome && useNavRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            navItems.forEach { item ->
                                val selected = currentDest.hasRoute(item.route::class)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = SbrRoute.Splash
                        ) {
                            composable<SbrRoute.Splash> {
                                SplashScreen(
                                    onboardingCompleted = uiState.preferences.onboardingCompleted,
                                    onFinished = { goToHome ->
                                        val target = if (goToHome) SbrRoute.Home else SbrRoute.Onboarding
                                        navController.navigate(target) {
                                            popUpTo<SbrRoute.Splash> { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable<SbrRoute.Onboarding> {
                                OnboardingScreen(
                                    onFinishOnboarding = {
                                        viewModel.completeOnboarding()
                                        navController.navigate(SbrRoute.Home) {
                                            popUpTo<SbrRoute.Onboarding> { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable<SbrRoute.Home> {
                                HomeScreen(
                                    uiState = uiState,
                                    onToggleConnection = viewModel::onToggleVpnConnection,
                                    onOpenServers = { navigateTopLevel(SbrRoute.Servers) },
                                    onOpenConnectionDetails = { navController.navigate(SbrRoute.Connection) },
                                    onOpenStats = { navigateTopLevel(SbrRoute.Stats) },
                                    onOpenSettings = { navigateTopLevel(SbrRoute.Settings) },
                                    onOpenProfile = { navController.navigate(SbrRoute.Profile) },
                                    onRefreshPublicIp = viewModel::refreshServers,
                                    onConnectImported = viewModel::connectImportedProfile
                                )
                            }

                            composable<SbrRoute.Servers> {
                                ServerSelectionScreen(
                                    uiState = uiState,
                                    onSelectServer = { server ->
                                        viewModel.selectServer(server)
                                        navigateTopLevel(SbrRoute.Home)
                                    },
                                    onToggleFavorite = viewModel::toggleFavoriteServer,
                                    onAutoSelectFastest = viewModel::autoSelectFastest,
                                    onRefresh = viewModel::refreshServers,
                                    onOpenSettings = { navigateTopLevel(SbrRoute.Settings) }
                                )
                            }

                            composable<SbrRoute.Connection> {
                                ConnectionScreen(
                                    uiState = uiState,
                                    onReconnect = viewModel::reconnectVpn,
                                    onDisconnect = viewModel::disconnectVpn,
                                    onConnect = viewModel::onToggleVpnConnection,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SbrRoute.Stats> {
                                StatsScreen(uiState = uiState)
                            }

                            composable<SbrRoute.Profile> {
                                ProfileAndAuthScreen(
                                    uiState = uiState,
                                    onGuestContinue = viewModel::continueAsGuest,
                                    onLogin = viewModel::login,
                                    onRegister = viewModel::register,
                                    onRequestReset = viewModel::requestPasswordReset,
                                    onConfirmReset = viewModel::confirmPasswordReset,
                                    onLogout = viewModel::logout,
                                    onOpenSubscription = { navController.navigate(SbrRoute.Subscription) },
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SbrRoute.Subscription> {
                                SubscriptionScreen(
                                    uiState = uiState,
                                    onPurchasePlan = viewModel::launchPlaySubscription,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SbrRoute.Settings> {
                                SettingsScreen(
                                    uiState = uiState,
                                    onAutoConnectChange = viewModel::setAutoConnect,
                                    onKillSwitchChange = viewModel::setKillSwitch,
                                    onWifiChange = viewModel::setConnectOnWifi,
                                    onMobileChange = viewModel::setConnectOnMobileData,
                                    onProtocolChange = viewModel::setPreferredProtocol,
                                    onDarkModeChange = viewModel::setDarkMode,
                                    onLanguageChange = viewModel::setLanguageCode,
                                    onSaveApiBaseUrl = viewModel::updateApiBaseUrl,
                                    onImportWireGuardConf = viewModel::importWireGuardConfig,
                                    onClearImportedConf = viewModel::clearImportedWireGuardConfig,
                                    onOpenPrivacyPolicy = { navController.navigate(SbrRoute.Legal(isPrivacyPolicy = true)) },
                                    onOpenTerms = { navController.navigate(SbrRoute.Legal(isPrivacyPolicy = false)) },
                                    onOpenSupport = { navController.navigate(SbrRoute.Support) },
                                    onOpenSubscription = { navController.navigate(SbrRoute.Subscription) },
                                    onBack = { navigateTopLevel(SbrRoute.Home) }
                                )
                            }

                            composable<SbrRoute.Legal> { entry ->
                                val route = entry.toRoute<SbrRoute.Legal>()
                                LegalScreen(
                                    isPrivacyPolicy = route.isPrivacyPolicy,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable<SbrRoute.Support> {
                                SupportScreen(
                                    uiState = uiState,
                                    onSubmitTicket = viewModel::submitSupportTicket,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
