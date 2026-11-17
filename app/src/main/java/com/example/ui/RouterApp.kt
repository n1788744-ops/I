package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import kotlinx.coroutines.launch

enum class RouterTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    ROUTER(
        route = "router_dashboard",
        labelRes = R.string.nav_router,
        selectedIcon = Icons.Rounded.Router,
        unselectedIcon = Icons.Outlined.Router,
        testTag = "nav_tab_router",
    ),
    SIM_SIGNAL(
        route = "sim_signal",
        labelRes = R.string.nav_sim_signal,
        selectedIcon = Icons.Rounded.CellTower,
        unselectedIcon = Icons.Outlined.CellTower,
        testTag = "nav_tab_sim",
    ),
    NETWORK_CONFIG(
        route = "clients_config",
        labelRes = R.string.nav_clients_config,
        selectedIcon = Icons.Rounded.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_tab_network",
    ),
}

@Composable
fun RouterApp(
    viewModel: RouterViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableStateOf(RouterTab.ROUTER) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermissionsAndTelemetry()
    }

    BackHandler(enabled = currentTab != RouterTab.ROUTER) {
        currentTab = RouterTab.ROUTER
    }

    fun copyToClipboard(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
        scope.launch {
            snackbarHostState.showSnackbar("$label скопирован: $value")
        }
    }

    fun requestPermissions() {
        permissionLauncher.launch(viewModel.requiredRuntimePermissions())
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        RouterTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        RouterTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)

                    when (currentTab) {
                        RouterTab.ROUTER -> RouterDashboardScreen(
                            state = state,
                            onSelectMode = viewModel::selectBroadcastMode,
                            onToggleBroadcast = {
                                if (!state.permissions.nearbyWifiGranted) {
                                    requestPermissions()
                                } else {
                                    viewModel.toggleRouterBroadcast()
                                }
                            },
                            onOpenSystemHotspotSettings = viewModel::openSystemHotspotSettings,
                            onCopyText = ::copyToClipboard,
                            onRequestPermissions = ::requestPermissions,
                            modifier = contentModifier,
                        )

                        RouterTab.SIM_SIGNAL -> SimSignalScreen(
                            state = state,
                            onTogglePinCellular = viewModel::setPinCellular,
                            onToggleWifiLock = viewModel::setWifiLock,
                            onToggleWakeLock = viewModel::setWakeLock,
                            onRunLatencyCheck = viewModel::runLatencyCheck,
                            onOpenMobileSettings = viewModel::openMobileNetworkSettings,
                            modifier = contentModifier,
                        )

                        RouterTab.NETWORK_CONFIG -> ClientsConfigScreen(
                            state = state,
                            onSaveProfile = { ssid, pass, band, port ->
                                viewModel.saveProfile(ssid, pass, band, port)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Профиль $ssid (WPA2-PSK) сохранён")
                                }
                            },
                            onResetDefaults = {
                                viewModel.resetProfileToDefaults()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Восстановлен профиль MyHotspot / sw22vmi55xr11")
                                }
                            },
                            onRefreshClients = viewModel::refreshPermissionsAndTelemetry,
                            onRequestPermissions = ::requestPermissions,
                            modifier = contentModifier,
                        )
                    }
                }
            }
        }
    }
}
