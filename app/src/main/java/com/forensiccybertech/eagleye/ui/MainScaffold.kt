package com.forensiccybertech.eagleye.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forensiccybertech.eagleye.EagleEyeApp
import com.forensiccybertech.eagleye.data.Alert
import com.forensiccybertech.eagleye.ui.alerts.AlertDetail
import com.forensiccybertech.eagleye.ui.alerts.AlertsTab
import com.forensiccybertech.eagleye.ui.components.AppHeader
import com.forensiccybertech.eagleye.ui.components.OverflowIcon
import com.forensiccybertech.eagleye.ui.dashboard.DashboardTab
import com.forensiccybertech.eagleye.ui.hosts.HostsTab
import com.forensiccybertech.eagleye.ui.theme.Eagle
import com.forensiccybertech.eagleye.ui.theme.ThemeMode
import com.forensiccybertech.eagleye.ui.vulns.VulnsTab
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val subtitle: String, val icon: ImageVector) {
    Dashboard("Dashboard", "Soc Dashboard", Icons.Filled.Dashboard),
    Alerts("Alerts", "Threat Alerts", Icons.Filled.NotificationsActive),
    Devices("Devices", "Managed Devices", Icons.Filled.Computer),
    Vulns("Vulns", "Vulnerabilities", Icons.Filled.BugReport),
}

@Composable
fun MainScaffold(app: EagleEyeApp) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Alert?>(null) }
    var menu by remember { mutableStateOf(false) }
    val theme by app.theme.mode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = selected != null) { selected = null }
    val e = Eagle.colors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                subtitle = if (selected != null) "Forensic Telemetry" else Tab.entries[tab].subtitle,
                onBack = if (selected != null) ({ selected = null }) else null,
            ) {
                Box {
                    OverflowIcon { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        Text("Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        ThemeMode.entries.forEach { m ->
                            DropdownMenuItem(text = { Text((if (m == theme) "● " else "○ ") + m.name) }, onClick = { app.theme.set(m); menu = false })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menu = false; scope.launch { app.auth.logout() } })
                    }
                }
            }
        },
        bottomBar = {
            if (selected == null) Column {
                HorizontalDivider(color = e.border)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            tab == i, { tab = i }, { Icon(t.icon, t.label) }, label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = e.glow, selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val sel = selected
            when {
                sel != null -> AlertDetail(sel)
                tab == 0 -> DashboardTab(app, onOpenAlert = { selected = it }, onSeeAllAlerts = { tab = 1 })
                tab == 1 -> AlertsTab(app, onOpen = { selected = it })
                tab == 2 -> HostsTab(app)
                else -> VulnsTab(app)
            }
        }
    }
}
