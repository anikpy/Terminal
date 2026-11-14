package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.AppContainer
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HostsScreen
import com.example.ui.screens.KeysScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.EmeraldAccent
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SlateSurface
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.HostsViewModel
import com.example.ui.viewmodel.KeysViewModel
import com.example.ui.viewmodel.TerminalViewModel
import kotlinx.serialization.Serializable

@Serializable
object HostsRoute

@Serializable
object TerminalRoute

@Serializable
object KeysRoute

@Serializable
object HistoryRoute

private data class TopDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun EasyTerminalNavRoot(container: AppContainer) {
    val navController = rememberNavController()

    val hostsVm: HostsViewModel = viewModel(factory = HostsViewModel.factory(container))
    val terminalVm: TerminalViewModel = viewModel(factory = TerminalViewModel.factory(container))
    val keysVm: KeysViewModel = viewModel(factory = KeysViewModel.factory(container))
    val historyVm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))

    val hostsState by hostsVm.uiState.collectAsStateWithLifecycle()
    val terminalState by terminalVm.uiState.collectAsStateWithLifecycle()
    val keysState by keysVm.uiState.collectAsStateWithLifecycle()
    val historyState by historyVm.uiState.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val destinations = listOf(
        TopDestination(HostsRoute, "Hosts", Icons.Filled.Dns, Icons.Outlined.Dns, "nav_hosts"),
        TopDestination(TerminalRoute, "Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal, "nav_terminal"),
        TopDestination(KeysRoute, "Keys", Icons.Filled.Key, Icons.Outlined.Key, "nav_keys"),
        TopDestination(HistoryRoute, "History", Icons.Filled.History, Icons.Outlined.History, "nav_history")
    )

    fun navigateTop(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianCanvas,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = SlateSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTop(dest.route) },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        selected = selected,
                                        activeTabCount = terminalState.tabs.size
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyanAccent,
                                    selectedTextColor = CyanAccent,
                                    indicatorColor = CyanContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
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
                if (useRail) {
                    NavigationRail(
                        containerColor = SlateSurface
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTop(dest.route) },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        selected = selected,
                                        activeTabCount = terminalState.tabs.size
                                    )
                                },
                                label = { Text(dest.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CyanAccent,
                                    selectedTextColor = CyanAccent,
                                    indicatorColor = CyanContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = HostsRoute
                    ) {
                        composable<HostsRoute> {
                            HostsScreen(
                                uiState = hostsState,
                                onSearchChange = hostsVm::updateSearchQuery,
                                onConnectHost = { host ->
                                    hostsVm.connectToHost(host)
                                    navigateTop(TerminalRoute)
                                },
                                onSaveHost = { id, alias, hostname, port, user, auth, keyId, pw, keepPw, cmd, color ->
                                    hostsVm.saveHost(
                                        id = id,
                                        alias = alias,
                                        hostname = hostname,
                                        port = port,
                                        username = user,
                                        authType = auth,
                                        sshKeyId = keyId,
                                        plainPassword = pw,
                                        keepExistingPasswordIfBlank = keepPw,
                                        startupCommand = cmd,
                                        colorTagHex = color
                                    )
                                },
                                onDuplicateHost = hostsVm::duplicateHost,
                                onDeleteHost = hostsVm::deleteHost,
                                onQuickConnect = { hostname, port, user, auth, keyId, pw, save, alias ->
                                    hostsVm.quickConnect(hostname, port, user, auth, keyId, pw, save, alias)
                                    navigateTop(TerminalRoute)
                                },
                                onOpenTerminalTab = { navigateTop(TerminalRoute) }
                            )
                        }

                        composable<TerminalRoute> {
                            TerminalScreen(
                                uiState = terminalState,
                                onSelectTab = terminalVm::selectTab,
                                onCloseTab = terminalVm::closeTab,
                                onReconnectTab = terminalVm::reconnectTab,
                                onOpenHostInNewTab = terminalVm::openHostInNewTab,
                                onGoToHosts = { navigateTop(HostsRoute) },
                                onResizePty = terminalVm::resizeTab,
                                onTypedText = terminalVm::sendTypedText,
                                onSendRawBytes = terminalVm::sendRawBytes,
                                onSendRawText = terminalVm::sendRawText,
                                onArrowKey = terminalVm::sendArrowKey,
                                onToggleCtrl = terminalVm::toggleCtrl,
                                onToggleAlt = terminalVm::toggleAlt,
                                onExecuteSnippet = terminalVm::executeSnippet,
                                onSaveSnippet = { title, command, category, execNow ->
                                    terminalVm.saveSnippet(0, title, command, category, execNow)
                                },
                                onDeleteSnippet = terminalVm::deleteSnippet,
                                onResolveHostTrust = terminalVm::resolveHostTrust,
                                onResolveAuthPrompt = terminalVm::resolveAuthPrompt,
                                onAdjustFontSize = terminalVm::adjustFontSize,
                                onClearScreen = terminalVm::clearActiveTerminalScreen
                            )
                        }

                        composable<KeysRoute> {
                            KeysScreen(
                                uiState = keysState,
                                onGenerateKey = keysVm::generateKey,
                                onImportKey = keysVm::importKey,
                                onDeleteKey = keysVm::deleteKey,
                                onClearMessages = keysVm::clearMessages
                            )
                        }

                        composable<HistoryRoute> {
                            HistoryScreen(
                                uiState = historyState,
                                onFilterChange = historyVm::updateFilter,
                                onReconnect = { entry ->
                                    historyVm.reconnectFromHistory(entry)
                                    navigateTop(TerminalRoute)
                                },
                                onDeleteEntry = historyVm::deleteEntry,
                                onClearHistory = historyVm::clearHistory,
                                onRemoveKnownHost = historyVm::removeKnownHost,
                                onClearKnownHosts = historyVm::clearKnownHosts,
                                onSetFontSize = historyVm::setFontSize,
                                onSetShowExtraKeybar = historyVm::setShowExtraKeybar,
                                onSetCursorBlink = historyVm::setCursorBlink,
                                onSetKeepScreenOn = historyVm::setKeepScreenOn
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    dest: TopDestination,
    selected: Boolean,
    activeTabCount: Int
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    if (dest.route is TerminalRoute && activeTabCount > 0) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = EmeraldAccent,
                    contentColor = ObsidianCanvas
                ) {
                    Text(activeTabCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = dest.label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = dest.label)
    }
}
